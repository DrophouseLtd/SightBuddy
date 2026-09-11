package com.example.sightbuddy.features.vision

import android.util.Log
import com.example.sightbuddy.core.OpenAiTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ObjectCommandResolver(
    private val transport: OpenAiTransport,
    private val model: String = "gpt-4o-mini",
    private val onQuotaExhausted: () -> Unit = {},
    private val onInstallRestricted: () -> Unit = {},
    private val messages: Messages = Messages(),
) {

    /**
     * The sentences the resolver speaks on its own, as opposed to the ones the
     * API writes. The caller passes localised text; the English defaults keep
     * previews and tests working without a Context.
     */
    class Messages(
        val didNotCatch: String = "I did not catch that. Please say an object name.",
        val couldNotMatch: String = "I could not safely match that. Please try again or open Browse Objects.",
        val sayAnotherWay: String = "Can you say that in another way?",
        val notSupported: String = "That object is not supported.",
        val notOnList: (String) -> String = { raw -> "I'm sorry I couldn't find \"$raw\" from the item list." },
    )

    sealed class ResolveResult {
        data class Activate(
            val item: String,
            val source: Source,
            val confidence: Float
        ) : ResolveResult()

        data class Clarify(
            val message: String,
            val options: List<String> = emptyList()
        ) : ResolveResult()

        data class Unavailable(
            val message: String,
            val suggestions: List<String> = emptyList()
        ) : ResolveResult()
    }

    enum class Source { LOCAL, API }

    private data class ScoredCandidate(
        val item: String,
        val score: Float,
        val matchDetail: String = "",
        val matchTokenCount: Int = 0
    )

    private data class ApiDecision(
        val action: String,
        val item: String?,
        val message: String,
        val confidence: Float
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val localActivationThreshold = 0.80f
    private val localOnlyActivationThreshold = 0.80f
    private val minApiActivationConfidence = 0.72f

    /**
     * Alias table actually used for matching: the English synonyms plus, when the
     * app is in Finnish, the Finnish words. [CocoFinnish.aliases] is empty in any
     * other language, so English behaviour is untouched.
     */
    private fun activeAliases(): Map<String, String> =
        aliasMap + CocoFinnish.aliases().mapKeys { (term, _) -> normalize(term) }

    /**
     * Every spoken form that should score against a given COCO item: the English
     * label always, plus the Finnish name and its aliases when the app is in
     * Finnish. Fuzzy-matching these locally keeps inflected Finnish words
     * ("kirjaan", "tuolilla") off the API path.
     */
    private fun surfaceForms(): Map<String, List<String>> {
        val forms = COCO_OBJECTS.associateWith { mutableListOf(normalize(it)) }
        CocoFinnish.aliases().forEach { (term, item) -> forms[item]?.add(normalize(term)) }
        return forms
    }

    private val aliasMap = mapOf(
        "bike" to "bicycle",
        "cycle" to "bicycle",
        "motorbike" to "motorcycle",
        "motor cycle" to "motorcycle",
        "plane" to "airplane",
        "aeroplane" to "airplane",
        "cellphone" to "cell phone",
        "phone" to "cell phone",
        "mobile" to "cell phone",
        "tv screen" to "tv",
        "television" to "tv",
        "telly" to "tv",
        "sofa" to "couch",
        "fridge" to "refrigerator",
        "table" to "dining table",
        "plant" to "potted plant",
        "trafficlight" to "traffic light",
        "stoplight" to "traffic light",
        "hydrant" to "fire hydrant",
        "mug" to "cup",
        "cup" to "cup",
        "glass" to "wine glass"
    )

    /**
     * Resolve a voice command to a COCO object.
     *
     * Path 1 — Local: exact alias, embedded item/alias, or very-high-confidence fuzzy match → activate immediately.
     * Path 2 — API:   everything else is delegated to OpenAI for disambiguation.
     * Path 3 — Fail:  API unavailable/error → Unavailable with local suggestions.
     */
    suspend fun resolve(userInput: String, llmEnabled: Boolean = true): ResolveResult = withContext(Dispatchers.Default) {
        val raw = userInput.trim()
        if (raw.isBlank()) {
            return@withContext ResolveResult.Clarify(message = messages.didNotCatch)
        }

        val normalized = normalize(raw)

        // --- Path 1a: direct alias lookup ---
        val directAlias = activeAliases()[normalized]
        if (directAlias != null && directAlias in COCO_OBJECTS) {
            Log.i(TAG, "Resolved locally by alias: '$raw' -> '$directAlias'")
            return@withContext ResolveResult.Activate(directAlias, Source.LOCAL, 1.0f)
        }

        // --- Path 1b: exact COCO label match (after normalization) ---
        val exactMatch = COCO_OBJECTS.find { normalize(it) == normalized }
        if (exactMatch != null) {
            Log.i(TAG, "Resolved locally by exact match: '$raw' -> '$exactMatch'")
            return@withContext ResolveResult.Activate(exactMatch, Source.LOCAL, 1.0f)
        }

        // --- Path 1c: item/alias embedded inside a longer spoken phrase ---
        val embeddedMatch = findEmbeddedLocalMatch(normalized)
        if (embeddedMatch != null) {
            Log.i(
                TAG,
                "Resolved locally by embedded match: '$raw' -> '${embeddedMatch.item}' (${embeddedMatch.matchDetail})"
            )
            return@withContext ResolveResult.Activate(embeddedMatch.item, Source.LOCAL, embeddedMatch.score)
        }

        // --- Path 1d: very-high-confidence fuzzy match ---
        val scored = scoreCandidates(normalized)
        val top = scored.firstOrNull()
        val activationThreshold = if (llmEnabled) localActivationThreshold else localOnlyActivationThreshold
        if (top != null && top.score >= activationThreshold) {
            Log.i(TAG, "Resolved locally by fuzzy match: '${top.item}' (${top.score})")
            return@withContext ResolveResult.Activate(top.item, Source.LOCAL, top.score)
        }

        if (!llmEnabled) {
            Log.i(TAG, "LLM disabled. Returning local-only unavailable for '$raw'")
            return@withContext ResolveResult.Unavailable(
                message = itemListUnavailableMessage(raw)
            )
        }

        // --- Path 2: delegate to API ---
        val localHints = scored.take(3).map { it.item }
        Log.i(TAG, "Local uncertain (top=${top?.item} score=${top?.score}). Calling API for '$raw'")

        val apiResult = resolveWithApi(raw, localHints)
        if (apiResult != null) {
            return@withContext mapApiDecision(apiResult)
        }

        if (top != null && top.score >= localOnlyActivationThreshold) {
            Log.i(TAG, "API fallback failed. Resolved locally by relaxed fuzzy match: '${top.item}' (${top.score})")
            return@withContext ResolveResult.Activate(top.item, Source.LOCAL, top.score)
        }

        // --- Path 3: API unreachable, fail with the local-only item-list message ---
        Log.w(TAG, "API fallback failed for '$raw'. Returning suggestions: $localHints")
        return@withContext ResolveResult.Unavailable(
            message = itemListUnavailableMessage(raw),
            suggestions = localHints
        )
    }

    private fun mapApiDecision(decision: ApiDecision): ResolveResult {
        return when (decision.action) {
            "activate" -> {
                val item = decision.item
                if (item != null && item in COCO_OBJECTS && decision.confidence >= minApiActivationConfidence) {
                    Log.i(TAG, "Resolved via API: '$item' (${decision.confidence})")
                    ResolveResult.Activate(item, Source.API, decision.confidence)
                } else {
                    Log.w(TAG, "API returned activate but failed validation (item=$item, conf=${decision.confidence})")
                    ResolveResult.Clarify(
                        message = decision.message.ifBlank { messages.couldNotMatch }
                    )
                }
            }
            "clarify" -> ResolveResult.Clarify(
                message = decision.message.ifBlank { messages.sayAnotherWay },
                options = listOfNotNull(decision.item).filter { it in COCO_OBJECTS }
            )
            else -> ResolveResult.Unavailable(
                message = decision.message.ifBlank { messages.notSupported }
            )
        }
    }

    // --- Text normalization ---

    private fun normalize(text: String): String {
        val camelSpaced = text.replace(Regex("([a-z])([A-Z])"), "\$1 \$2")
        val lower = camelSpaced.lowercase(Locale.US)
        // Fold the Finnish vowels before the a-z filter below strips them: without
        // this, "henkilö" would normalize to "henkil" and never match its alias.
        val folded = lower.replace('ä', 'a').replace('ö', 'o').replace('å', 'a')
        val cleaned = folded.replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return when {
            cleaned.endsWith("es") && cleaned.length > 4 -> cleaned.dropLast(2)
            cleaned.endsWith("s") && cleaned.length > 3 -> cleaned.dropLast(1)
            else -> cleaned
        }
    }

    // --- Local fuzzy scoring ---

    private fun findEmbeddedLocalMatch(normalizedInput: String): ScoredCandidate? {
        if (normalizedInput.isBlank()) return null

        val inputTokens = normalizedInput.split(" ").filter { it.isNotBlank() }.toSet()
        if (inputTokens.isEmpty()) return null

        val compactInput = normalizedInput.replace(" ", "")
        val terms = COCO_OBJECTS.map { item -> item to item } +
            activeAliases().map { (term, item) -> term to item }

        return terms.asSequence()
            .mapNotNull { (term, item) ->
                if (item !in COCO_OBJECTS) return@mapNotNull null

                val normalizedTerm = normalize(term)
                val termTokens = normalizedTerm.split(" ").filter { it.isNotBlank() }
                if (termTokens.isEmpty()) return@mapNotNull null

                val phraseMatches = Regex("\\b${Regex.escape(normalizedTerm)}\\b").containsMatchIn(normalizedInput)
                val allTokensMatch = termTokens.all { it in inputTokens }
                val anyTokenMatch = termTokens.any { it in inputTokens }
                val compactTokenMatch = termTokens.any { token ->
                    token.length >= MIN_EMBEDDED_TOKEN_LENGTH && compactInput.contains(token)
                }

                val score = when {
                    phraseMatches -> 1.0f
                    allTokensMatch -> 0.98f
                    anyTokenMatch -> 0.93f
                    compactTokenMatch -> 0.86f
                    else -> return@mapNotNull null
                }
                ScoredCandidate(
                    item = item,
                    score = score,
                    matchDetail = "term='$term', tokens=${termTokens.joinToString("+")}",
                    matchTokenCount = termTokens.size
                )
            }
            .sortedWith(
                compareByDescending<ScoredCandidate> { it.score }
                    .thenByDescending { it.matchTokenCount }
                    .thenBy { COCO_OBJECTS.indexOf(it.item) }
            )
            .firstOrNull()
    }

    private fun scoreCandidates(normalizedInput: String): List<ScoredCandidate> {
        val inputTokens = normalizedInput.split(" ").filter { it.isNotBlank() }.toSet()
        val forms = surfaceForms()
        return COCO_OBJECTS.map { item ->
            // Best score across the item's spoken forms. In English that is the label
            // and nothing else, so scoring is unchanged from before.
            val best = forms[item].orEmpty()
                .maxOfOrNull { scoreSurface(normalizedInput, inputTokens, it) } ?: 0f
            ScoredCandidate(item = item, score = best)
        }.sortedByDescending { it.score }
    }

    private fun scoreSurface(
        normalizedInput: String,
        inputTokens: Set<String>,
        normalizedItem: String,
    ): Float {
        val distanceScore = 1f - (levenshtein(normalizedInput, normalizedItem).toFloat() /
            max(normalizedInput.length, normalizedItem.length).toFloat())
        val itemTokens = normalizedItem.split(" ").filter { it.isNotBlank() }.toSet()
        val overlap = if (inputTokens.isEmpty() || itemTokens.isEmpty()) 0f
        else inputTokens.intersect(itemTokens).size.toFloat() / itemTokens.size.toFloat()
        val containment = if (normalizedItem.contains(normalizedInput) ||
            normalizedInput.contains(normalizedItem)
        ) 1f else 0f
        val score = (distanceScore * 0.55f) + (overlap * 0.30f) + (containment * 0.15f)
        return score.coerceIn(0f, 1f)
    }

    private fun itemListUnavailableMessage(raw: String): String = messages.notOnList(raw)

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                curr[j + 1] = minOf(curr[j] + 1, prev[j + 1] + 1, prev[j] + cost)
            }
            for (k in prev.indices) prev[k] = curr[k]
        }
        return prev[b.length]
    }

    // --- OpenAI API fallback ---

    private fun resolveWithApi(userText: String, localCandidates: List<String>): ApiDecision? {
        if (!transport.isConfigured()) {
            Log.w(TAG, "API fallback skipped: LLM not configured")
            return null
        }
        return try {
            val userPrompt = JSONObject()
                .put("user_text", userText)
                .put("allowed_items", COCO_OBJECTS)
                .put("local_candidates", localCandidates)
                .toString()

            val messages = org.json.JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt()))
                put(JSONObject().put("role", "user").put("content", userPrompt))
            }
            val body = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("max_tokens", 120)
                put("temperature", 0.0)
            }
            val content = OpenAiTransport.executeChatCompletion(
                httpClient = httpClient,
                transport = transport,
                openAiRequestBody = body,
                logTag = TAG,
                onQuotaExhausted = onQuotaExhausted,
                onInstallRestricted = onInstallRestricted,
                registerCall = {},
            )
            parseApiDecision(content)
        } catch (e: Exception) {
            Log.w(TAG, "API parse/network error", e)
            null
        }
    }

    /**
     * Only the prompt for the language in use is sent. The two synonym sections
     * never travel together: an English user pays no Finnish tokens, and a
     * Finnish user pays no English ones.
     */
    private fun systemPrompt(): String =
        if (CocoFinnish.isActive()) SYSTEM_PROMPT_FI else SYSTEM_PROMPT

    private fun parseApiDecision(content: String): ApiDecision? {
        return try {
            val json = JSONObject(content)
            val actionRaw = json.optString("action", "unavailable")
            val action = when (actionRaw.lowercase(Locale.US)) {
                "activate", "clarify", "unavailable" -> actionRaw.lowercase(Locale.US)
                else -> "unavailable"
            }
            val item = if (!json.has("item") || json.isNull("item")) {
                null
            } else {
                json.optString("item", "").trim().ifBlank { null }
            }
            val message = json.optString("message", "")
            val confidence = json.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f)
            ApiDecision(action = action, item = item, message = message, confidence = confidence)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse API JSON: $content", e)
            null
        }
    }

    companion object {
        private const val TAG = "ObjectCommandResolver"
        private const val MIN_EMBEDDED_TOKEN_LENGTH = 3
        private val SYSTEM_PROMPT = """
            You are an object-command resolver for a vision assistant app used by visually impaired people.
            ${com.example.sightbuddy.core.OpenAiTransport.LLM_USER_SAFETY_INSTRUCTION}
            The user spoke a voice command to find an object. Their speech may be noisy, contain extra words, or use everyday synonyms instead of the exact label.

            IMPORTANT — Synonym and common-name mapping:
            Users will say common names that are NOT on the allowed list but clearly refer to an item that IS.
            You MUST map these to the correct allowed item. Examples:
            - "mug", "coffee mug", "tea cup" → "cup"
            - "sofa" → "couch"
            - "fridge" → "refrigerator"
            - "phone", "mobile", "cellphone" → "cell phone"
            - "bike" → "bicycle"
            - "tv", "telly", "television" → "tv"
            - "plane" → "airplane"
            - "table" → "dining table"
            - "plant" → "potted plant"
            Apply the same reasoning to any other common synonym even if not listed above.

            Extract the intended object from the full sentence and map it to exactly one item from the allowed list.
            Return ONLY a JSON object with these keys:
            - action: "activate" if you can confidently match (even via synonym), "clarify" if truly ambiguous between multiple items, "unavailable" if the object genuinely does not exist on the list.
            - item: the exact string from allowed_items, or null.
            - message: a short TTS-friendly sentence for the user (80 words maximum).
            - confidence: a number between 0.0 and 1.0.
            Never return an item that is not in the allowed list.
        """.trimIndent()

        /**
         * The Finnish counterpart. Same contract, but the spoken command arrives in
         * Finnish while allowed_items stays English, and the reply to the user must
         * be Finnish. Sent instead of [SYSTEM_PROMPT], never alongside it.
         */
        private val SYSTEM_PROMPT_FI = """
            You are an object-command resolver for a vision assistant app used by visually impaired people.
            ${com.example.sightbuddy.core.OpenAiTransport.LLM_USER_SAFETY_INSTRUCTION}
            The user speaks FINNISH. Their speech may be noisy, contain extra words, or use everyday Finnish synonyms.
            The allowed_items list is in ENGLISH and you must return an English item from it.

            IMPORTANT - Finnish to English mapping:
            Translate the Finnish object the user meant into the matching English item, including inflected
            and colloquial forms. Examples:
            - "muki", "kuppi", "kahvikuppi" -> "cup"
            - "sohva" -> "couch"
            - "jaakaappi", "jaakaappia" -> "refrigerator"
            - "kannykka", "puhelin", "matkapuhelin" -> "cell phone"
            - "lappari", "kannettava", "tietokone" -> "laptop"
            - "pyora", "fillari" -> "bicycle"
            - "telkkari", "televisio" -> "tv"
            - "vessa", "pytty" -> "toilet"
            - "poyta", "ruokapoyta" -> "dining table"
            - "ruukkukasvi", "kasvi" -> "potted plant"
            Apply the same reasoning to any other Finnish word, including partitive and plural forms.

            Extract the intended object from the full sentence and map it to exactly one item from the allowed list.
            Return ONLY a JSON object with these keys:
            - action: "activate" if you can confidently match, "clarify" if truly ambiguous between multiple items, "unavailable" if the object genuinely is not on the list.
            - item: the exact ENGLISH string from allowed_items, or null.
            - message: a short TTS-friendly sentence IN FINNISH for the user (80 words maximum).
            - confidence: a number between 0.0 and 1.0.
            Never return an item that is not in the allowed list. Always write message in Finnish.
        """.trimIndent()
    }
}
