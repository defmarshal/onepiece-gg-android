package gg.opgg.android.data

/** A card the deck needs more of than the collection holds. */
data class MissingCard(
    /** Canonical id, so all printings of the card collapse into one row. */
    val normalId: String,
    val name: String?,
    /** Copies the deck runs, summed across its printings. */
    val required: Int,
    /** Copies held across every printing, normal and foil. */
    val owned: Int,
    val missing: Int,
    /** The deck's own printing ids, so the UI can show what it asked for. */
    val printingsInDeck: List<String>,
    /** True when the card is the deck's Leader, which sits outside the 50. */
    val isLeader: Boolean = false,
)

/** The result of comparing a deck against the collection. */
data class DeckDiffResult(
    val missing: List<MissingCard> = emptyList(),
    /** Cards the deck runs and the collection covers completely. */
    val covered: Int = 0,
    /** Copies required across the whole deck, Leader included. */
    val requiredCards: Int = 0,
) {
    val missingCopies: Int get() = missing.sumOf { it.missing }
    val isComplete: Boolean get() = missing.isEmpty()
}

/**
 * "Cards I still need" - what a deck runs that the collection does not cover.
 *
 * Two judgement calls, both deliberate:
 *
 * 1. **Printings collapse.** The deck is authored in printings, but a holding of
 *    `OP12-034_p2` does satisfy a requirement for `OP12-034` - you would simply
 *    play the parallel. So the count is per *canonical* card, not per printing.
 *    The strict alternative would report you as 4 short of a card you own four
 *    printings of, which is not a true answer to "what do I need to buy".
 *
 * 2. **The Leader is included.** It sits outside the 50, but if you do not own
 *    it you cannot play the deck at all, so it is a genuine requirement. It is
 *    flagged with [MissingCard.isLeader] so the UI can label it.
 *
 * Foil counts as owned: a foil is a real playable copy, and a user asking what
 * to buy wants to know whether they can field the card, not whether it is
 * standard.
 */
object DeckDiff {

    /**
     * @param ownedByNormal canonical id -> copies held across all its printings.
     * @param cardById printing id -> card, used for names and the Leader check.
     */
    fun diff(
        deck: LocalDeck,
        ownedByNormal: (String) -> Int,
        cardById: (String) -> Card?,
    ): DeckDiffResult {
        // printing id -> canonical id, then canonical -> total required.
        val requiredByNormal = LinkedHashMap<String, Int>()
        val printingsByNormal = LinkedHashMap<String, MutableList<String>>()

        for ((printingId, count) in deck.cards) {
            if (count <= 0) continue
            val canonical = canonicalOf(printingId, cardById)
            requiredByNormal[canonical] = (requiredByNormal[canonical] ?: 0) + count
            printingsByNormal.getOrPut(canonical) { ArrayList() }.add(printingId)
        }

        val missing = ArrayList<MissingCard>()
        var covered = 0
        for ((normalId, required) in requiredByNormal) {
            val owned = ownedByNormal(normalId).coerceAtLeast(0)
            val short = required - owned
            val printings = printingsByNormal[normalId].orEmpty()
            if (short > 0) {
                missing += MissingCard(
                    normalId = normalId,
                    name = printings.firstNotNullOfOrNull { cardById(it)?.name },
                    required = required,
                    owned = owned,
                    missing = short,
                    printingsInDeck = printings,
                    isLeader = printings.any { DeckRules.isLeader(cardById(it)) },
                )
            } else {
                covered++
            }
        }
        // Biggest gap first: that is the card worth buying first.
        missing.sortWith(compareByDescending<MissingCard> { it.missing }.thenBy { it.name })

        return DeckDiffResult(
            missing = missing,
            covered = covered,
            requiredCards = requiredByNormal.values.sum(),
        )
    }

    /**
     * Canonical id for a printing.
     *
     * Prefers the card's own `id_normal`, which is the server's authority on
     * which printings collapse together, and falls back to the printing id for a
     * card the database does not know.
     */
    private fun canonicalOf(printingId: String, cardById: (String) -> Card?): String =
        cardById(printingId)?.idNormal ?: printingId
}
