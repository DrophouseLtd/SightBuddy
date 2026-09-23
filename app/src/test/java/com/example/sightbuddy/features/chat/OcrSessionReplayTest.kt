package com.example.sightbuddy.features.chat

import com.example.sightbuddy.features.chat.TextAimGuide.Direction
import com.example.sightbuddy.features.chat.TextAimGuide.Step
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays sessions recorded on a phone by the dev build's OCR overlay (the
 * `.jsonl` files it writes under `ocr`) through [PageTracker] and [TextAimGuide].
 *
 * The first recording is the one that showed the guidance hopping: a magazine
 * page at 720 px, where the steered-by box swung between the heading and the
 * whole page and 28 cues flipped direction in 23 seconds.
 */
class OcrSessionReplayTest {

    data class Frame(val t: Long, val widthPx: Int, val heightPx: Int, val blocks: List<PageTracker.Block>)

    private fun load(name: String): List<Frame> {
        val stream = javaClass.getResourceAsStream("/ocr/$name") ?: error("missing fixture $name")
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.contains("\"blocks\"") && it.contains("\"src\":\"aim\"") }
            .map { line ->
                Frame(
                    t = number(line, "t").toLong(),
                    widthPx = number(line, "w").toInt(),
                    heightPx = number(line, "h").toInt(),
                    blocks = BLOCK.findAll(line).map { m ->
                        val (l, t, r, b, chars, lineH, text) = m.destructured
                        PageTracker.Block(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), chars.toInt(), lineH.toInt(), text)
                    }.toList(),
                )
            }
    }

    private fun number(line: String, key: String): String =
        Regex("\"$key\":(-?[0-9.]+)").find(line)!!.groupValues[1]

    data class Replayed(val t: Long, val step: Step, val target: LocalTextExtractor.TextFrame?)

    private fun replay(frames: List<Frame>): List<Replayed> {
        val tracker = PageTracker()
        val guide = TextAimGuide()
        val out = mutableListOf<Replayed>()
        for (f in frames) {
            val track = tracker.update(f.blocks, f.widthPx, f.heightPx, f.t)
            val step = guide.next(track.target, f.t)
            out += Replayed(f.t, step, track.target)
            // The app takes the picture and stops guiding here.
            if (step == Step.Capture) break
        }
        return out
    }

    private fun cuesOf(replayed: List<Replayed>): List<Pair<Long, Direction>> =
        replayed.mapNotNull { r -> (r.step as? Step.Cue)?.let { r.t to it.direction } }

    private fun describe(replayed: List<Replayed>): String {
        val t0 = replayed.first().t
        return replayed.joinToString("\n") { r ->
            val x = r.target?.let { "x %.2f-%.2f".format(it.left, it.right) } ?: "-"
            "%5.1fs %-14s %s".format((r.t - t0) / 1000.0, x, if (r.step == Step.Silent) "" else r.step)
        }
    }

    private fun opposite(a: Direction, b: Direction) =
        setOf(a, b) == setOf(Direction.LEFT, Direction.RIGHT) ||
            setOf(a, b) == setOf(Direction.UP, Direction.DOWN) ||
            setOf(a, b) == setOf(Direction.CLOSER, Direction.FURTHER)

    @Test
    fun `the magazine page no longer flips the cue back and forth`() {
        val replayed = replay(load("magazine-720px-2026-09-19.jsonl"))
        println(describe(replayed))
        val cues = cuesOf(replayed)

        // The union-box guide changed direction 24 times on this recording. The
        // phone really was moving, so some changes are right; half is the bar.
        val changes = cues.zipWithNext().count { (a, b) -> a.second != b.second }
        assertTrue("too many changes of direction: $changes", changes <= 12)

        val flips = cues.zipWithNext().filter { (a, b) -> opposite(a.second, b.second) && b.first - a.first < 1_500L }
        assertTrue("opposite cues within 1.5 s: $flips", flips.isEmpty())
    }

    /** A session's guidance runs: frames with no gap of more than [gapMs]. */
    private fun runsOf(frames: List<Frame>, gapMs: Long = 3_000L): List<List<Frame>> {
        val runs = mutableListOf(mutableListOf<Frame>())
        for (f in frames) {
            val last = runs.last().lastOrNull()
            if (last != null && f.t - last.t > gapMs) runs += mutableListOf<Frame>()
            runs.last() += f
        }
        return runs
    }

    /** Times the target's middle moved more than a third of the frame between frames. */
    private fun jumpsIn(replayed: List<Replayed>): Int = replayed.zipWithNext().count { (a, b) ->
        val ax = a.target ?: return@count false
        val bx = b.target ?: return@count false
        kotlin.math.abs((bx.left + bx.right) / 2f - (ax.left + ax.right) / 2f) > 0.33f
    }

    @Test
    fun `the text fallback on the magazine recordings`() {
        // Without the paper (these recordings have no photos) and without the
        // page lock, which was taken out: the largest text page each frame.
        // The bounds are what this tracker does on them, so a change that
        // makes it hop more shows up here.
        val bounds = mapOf(
            "magazine-spread-1440px-2026-09-19.jsonl" to JUMP_BOUND_SPREAD,
            "magazine-spread-runs-2026-09-19.jsonl" to JUMP_BOUND_RUNS,
            "magazine-clean-table-2026-09-19.jsonl" to JUMP_BOUND_CLEAN,
        )
        for ((name, bound) in bounds) {
            val jumps = runsOf(load(name)).sumOf { run -> jumpsIn(replay(run)) }
            println("$name: target jumped $jumps times")
            assertTrue("$name: target jumped $jumps times", jumps <= bound)
        }
    }

    private companion object {
        const val JUMP_BOUND_SPREAD = 2
        const val JUMP_BOUND_RUNS = 1
        const val JUMP_BOUND_CLEAN = 1
        val BLOCK = Regex(
            "\\{\"l\":(-?[0-9.]+),\"t\":(-?[0-9.]+),\"r\":(-?[0-9.]+),\"b\":(-?[0-9.]+)," +
                "\"chars\":(\\d+),\"lineH\":(\\d+)(?:,\"kept\":\\w+)?,\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"\\}"
        )
    }
}
