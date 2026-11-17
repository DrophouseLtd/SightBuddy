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
 * E2B is the current pinned model. The catalog keeps the selection plumbing,
 * so a second pinned model can be added later. Downloads are user-initiated
 * and pinned by size and SHA-256. Only one native engine is held at a time.
 *
 * One engine, one request at a time: LiteRT-LM runs a single decode, and two
 * features asking at once would only slow both down.
 */
class LocalGemma(context: Context) : LocalAnswerer {

    private val modelDir = File(context.getExternalFilesDir(null), "llm")
    private val cacheRoot = File(context.cacheDir, "local_ai")
    private val cacheDir: String get() = File(cacheRoot, selectedModel.value.id).apply { mkdirs() }.absolutePath
    private val preferences = context.getSharedPreferences("local_ai_model", Context.MODE_PRIVATE)
    private val checks = VerifiedModels(File(context.filesDir, VerifiedModels.DIR))
    private val repository = LocalModelRepository(
        modelDir, checks,
        { preferences.getString("selected", null) },
        { id -> check(preferences.edit().putString("selected", id).commit()) { "Could not save model choice" } },
    )
    private val mutation = java.util.concurrent.Semaphore(1)
    val selectedModel = repository.selected
    val installedState = repository.installed
    val installed: Boolean get() = installedState.value
    val modelFile: File? get() = repository.file().takeIf { it.isFile }
    val downloadPercent = repository.progress
    fun sizeOnDisk(): Long = modelFile?.length() ?: 0L
    fun partialPercent(): Int = repository.partialPercent()
    fun isInstalled(spec: LocalModelSpec): Boolean = repository.isInstalled(spec)

    /** Selecting keeps other model files. Refuse while a load/decode/download is busy. */
    fun selectModel(id: String): Boolean {
        if (id == selectedModel.value.id) return true
        if (LocalModelCatalog.fromId(id) == null || !mutation.tryAcquire()) return false
        try {
            if (nativeAbandoned || loadingState.value || !inference.tryEnter()) return false
            try {
                if (nativeAbandoned || retired) return false
                if (!repository.select(id)) return false
                generation++
                engine?.close()
                engine = null
                _ready.value = false
                loadStarted.set(false)
                loadFailed = false
                return true
            } finally { inference.leave() }
        } finally { mutation.release() }
    }

    /** Explicit user-initiated download of the selected model; interrupted files resume. */
    suspend fun download(expectedId: String = selectedModel.value.id): Boolean = withContext(Dispatchers.IO) {
        if (!mutation.tryAcquire()) return@withContext false
        try {
            if (selectedModel.value.id != expectedId) return@withContext false
            for (attempt in 1..DOWNLOAD_ATTEMPTS) {
                if (repository.download()) return@withContext true
                if (attempt < DOWNLOAD_ATTEMPTS) kotlinx.coroutines.delay(attempt * 5_000L)
            }
            false
        } finally { mutation.release() }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loadStarted = AtomicBoolean(false)
    /** One model call at a time; waiting can be given up, and any thread may let go. */
    private val inference = PROCESS_INFERENCE

    /** Bumped by [cancel]; a request that sees it change gives up. */
    @Volatile
    private var cancelToken = 0L

    @Volatile
    private var active: com.google.ai.edge.litertlm.Conversation? = null

    @Volatile
    private var engine: Engine? = null

    /** A native stream did not acknowledge cancellation. Quarantine until process restart. */
    private var nativeAbandoned: Boolean
        get() = PROCESS_QUARANTINED.get()
        set(value) { if (value) PROCESS_QUARANTINED.set(true) }
    private val conversationLock = Any()
    @Volatile private var retired = false

    /** The model would not load on this phone; requests stop waiting for it. */
    @Volatile
    private var loadFailed = false

    /** Bumped by [release], so a load still running when released is thrown away. */
    @Volatile
    private var generation = 0

    private val _loading = MutableStateFlow(false)
    val loadingState = _loading.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    /** Load in the background when the model is present. Single-flight. */
    fun loadIfPresent() {
        if (!mutation.tryAcquire()) return
        var handedOff = false
        var ownsInference = false
        try {
            // Reserve BEFORE scheduling: there are no queued, stale load jobs
            // whose finally could clear a later flight after release/recreate.
            if (!inference.tryEnter()) return
            ownsInference = true
            if (nativeAbandoned || retired || loadFailed || engine != null || loadStarted.get()) return
            val spec = selectedModel.value
            val file = repository.file(spec)
            if (!file.isFile) return
            loadStarted.set(true)
            _loading.value = true
            verifying = true
            val gen = generation
            scope.launch {
                try {
                    if (nativeAbandoned || retired || gen != generation) return@launch
                    val checked = verifiedForLoad(file, spec) ?: return@launch
                    verifying = false
                    if (nativeAbandoned || retired || gen != generation) return@launch
                    PROCESS_OWNER.replace(this@LocalGemma) { closeOwnedEngine() }
                    val loaded = localBackendAttempts().firstNotNullOfOrNull { (llm, vision) -> open(checked, llm, vision) }
                    if (loaded == null) { loadFailed = true; return@launch }
                    if (retired || gen != generation) { loaded.close(); return@launch }
                    engine = loaded
                    warmUp(loaded)
                    _ready.value = true
                } finally {
                    verifying = false
                    _loading.value = false
                    loadStarted.set(false)
                    inference.leave()
                }
            }
            handedOff = true
        } finally {
            if (ownsInference && !handedOff) inference.leave()
            mutation.release()
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
    private suspend fun verifiedForLoad(file: File, spec: LocalModelSpec): ForLoad.Verified? = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        when (val result = ModelFileGate.forLoad(file, spec.sha256, checks)) {
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
                File(cacheDir).deleteRecursively()
                repository.refresh()
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
    private fun warmUp(eng: Engine) {
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
    private fun open(model: ForLoad.Verified, backend: Backend, visionBackend: Backend): Engine? = try {
        Engine(
            EngineConfig(
                modelPath = model.file.absolutePath,
                backend = backend,
                visionBackend = visionBackend,
                // The audio encoder must run on the CPU for Gemma's E models.
                audioBackend = Backend.CPU(),
                cacheDir = cacheDir,
            )
        ).also { created ->
            try { created.initialize() } catch (failure: Throwable) {
                runCatching { created.close() }
                throw failure
            }
        }
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
        while (!ready.value && !loadFailed && installed && !nativeAbandoned && !retired) {
            if (wait.expired(SystemClock.elapsedRealtime(), verifying)) break
            // The process permit may have belonged to the preceding Activity
            // when the first attempt ran. Retry within the existing bounded wait.
            loadIfPresent()
            kotlinx.coroutines.delay(100)
        }
        return withContext(Dispatchers.Default) {
            inference.withModel {
                // Read under the lock: a release in between would leave a closed engine.
                if (nativeAbandoned || retired) return@withModel null
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
        synchronized(conversationLock) { active?.cancelProcess() }
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
            if (nativeAbandoned || retired || loadFailed || !installed || cancelled()) return null
            if (wait.expired(SystemClock.elapsedRealtime(), verifying)) return null
            loadIfPresent()
            Thread.sleep(100)
        }
        if (!inference.enter { cancelled() }) return null
        try {
            if (cancelled()) return null
            // Read under the lock: a release in between would leave a closed engine.
            if (nativeAbandoned || retired) return null
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
                    synchronized(conversationLock) { active = conversation }
                    // A cancel between the check above and here would have missed it.
                    if (token != cancelToken) conversation.cancelProcess()
                    if (onDelta == null) {
                        conversation.sendMessage(question).toString().trim()
                    } else {
                        val streamed = try {
                            streamInto(conversation, question, token, started, onDelta)
                        } catch (failure: Throwable) {
                            // An exception is not native completion. Keep the engine
                            // alive and forbid all new engines in this process.
                            abandoned = true
                            nativeAbandoned = true
                            loadFailed = true
                            _ready.value = false
                            throw failure
                        }
                        abandoned = streamed.abandoned
                        if (abandoned) {
                            nativeAbandoned = true
                            loadFailed = true
                            _ready.value = false
                        }
                        streamed.text
                    }
                } finally {
                    synchronized(conversationLock) {
                        active = null
                        if (!abandoned) conversation.close()
                    }
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
                synchronized(conversationLock) { conversation.cancelProcess() }
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
    private fun closeOwnedEngine() {
        generation++
        engine?.close()
        engine = null
        _ready.value = false
        loadStarted.set(false)
        loadFailed = false
    }

    fun release() = inference.withModel {
        if (nativeAbandoned) return@withModel
        if (!PROCESS_OWNER.release(this)) closeOwnedEngine()
    }

    /** Prevent a destroyed Activity from reloading while its asynchronous release waits. */
    fun retire() {
        retired = true
        cancel()
    }

    /** Unloads and deletes the model and its GPU caches. */
    fun deleteModel(): Boolean {
        if (!mutation.tryAcquire()) return false
        try {
            if (nativeAbandoned || (loadStarted.get() && !ready.value)) return false
            if (!inference.tryEnter()) return false
            try {
                if (nativeAbandoned || retired) return false
                generation++
                engine?.close()
                engine = null
                _ready.value = false
                loadStarted.set(false)
                loadFailed = false
                val deleted = repository.delete()
                File(cacheDir).deleteRecursively()
                return deleted
            } finally { inference.leave() }
        } finally { mutation.release() }
    }

    companion object {
        private val PROCESS_OWNER = ProcessModelOwner()
        private val PROCESS_INFERENCE = ModelGate()
        private val PROCESS_QUARANTINED = AtomicBoolean(false)
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
