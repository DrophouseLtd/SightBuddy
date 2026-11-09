package com.example.sightbuddy.core

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.Preview

class CameraXManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Conflated channel keeps only the latest frame.
    // Any overwritten (undelivered) frame is explicitly closed to avoid leaks/jams.
    private val frameChannel = Channel<ImageProxy>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { proxy ->
            droppedFrames.incrementAndGet()
            closedFrames.incrementAndGet()
            runCatching { proxy.close() }
        }
    )
    val frameFlow = frameChannel.receiveAsFlow()

    private val emittedFrames = AtomicLong(0)
    private val droppedFrames = AtomicLong(0)
    private val closedFrames = AtomicLong(0)
    private val consumedFrames = AtomicLong(0)
    private var lastStatsLogTime = 0L

    // Used for battery saving: throttle the frame rate out of latency-critical modes
    private var lastFrameTime = 0L
    var throttleIntervalMs: Long = 0L // 0 means no throttling

    private var preview: Preview? = null
    private var analysisResolution = DEFAULT_ANALYSIS_SIZE
    private var stillsWanted = false
    private var imageCapture: ImageCapture? = null
    /** True while the camera is being bound; [awaitBound] waits for it. */
    private val binding = MutableStateFlow(false)
    private var boundOwner: LifecycleOwner? = null
    private var boundSurfaceProvider: Preview.SurfaceProvider? = null

    /**
     * The size analysis frames are asked for. Text needs far more pixels than
     * the object detector: at 640 a page's body text is under 16 px a line and
     * comes and goes from frame to frame. With [stills], full-size photos can
     * be taken too ([takeStill]); the camera then works harder and runs warm,
     * so photos are bound only while one is coming. A change rebinds a
     * running camera.
     */
    fun setAnalysisResolution(size: Size, stills: Boolean = false) {
        if (size == analysisResolution && stills == stillsWanted) return
        analysisResolution = size
        stillsWanted = stills
        val owner = boundOwner ?: return
        val surface = boundSurfaceProvider ?: return
        if (camera != null) startCamera(owner, surface)
    }

    // Torch. Held in memory only — it must never survive the app being closed,
    // so there is deliberately no persisted preference behind it.
    private var camera: Camera? = null
    private var torchRequested = false

    /**
     * True when the device has a flash unit at all. Queried from [CameraManager]
     * rather than the bound camera, because the camera is deliberately unbound
     * while Settings and other overlays are open — where the torch control lives.
     */
    fun deviceHasFlash(): Boolean = runCatching {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        manager.cameraIdList.any { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrDefault(false)

    /** Whether the user currently wants the torch on. */
    fun isTorchRequested(): Boolean = torchRequested

    /** Forget the torch entirely — used when the app leaves the foreground. */
    fun clearTorchRequest() {
        torchRequested = false
    }

    /**
     * Turn the torch on or off. Remembered across a rebind (e.g. returning to the
     * app) only for as long as the process lives; [stopCamera] clears it.
     */
    fun setTorch(enabled: Boolean) {
        torchRequested = enabled
        val cam = camera ?: return
        if (cam.cameraInfo.hasFlashUnit()) {
            runCatching { cam.cameraControl.enableTorch(enabled) }
                .onFailure { Log.w("CameraXManager", "enableTorch($enabled) failed", it) }
        }
    }

    fun startCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        boundOwner = lifecycleOwner
        boundSurfaceProvider = surfaceProvider
        binding.value = true
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(surfaceProvider)
                }

            imageAnalyzer = ImageAnalysis.Builder()
                // Keep model input quality high enough to avoid under-resolving detections.
                .setTargetResolution(analysisResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastFrameTime >= throttleIntervalMs) {
                            lastFrameTime = currentTime
                            val result = frameChannel.trySend(imageProxy)
                            if (result.isSuccess) {
                                emittedFrames.incrementAndGet()
                            } else {
                                droppedFrames.incrementAndGet()
                                closedFrames.incrementAndGet()
                                imageProxy.close()
                            }
                            maybeLogFrameStats(currentTime)
                        } else {
                            // Drop frame to save battery
                            droppedFrames.incrementAndGet()
                            closedFrames.incrementAndGet()
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // The same 4:3 view as the analysis frames, so a page's corners found
            // in a frame fall on the photo too.
            imageCapture = if (stillsWanted) {
                ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(STILL_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                            )
                            .build(),
                    )
                    .setJpegQuality(STILL_JPEG_QUALITY)
                    .build()
            } else {
                null
            }

            try {
                cameraProvider?.unbindAll()
                camera = try {
                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        *listOfNotNull(preview, imageAnalyzer, imageCapture).toTypedArray(),
                    )
                } catch (exc: Exception) {
                    // A camera that cannot add photos to the other two: frames only.
                    if (imageCapture == null) throw exc
                    Log.w("CameraXManager", "No photos alongside the frames; captures use a frame", exc)
                    imageCapture = null
                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalyzer)
                }
                // Re-apply the requested torch state to the freshly bound camera.
                setTorch(torchRequested)
                Log.i(
                    "CameraXManager",
                    "Camera bound successfully: frames ${imageAnalyzer?.resolutionInfo?.resolution}, " +
                        "photos ${imageCapture?.resolutionInfo?.resolution ?: "none"}",
                )
            } catch (exc: Exception) {
                Log.e("CameraXManager", "Use case binding failed", exc)
            }
            binding.value = false

        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Waits up to [timeoutMs] for a bind under way to finish, so a photo asked
     * for next finds its stream bound. At once when nothing is being bound.
     */
    suspend fun awaitBound(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { binding.first { !it } }
    }

    /**
     * Unbinds the camera, extinguishing the torch with it. The user's request is
     * deliberately kept: Settings and other overlays unbind the camera by design,
     * and the light must come back when the camera does. Leaving the app clears
     * the request separately via [clearTorchRequest], so the torch can never be
     * left burning in the background.
     */
    /**
     * Binds again to the view the preview last used, after [stopCamera]. The
     * preview is started from the composable that holds the view, which does not
     * run again on its own: without this, the camera stayed dark after any
     * pop-up (a model download, Settings) until something rebuilt that view.
     */
    fun restartPreview() {
        if (camera != null) return
        val owner = boundOwner ?: return
        val surface = boundSurfaceProvider ?: return
        startCamera(owner, surface)
    }

    /**
     * A full-size photo, upright by its rotation, or null when photos are not
     * bound or the camera fails; the caller closes it. Asked for when a frame
     * has too few pixels a letter for reading a page.
     */
    suspend fun takeStill(): ImageProxy? {
        val capture = imageCapture ?: return null
        return suspendCancellableCoroutine { cont ->
            capture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        // Given up on meanwhile (its time ran out): nobody will close it.
                        cont.resume(image) { image.close() }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.w("CameraXManager", "Photo failed; the capture uses a frame", exception)
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }
    }

    fun stopCamera() {
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                runCatching { cam.cameraControl.enableTorch(false) }
            }
        }
        camera = null
        cameraProvider?.unbindAll()
    }

    fun shutdown() {
        stopCamera()
        frameChannel.close()
        cameraExecutor.shutdown()
    }

    fun markFrameConsumed() {
        consumedFrames.incrementAndGet()
    }

    fun markFrameClosed() {
        closedFrames.incrementAndGet()
    }

    private fun maybeLogFrameStats(now: Long) {
        if (now - lastStatsLogTime < 5000L) return
        lastStatsLogTime = now
        Log.d(
            "CameraXManager",
            "Frame stats emitted=${emittedFrames.get()} consumed=${consumedFrames.get()} dropped=${droppedFrames.get()} closed=${closedFrames.get()} throttleMs=$throttleIntervalMs"
        )
    }

    companion object {
        /** Portrait width x height; the camera picks the nearest size it has. */
        val DEFAULT_ANALYSIS_SIZE = Size(640, 640)
        val TEXT_ANALYSIS_SIZE = Size(1080, 1440)

        /** The photo's size at most, in the sensor's landscape terms: 12 MP. */
        val STILL_SIZE = Size(4032, 3024)
        const val STILL_JPEG_QUALITY = 95
    }
}
