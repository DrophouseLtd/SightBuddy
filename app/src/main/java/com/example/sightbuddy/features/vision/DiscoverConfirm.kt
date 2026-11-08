package com.example.sightbuddy.features.vision

/**
 * Decides what Discover objects names: an object only once it has been seen
 * in [framesInARow] frames in a row, each time at or above its own score
 * ([minScore]).
 *
 * Discover used to name the top detection of every frame at the detector's
 * floor, so a guess that flickered up in one frame was said at once: cats,
 * dogs and a butterfly that were not there (issue #14). A real object stays in
 * view from frame to frame; a wrong guess seldom holds. Like Find objects'
 * [com.example.sightbuddy.core.CueBeat], each frame only records what was
 * seen; unlike it, a missed frame starts the count again, since here the
 * question is whether the thing is there at all.
 *
 * Of the objects confirmed in this frame, the most confident is returned, as
 * before. The speech cooldown still decides how often the same name repeats.
 */
class DiscoverConfirm(
    private val framesInARow: Int = DetectionRules.DISCOVER_FRAMES_IN_A_ROW,
    private val minScore: (String) -> Float = DetectionRules::discoverMinScore,
) {
    /** Label -> the number of frames in a row it has been seen, up to now. */
    private val runs = HashMap<String, Int>()

    fun reset() = runs.clear()

    /**
     * Records one frame's detections (label and score) and returns the label
     * to say, or null.
     */
    fun next(detections: List<Pair<String, Float>>): String? {
        // The best score per label: two cups in a frame are one sighting.
        val seen = HashMap<String, Float>()
        for ((label, score) in detections) {
            if (score >= minScore(label)) seen.merge(label, score, ::maxOf)
        }
        runs.keys.retainAll(seen.keys)
        for (label in seen.keys) runs[label] = (runs[label] ?: 0) + 1
        return seen.entries
            .filter { (runs[it.key] ?: 0) >= framesInARow }
            .maxByOrNull { it.value }
            ?.key
    }
}
