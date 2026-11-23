package com.example.sightbuddy.features.vision

import java.util.Locale

/**
 * Finnish names and voice aliases for the COCO objects.
 *
 * The COCO labels themselves stay English everywhere in the app's logic — they
 * are the detector's own vocabulary and the identifiers the resolver activates
 * on. This layer only adds, for Finnish:
 *
 *  - [displayName]: what the user reads in the picker and hears announced.
 *  - [aliases]: Finnish words (plus common synonyms and inflected forms) that
 *    map *back* to the English label, so saying "kuppi" activates "cup".
 *
 * Everything here is inert unless the app language is Finnish, so the English
 * build behaves exactly as before.
 */
object CocoFinnish {

    fun isActive(): Boolean = Locale.getDefault().language == "fi"

    /** Finnish name for a COCO label, or the label itself in any other language. */
    fun displayName(label: String): String =
        if (isActive()) NAMES[label] ?: label else label

    /** Finnish voice aliases, or empty when the app is not in Finnish. */
    fun aliases(): Map<String, String> = if (isActive()) ALIASES else emptyMap()

    /** English COCO label -> Finnish name shown and spoken. */
    private val NAMES: Map<String, String> = mapOf(
        "person" to "henkilö", "bicycle" to "polkupyörä", "car" to "auto",
        "motorcycle" to "moottoripyörä", "airplane" to "lentokone", "bus" to "bussi",
        "train" to "juna", "truck" to "kuorma-auto", "boat" to "vene",
        "traffic light" to "liikennevalo", "fire hydrant" to "paloposti",
        "stop sign" to "stop-merkki", "parking meter" to "pysäköintimittari",
        "bench" to "penkki", "bird" to "lintu", "cat" to "kissa", "dog" to "koira",
        "horse" to "hevonen", "sheep" to "lammas", "cow" to "lehmä",
        "elephant" to "norsu", "bear" to "karhu", "zebra" to "seepra",
        "giraffe" to "kirahvi", "backpack" to "reppu", "umbrella" to "sateenvarjo",
        "handbag" to "käsilaukku", "tie" to "solmio", "suitcase" to "matkalaukku",
        "frisbee" to "frisbee", "skis" to "sukset", "snowboard" to "lumilauta",
        "sports ball" to "pallo", "kite" to "leija", "baseball bat" to "pesäpallomaila",
        "baseball glove" to "räpylä", "skateboard" to "rullalauta",
        "surfboard" to "surffilauta", "tennis racket" to "tennismaila",
        "bottle" to "pullo", "wine glass" to "viinilasi", "cup" to "kuppi",
        "fork" to "haarukka", "knife" to "veitsi", "spoon" to "lusikka",
        "bowl" to "kulho", "banana" to "banaani", "apple" to "omena",
        "sandwich" to "voileipä", "orange" to "appelsiini", "broccoli" to "parsakaali",
        "carrot" to "porkkana", "hot dog" to "nakkisämpylä", "pizza" to "pizza",
        "donut" to "donitsi", "cake" to "kakku", "chair" to "tuoli",
        "couch" to "sohva", "potted plant" to "ruukkukasvi", "bed" to "sänky",
        "dining table" to "ruokapöytä", "toilet" to "wc", "tv" to "televisio",
        "laptop" to "kannettava tietokone", "mouse" to "hiiri",
        "remote" to "kaukosäädin", "keyboard" to "näppäimistö",
        "cell phone" to "kännykkä", "microwave" to "mikroaaltouuni", "oven" to "uuni",
        "toaster" to "leivänpaahdin", "sink" to "pesuallas",
        "refrigerator" to "jääkaappi", "book" to "kirja", "clock" to "kello",
        "vase" to "maljakko", "scissors" to "sakset", "teddy bear" to "nallekarhu",
        "hair drier" to "hiustenkuivaaja", "toothbrush" to "hammasharja",
    )

    /**
     * Finnish spoken word -> English COCO label.
     *
     * Includes each name plus everyday synonyms and the partitive/colloquial
     * forms people actually say ("kuppia", "läppäri", "vessa"). The resolver's
     * fuzzy matching covers the remaining inflections.
     */
    private val ALIASES: Map<String, String> = buildMap {
        // Every Finnish name resolves back to its label.
        NAMES.forEach { (label, fi) -> put(fi, label) }

        putAll(
            mapOf(
                "ihminen" to "person", "mies" to "person", "nainen" to "person",
                "pyörä" to "bicycle", "fillari" to "bicycle", "polkupyörää" to "bicycle",
                "autoa" to "car", "henkilöauto" to "car",
                "moottoripyörää" to "motorcycle", "mopo" to "motorcycle",
                "kone" to "airplane", "lentokonetta" to "airplane",
                "linja-auto" to "bus", "bussia" to "bus",
                "junaa" to "train", "rekka" to "truck", "kuorma-autoa" to "truck",
                "venettä" to "boat", "liikennevaloa" to "traffic light",
                "paloposti" to "fire hydrant", "stop merkki" to "stop sign",
                "penkkiä" to "bench", "lintua" to "bird",
                "kissaa" to "cat", "koiraa" to "dog", "hevosta" to "horse",
                "lammasta" to "sheep", "lehmää" to "cow", "norsua" to "elephant",
                "karhua" to "bear", "reppua" to "backpack",
                "sateenvarjoa" to "umbrella", "laukku" to "handbag",
                "käsilaukkua" to "handbag", "kassi" to "handbag",
                "solmiota" to "tie", "matkalaukkua" to "suitcase",
                "suksi" to "skis", "lauta" to "snowboard",
                "pallo" to "sports ball", "palloa" to "sports ball",
                "maila" to "baseball bat", "skeitti" to "skateboard",
                "pulloa" to "bottle", "lasi" to "wine glass", "viinilasia" to "wine glass",
                "muki" to "cup", "kuppia" to "cup", "mukia" to "cup",
                "haarukkaa" to "fork", "veistä" to "knife", "lusikkaa" to "spoon",
                "kulhoa" to "bowl", "banaania" to "banana", "omenaa" to "apple",
                "leipä" to "sandwich", "voileipää" to "sandwich",
                "appelsiinia" to "orange", "porkkanaa" to "carrot",
                "hodari" to "hot dog", "nakki" to "hot dog",
                "pizzaa" to "pizza", "donitsia" to "donut", "kakkua" to "cake",
                "tuolia" to "chair", "sohvaa" to "couch",
                "kasvi" to "potted plant", "ruukkukasvia" to "potted plant",
                "vuode" to "bed", "sänkyä" to "bed",
                "pöytä" to "dining table", "ruokapöytää" to "dining table",
                "vessa" to "toilet", "pytty" to "toilet", "vessaa" to "toilet",
                "telkkari" to "tv", "tv" to "tv", "televisiota" to "tv",
                "läppäri" to "laptop", "kannettava" to "laptop", "tietokone" to "laptop",
                "hiirtä" to "mouse", "kaukosäädintä" to "remote", "kaukkari" to "remote",
                "näppäimistöä" to "keyboard", "puhelin" to "cell phone",
                "matkapuhelin" to "cell phone", "kännykkää" to "cell phone",
                "puhelinta" to "cell phone", "mikro" to "microwave",
                "mikroaaltouunia" to "microwave", "uunia" to "oven",
                "paahdin" to "toaster", "allas" to "sink", "tiskiallas" to "sink",
                "pesuallasta" to "sink", "jääkaappia" to "refrigerator",
                "kirjaa" to "book", "kelloa" to "clock", "maljakkoa" to "vase",
                "saksia" to "scissors", "nalle" to "teddy bear",
                "nallekarhua" to "teddy bear", "fööni" to "hair drier",
                "hiustenkuivaajaa" to "hair drier", "hammasharjaa" to "toothbrush",
            )
        )
    }
}
