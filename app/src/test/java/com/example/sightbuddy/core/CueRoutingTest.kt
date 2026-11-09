package com.example.sightbuddy.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every sound or vibration the app makes goes through [Cues], which obeys
 * Settings' Sound effects and Vibration. A sample played, or a vibration
 * started, straight from anywhere else would slip past the switches, so the
 * sources are read for such calls. Only the devices themselves and the one
 * place that wires them to [Cues] may make them. Stopping is not checked:
 * stopping is always allowed.
 */
class CueRoutingTest {

    private val allowed = setOf(
        "SoundFXService.kt",
        "HapticManager.kt",
        "PitchToneService.kt",
        "CueWiring.kt",
        "BeepService.kt",
    )

    /** Calls that make a sound or a vibration. */
    private val making = listOf(
        Regex("""\bSFX\.[A-Z_]+"""),
        Regex("""\.playAlongside\("""),
        Regex("""\bproximityPulse\("""),
        Regex("""\bstepFor\("""),
        Regex("""\b(vibrateWarning|vibrateAlert)\("""),
        Regex("""\b(hapticManager|haptics)\.(tap|tick)\("""),
        Regex("""\b(pitchToneService|soundFXService)\.(start|play)\("""),
        // The devices' own APIs, and the unused beeper, anywhere else (audit, 2026-10-28).
        Regex("""\bbeepService\.(?!shutdown\b)"""),
        Regex("""\b(ToneGenerator|MediaPlayer|SoundPool|AudioTrack)\b"""),
        Regex("""\.vibrate\("""),
    )

    @Test
    fun noSoundOrVibrationIsMadeOutsideTheCues() {
        // Unit tests run in the app module's folder.
        val sources = File("src/main/java")
        assertTrue(sources.absolutePath, sources.isDirectory)
        val found = sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in allowed }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> !line.trimStart().startsWith("//") && !line.trimStart().startsWith("*") }
                    .filter { (_, line) -> making.any { it.containsMatchIn(line) } }
                    .map { (i, line) -> "${file.name}:${i + 1}: ${line.trim()}" }
            }
            .toList()

        assertEquals("Sounds or vibration made outside Cues", emptyList<String>(), found)
    }
}
