package com.example.sightbuddy

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sightbuddy.core.LanguageStore
import com.example.sightbuddy.core.stt.SttModelManager
import com.example.sightbuddy.core.stt.WhisperEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class MultilingualWhisperInstrumentedTest {
    /** Backend smoke, not an accuracy benchmark. Original quality probe is retained in QA. */
    @Test fun downloadVerifyAndDecodeLanguageSmoke() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "multilingual-whisper-qa")
        val manager = SttModelManager(root, BuildConfig.STT_MODEL_BASE_URL)
        assertTrue("Real model download failed", manager.downloadIfNeeded())
        val files = requireNotNull(manager.verifiedFiles())
        val languageStore = LanguageStore(context)
        val original = languageStore.language()
        try {
            for ((language, word) in listOf("pl" to "oczy", "fi" to "budjetti", "en" to "gospel")) {
                languageStore.setLanguage(language)
                assertEquals(language, languageStore.language())
                val engine = requireNotNull(WhisperEngine.load(files, languageStore.language()))
                try {
                    val bytes = File(context.filesDir, "stt-qa-$language.f32").readBytes()
                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    val samples = FloatArray(buffer.remaining()); buffer.get(samples)
                    val text = engine.transcribe(samples)
                    println("MULTILINGUAL_JNI $language $text")
                    assertTrue("No recognition for $language", text.isNotBlank())
                    val evidence = android.os.Bundle().apply {
                        putString("language", language)
                        putString("transcript", text)
                        putString("quality_control_word_present", text.contains(word, ignoreCase = true).toString())
                    }
                    InstrumentationRegistry.getInstrumentation().sendStatus(2, evidence)
                } finally { engine.release() }
            }
        } finally { languageStore.setLanguage(original) }
    }
}
