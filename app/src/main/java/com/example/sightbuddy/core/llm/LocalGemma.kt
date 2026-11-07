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
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.Color
import com.example.sightbuddy.core.ModelFileGate
import com.example.sightbuddy.core.ModelFileGate.ForLoad
import com.example.sightbuddy.core.ModelFileGate.Install
import com.example.sightbuddy.core.ModelSetup
import com.example.sightbuddy.core.OpenAiTransport
import com.example.sightbuddy.core.VerifiedModels
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

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

    /** Model files already checked against their SHA-256, so each is hashed once. */
    private val checks = VerifiedModels(File(context.filesDir, VerifiedModels.DIR))

    /**
     * Downloads the model from Hugging Face (litert-community, Apache 2.0, no
     * login), pinned to one revision and checked by size and SHA-256. Resumes a
     * partial download. Single-flight: a second call while one runs returns false.
     */
    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        if (installed) return@withContext true
        if (!_downloadPercent.compareAndSet(null, partialPercent())) return@withContext false
        try {
            // A dropped connection is normal over 2.6 GB on a phone: retry, resuming
            // where it stopped, before giving up.
            for (attempt in 1..DOWNLOAD_ATTEMPTS) {
                when (downloadOnce()) {
                    Attempt.DONE -> return@withContext true
                    // Not retried: 2.6 GB again would most likely bring the same file.
                    Attempt.WRONG_FILE -> return@withContext false
                    Attempt.INTERRUPTED ->
                        if (attempt < DOWNLOAD_ATTEMPTS) kotlinx.coroutines.delay(attempt * 5_000L)
                }
            }
            false
        } finally {
            _downloadPercent.value = null
        }
    }

    private enum class Attempt { DONE, INTERRUPTED, WRONG_FILE }

    /** One attempt, resuming a partial file; [Attempt.DONE] when the model is complete, checked and in place. */
    private fun downloadOnce(): Attempt {
        try {
            modelDir.mkdirs()
            val part = partFile
            if (part.length() > E2B_SIZE) part.delete()
            // Already whole (the app stopped before the rename): asking for more would
            // be refused (HTTP 416) on every attempt.
            if (part.length() < E2B_SIZE) fetchInto(part)
            if (part.length() != E2B_SIZE) {
                Log.e(TAG, "Download incomplete: ${part.length()} of $E2B_SIZE bytes")
                return Attempt.INTERRUPTED
            }
            // Progress stays at 99 % for the few seconds this takes: the model
            // is not installed until it is known to be the right file.
            val started = SystemClock.elapsedRealtime()
            val result = ModelFileGate.install(part, File(modelDir, E2B_FILE), E2B_SHA256, checks)
            Log.i(TAG, "SHA-256 checked in ${SystemClock.elapsedRealtime() - started} ms: $result")
            return when (result) {
                Install.INSTALLED -> {
                    _installed.value = true
                    Attempt.DONE
                }
                Install.WRONG_FILE -> {
                    Log.e(TAG, "Downloaded model does not match its SHA-256: deleted")
                    Attempt.WRONG_FILE
                }
                Install.NOT_MOVED -> Attempt.INTERRUPTED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download interrupted at ${partFile.length()} bytes", e)
            return Attempt.INTERRUPTED
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
    /** One model call at a time; waiting can be given up, and any thread may let go. */
    private val inference = ModelGate()

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
        // Set before the launch, so a request starting its wait now sees it.
        verifying = true
        scope.launch {
            val checked = try {
                verifiedForLoad(file)
            } finally {
                verifying = false
            }
            if (checked == null || gen != generation) {
                loadStarted.set(false)
                return@launch
            }
            val started = SystemClock.elapsedRealtime()
            val loaded = open(checked, Backend.GPU()) ?: open(checked, Backend.CPU())
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
     * [file] once it has matched its SHA-256, or null: nothing else is ever
     * loaded ([ModelFileGate]). A model downloaded by an earlier version, or
     * put there by hand, is hashed here once, off the main thread (a few
     * seconds, remembered after; a request's wait does not count them, see
     * [LoadWait]). One that does not match is deleted: the model is then not
     * installed, a request waiting for it gives up, the download is offered
     * again, and [removedAsDamaged] is set for the app to say so.
     */
    private suspend fun verifiedForLoad(file: File): ForLoad.Verified? = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        when (val result = ModelFileGate.forLoad(file, E2B_SHA256, checks)) {
            is ForLoad.Verified -> {
                val ms = SystemClock.elapsedRealtime() - started
                if (ms > 100) Log.i(TAG, "SHA-256 of ${file.name} checked in $ms ms")
                result
            }
            ForLoad.Missing -> null
            is ForLoad.Unreadable -> {
                Log.e(TAG, "Could not read ${file.name} to check it", result.error)
                loadFailed = true
                null
            }
            ForLoad.Damaged -> {
                Log.e(TAG, "${file.name} does not match its SHA-256: deleted")
                File(cacheDir).listFiles { f -> f.name.startsWith(E2B_FILE) }?.forEach { it.delete() }
                _installed.value = false
                _removedAsDamaged.value = true
                null
            }
        }
    }

    /** True while the model file is being hashed before a load. */
    @Volatile
    private var verifying = false

    private val _removedAsDamaged = MutableStateFlow(false)

    /** True when the model on the phone failed its check and was removed, until [damagedNoticeSaid]. */
    val removedAsDamaged = _removedAsDamaged.asStateFlow()

    fun damagedNoticeSaid() {
        _removedAsDamaged.value = false
    }

    /**
     * The first image and the first recording each paid a one-off set-up cost
     * (the first picture took 15 s, later ones 7 s). Pay it here, before anyone
     * is waiting: one tiny image question and one second of silence.
     */
    private fun warmUp(eng: Engine) = inference.withModel {
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

    /** Takes only a file the gate has checked: an unchecked [File] cannot be opened. */
    private fun open(model: ForLoad.Verified, backend: Backend): Engine? = try {
        Engine(
            EngineConfig(
                modelPath = model.file.absolutePath,
                backend = backend,
                visionBackend = Backend.GPU(),
                // The audio encoder must run on the CPU for Gemma's E models.
                audioBackend = Backend.CPU(),
                cacheDir = cacheDir,
            )
        ).also { it.initialize() }
    } catch (e: Throwable) {
        Log.e(TAG, "Could not load ${model.file.name} on $backend", e)
        null
    }

    /**
     * Speech to text. [pcm] is 16-bit mono. Returns null when the model is not
     * loaded or fails, so the caller can fall back.
     */
    suspend fun transcribe(pcm: ShortArray, sampleRate: Int): String? {
        // A question asked while the model is still loading waits for it.
        loadIfPresent()
        val wait = LoadWait(LOAD_WAIT_MS, SystemClock.elapsedRealtime())
        while (!ready.value && !loadFailed && installed) {
            if (wait.expired(SystemClock.elapsedRealtime(), verifying)) break
            kotlinx.coroutines.delay(100)
        }
        return withContext(Dispatchers.Default) {
            inference.withModel {
                // Read under the lock: a release in between would leave a closed engine.
                val eng = engine ?: return@withModel null
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

    override fun complete(openAiRequestBody: JSONObject): String? = answer(openAiRequestBody, onDelta = null)

    /**
     * [complete], with the reply handed to [onDelta] as LiteRT-LM writes it
     * (issue #24). Through the callback form of `Conversation.sendMessageAsync`,
     * not its Flow form: that one is a `callbackFlow` that drops messages once
     * its buffer is full (`trySend`), and leaves the generation running when
     * its collector goes away. The callback is waited for on this thread, so
     * the model is taken and let go on the same one ([ModelGate]).
     */
    override fun stream(openAiRequestBody: JSONObject, onDelta: (String) -> Unit): String? =
        answer(openAiRequestBody, onDelta)

    /** One answer, whole ([onDelta] null) or streamed. */
    private fun answer(openAiRequestBody: JSONObject, onDelta: ((String) -> Unit)?): String? {
        val token = cancelToken
        val waitStarted = SystemClock.elapsedRealtime()
        fun cancelled() = (token != cancelToken).also {
            if (it) Log.i(TAG, "Cancelled after ${SystemClock.elapsedRealtime() - waitStarted} ms")
        }
        // Wait for the load and then for any other request, giving up on cancel.
        loadIfPresent()
        val wait = LoadWait(LOAD_WAIT_MS, waitStarted)
        while (!ready.value) {
            // Not installed: deleted meanwhile, or it failed its SHA-256 check.
            if (loadFailed || !installed || cancelled()) return null
            if (wait.expired(SystemClock.elapsedRealtime(), verifying)) return null
            Thread.sleep(100)
        }
        if (!inference.enter { cancelled() }) return null
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
                val maxTokens = replyBudget(messages, openAiRequestBody.optInt("max_tokens", 256))
                val temperature = openAiRequestBody.optDouble("temperature", 0.7)
                if (onDelta != null) {
                    StreamLog.i {
                        "request: waited ${started - waitStarted} ms for the model; ${history.size} earlier turns, " +
                            "image ${question.contents.any { it is Content.ImageBytes }}, up to $maxTokens tokens"
                    }
                }
                val conversation = eng.createConversation(
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
                )
                // Closed when done, unless a streamed answer never said it was:
                // closing a conversation the model may still be running is worse
                // than leaving it.
                var abandoned = false
                val text = try {
                    active = conversation
                    // A cancel between the check above and here would have missed it.
                    if (token != cancelToken) conversation.cancelProcess()
                    if (onDelta == null) {
                        conversation.sendMessage(question).toString().trim()
                    } else {
                        val streamed = streamInto(conversation, question, token, started, onDelta)
                        abandoned = streamed.abandoned
                        streamed.text
                    }
                } finally {
                    active = null
                    if (!abandoned) conversation.close()
                }
                if (cancelled()) return null
                Log.i(TAG, "Answered in ${SystemClock.elapsedRealtime() - started} ms (${history.size} earlier turns)")
                text?.ifBlank { null }
            } catch (e: PromptTooLongException) {
                throw e
            } catch (e: Throwable) {
                if (cancelled()) return null
                Log.e(TAG, "Local answer failed", e)
                null
            }
        } finally {
            inference.leave()
        }
    }

    /** A streamed answer's text, null when it failed or was stopped part way; [abandoned]: never said it was done. */
    private class Streamed(val text: String?, val abandoned: Boolean)

    /**
     * Runs [question] with LiteRT-LM's streaming callback and waits here for it
     * to finish, handing each message's new text to [onDelta] (on LiteRT-LM's
     * own thread) unless the answer has been cancelled meanwhile.
     *
     * The text is the whole answer only when the model finished it. Stopped by
     * the time limit or failing part way, it is null, as for any failure: what
     * was said by then is not an answer (the caller says the failure after
     * it). A cancel or the limit tells the model to stop; if it has still not
     * called back [STOP_WAIT_MS] later, the wait ends anyway ([StreamWatch]),
     * the model is let go, and nothing it writes later is handed on.
     */
    private fun streamInto(
        conversation: com.google.ai.edge.litertlm.Conversation,
        question: Contents,
        token: Long,
        started: Long,
        onDelta: (String) -> Unit,
    ): Streamed {
        val deltas = StreamDeltas()
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val over = AtomicBoolean(false)
        var count = 0
        conversation.sendMessageAsync(
            question,
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    if (over.get()) return
                    val chunk = message.toString()
                    val new = deltas.next(chunk)
                    count++
                    val at = SystemClock.elapsedRealtime() - started
                    StreamLog.i { StreamDeltas.logLine(count, at, chunk, new) }
                    if (new.isNotEmpty() && token == cancelToken) onDelta(new)
                }

                override fun onDone() = done.countDown()

                override fun onError(throwable: Throwable) {
                    failure.set(throwable)
                    done.countDown()
                }
            },
        )
        val watch = StreamWatch(limitMs = STREAM_LIMIT_MS, capMs = STOP_WAIT_MS)
        var abandoned = false
        while (!done.await(100, TimeUnit.MILLISECONDS)) {
            val step = watch.step(SystemClock.elapsedRealtime() - started, cancelled = token != cancelToken)
            if (step == StreamWatch.Step.STOP) {
                if (watch.stoppedByLimit) Log.w(TAG, "Streamed answer still running after $STREAM_LIMIT_MS ms: stopped")
                conversation.cancelProcess()
            } else if (step == StreamWatch.Step.GIVE_UP) {
                Log.w(TAG, "No word from the model $STOP_WAIT_MS ms after stopping it: no longer waited for")
                abandoned = true
                break
            }
        }
        over.set(true)
        val failed = failure.get()
        // Read after the wait: on a give-up the model's thread may still be writing.
        val text = if (abandoned) "" else deltas.text.trim()
        StreamLog.i {
            "done at ${SystemClock.elapsedRealtime() - started} ms: $count messages, ${text.length} chars, " +
                "each message ${if (deltas.cumulative) "the whole text so far" else "new text"}" +
                (failed?.let { ", ended by $it" } ?: "") +
                (if (watch.stoppedByLimit) ", stopped by the time limit" else "") +
                (if (abandoned) ", given up" else "")
        }
        if (failed != null && token == cancelToken) Log.e(TAG, "Streamed answer failed", failed)
        val whole = failed == null && !watch.stoppedByLimit && !abandoned
        return Streamed(if (whole) text else null, abandoned)
    }

    /**
     * The reply's token cap: [maxTokens], or what the context window has left
     * after the prompt ([ContextBudget]). Refuses a request that would not leave
     * room for an ordinary answer, rather than let it fail inside the model. A
     * rough count on the safe side: three characters a token (English runs
     * nearer four), a picture [IMAGE_TOKENS].
     */
    private fun replyBudget(messages: JSONArray, maxTokens: Int): Int {
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
        val prompt = all.sumOf { tokens(it) }
        fun budget(promptTokens: Int) = ContextBudget.replyTokens(
            promptTokens = promptTokens,
            requested = maxTokens,
            window = CONTEXT_TOKENS,
            floor = OpenAiTransport.LLM_MAX_OUTPUT_TOKENS,
        )
        budget(prompt)?.let { return it }
        Log.w(TAG, "Too long for the context: about $prompt tokens before the reply (latest message alone ${system + latest})")
        // Too long only with the conversation: the chat is full, rather than the message too long.
        throw PromptTooLongException(chatTooLong = budget(system + latest) != null)
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
    fun release() = inference.withModel {
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
        checks.forget(file)
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
        internal const val E2B_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
                "6e5c4f1e395deb959c494953478fa5cec4b8008f/$E2B_FILE"

        /**
         * What the model must be, or it is deleted and never loaded. The Git LFS
         * object id (the file's SHA-256) Hugging Face lists for this file at the
         * pinned revision above (`lfs.oid` in api/models/litert-community/
         * gemma-4-E2B-it-litert-lm/tree/6e5c4f1e...), read 2026-10-28. A new
         * revision needs a new hash here.
         */
        internal const val E2B_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"

        /** Longest a recording waits for the model to finish loading (first-ever load: ~70 s). */
        private const val LOAD_WAIT_MS = 90_000L

        /**
         * Longest a streamed answer may run before it is stopped: 1,200 tokens
         * (a reading's cap) took about a minute on the test phone.
         */
        private const val STREAM_LIMIT_MS = 180_000L

        /** How long a stopped streamed answer is waited for before it is given up on. */
        private const val STOP_WAIT_MS = 10_000L

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
