package com.example.sightbuddy.core.llm

import com.example.sightbuddy.core.ModelFileGate
import com.example.sightbuddy.core.VerifiedModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Semaphore

/** Per-model files and persisted choice. Selecting is not consent to download. */
internal class LocalModelRepository(
    private val dir: File,
    private val checks: VerifiedModels,
    readSelection: () -> String?,
    private val saveSelection: (String) -> Unit,
    private val specs: List<LocalModelSpec> = LocalModelCatalog.all,
) {
    private val operation = Semaphore(1)
    private val _selected = MutableStateFlow(specs.firstOrNull { it.id == readSelection() } ?: specs.first())
    val selected = _selected.asStateFlow()
    private val _installed = MutableStateFlow(isInstalled())
    val installed = _installed.asStateFlow()
    private val _progress = MutableStateFlow<Int?>(null)
    val progress = _progress.asStateFlow()
    fun file(spec: LocalModelSpec = selected.value) = File(dir, spec.fileName)
    fun part(spec: LocalModelSpec = selected.value) = File(dir, spec.fileName + ".part")
    fun isInstalled(spec: LocalModelSpec = selected.value) = file(spec).isFile && file(spec).length() == spec.sizeBytes
    fun refresh() { _installed.value = isInstalled() }
    fun partialPercent(): Int = ((part().length() * 100) / selected.value.sizeBytes).toInt().coerceIn(0, 99)
    fun select(id: String): Boolean {
        val spec = specs.firstOrNull { it.id == id } ?: return false
        if (!operation.tryAcquire()) return false
        try {
            saveSelection(spec.id)
            _selected.value = spec
            refresh()
            return true
        } finally { operation.release() }
    }
    fun verified(spec: LocalModelSpec = selected.value): ModelFileGate.ForLoad =
        ModelFileGate.forLoad(file(spec), spec.sha256, checks)
    /** Blocking HTTP fetch; caller runs on IO. Size and SHA gate the final name. */
    fun download(): Boolean {
        if (!operation.tryAcquire()) return false
        try {
            val spec = selected.value
            if (isInstalled(spec) && verified(spec) is ModelFileGate.ForLoad.Verified) {
                refresh()
                return true
            }
            _progress.value = partialPercent()
            dir.mkdirs()
            val partial = part(spec)
            if (partial.length() > spec.sizeBytes) partial.delete()
            if (partial.length() < spec.sizeBytes) {
                val offset = partial.length()
                val request = okhttp3.Request.Builder().url(spec.url)
                    .apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return false
                    val start = response.header("Content-Range")?.substringAfter("bytes ", "")?.substringBefore('-')?.toLongOrNull()
                    val append = response.code == 206 && start == offset
                    // A 206 for an unrelated nonzero range cannot be the full file.
                    if (response.code == 206 && !append && start != 0L) return false
                    val body = response.body ?: return false
                    java.io.FileOutputStream(partial, append).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(65536)
                            var done = if (append) offset else 0L
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                if (done + n > spec.sizeBytes) {
                                    partial.delete()
                                    return false
                                }
                                out.write(buffer, 0, n)
                                done += n
                                _progress.value = (done * 100 / spec.sizeBytes).toInt().coerceIn(0, 99)
                            }
                        }
                    }
                }
            }
            if (partial.length() != spec.sizeBytes) return false
            return ModelFileGate.install(partial, file(spec), spec.sha256, checks) == ModelFileGate.Install.INSTALLED
        } catch (_: java.io.IOException) {
            return false
        } finally {
            refresh()
            _progress.value = null
            operation.release()
        }
    }
    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()

    fun delete(): Boolean {
        if (!operation.tryAcquire()) return false
        try {
            val target = file()
            val ok = !target.exists() || target.delete()
            checks.forget(target)
            part().delete()
            refresh()
            return ok
        } finally { operation.release() }
    }
}
