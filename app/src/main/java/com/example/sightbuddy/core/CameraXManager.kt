package com.example.sightbuddy.core

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    private var preview: androidx.camera.core.Preview? = null
    private val minAnalysisResolution = Size(640, 640)

    fun startCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: androidx.camera.core.Preview.SurfaceProvider) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            preview = androidx.camera.core.Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(surfaceProvider)
                }

            imageAnalyzer = ImageAnalysis.Builder()
                // Keep model input quality high enough to avoid under-resolving detections.
                .setTargetResolution(minAnalysisResolution)
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

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                Log.i("CameraXManager", "Camera bound successfully")
            } catch (exc: Exception) {
                Log.e("CameraXManager", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    fun shutdown() {
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
}
