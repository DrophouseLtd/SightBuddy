package com.example.sightbuddy.features.vision

/**
 * What each feature takes from the object detector ([TFLiteObjectAnalyzer]).
 *
 * Find objects and Discover objects share one detector and one floor, but use
 * what it returns differently. Find objects listens for the one label the user
 * asked for, so a wrong guess of another class never reaches it.
 */
object DetectionRules {

    /**
     * The lowest score the detector returns at all, and all that Find objects
     * asks for. Raising it would make Find objects slower to find a cup.
     */
    const val DETECTOR_FLOOR = 0.4f

    // Discover objects names whatever the detector sees, so every class's wrong
    // guesses reach the user (issue #14). Its numbers are a starting point, to
    // be tuned by ear on the phone; DiscoverConfirmTest follows them.

    /**
     * Discover's score for ordinary objects. 0.5 is the default threshold of
     * TensorFlow's own example app for this model (EfficientDet-Lite), one
     * step of 0.1 above the floor. Not higher to start with: Lite0 is a small
     * model (COCO mAP about 26), and set too high Discover names nothing.
     */
    const val DISCOVER_MIN_SCORE = 0.5f

    /**
     * Discover's score for [OFTEN_WRONG], one more step of 0.1. Kept below
     * 0.7 so a real cat or dog can still be named; the frames in a row
     * ([DISCOVER_FRAMES_IN_A_ROW]) do most of the work.
     */
    const val DISCOVER_OFTEN_WRONG_MIN_SCORE = 0.6f

    /**
     * How many frames in a row Discover must see an object before naming it.
     * Discover's frames are at least 300 ms apart (its camera throttle), so 3
     * is 0.6 to 0.9 s from first sight to the word: quick enough while the
     * camera moves slowly, as Help asks, while a wrong guess must hold for
     * most of a second. With 2, a guess lasting two frames would still be said.
     */
    const val DISCOVER_FRAMES_IN_A_ROW = 3

    /**
     * The classes Discover is stricter with: the COCO animals, which is what
     * was wrongly named most (cats, dogs). An animal that is not there misleads more
     * than a missed bowl. Teddy bear is an object and is not here.
     */
    val OFTEN_WRONG: Set<String> = setOf(
        "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe",
    )

    /** The score Discover asks of [label]. Find objects does not use it. */
    fun discoverMinScore(label: String): Float =
        if (label in OFTEN_WRONG) DISCOVER_OFTEN_WRONG_MIN_SCORE else DISCOVER_MIN_SCORE

    /**
     * Find objects' pick from one frame: the first detection of [target]. No
     * extra score threshold and no confirmation over frames, so the first
     * sighting counts at once.
     */
    fun <D> findTarget(detections: List<D>, target: String, labelOf: (D) -> String): D? =
        detections.find { labelOf(it).equals(target, ignoreCase = true) }
}
