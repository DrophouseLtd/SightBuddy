package com.example.sightbuddy.core.stt

import com.example.sightbuddy.core.ModelChecksums
import com.example.sightbuddy.core.stt.SttModelSet.LEGACY_EN
import com.example.sightbuddy.core.stt.SttModelSet.MULTILINGUAL
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The download path against a real HTTP server: every file is fetched, checked
 * against its pinned SHA-256 and only then moved into place; a missing or
 * damaged file leaves the model not ready rather than half-installed; the two
 * model sets never touch each other's files.
 */
class SttDownloadTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: HttpServer
    private val bodies = ConcurrentHashMap<String, ByteArray>()
    private val requests = ConcurrentHashMap<String, AtomicInteger>()

    private fun sha(bytes: ByteArray) = ModelChecksums.sha256(ByteArrayInputStream(bytes))

    /** Four small stand-ins, one per file the multilingual set needs. */
    private val multilingualBodies = linkedMapOf(
        "base-encoder.int8.onnx" to "modern-encoder".toByteArray(),
        "base-decoder.int8.onnx" to "modern-decoder".toByteArray(),
        "base-tokens.txt" to "modern-tokens".toByteArray(),
        "silero_vad.onnx" to "vad".toByteArray(),
    )

    private lateinit var multilingual: SttModelSpec

    @Before
    fun serve() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange: HttpExchange ->
            val name = exchange.requestURI.path.trimStart('/')
            requests.computeIfAbsent(name) { AtomicInteger() }.incrementAndGet()
            val body = bodies[name]
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
        multilingualBodies.forEach { (name, body) -> bodies[name] = body }

        // The real manifest's URLs, with the files swapped for these stand-ins:
        // same names, same order, same VAD slot, our own hashes and sizes.
        val real = SttModelCatalog.of(MULTILINGUAL)
        multilingual = SttModelSpec(
            set = MULTILINGUAL,
            dirName = real.dirName,
            files = real.files.map { spec ->
                val body = multilingualBodies.getValue(spec.name)
                ModelFileSpec(spec.name, sha(body), body.size.toLong(), absoluteUrl = null)
            },
            vad = ModelFileSpec(real.vad.name, sha(multilingualBodies.getValue(real.vad.name)), 3L),
            totalBytes = real.totalBytes,
            marker = real.marker,
        )
    }

    @After
    fun stop() {
        server.stop(0)
    }

    private fun baseUrl() = "http://127.0.0.1:${server.address.port}"

    private fun manager(dir: File = temp.newFolder("files")) = SttModelManager(
        filesDir = dir,
        baseUrl = baseUrl(),
        files = emptyMap(),
        catalog = { multilingual },
    )

    @Test
    fun everyFileIsDownloadedAndThenTheModelIsReady() = runBlocking {
        val models = manager()
        assertFalse(models.isReady(MULTILINGUAL))
        assertTrue(models.downloadIfNeeded(MULTILINGUAL))

        assertTrue(models.isReady(MULTILINGUAL))
        val files = models.verifiedFiles(MULTILINGUAL)
        assertNotNull(files)
        assertEquals("modern-encoder", files!!.encoder.file.readText())
        assertEquals("modern-decoder", files.decoder.file.readText())
        assertEquals("modern-tokens", files.tokens.file.readText())
        // Each of the four was fetched once, every byte on disk.
        for ((name, body) in multilingualBodies) {
            assertEquals(name, 1, requests.getValue(name).get())
            assertEquals(name, body.size.toLong(), File(models.dirFor(MULTILINGUAL), name).length())
        }
        assertEquals(SttModelManager.DownloadState.Ready, models.stateOf(MULTILINGUAL).value)
    }

    @Test
    fun aFileThatIsNotThePublisherFileFailsTheModelAndIsNeverUsable() = runBlocking {
        // The server hands over tampered bytes for the decoder, as a broken
        // mirror or a cut connection would.
        bodies["base-decoder.int8.onnx"] = "not-the-model".toByteArray()
        val models = manager()

        assertFalse(models.downloadIfNeeded(MULTILINGUAL))
        assertFalse(models.isReady(MULTILINGUAL))
        assertNull(models.verifiedFiles(MULTILINGUAL))
        assertFalse(File(models.dirFor(MULTILINGUAL), "base-decoder.int8.onnx").exists())
        assertFalse(File(models.dirFor(MULTILINGUAL), SttModelManager.MARKER).exists())
        val state = models.stateOf(MULTILINGUAL).value
        assertTrue(state.toString(), state is SttModelManager.DownloadState.Failed)
    }

    @Test
    fun aGoodDownloadIsNotFetchedAgain() = runBlocking {
        val models = manager()
        assertTrue(models.downloadIfNeeded(MULTILINGUAL))
        assertTrue(models.downloadIfNeeded(MULTILINGUAL))
        // Second call: ready already, so nothing is asked of the server twice.
        for (name in multilingualBodies.keys) assertEquals(name, 1, requests.getValue(name).get())
    }

    @Test
    fun anInterruptedDownloadKeepsWhatItVerifiedAndFetchesOnlyTheRest() = runBlocking {
        val filesDir = temp.newFolder("files")
        // A first attempt put the encoder down and then died: no marker.
        val dir = File(filesDir, multilingual.dirName).apply { mkdirs() }
        File(dir, "base-encoder.int8.onnx").writeBytes(multilingualBodies.getValue("base-encoder.int8.onnx"))
        File(dir, "base-encoder.int8.onnx.part").writeText("half a file")

        val models = manager(filesDir)
        assertFalse(models.isReady(MULTILINGUAL))
        assertTrue(models.downloadIfNeeded(MULTILINGUAL))

        assertTrue(models.isReady(MULTILINGUAL))
        // The encoder was checked in place, not downloaded again.
        assertNull(requests["base-encoder.int8.onnx"])
        assertNotNull(requests["base-decoder.int8.onnx"])
        assertFalse(File(dir, "base-encoder.int8.onnx.part").exists())
    }

    @Test
    fun aMissingServerFileLeavesTheModelNotReady() = runBlocking {
        bodies.remove("base-tokens.txt")
        val models = manager()
        assertFalse(models.downloadIfNeeded(MULTILINGUAL))
        assertFalse(models.isReady(MULTILINGUAL))
        assertTrue(models.stateOf(MULTILINGUAL).value is SttModelManager.DownloadState.Failed)
    }

    @Test
    fun theTwoModelsKeepTheirOwnFilesAndDeletingOneKeepsTheOther() = runBlocking {
        val filesDir = temp.newFolder("files")
        // A phone that already had the legacy model.
        val legacyDir = File(filesDir, "stt").apply { mkdirs() }
        File(legacyDir, SttModelManager.ENCODER).writeText("legacy-encoder")
        File(legacyDir, SttModelManager.MARKER).writeText("v1")

        val models = manager(filesDir)
        assertTrue(models.downloadIfNeeded(MULTILINGUAL))

        // The new download wrote nothing over the old model.
        assertEquals("legacy-encoder", File(legacyDir, SttModelManager.ENCODER).readText())
        assertFalse(File(models.dirFor(MULTILINGUAL), SttModelManager.ENCODER).exists())

        // And deleting the new one leaves the old in place.
        assertTrue(models.deleteModels(MULTILINGUAL))
        assertFalse(models.dirFor(MULTILINGUAL).exists())
        assertEquals("legacy-encoder", File(legacyDir, SttModelManager.ENCODER).readText())
        assertTrue(File(legacyDir, SttModelManager.MARKER).exists())
    }

    @Test
    fun theSizeQuotedIsTheSizeOfTheFilesNotARoundNumber() = runBlocking {
        val models = manager()
        assertTrue(models.downloadIfNeeded(MULTILINGUAL))
        val onDisk = models.sizeOnDisk(MULTILINGUAL)
        assertEquals(multilingualBodies.values.sumOf { it.size.toLong() }, onDisk)
        // The published figure is what the offer quotes before the download.
        assertEquals(161_253_144L, SttModelCatalog.of(MULTILINGUAL).totalBytes)
    }

    @Test
    fun withNoDownloadSourceNothingIsFetched() = runBlocking {
        val models = SttModelManager(temp.newFolder("files"), "", files = emptyMap(), catalog = { multilingual })
        assertFalse(models.downloadEnabled)
        assertFalse(models.downloadIfNeeded(MULTILINGUAL))
        assertFalse(models.isReady(MULTILINGUAL))
    }

    @Test
    fun theLegacyModelStillDownloadsFromTheBuildUrl() = runBlocking {
        val legacyFiles = linkedMapOf(
            SttModelManager.ENCODER to "legacy-encoder".toByteArray(),
            SttModelManager.DECODER to "legacy-decoder".toByteArray(),
            SttModelManager.TOKENS to "legacy-tokens".toByteArray(),
            SttModelManager.VAD to "legacy-vad".toByteArray(),
        )
        legacyFiles.forEach { (name, body) -> bodies[name] = body }
        val legacySpec = SttModelSpec(
            set = LEGACY_EN,
            dirName = "stt",
            files = listOf(SttModelManager.ENCODER, SttModelManager.DECODER, SttModelManager.TOKENS).map { name ->
                val body = legacyFiles.getValue(name)
                ModelFileSpec(name, sha(body), body.size.toLong())
            },
            vad = ModelFileSpec(SttModelManager.VAD, sha(legacyFiles.getValue(SttModelManager.VAD)), 4L),
            totalBytes = 161_300_000L,
            marker = SttModelManager.MARKER,
        )
        val models = SttModelManager(
            filesDir = temp.newFolder("files"),
            baseUrl = baseUrl(),
            files = emptyMap(),
            catalog = { set -> if (set == LEGACY_EN) legacySpec else multilingual },
        )

        assertTrue(models.downloadIfNeeded(LEGACY_EN))
        assertTrue(models.isReady(LEGACY_EN))
        assertFalse(models.isReady(MULTILINGUAL))
        // Relative names against the build's base URL, as the old release serves them.
        assertEquals(1, requests.getValue(SttModelManager.ENCODER).get())
    }
}
