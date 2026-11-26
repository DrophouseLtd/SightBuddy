package com.example.sightbuddy.features.vision

/** Polish names and voice aliases; detector identifiers remain unchanged. */
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

    val aliases: Map<String, String> = buildMap {
        names.forEach { (id, name) -> put(name, id) }
        listOf(
            "osobę|osoby|człowiek|człowieka" to "person", "roweru" to "bicycle",
            "samochodu|auto" to "car", "motocykla" to "motorcycle", "samolotu" to "airplane",
            "autobusu" to "bus", "pociągu" to "train", "ciężarówkę|ciężarówki" to "truck",
            "łodzi" to "boat", "sygnalizację świetlną|sygnalizacji świetlnej" to "traffic light",
            "hydrantu" to "fire hydrant", "znaku stop" to "stop sign", "parkometru" to "parking meter",
            "ławkę|ławki" to "bench", "ptaka" to "bird", "kota" to "cat", "psa" to "dog",
            "konia" to "horse", "owcę|owcy" to "sheep", "krowę|krowy" to "cow", "słonia" to "elephant",
            "niedźwiedzia" to "bear", "zebrę|zebry" to "zebra", "żyrafę|żyrafy" to "giraffe",
            "plecaka" to "backpack", "parasola" to "umbrella", "torebkę|torebki" to "handbag",
            "krawata" to "tie", "walizkę|walizki" to "suitcase", "nart" to "skis",
            "deskę snowboardową|deski snowboardowej" to "snowboard", "piłkę|piłki" to "sports ball",
            "latawca" to "kite", "kija baseballowego" to "baseball bat",
            "rękawicę baseballową|rękawicy baseballowej" to "baseball glove",
            "deskorolkę|deskorolki" to "skateboard", "deskę surfingową|deski surfingowej" to "surfboard",
            "rakietę tenisową|rakiety tenisowej" to "tennis racket", "butelkę|butelki" to "bottle",
            "kieliszka|kieliszek" to "wine glass", "kubka|filiżanka|filiżankę|filiżanki" to "cup",
            "widelca" to "fork", "noża" to "knife", "łyżkę|łyżki" to "spoon", "miskę|miski" to "bowl",
            "banana" to "banana", "jabłka" to "apple", "kanapkę|kanapki" to "sandwich",
            "pomarańczę|pomarańczy" to "orange", "brokuła" to "broccoli", "marchewkę|marchewki" to "carrot",
            "pizzę|pizzy" to "pizza", "pączka" to "donut", "ciasta" to "cake", "krzesła" to "chair",
            "kanapę|kanapy|sofa|sofę|sofy" to "couch", "roślinę doniczkową|rośliny doniczkowej" to "potted plant",
            "łóżka" to "bed", "stołu" to "dining table", "toaletę|toalety" to "toilet",
            "telewizora" to "tv", "laptopa" to "laptop", "mysz komputerową|myszy komputerowej" to "mouse",
            "pilota" to "remote", "klawiaturę|klawiatury" to "keyboard", "telefonu" to "cell phone",
            "kuchenkę mikrofalową|kuchenki mikrofalowej" to "microwave", "piekarnika" to "oven",
            "tostera" to "toaster", "zlewu" to "sink", "lodówkę|lodówki" to "refrigerator",
            "książkę|książki" to "book", "zegara" to "clock", "wazonu" to "vase", "nożyczek" to "scissors",
            "pluszowego misia" to "teddy bear", "suszarkę do włosów|suszarki do włosów" to "hair drier",
            "szczoteczkę do zębów|szczoteczki do zębów" to "toothbrush"
        ).forEach { (forms, id) -> forms.split('|').forEach { put(it, id) } }
    }
}
