package gg.opgg.android.data

/**
 * onepiece.gg's deck text format.
 *
 * The site exports it from `cardsArrayToCode`:
 *
 *   for each card -> `${count}x${id}` , joined with "\n"
 *
 * and imports by resolving each token against card ids case-insensitively — the
 * importer's `case "cookierun": case "onepiece":` branch lowercases both sides.
 * So the interchange unit is the **printing** id, which is what `LocalDeck`
 * stores.
 *
 * Pure and Android-free, so it is unit testable.
 */
object DeckCode {

    fun export(deck: LocalDeck): String =
        deck.cards.entries
            .filter { it.value > 0 }
            .sortedBy { it.key }          // stable order makes the text diffable
            .joinToString("\n") { "${it.value}x${it.key}" }

    /**
     * The site's *import* shape, which is not the same as its export shape.
     *
     * The site emits `${count}x${printingId}` including the reprint suffix
     * (`1xEB01-015_r1`), but its importer wants the suffix gone while keeping
     * the set prefix (`1xEB01-015`). Confirmed by the user against a real
     * round trip. So this strips `_<p|r><n>` and nothing else.
     *
     * Deliberately separate from [export]: the full printing id is what
     * [LocalDeck] stores, so the plain export is the lossless one and is what the
     * app's own importer should be fed. Collapsing printings here is a one-way
     * trip to the site.
     */
    fun exportForSite(deck: LocalDeck): SiteExport {
        val rows = deck.cards.entries
            .filter { it.value > 0 }
            .sortedBy { it.key }
            .map { it.value to stripPrintingSuffix(it.key) }

        // Two printings of the same card number collapse onto one line, and the
        // site would then see a count it cannot honour. Surfaced rather than
        // silently merged, because merging could produce an illegal count.
        val byStripped = rows.groupBy({ it.second }, { it.first })
        val collapsed = byStripped.filterValues { it.size > 1 }.keys.sorted()

        return SiteExport(
            code = rows.joinToString("\n") { "${it.first}x${it.second}" },
            collapsedPrintings = collapsed,
        )
    }

    /** `EB01-015_r1` -> `EB01-015`; `ST02-007_p4` -> `ST02-007`; `EB03-019` unchanged. */
    private val SUFFIX = Regex("_[pP][0-9]+$|_[rR][0-9]+$")

    private fun stripPrintingSuffix(printingId: String): String =
        SUFFIX.replace(printingId, "")

    data class SiteExport(
        val code: String,
        /** Card ids that more than one printing collapsed onto. */
        val collapsedPrintings: List<String> = emptyList(),
    ) {
        val hasCollisions: Boolean get() = collapsedPrintings.isNotEmpty()
    }

    data class Parsed(
        /** printing id -> count, for ids that resolved. */
        val cards: Map<String, Int>,
        /** Tokens that matched no card, so cards are never lost silently. */
        val unresolved: List<String>,
    ) {
        val total: Int get() = cards.values.sum()
    }

    /**
     * Resolves a token to a printing id.
     *
     * Accepts a printing id (`OP01-001`) or a canonical id (`OP01-001_p1`'s
     * `id_normal`), case-insensitively, matching the site.
     */
    class Lookup(printings: Collection<Card>) {
        private val byPrinting = HashMap<String, String>()
        private val byNormal = HashMap<String, String>()

        init {
            for (c in printings) {
                val printing = c.id ?: continue
                byPrinting.putIfAbsent(printing.lowercase(), printing)
                val normal = c.idNormal ?: continue
                // Prefer the base printing when several share a canonical id.
                byNormal.putIfAbsent(normal.lowercase(), printing)
            }
            // Make the base printing win for canonical lookups.
            for (c in printings) {
                val printing = c.id ?: continue
                if (printing == c.idNormal) byNormal[printing.lowercase()] = printing
            }
        }

        fun resolve(token: String): String? {
            val lower = token.trim().lowercase()
            if (lower.isEmpty()) return null
            return byPrinting[lower] ?: byNormal[lower]
        }
    }

    private val countFirst = Regex("^(\\d+)\\s*[xX]\\s*(.+)$")
    private val idFirst = Regex("^(.+?)\\s*[xX]\\s*(\\d+)$")

    /**
     * Accepts a forgiving superset of the site's format, because deck text
     * arrives from spreadsheets and other deck sites:
     *   `4xOP01-001`   the site format
     *   `4 x OP01-001`  spaces around the x
     *   `OP01-001 x 4`  count after the id
     *   `OP01-001`      defaults to one copy
     * Blank lines and `#` comments are ignored.
     */
    fun parse(text: String, lookup: Lookup): Parsed {
        val cards = LinkedHashMap<String, Int>()
        val unresolved = ArrayList<String>()

        for (rawLine in text.split('\n', '\r')) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            var count = 1
            var id = line
            countFirst.find(line)?.let {
                count = it.groupValues[1].toIntOrNull() ?: 1
                id = it.groupValues[2].trim()
            } ?: idFirst.find(line)?.let {
                id = it.groupValues[1].trim()
                count = it.groupValues[2].toIntOrNull() ?: 1
            }

            if (id.isEmpty() || count <= 0) continue

            val printing = lookup.resolve(id)
            if (printing == null) unresolved += id
            else cards[printing] = (cards[printing] ?: 0) + count
        }
        return Parsed(cards, unresolved)
    }

    /**
     * CSV for a private, human-readable record.
     *
     * Deliberately NOT an `importcollection` payload — that endpoint is for the
     * collection, and a deck CSV that looked like one would corrupt data.
     */
    fun toCsv(deck: LocalDeck, cardById: (String) -> Card?): String {
        val sb = StringBuilder("printing_id,card_id,name,set,rarity,count\n")
        for ((id, count) in deck.cards.entries.sortedBy { it.key }) {
            val c = cardById(id)
            sb.append(
                listOf(
                    id,
                    c?.idNormal.orEmpty(),
                    c?.name.orEmpty(),
                    c?.set.orEmpty(),
                    c?.rarity.orEmpty(),
                    count.toString(),
                ).joinToString(",") { cell(it) },
            )
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun cell(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' }) "\"${v.replace("\"", "\"\"")}\"" else v
}
