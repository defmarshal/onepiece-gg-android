package gg.opgg.android.data

/**
 * Typed search over the card database, mirroring the filter semantics that
 * onepiece.gg defines in its own `cards.filterConfig`:
 *
 *   textSearch  containsText            -> bare words
 *   name        equalText               -> name:<exact>
 *   cardtype    commaSplitSearchTerms   -> t:leader,character
 *   type        arrayTextItemsPassAny   -> type:straw hat   (field is "Crew/Crew 2")
 *   color       arrayTextItemsPassAny   -> c:red            (field is "Red/Blue")
 *   attribute   arrayTextItemsPassAny   -> attr:slash       (field is "Slash / Wisdom")
 *   rarity      equalText               -> rarity:SR
 *   setId       equalTextAny            -> set:OP01
 *   cost        numberList              -> cost:3
 *   effects     containsText            -> effects:"draw 1"
 *   block       equalText               -> block:X
 *   counter     equalText + buckets      -> counter:2000
 *                                           counter:none / plus1000 / plus2000
 *   usdprice    floatGreaterThen        -> usdprice:1.50
 *
 * Semantics: AND across different fields, OR within one field's value list,
 * a leading `-` negates, and double quotes group a phrase.
 */

enum class QField(val canonical: String, val label: String, val aliases: List<String>) {
    NAME("name", "Name", listOf()),
    CARDTYPE("t", "Card type", listOf("cardtype", "ct")),
    TYPE("type", "Crew", listOf("crew")),
    COLOR("c", "Color", listOf("color")),
    ATTRIBUTE("attr", "Attribute", listOf("attribute", "a")),
    RARITY("rarity", "Rarity", listOf("r")),
    SET("set", "Set", listOf("setid", "sid")),
    COST("cost", "Cost", listOf()),
    EFFECTS("effects", "Effect text", listOf("effect", "fx")),
    BLOCK("block", "Block", listOf()),
    COUNTER("counter", "Counter", listOf()),
    USDPRICE("usdprice", "Min price $", listOf("price", "usd"));

    companion object {
        private val byKey = entries.associateBy { it.canonical } +
            entries.flatMap { f -> f.aliases.map { it to f } }.toMap()
        fun fromKey(key: String): QField? = byKey[key.lowercase()]
    }
}

data class Term(
    val field: QField,
    val values: List<String>,
    val negated: Boolean = false,
)

data class ParsedQuery(
    val terms: List<Term> = emptyList(),
    /** Bare words, ANDed together. */
    val words: List<String> = emptyList(),
    val unknownTokens: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = terms.isEmpty() && words.isEmpty()

    fun activeValues(field: QField): List<String> =
        terms.filter { it.field == field && !it.negated }.flatMap { it.values }

    fun has(field: QField, value: String): Boolean =
        terms.any { it.field == field && !it.negated && it.values.any { it.equals(value, true) } }

    fun isNegated(field: QField): Boolean = terms.any { it.field == field && it.negated }
}

data class Suggestion(
    val insert: String,
    val label: String,
    val hint: String,
)

object CardSearch {

    /** Start index of the token currently being typed, quote-aware. */
    fun partialTokenStart(text: String): Int {
        var inQuotes = false
        var last = 0
        for ((i, ch) in text.withIndex()) {
            if (ch == '"') inQuotes = !inQuotes
            else if (ch.isWhitespace() && !inQuotes) last = i + 1
        }
        return last
    }

    /**
     * Context-aware completion for the token under the cursor:
     *   "t"     -> field names
     *   "t:"    -> every value that field accepts
     *   "t:lea" -> the values starting with "lea"
     *   "luf"   -> card names
     */
    fun suggest(
        query: String,
        vocab: Map<QField, Set<String>>,
        names: List<String>,
        limit: Int = 8,
    ): List<Suggestion> {
        val start = partialTokenStart(query)
        val partial = query.substring(start)
        val out = ArrayList<Suggestion>()

        val negated = partial.startsWith("-")
        val body = if (negated) partial.substring(1) else partial

        if (body.contains(':')) {
            val key = body.substringBefore(':')
            val field = QField.fromKey(key) ?: return emptyList()
            val rawValue = body.substringAfter(':')
            val clean = rawValue.removePrefix("\"")
            // Complete to the canonical prefix so results match what the filter
            // chips and render() produce, not whatever alias was typed.
            val prefix = (if (negated) "-" else "") + field.canonical + ":"
            val values = vocab[field].orEmpty()
                .filter { it.startsWith(clean, ignoreCase = true) }
                .sortedWith(compareBy({ it.length }, { it }))
            for (v in values.take(limit)) {
                val quoted = if (v.contains(' ')) "\"$v\"" else v
                out += Suggestion(prefix + quoted, v, field.label)
            }
            if (out.isNotEmpty()) return out
            // Free-text fields have no vocabulary; nothing to suggest.
            return emptyList()
        }

        // No colon yet: offer matching field names. Ranked so an exact or
        // canonical prefix wins: typing "c" must surface "c:" first, not the
        // "ct" (cardtype) and "crew" (type) aliases.
        val scored = ArrayList<Pair<Int, QField>>()
        for (f in QField.entries) {
            val score = when {
                f.canonical.equals(partial, ignoreCase = true) -> 0
                f.canonical.startsWith(partial, ignoreCase = true) -> 1
                f.aliases.any { it.startsWith(partial, ignoreCase = true) } -> 2
                else -> -1
            }
            if (score >= 0) scored += score to f
        }
        for ((_, f) in scored.sortedBy { it.first }) {
            out += Suggestion(f.canonical + ":", f.canonical, f.label)
        }
        if (out.isNotEmpty()) return out.take(limit)

        if (partial.length >= 2) {
            val seen = HashSet<String>()
            // `names` arrives pre-sorted by length, so iterate it directly.
            // Re-sorting 3,063 strings on every keystroke was a real cost.
            for (n in names) {
                if (n.contains(partial, ignoreCase = true) && seen.add(n.lowercase())) {
                    out += Suggestion(n, n, "card name")
                    if (out.size >= limit) break
                }
            }
        }
        return out
    }

    /** Splits on whitespace but keeps "quoted phrases" together. */
    fun tokenize(input: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in input) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch.isWhitespace() && !inQuotes -> {
                    if (cur.isNotEmpty()) {
                        out.add(cur.toString()); cur.clear()
                    }
                }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    /**
     * True when the fragment looks like the start of a new filter rather than a
     * continuation of the current value, so `rarity:sp card c:red` keeps
     * "sp card" as the rarity but still sees `c:red` as a separate filter.
     */
    private fun looksLikeToken(fragment: String): Boolean {
        val body = if (fragment.startsWith("-") && fragment.length > 1) fragment.substring(1) else fragment
        val colon = body.indexOf(':')
        if (colon <= 0) return false
        return QField.fromKey(body.substring(0, colon)) != null
    }

    /**
     * Enum-like fields hold a fixed vocabulary, so a value is complete as soon
     * as it matches one. That stops `c:red luffy` from swallowing "luffy" while
     * still letting `rarity:sp card` complete into "SP CARD".
     */
    private val enumLike = setOf(
        QField.RARITY, QField.SET, QField.COST, QField.COLOR,
        QField.CARDTYPE, QField.BLOCK, QField.COUNTER, QField.USDPRICE,
    )

    fun parse(input: String, vocab: Map<QField, Set<String>> = emptyMap()): ParsedQuery {
        val terms = ArrayList<Term>()
        val words = ArrayList<String>()
        val unknown = ArrayList<String>()

        val raw = tokenize(input)
        var i = 0
        while (i < raw.size) {
            val token = raw[i]
            val negated = token.startsWith("-") && token.length > 1
            val body = if (negated) token.substring(1) else token
            val colon = body.indexOf(':')
            val field = if (colon > 0 && colon < body.length) QField.fromKey(body.substring(0, colon)) else null

            if (field == null) {
                if (colon > 0) unknown += token
                words += body
                i++
                continue
            }

            val value = StringBuilder(body.substring(colon + 1))
            var j = i + 1
            while (j < raw.size && !looksLikeToken(raw[j])) {
                if (field in enumLike) {
                    val known = vocab[field].orEmpty()
                    val current = value.toString().trim()
                    // Already a real value: the next word belongs to the query.
                    if (known.any { it.equals(current, ignoreCase = true) }) break
                    // Only keep absorbing while some known value could still match.
                    val candidate = "$current ${raw[j]}".trim()
                    if (known.none { it.startsWith(candidate, ignoreCase = true) }) break
                }
                value.append(' ').append(raw[j])
                j++
            }
            val values = value.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (values.isNotEmpty()) terms += Term(field, values, negated)
            i = j
        }
        return ParsedQuery(terms, words, unknown)
    }

    /** Known values per field, harvested from the card database. */
    fun vocabulary(cards: List<Card>): Map<QField, Set<String>> {
        fun collect(selector: (Card) -> String?): Set<String> =
            cards.mapNotNull(selector).flatMap { v -> v.split('/') }
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "?" }
                .toSet()

        return mapOf(
            QField.CARDTYPE to collect { it.cardType },
            QField.COLOR to collect { it.Color },
            QField.ATTRIBUTE to collect { it.Attribute },
            QField.RARITY to collect { it.rarity },
            QField.SET to cards.mapNotNull { it.set }.toSet(),
            QField.COST to cards.mapNotNull { it.Cost }.toSet(),
            QField.BLOCK to collect { it.BlockIcon },
                // The three named buckets are part of the vocabulary so the parser
                // completes `counter:plus2000` instead of swallowing the next word.
                QField.COUNTER to collect { it.Counter } + COUNTER_BUCKETS,
        )
    }

    /** Rebuilds query text from terms, for the chip UI writing back into the box. */
    fun render(terms: List<Term>, words: List<String> = emptyList()): String {
        val parts = ArrayList<String>()
        for (t in terms) {
            val value = t.values.joinToString(",") { if (it.contains(' ')) "\"$it\"" else it }
            parts += (if (t.negated) "-" else "") + t.field.canonical + ":" + value
        }
        parts += words
        return parts.joinToString(" ")
    }

    fun matches(card: Card, q: ParsedQuery): Boolean {
        for (word in q.words) if (!freeTextMatch(card, word)) return false
        for (term in q.terms) {
            val hit = term.values.any { valueMatch(card, term.field, it) }
            if (hit == term.negated) return false
        }
        return true
    }

    private fun freeTextMatch(card: Card, word: String): Boolean =
        card.searchBlob.contains(word.lowercase())

    private fun valueMatch(card: Card, field: QField, value: String): Boolean = when (field) {
        QField.NAME -> card.name.equals(value, ignoreCase = true)

        // The field is "LEADER" etc; commaSplitSearchTerms on the card side.
        QField.CARDTYPE -> card.cardType.orEmpty()
            .split(',').map { it.trim() }
            .any { it.equals(value, true) || it.startsWith(value, true) }

        // "Alabasta/Straw Hat Crew" - any segment, and long names are fuzzy.
        QField.TYPE -> segments(card.Type).any { it.contains(value, true) }

        // "Red/Blue" - match any colour in a multi-colour card.
        QField.COLOR -> segments(card.Color).any { it.equals(value, true) || it.startsWith(value, true) }

        // "Slash / Wisdom" mixes spaced and unspaced separators.
        QField.ATTRIBUTE -> segments(card.Attribute).any { it.equals(value, true) || it.startsWith(value, true) }

        QField.RARITY -> card.rarity.equals(value, ignoreCase = true)
        QField.SET -> card.set.equals(value, ignoreCase = true)
        QField.COST -> card.Cost.orEmpty()
            .split(',').map { it.trim() }
            .any { it == value.trim() }
        QField.EFFECTS -> card.Effect.orEmpty().contains(value, ignoreCase = true)
        QField.BLOCK -> card.BlockIcon.equals(value, ignoreCase = true)
            QField.COUNTER -> counterMatch(card, value)
        QField.USDPRICE -> card.price?.toDoubleOrNull()?.let { it > (value.toDoubleOrNull() ?: Double.MAX_VALUE) } == true
    }

    private fun segments(value: String?): List<String> =
        value.orEmpty().split('/').map { it.trim() }.filter { it.isNotEmpty() }

    /** The three named counter buckets the deck analytics also uses. */
    val COUNTER_BUCKETS = listOf("none", "plus1000", "plus2000")

    /**
     * Counter matching, exact or bucketed.
     *
     * The raw `Counter` field is noisy - "0", "1000", "1500", "2000", "3000" all
     * occur - so exact matching is kept for numbers (a search for a single card
     * must not silently widen) and three named buckets are added on top. The
     * buckets are the same three the analytics panel reports, and both go through
     * [DeckAnalyticsCalc.counterBucket] so a filter and the statistics can never
     * disagree. Named rather than numeric because `counter:2000` already means
     * "exactly 2000" and must keep meaning that.
     */
    private fun counterMatch(card: Card, value: String): Boolean {
        val bucket = when (value.lowercase()) {
            "none" -> 0
            "plus1000" -> 1
            "plus2000" -> 2
            else -> return card.Counter.equals(value, ignoreCase = true)
        }
        return DeckAnalyticsCalc.counterBucket(card.Counter) == bucket
    }
}
