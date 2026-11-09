package com.example.sightbuddy.core.stt

import com.example.sightbuddy.core.ModelChecksums
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/** Files already on the phone: only ones that match their pinned SHA-256 are handed out. */
class SttModelVerifyTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val contents = mapOf(
        SttModelManager.ENCODER to "encoder",
        SttModelManager.DECODER to "decoder",
        SttModelManager.TOKENS to "tokens",
        SttModelManager.VAD to "vad",
    )

    private fun sha(text: String) = ModelChecksums.sha256(ByteArrayInputStream(text.toByteArray()))

    private val pins = contents.mapValues { (_, text) -> SttModelManager.Pinned(1L, sha(text)) }

    /** A finished download as an earlier version left it: every file and the marker. */
    private fun installed(vararg replaced: Pair<String, String>): SttModelManager {
        val filesDir = temp.newFolder("files")
        val dir = File(filesDir, "stt").apply { mkdirs() }
        val texts = contents + replaced
        texts.forEach { (name, text) -> File(dir, name).writeText(text) }
        File(dir, SttModelManager.MARKER).writeText("v1")
        return SttModelManager(filesDir, baseUrl = "", files = pins)
    }

    @Test
    fun matchingFilesAreHandedOutForLoading() {
        val models = installed()
        val files = models.verifiedFiles()
        assertNotNull(files)
        assertEquals("encoder", files!!.encoder.file.readText())
        assertEquals("vad", files.vad.file.readText())
        assertEquals(SttModelManager.DownloadState.Ready, models.downloadState.value)
        assertFalse(models.removedAsDamaged.value)
    }

    @Test
    fun aDamagedFileIsNeverHandedOutAndTheModelsCountAsMissing() {
        val models = installed(SttModelManager.DECODER to "tampered")
        assertNull(models.verifiedFiles())
        assertFalse(File(models.modelDir, SttModelManager.DECODER).exists())
        assertFalse(models.isReady())
        assertEquals(SttModelManager.DownloadState.Idle, models.downloadState.value)
        // Said once, then marked as said.
        assertTrue(models.removedAsDamaged.value)
        models.damagedNoticeSaid()
        assertFalse(models.removedAsDamaged.value)
    }

    @Test
    fun withoutTheMarkerNothingIsHandedOut() {
        val models = installed()
        File(models.modelDir, SttModelManager.MARKER).delete()
        assertNull(models.verifiedFiles())
    }

    @Test
    fun theVadIsHandedOutOnlyWhenItMatches() {
        assertEquals("vad", installed().verifiedVad()?.file?.readText())
        temp.delete()
        temp.create()
        val damaged = installed(SttModelManager.VAD to "tampered")
        assertNull(damaged.verifiedVad())
        assertFalse(File(damaged.modelDir, SttModelManager.VAD).exists())
        assertTrue(damaged.removedAsDamaged.value)
    }
}
