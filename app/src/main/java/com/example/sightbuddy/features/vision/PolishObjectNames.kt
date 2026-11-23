package com.example.sightbuddy.features.vision

/** Polish display words only. Detector identifiers and voice matching stay unchanged. */
internal object PolishObjectNames {
    fun displayName(identifier: String): String = names[identifier] ?: identifier

    private val names = mapOf(
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
        "clock" to "zegar", "vase" to "wazon", "scissors" to "nożyczki", "teddy bear" to "pluszowy miś",
        "hair drier" to "suszarka do włosów", "toothbrush" to "szczoteczka do zębów",
    )
}
