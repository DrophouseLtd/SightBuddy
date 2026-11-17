package com.example.sightbuddy.features.vision

import java.util.Locale

/** English detector identifiers stay unchanged; names are nominative for display. */
object CocoPolish {
    fun isActive() = Locale.getDefault().language == "pl"
    fun displayName(label: String) = if (isActive()) names[label] ?: label else label
    fun aliases(): Map<String, String> = if (isActive()) words else emptyMap()

    val names = mapOf(
        "person" to "osoba", "bicycle" to "rower", "car" to "samochód",
        "motorcycle" to "motocykl", "airplane" to "samolot", "bus" to "autobus",
        "train" to "pociąg", "truck" to "ciężarówka", "boat" to "łódź",
        "traffic light" to "sygnalizacja świetlna", "fire hydrant" to "hydrant",
        "stop sign" to "znak stop", "parking meter" to "parkometr", "bench" to "ławka",
        "bird" to "ptak", "cat" to "kot", "dog" to "pies", "horse" to "koń",
        "sheep" to "owca", "cow" to "krowa", "elephant" to "słoń", "bear" to "niedźwiedź",
        "zebra" to "zebra", "giraffe" to "żyrafa", "backpack" to "plecak",
        "umbrella" to "parasol", "handbag" to "torebka", "tie" to "krawat",
        "suitcase" to "walizka", "frisbee" to "frisbee", "skis" to "narty",
        "snowboard" to "deska snowboardowa", "sports ball" to "piłka", "kite" to "latawiec",
        "baseball bat" to "kij baseballowy", "baseball glove" to "rękawica baseballowa",
        "skateboard" to "deskorolka", "surfboard" to "deska surfingowa", "tennis racket" to "rakieta tenisowa",
        "bottle" to "butelka", "wine glass" to "kieliszek do wina", "cup" to "kubek",
        "fork" to "widelec", "knife" to "nóż", "spoon" to "łyżka", "bowl" to "miska",
        "banana" to "banan", "apple" to "jabłko", "sandwich" to "kanapka", "orange" to "pomarańcza",
        "broccoli" to "brokuł", "carrot" to "marchewka", "hot dog" to "hot dog",
        "pizza" to "pizza", "donut" to "pączek", "cake" to "ciasto", "chair" to "krzesło",
        "couch" to "kanapa", "potted plant" to "roślina doniczkowa", "bed" to "łóżko",
        "dining table" to "stół", "toilet" to "toaleta", "tv" to "telewizor",
        "laptop" to "laptop", "mouse" to "mysz komputerowa", "remote" to "pilot", "keyboard" to "klawiatura",
        "cell phone" to "telefon", "microwave" to "kuchenka mikrofalowa", "oven" to "piekarnik",
        "toaster" to "toster", "sink" to "zlew", "refrigerator" to "lodówka", "book" to "książka",
        "clock" to "zegar", "vase" to "wazon", "scissors" to "nożyczki", "teddy bear" to "miś",
        "hair drier" to "suszarka do włosów", "toothbrush" to "szczoteczka do zębów",
    )
    private val words = buildMap {
        names.forEach { (label, name) -> put(name, label) }
        putAll(mapOf(
            "człowiek" to "person", "auto" to "car", "kota" to "cat", "psa" to "dog",
            "książkę" to "book", "książki" to "book", "krzesła" to "chair", "kubka" to "cup",
            "filiżanka" to "cup", "filiżankę" to "cup", "butelkę" to "bottle", "butelki" to "bottle",
            "telefonu" to "cell phone", "komórka" to "cell phone", "komórkę" to "cell phone",
            "stolik" to "dining table", "stołu" to "dining table", "sofa" to "couch",
            "lodówkę" to "refrigerator", "lodówki" to "refrigerator",
            "osobę" to "person", "osoby" to "person", "człowieka" to "person",
            "komórki" to "cell phone", "filiżanki" to "cup", "widelca" to "fork",
            "widelce" to "fork", "noża" to "knife", "łyżkę" to "spoon", "łyżki" to "spoon",
            "parasola" to "umbrella", "samochodu" to "car", "samochód" to "car",
            "auta" to "car", "roweru" to "bicycle", "plecaka" to "backpack",
            "torebkę" to "handbag", "torebki" to "handbag", "walizkę" to "suitcase", "walizki" to "suitcase",
            "krzesło" to "chair", "kanapę" to "couch", "kanapy" to "couch", "sofę" to "couch", "sofy" to "couch",
            "łóżka" to "bed", "stołem" to "dining table", "telewizora" to "tv", "pilota" to "remote",
            "laptopa" to "laptop", "myszkę komputerową" to "mouse", "myszki komputerowej" to "mouse",
            "myszy komputerowej" to "mouse", "myszy do komputera" to "mouse", "myszka do komputera" to "mouse",
            "klawiaturę" to "keyboard", "klawiatury" to "keyboard",
            "kubek" to "cup", "miski" to "bowl", "miskę" to "bowl", "jabłka" to "apple", "banana" to "banana",
            "zegara" to "clock", "szczoteczkę do zębów" to "toothbrush",
            "psów" to "dog", "kotów" to "cat"
        ))
    }
}
