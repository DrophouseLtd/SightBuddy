package com.example.sightbuddy.features.chat

/**
 * Text in view, as the recogniser found it, placed where it sits in the camera
 * frame. [generation] counts up each time the words change; between changes
 * the blocks only move.
 */
data class LiveText(
    val blocks: List<Block>,
    val frameWidthPx: Int,
    val frameHeightPx: Int,
    val generation: Int = 0,
) {
    /** One block of text; its box as fractions of the frame, its line height in frame pixels. */
    data class Block(
        val text: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val lineHeightPx: Int,
    )
}
