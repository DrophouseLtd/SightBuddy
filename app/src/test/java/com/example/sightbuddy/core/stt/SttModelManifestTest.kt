package com.example.sightbuddy.core.stt

import com.example.sightbuddy.core.ModelSetup
import com.example.sightbuddy.core.stt.SttModelSet.LEGACY_EN
import com.example.sightbuddy.core.stt.SttModelSet.MULTILINGUAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two Whisper models' manifests: what each needs, where it goes and what it
 * must hash to. The multilingual model is OpenAI Whisper base (not base.en),
 * converted by k2-fsa sherpa-onnx, pinned to one Hugging Face revision; the
 * legacy base.en files stay exactly as they shipped and are never deleted by a
 * download of the new model.
 */
class SttModelManifestTest {

    private val multilingual = SttModelCatalog.of(MULTILINGUAL)
    private val legacy = SttModelCatalog.of(LEGACY_EN)
    private val small = SttModelCatalog.of(SttModelSet.SMALL)

    @Test
    fun theMultilingualModelIsPinnedToItsHuggingFaceFiles() {
        assertEquals(
            listOf("base-encoder.int8.onnx", "base-decoder.int8.onnx", "base-tokens.txt"),
            multilingual.files.map { it.name },
        )
        assertEquals(
            mapOf(
                "base-encoder.int8.onnx" to
                    "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11",
                "base-decoder.int8.onnx" to
                    "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d",
                "base-tokens.txt" to
                    "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
            ),
            multilingual.files.associate { it.name to it.sha256 },
        )
        assertEquals(
            listOf(29_120_534L, 130_672_026L, 816_730L),
            multilingual.files.map { it.bytes },
        )
    }

    @Test
    fun everyFileHasARealHttpsUrlAtThePinnedRevision() {
        val revision = "bb53ee204431c90d314c1cc08d28d23e5b7927cc"
        for (file in multilingual.files) {
            val url = file.url("")
            assertTrue(url, url.startsWith("https://huggingface.co/"))
            assertTrue(url, url.contains("/resolve/$revision/"))
            assertTrue(url, url.endsWith("/${file.name}"))
            assertTrue(file.sha256, file.sha256.matches(Regex("[0-9a-f]{64}")))
        }
        // The VAD is shared with the legacy download and comes from the release
        // that already serves it: its pinned GitHub bytes, unchanged.
        assertEquals(
            "https://github.com/DrophouseLtd/sightbuddy-stt-models/releases/download/whisper-base-en-v1/silero_vad.onnx",
            multilingual.vad.url(""),
        )
        assertEquals("9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6", multilingual.vad.sha256)
        assertEquals(643_854L, multilingual.vad.bytes)
    }

    @Test
    fun theTwoModelsLiveInTheirOwnFoldersSoNeitherDeletesTheOther() {
        assertNotEquals(multilingual.dirName, legacy.dirName)
        // The legacy folder and file names are what phones already have.
        assertEquals("stt", legacy.dirName)
        assertEquals(SttModelManager.ENCODER, legacy.files[0].name)
        assertEquals(SttModelManager.DECODER, legacy.files[1].name)
        assertEquals(SttModelManager.TOKENS, legacy.files[2].name)
        assertEquals(SttModelManager.VAD, legacy.vad.name)
        assertEquals("stt-multilingual", multilingual.dirName)
    }

    @Test
    fun eachModelCountsEveryFileForItsSize() {
        val expected = multilingual.files.sumOf { it.bytes } + multilingual.vad.bytes
        assertEquals(161_253_144L, expected)
        assertEquals(expected, multilingual.totalBytes)
        // The legacy spec's quoted size stays the figure the UI already showed.
        assertEquals(ModelSetup.WHISPER_BYTES, legacy.totalBytes)
    }

    @Test
    fun theManifestMatchesTheFilesThePilotVerified() {
        // The pilot measured these exact files; a manifest that drifts from it is
        // a different model than the one proven to recognise Polish and Finnish.
        val pilot = java.io.File(".local/qa/multilingual/model-pilot/MANIFEST.json")
        if (!pilot.isFile) return // unit tests run from the module dir; skip outside the repo
        val text = pilot.readText()
        for (file in multilingual.files) {
            assertTrue("${file.name} missing from the pilot manifest", text.contains(file.name))
            assertTrue("${file.sha256} missing from the pilot manifest", text.contains(file.sha256))
            assertTrue("${file.bytes} missing from the pilot manifest", text.contains(file.bytes.toString()))
        }
    }
}

/**
 * The optional small model: its own folder (so deleting it cannot touch the
 * base model), its own pinned Hugging Face revision, and byte sizes that match
 * the files the pilot downloaded and verified against the GitHub archive.
 */
class SttModelSmallManifestTest {

    private val small = SttModelCatalog.of(SttModelSet.SMALL)

    @Test
    fun theSmallModelIsPinnedToItsOwnFolderAndRevision() {
        assertEquals("stt-small", small.dirName)
        assertNotEquals(small.dirName, SttModelCatalog.of(MULTILINGUAL).dirName)
        assertEquals(
            listOf("small-encoder.int8.onnx", "small-decoder.int8.onnx", "small-tokens.txt"),
            small.files.map { it.name },
        )
        assertEquals(
            listOf(112_442_483L, 262_226_114L, 816_730L),
            small.files.map { it.bytes },
        )
        val revision = SttModelCatalog.SMALL_REVISION
        for (file in small.files) {
            val url = file.url("")
            assertTrue(url, url.startsWith("https://huggingface.co/"))
            assertTrue(url, url.contains("/resolve/$revision/"))
            assertTrue(file.sha256, file.sha256.matches(Regex("[0-9a-f]{64}")))
        }
        assertEquals("4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9", small.files[0].sha256)
        assertEquals("acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee", small.files[1].sha256)
        // The quoted download size is the files plus the shared VAD.
        assertEquals(112_442_483L + 262_226_114L + 816_730L + 643_854L, small.totalBytes)
    }

    @Test
    fun theSmallModelIsNeverPartOfTheRecommendedSetup() {
        // The setup offer and its plans stay on the base multilingual model;
        // small is a deliberate download from Settings only.
        val whisperOnly = SetupDownloadPlan.whisperOnly()
        assertTrue(whisperOnly.whisperSets.none { it == SttModelSet.SMALL })
        for (tier in com.example.sightbuddy.core.ModelSetup.Tier.entries) {
            val plan = SetupDownloadPlan.forTier(tier, gemmaAccepted = true)
            assertTrue(plan.whisperSets.none { it == SttModelSet.SMALL })
        }
    }
}
