package com.example.sightbuddy.core.stt

import com.example.sightbuddy.core.ModelSetup

/**
 * The two offline Whisper models this app knows, each with its own folder and
 * its own completion marker so neither can be mistaken for, or delete, the
 * other.
 *
 * - [MULTILINGUAL]: OpenAI Whisper base (not base.en), converted to ONNX int8 by
 *   k2-fsa sherpa-onnx, pinned to one Hugging Face revision. Recognises Polish,
 *   Finnish, English and the rest of Whisper's languages on the phone, offline.
 * - [LEGACY_EN]: the base.en int8 files the app shipped before this one, from the
 *   project's own GitHub release. English only. Kept so a phone that already has
 *   it keeps working (and keeps its 150 MB) until the multilingual model is
 *   downloaded; nothing removes it behind the user's back.
 */
enum class SttModelSet {
    MULTILINGUAL,
    LEGACY_EN,
}

/**
 * One model file: where it comes from, how big it is and what it must hash to.
 * [absoluteUrl] is used as is (the multilingual files live on Hugging Face, at a
 * pinned revision); when null the file is fetched from the build's
 * `STT_MODEL_BASE_URL`, as the legacy release is.
 */
class ModelFileSpec(
    val name: String,
    val sha256: String,
    /** The size the publisher states for this exact file, in bytes. */
    val bytes: Long,
    val absoluteUrl: String? = null,
) {
    fun url(baseUrl: String): String =
        absoluteUrl ?: (baseUrl.trimEnd('/') + "/" + name)
}

/**
 * Everything one model set is: its folder, its four files (encoder, decoder,
 * tokens, VAD), its marker and the size the user is quoted.
 */
class SttModelSpec(
    val set: SttModelSet,
    /** Folder under `filesDir`, keeping the sets apart. */
    val dirName: String,
    /** Encoder, decoder, tokens — in that order. */
    val files: List<ModelFileSpec>,
    /** Silero voice-activity detection, pinned to the release that already serves it. */
    val vad: ModelFileSpec,
    /** The whole download as the UI quotes it. */
    val totalBytes: Long,
    val marker: String,
) {
    val encoder: ModelFileSpec get() = files[0]
    val decoder: ModelFileSpec get() = files[1]
    val tokens: ModelFileSpec get() = files[2]

    /** Every file the set needs, the VAD last. */
    val allFiles: List<ModelFileSpec> get() = files + vad
}

object SttModelCatalog {

    /** The pinned Hugging Face revision of csukuangfj/sherpa-onnx-whisper-base. */
    const val HUGGING_FACE_REVISION = "bb53ee204431c90d314c1cc08d28d23e5b7927cc"

    private const val HUGGING_FACE_BASE =
        "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/" + HUGGING_FACE_REVISION

    /** The silero VAD the app has always downloaded; unchanged, same bytes. */
    const val VAD_NAME = SttModelManager.VAD
    const val VAD_SHA256 = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"
    const val VAD_BYTES = 643_854L
    const val VAD_URL =
        "https://github.com/DrophouseLtd/sightbuddy-stt-models/releases/download/whisper-base-en-v1/silero_vad.onnx"

    /**
     * The multilingual model, as measured by the model pilot: byte sizes and
     * SHA-256 of the three files at [HUGGING_FACE_REVISION]. The hashes are the
     * archive-extracted files, compared with Hugging Face's own LFS metadata;
     * the tokens were downloaded separately at the same revision and compared
     * byte for byte. See `.local/qa/multilingual/model-pilot/MANIFEST.json`.
     */
    private val MULTILINGUAL_FILES = listOf(
        ModelFileSpec(
            name = "base-encoder.int8.onnx",
            sha256 = "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11",
            bytes = 29_120_534L,
            absoluteUrl = "$HUGGING_FACE_BASE/base-encoder.int8.onnx",
        ),
        ModelFileSpec(
            name = "base-decoder.int8.onnx",
            sha256 = "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d",
            bytes = 130_672_026L,
            absoluteUrl = "$HUGGING_FACE_BASE/base-decoder.int8.onnx",
        ),
        ModelFileSpec(
            name = "base-tokens.txt",
            sha256 = "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
            bytes = 816_730L,
            absoluteUrl = "$HUGGING_FACE_BASE/base-tokens.txt",
        ),
    )

    private val LEGACY_VAD = ModelFileSpec(VAD_NAME, VAD_SHA256, VAD_BYTES, VAD_URL)

    private val MULTILINGUAL = SttModelSpec(
        set = SttModelSet.MULTILINGUAL,
        // A sibling of "stt", not a folder inside it: deleting the old model must
        // never take the new one with it.
        dirName = "stt-multilingual",
        files = MULTILINGUAL_FILES,
        vad = LEGACY_VAD,
        totalBytes = MULTILINGUAL_FILES.sumOf { it.bytes } + VAD_BYTES,
        marker = ".download-complete",
    )

    /**
     * The legacy files as the manager holds them: names and hashes come from
     * [SttModelManager.FILES] so there is one pin per file, not two that could
     * drift apart. Its quoted size stays the figure the app already showed.
     */
    private fun legacy(files: Map<String, SttModelManager.Pinned>): SttModelSpec {
        fun spec(name: String, absoluteUrl: String? = null) = ModelFileSpec(
            name = name,
            sha256 = files.getValue(name).sha256,
            bytes = files.getValue(name).minBytes,
            absoluteUrl = absoluteUrl,
        )
        return SttModelSpec(
            set = SttModelSet.LEGACY_EN,
            dirName = "stt",
            files = listOf(spec(SttModelManager.ENCODER), spec(SttModelManager.DECODER), spec(SttModelManager.TOKENS)),
            vad = spec(SttModelManager.VAD, VAD_URL),
            totalBytes = ModelSetup.WHISPER_BYTES,
            marker = SttModelManager.MARKER,
        )
    }

    internal fun of(set: SttModelSet, files: Map<String, SttModelManager.Pinned> = SttModelManager.FILES): SttModelSpec =
        when (set) {
            SttModelSet.MULTILINGUAL -> MULTILINGUAL
            SttModelSet.LEGACY_EN -> legacy(files)
        }
}
