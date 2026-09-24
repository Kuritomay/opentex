package com.kuritomay.opentex.data

import org.json.JSONArray
import org.json.JSONObject

data class ParsedCard(
    val type: String,
    val front: String,
    val back: String,
    val frontLatex: String? = null,
    val backLatex: String? = null,
    val explanation: String? = null,
    val options: String? = null,
    val correctOptionIds: String? = null,
    val tags: String? = null,
    val image: String? = null,
    val sourceDocument: String? = null,
    val sourceUri: String? = null,
    val sourcePage: Int? = null,
)

data class ParsedDeck(val name: String, val cards: List<ParsedCard>)

object FlashcardPort {
    const val FORMAT = "opentex-flashcards"
    const val VERSION = 1

    fun export(cards: List<FlashcardWithDeck>, documentInfo: (Long) -> Pair<String, String?>?, imageEntry: (String) -> String?): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
        val decks = JSONArray()
        cards.groupBy { it.deck.deckName }.forEach { (deckName, group) ->
            val cardsArray = JSONArray()
            group.forEach { entry -> cardsArray.put(cardJson(entry.card, documentInfo, imageEntry)) }
            decks.put(JSONObject().put("name", deckName).put("cards", cardsArray))
        }
        root.put("decks", decks)
        return root.toString(2)
    }

    private fun cardJson(card: FlashcardEntity, documentInfo: (Long) -> Pair<String, String?>?, imageEntry: (String) -> String?): JSONObject {
        val json = JSONObject()
            .put("type", if (card.type == "explained") "basic" else card.type)
            .put("front", card.frontText)
            .put("back", card.backText)
        card.backExplanation?.takeIf { it.isNotBlank() }?.let { json.put("explanation", it) }
        card.frontLatex?.takeIf { it.isNotBlank() }?.let { json.put("frontLatex", it) }
        card.backLatex?.takeIf { it.isNotBlank() }?.let { json.put("backLatex", it) }
        card.options?.takeIf { it.isNotBlank() }?.let { json.put("options", JSONArray(it)) }
        card.correctOptionIds?.takeIf { it.isNotBlank() }?.let { json.put("correct", JSONArray(it)) }
        card.tags?.takeIf { it.isNotBlank() }?.let { json.put("tags", JSONArray(it.split(',').map(String::trim).filter(String::isNotBlank))) }
        card.frontImage?.let(imageEntry)?.let { json.put("image", it) }
        if (card.documentId != null) {
            val info = documentInfo(card.documentId)
            if (info != null && card.pageNumber != null) {
                json.put("source", JSONObject().put("document", info.first).put("page", card.pageNumber + 1).also { put -> info.second?.let { put.put("uri", it) } })
            }
        }
        return json
    }

    fun options(card: FlashcardEntity): List<String> = card.options?.let { raw ->
        runCatching { val array = JSONArray(raw); (0 until array.length()).map { array.getString(it) } }.getOrNull()
    } ?: emptyList()

    fun correct(card: FlashcardEntity): Set<Int> = card.correctOptionIds?.let { raw ->
        runCatching { val array = JSONArray(raw); (0 until array.length()).map { array.getInt(it) }.toSet() }.getOrNull()
    } ?: emptySet()

    fun parse(text: String): List<ParsedDeck> {
        val root = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("El archivo no contiene flashcards válidas.") }
        if (root.optString("format") != FORMAT) throw IllegalArgumentException("El archivo no es una exportación de OpenTex.")
        if (root.optInt("version", 1) > VERSION) throw IllegalArgumentException("Esta exportación usa un formato más reciente de OpenTex.")
        val decks = root.optJSONArray("decks") ?: return emptyList()
        return (0 until decks.length()).mapNotNull { deckIndex ->
            val deck = decks.getJSONObject(deckIndex)
            val name = deck.optString("name").ifBlank { "General" }
            val cardsArray = deck.optJSONArray("cards") ?: JSONArray()
            val parsed = (0 until cardsArray.length()).map { cardIndex -> cardJson(cardsArray.getJSONObject(cardIndex)) }
            if (parsed.isEmpty()) null else ParsedDeck(name, parsed)
        }
    }

    private fun cardJson(json: JSONObject): ParsedCard {
        val source = json.optJSONObject("source")
        return ParsedCard(
            type = json.optString("type").ifBlank { "basic" },
            front = json.optString("front"),
            back = json.optString("back"),
            frontLatex = json.optString("frontLatex").ifBlank { null },
            backLatex = json.optString("backLatex").ifBlank { null },
            explanation = json.optString("explanation").ifBlank { null },
            options = json.optJSONArray("options")?.toString(),
            correctOptionIds = json.optJSONArray("correct")?.toString(),
            tags = json.optJSONArray("tags")?.let { array -> (0 until array.length()).map { array.getString(it) }.joinToString(",") } ?: json.optString("tags").ifBlank { null },
            image = json.optString("image").ifBlank { null },
            sourceDocument = source?.optString("document")?.ifBlank { null },
            sourceUri = source?.optString("uri")?.ifBlank { null },
            sourcePage = source?.optInt("page", 0)?.takeIf { it > 0 }?.let { it - 1 },
        )
    }
}
