package com.example.sightbuddy.features.vision

/**
 * Default visibility for the Find-objects picker (UI only).
 * Curated for everyday UK situations; exotic animals and niche sports are
 * hidden until the user enables them in settings.
 */
val DEFAULT_VISIBLE_COCO_OBJECTS: Set<String> = setOf(
    "person", "bicycle", "car", "motorcycle", "bus", "train", "truck",
    "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
    "cat", "dog",
    "backpack", "umbrella", "handbag", "tie", "suitcase",
    "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl",
    "banana", "apple", "sandwich", "orange", "broccoli", "carrot",
    "hot dog", "pizza", "donut", "cake",
    "chair", "couch", "potted plant", "bed", "dining table", "toilet",
    "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
    "microwave", "oven", "toaster", "sink", "refrigerator",
    "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush",
)

fun defaultHiddenCocoObjects(): Set<String> =
    COCO_OBJECTS.filter { it !in DEFAULT_VISIBLE_COCO_OBJECTS }.toSet()

fun filterVisibleCocoObjects(hidden: Set<String>): List<String> =
    COCO_OBJECTS.filter { it !in hidden }
