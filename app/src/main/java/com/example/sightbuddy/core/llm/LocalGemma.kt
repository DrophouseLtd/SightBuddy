package com.example.sightbuddy.core.llm

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.withLock
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.Color
import com.example.sightbuddy.core.ModelSetup
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * On-device Gemma through LiteRT-LM.
 *
 * Spike: the model file is side-loaded, not downloaded. Push it to
 * `Android/data/<app id>/files/llm/` and it loads at start-up. Nothing here
 * touches the network.
 *
 * One engine, one request at a time: LiteRT-LM runs a single decode, and two
 * features asking at once would only slow both down.
 */
class LocalGemma(context: Context) : LocalAnswerer {

    private val modelDir = File(context.getExternalFilesDir(null), "llm")
    private val cacheDir = context.cacheDir.absolutePath

    /** The multimodal model (text, image, audio), or null when not on the device. */
    val modelFile: File? get() = File(modelDir, E2B_FILE).takeIf { it.isFile }

    private val _installed = MutableStateFlow(modelFile != null)

    /** On the device, loaded or not. */
    val installedState = _installed.asStateFlow()
    val installed: Boolean get() = _installed.value

    fun sizeOnDisk(): Long = modelFile?.length() ?: 0L

    private val _downloadPercent = MutableStateFlow<Int?>(null)

    private val partFile get() = File(modelDir, "$E2B_FILE.part")

    /** How far an interrupted download got, 0 when none; a new download resumes from it. */
    fun partialPercent(): Int = (partFile.length() * 100 / E2B_SIZE).toInt().coerceIn(0, 99)

    /** Progress of a download in progress, or null. */
    val downloadPercent = _downloadPercent.asStateFlow()

    /**
     * Downloads the model from Hugging Face (litert-community, Apache 2.0, no
     * login), pinned to one revision and checked by size. Resumes a partial
     * download. Single-flight: a second call while one runs returns false.
     */
    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        if (installed) return@withContext true
        if (!_downloadPercent.compareAndSet(null, partialPercent())) return@withContext false
        try {
            // A dropped connection is normal over 2.6 GB on a phone: retry, resuming
            // where it stopped, before giving up.
            for (attempt in 1..DOWNLOAD_ATTEMPTS) {
                if (downloadOnce()) return@withContext true
                if (attempt < DOWNLOAD_ATTEMPTS) kotlinx.coroutines.delay(attempt * 5_000L)
            }
            false
        } finally {
            _downloadPercent.value = null
        }
    }

    /** One attempt, resuming a partial file. True when the model is complete and in place. */
    private fun downloadOnce(): Boolean {
        try {
            modelDir.mkdirs()
            val part = partFile
            if (part.length() > E2B_SIZE) part.delete()
            // Already whole (the app stopped before the rename): asking for more would
            // be refused (HTTP 416) on every attempt.
            if (part.length() < E2B_SIZE) fetchInto(part)
            if (part.length() != E2B_SIZE) {
                Log.e(TAG, "Download incomplete: ${part.length()} of $E2B_SIZE bytes")
                return false
            }
            val ok = part.renameTo(File(modelDir, E2B_FILE))
            _installed.value = ok
            return ok
        } catch (e: Exception) {
            Log.w(TAG, "Download interrupted at ${partFile.length()} bytes", e)
            return false
        }
    }

    /**
     * Appends the rest of the model to [part], or starts it again when the
     * server will not carry on from where the file ends.
     *
     * The server's answer decides, not the request: asked for the rest, it may
     * send the whole file back with a 206 anyway. Appending that gave a file
     * 65 MB too long, which the size check then threw away — after the whole
     * 2.6 GB had come down.
     */
    private fun fetchInto(part: File) {
        val client = okhttp3.OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val request = okhttp3.Request.Builder()
            .url(E2B_URL)
            .apply { if (part.length() > 0) header("Range", "bytes=${part.length()}-") }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed: HTTP ${response.code}")
                return
            }
            // "bytes 1234-2588147711/2588147712": where what follows belongs.
            val resumesAt = response.header("Content-Range")
                ?.substringAfter("bytes ", "")
                ?.substringBefore('-')
                ?.trim()
                ?.toLongOrNull()
            val append = response.code == 206 && resumesAt == part.length()
            if (!append) {
                if (response.code == 206) {
                    Log.w(TAG, "Server resumed at $resumesAt, not ${part.length()}: starting again")
                }
                part.delete()
            }
            val body = response.body ?: return
            FileOutputStream(part, append).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(1 shl 16)
                    var done = part.length()
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        // Never write past the end of the model: a server that
                        // sends more than was asked for is not worth a full disk.
                        if (done + n > E2B_SIZE) {
                            Log.w(TAG, "Server sent more than the model's size; stopping")
                            out.write(buffer, 0, (E2B_SIZE - done).toInt())
                            return
                        }
                        out.write(buffer, 0, n)
                        done += n
                        _downloadPercent.value = (done * 100 / E2B_SIZE).toInt().coerceIn(0, 99)
                    }
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loadStarted = AtomicBoolean(false)
    /** One model call at a time. A lock, not synchronized, so waiting can be given up. */
    private val inference = ReentrantLock()

    /** Bumped by [cancel]; a request that sees it change gives up. */
    @Volatile
    private var cancelToken = 0L

    @Volatile
    private var active: com.google.ai.edge.litertlm.Conversation? = null

    @Volatile
    private var engine: Engine? = null

    /** The model would not load on this phone; requests stop waiting for it. */
    @Volatile
    private var loadFailed = false

    /** Bumped by [release], so a load still running when released is thrown away. */
    @Volatile
    private var generation = 0

    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    /** Load in the background when the model is present. Single-flight. */
    fun loadIfPresent() {
        val file = modelFile ?: return
        if (loadFailed || !loadStarted.compareAndSet(false, true)) return
        val gen = generation
        scope.launch {
            val started = SystemClock.elapsedRealtime()
            val loaded = open(file, Backend.GPU()) ?: open(file, Backend.CPU())
            if (loaded == null) {
                loadFailed = true
                loadStarted.set(false)
                return@launch
            }
            if (gen != generation) {
                loaded.close()
                return@launch
            }
            engine = loaded
            Log.i(TAG, "Loaded ${file.name} in ${SystemClock.elapsedRealtime() - started} ms")
            warmUp(loaded)
            _ready.value = true
        }
    }

    /**
     * The first image and the first recording each paid a one-off set-up cost
     * (the first picture took 15 s, later ones 7 s). Pay it here, before anyone
     * is waiting: one tiny image question and one second of silence.
     */
    private fun warmUp(eng: Engine) = inference.withLock {
        val started = SystemClock.elapsedRealtime()
        try {
            eng.createConversation(ConversationConfig(maxOutputToken = 1)).use {
                it.sendMessage(Contents.of(Content.ImageBytes(blankJpeg()), Content.Text("Hi")))
            }
            eng.createConversation(ConversationConfig(maxOutputToken = 1)).use {
                it.sendMessage(Contents.of(Content.AudioBytes(wav(ShortArray(16_000), 16_000)), Content.Text("Hi")))
            }
            Log.i(TAG, "Warmed up in ${SystemClock.elapsedRealtime() - started} ms")
        } catch (e: Throwable) {
            Log.w(TAG, "Warm-up failed", e)
        }
    }

    private fun blankJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun open(file: File, backend: Backend): Engine? = try {
        Engine(
            EngineConfig(
                modelPath = file.absolutePath,
                backend = backend,
                visionBackend = Backend.GPU(),
                // The audio encoder must run on the CPU for Gemma's E models.
                audioBackend = Backend.CPU(),
                cacheDir = cacheDir,
            )
        ).also { it.initialize() }
    } catch (e: Throwable) {
        Log.e(TAG, "Could not load ${file.name} on $backend", e)
        null
    }

    /**
     * Speech to text. [pcm] is 16-bit mono. Returns null when the model is not
     * loaded or fails, so the caller can fall back.
     */
    suspend fun transcribe(pcm: ShortArray, sampleRate: Int): String? {
        // A question asked while the model is still loading waits for it.
        loadIfPresent()
        withTimeoutOrNull(LOAD_WAIT_MS) { while (!ready.value && !loadFailed) kotlinx.coroutines.delay(100) }
        return withContext(Dispatchers.Default) {
            inference.withLock {
                // Read under the lock: a release in between would leave a closed engine.
                val eng = engine ?: return@withLock null
                try {
                    val started = SystemClock.elapsedRealtime()
                    val text = eng.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(TRANSCRIBE_INSTRUCTION),
                            // Greedy: a transcript has one right answer.
                            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
                        )
                    ).use { conversation ->
                        conversation.sendMessage(
                            Contents.of(
                                Content.AudioBytes(wav(pcm, sampleRate)),
                                Content.Text(TRANSCRIBE_PROMPT),
                            )
                        ).toString().trim()
                    }
                    Log.i(
                        TAG,
                        "Transcribed ${pcm.size * 1000L / sampleRate} ms of audio in " +
                            "${SystemClock.elapsedRealtime() - started} ms",
                    )
                    text
                } catch (e: Throwable) {
                    Log.e(TAG, "Transcription failed", e)
                    null
                }
            }
        }
    }

    /** True when installed: a request made while it is still loading waits for it. */
    override fun isReady(): Boolean = installed

    /**
     * Answers an OpenAI-shaped chat request on the device: system message,
     * earlier turns, and a last user turn that may carry a base64 JPEG.
     */
    override fun cancel() {
        cancelToken++
        active?.cancelProcess()
    }

    override fun complete(openAiRequestBody: JSONObject): String? {
        val token = cancelToken
        val waitStarted = SystemClock.elapsedRealtime()
        fun cancelled() = (token != cancelToken).also {
            if (it) Log.i(TAG, "Cancelled after ${SystemClock.elapsedRealtime() - waitStarted} ms")
        }
        // Wait for the load and then for any other request, giving up on cancel.
        loadIfPresent()
        while (!ready.value) {
            if (loadFailed || cancelled() || SystemClock.elapsedRealtime() - waitStarted > LOAD_WAIT_MS) return null
            Thread.sleep(100)
        }
        while (!inference.tryLock(100, TimeUnit.MILLISECONDS)) {
            if (cancelled()) return null
        }
        try {
            if (cancelled()) return null
            // Read under the lock: a release in between would leave a closed engine.
            val eng = engine ?: return null
            return try {
                val started = SystemClock.elapsedRealtime()
                val messages = openAiRequestBody.getJSONArray("messages")
                var system: String? = null
                val history = mutableListOf<Message>()
                var last: Contents? = null
                for (i in 0 until messages.length()) {
                    val m = messages.getJSONObject(i)
                    val isLast = i == messages.length() - 1
                    when (m.getString("role")) {
                        "system" -> system = listOfNotNull(system, m.getString("content")).joinToString("\n\n")
                        "assistant" -> history += Message.model(Contents.of(m.getString("content")), emptyList(), emptyMap())
                        else -> {
                            val contents = userContents(m.get("content"))
                            if (isLast) last = contents else history += Message.user(contents)
                        }
                    }
                }
                val question = last ?: return null
                val maxTokens = openAiRequestBody.optInt("max_tokens", 256)
                checkFits(messages, maxTokens)
                val temperature = openAiRequestBody.optDouble("temperature", 0.7)
                val text = eng.createConversation(
                    ConversationConfig(
                        systemInstruction = system?.let { Contents.of(it) },
                        initialMessages = history,
                        samplerConfig = SamplerConfig(
                            topK = if (temperature <= 0.0) 1 else 64,
                            topP = 0.95,
                            temperature = temperature,
                        ),
                        maxOutputToken = maxTokens,
                    )
                ).use { conversation ->
                    active = conversation
                    // A cancel between the check above and here would have missed it.
                    if (token != cancelToken) conversation.cancelProcess()
                    try {
                        conversation.sendMessage(question).toString().trim()
                    } finally {
                        active = null
                    }
                }
                if (cancelled()) return null
                Log.i(TAG, "Answered in ${SystemClock.elapsedRealtime() - started} ms (${history.size} earlier turns)")
                text.ifBlank { null }
            } catch (e: PromptTooLongException) {
                throw e
            } catch (e: Throwable) {
                if (cancelled()) return null
                Log.e(TAG, "Local answer failed", e)
                null
            }
        } finally {
            inference.unlock()
        }
    }

    /**
     * Refuses a request that would not fit the context window, rather than let it
     * fail inside the model. A rough count on the safe side: three characters a
     * token (English runs nearer four), a picture [IMAGE_TOKENS].
     */
    private fun checkFits(messages: JSONArray, maxTokens: Int) {
        fun tokens(message: JSONObject): Int {
            val content = message.opt("content")
            if (content !is JSONArray) return content.toString().length / 3 + 8
            var sum = 8
            for (i in 0 until content.length()) {
                val part = content.getJSONObject(i)
                sum += if (part.optString("type") == "image_url") IMAGE_TOKENS else part.optString("text").length / 3
            }
            return sum
        }
        val all = (0 until messages.length()).map { messages.getJSONObject(it) }
        val system = all.filter { it.optString("role") == "system" }.sumOf { tokens(it) }
        val latest = tokens(all.last())
        val total = all.sumOf { tokens(it) } + maxTokens
        if (total <= CONTEXT_TOKENS) return
        val latestAlone = system + latest + maxTokens
        Log.w(TAG, "Too long for the context: about $total tokens (latest message alone $latestAlone)")
        throw PromptTooLongException(chatTooLong = latestAlone <= CONTEXT_TOKENS)
    }

    /** OpenAI user content: a string, or parts of type text / image_url (data URL). */
    private fun userContents(content: Any): Contents {
        if (content !is JSONArray) return Contents.of(content.toString())
        val parts = mutableListOf<Content>()
        for (i in 0 until content.length()) {
            val part = content.getJSONObject(i)
            when (part.optString("type")) {
                "text" -> parts += Content.Text(part.getString("text"))
                "image_url" -> {
                    val url = part.getJSONObject("image_url").getString("url")
                    val base64 = url.substringAfter("base64,", "")
                    if (base64.isNotEmpty()) parts += Content.ImageBytes(Base64.decode(base64, Base64.DEFAULT))
                }
            }
        }
        // Image first, then the question: the order Gemma was trained on.
        return Contents.of(parts.sortedBy { if (it is Content.ImageBytes) 0 else 1 })
    }

    /** Frees the memory (about 2 GB). Waits for a request in progress to finish. */
    fun release() = inference.withLock {
        generation++
        engine?.close()
        engine = null
        _ready.value = false
        loadStarted.set(false)
        loadFailed = false
    }

    /** Unloads and deletes the model and its GPU caches. */
    fun deleteModel(): Boolean {
        release()
        val file = File(modelDir, E2B_FILE)
        val deleted = !file.exists() || file.delete()
        File(cacheDir).listFiles { f -> f.name.startsWith(E2B_FILE) }?.forEach { it.delete() }
        _installed.value = !deleted
        return deleted
    }

    companion object {
        private const val TAG = "LocalGemma"

        const val E2B_FILE = "gemma-4-E2B-it.litertlm"
        private const val E2B_SIZE = ModelSetup.GEMMA_BYTES
        private const val DOWNLOAD_ATTEMPTS = 5

        /** LiteRT-LM's default window for this model: prompt and reply together. */
        private const val CONTEXT_TOKENS = 4096

        /** What a picture costs in the prompt (the vision encoder's token budget, rounded up). */
        private const val IMAGE_TOKENS = 300
        private const val E2B_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
                "6e5c4f1e395deb959c494953478fa5cec4b8008f/$E2B_FILE"

        /** Longest a recording waits for the model to finish loading (first-ever load: ~70 s). */
        private const val LOAD_WAIT_MS = 90_000L

        private const val TRANSCRIBE_INSTRUCTION =
            "You are a speech-to-text engine. Output only the exact words spoken, " +
                "with normal punctuation. Never answer, translate or comment."
        private const val TRANSCRIBE_PROMPT = "Transcribe this audio."

        /** 16-bit mono PCM wrapped in a WAV header, which the audio encoder expects. */
        fun wav(pcm: ShortArray, sampleRate: Int): ByteArray {
            val dataBytes = pcm.size * 2
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt(36 + dataBytes)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16)
                putShort(1) // PCM
                putShort(1) // mono
                putInt(sampleRate)
                putInt(sampleRate * 2)
                putShort(2)
                putShort(16)
                put("data".toByteArray())
                putInt(dataBytes)
            }
            val body = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            body.asShortBuffer().put(pcm)
            return ByteArrayOutputStream(44 + dataBytes).apply {
                write(header.array())
                write(body.array())
            }.toByteArray()
        }
    }
}
