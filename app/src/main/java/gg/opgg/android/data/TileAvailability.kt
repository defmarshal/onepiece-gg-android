package gg.opgg.android.data

/** Why a card cannot be added to a deck right now. */
enum class TileBlockReason {
    /** The deck has no Leader yet. */
    NO_LEADER,

    /** A second Leader would be needed, but a deck holds exactly one. */
    MULTIPLE_LEADERS,

    /** The main deck already holds 50 cards. */
    DECK_FULL,

    /** Already 4 copies of this printing, the per-printing cap. */
    AT_COPY_LIMIT,

    /** Already as many copies as the collection holds, in Owned only mode. */
    AT_OWNED_CAP,

    /** Outside the colours the Leader allows. */
    COLOUR_NOT_ALLOWED,
}

/**
 * A single, typed reason a tile is not addable.
 *
 * The distinction that matters is [deckLevel]. A missing Leader or a full deck
 * is a property of the *deck*, and the editor already states both in the status
 * line above the grid. Repeating them on every tile is what produced "This deck
 * has no Leader." across the whole grid, so those reasons carry no [label] and
 * the UI leaves the tile at full opacity.
 */
data class TileBlock(val reason: TileBlockReason, val label: String? = null) {
    val deckLevel: Boolean
        get() = reason == TileBlockReason.NO_LEADER ||
            reason == TileBlockReason.MULTIPLE_LEADERS ||
            reason == TileBlockReason.DECK_FULL
}

/**
 * Decides whether a card can be added, and says why not.
 *
 * This exists so the answer is decided in exactly one place. The grid used to
 * ask [DeckRules.canAdd] per tile and print the returned message, which meant a
 * deck-wide condition surfaced as prose on all 50 tiles at once, and a card's
 * own status was indistinguishable from the deck's.
 *
 * Pure and free of Android types, so it is unit tested on the JVM.
 */
object TileAvailability {

    /**
     * @param ownedCap when non-null (Owned only mode), the number of copies the
     *   collection holds and therefore the ceiling for this card.
     * @return null when the card can be added.
     */
    fun blockFor(
        deck: LocalDeck,
        printingId: String,
        cardById: (String) -> Card?,
        ownedCap: Int? = null,
    ): TileBlock? {
        val card = cardById(printingId) ?: return null
        val inDeck = deck.cards[printingId] ?: 0

        // Leaders are not capped and setting one replaces the current Leader,
        // so they are never blocked.
        if (DeckRules.isLeader(card)) return null

        val leaders = deck.cards.keys.filter { id ->
            (deck.cards[id] ?: 0) > 0 && DeckRules.isLeader(cardById(id))
        }
        if (leaders.isEmpty()) {
            // Deck-wide, and already shown in the status line. No label, so it
            // is never written under the card name.
            return TileBlock(TileBlockReason.NO_LEADER)
        }
        if (leaders.size > 1) {
            return TileBlock(TileBlockReason.MULTIPLE_LEADERS)
        }

        if (deck.mainDeckCount() >= DeckRules.MAIN_DECK_SIZE) {
            return TileBlock(TileBlockReason.DECK_FULL)
        }

        if (ownedCap != null && inDeck >= ownedCap) {
            // No label: the tile already prints "max N" under its + button in
            // Owned only mode, so a caption here would only repeat it.
            return TileBlock(TileBlockReason.AT_OWNED_CAP)
        }
        if (inDeck >= DeckRules.MAX_COPIES_PER_PRINTING) {
            // Short on purpose: a long message wraps to two lines and makes the
            // tile taller than its neighbours in the same grid row.
            return TileBlock(TileBlockReason.AT_COPY_LIMIT, "max ${DeckRules.MAX_COPIES_PER_PRINTING}")
        }

        val leader = cardById(leaders.first())
        val allowed = DeckRules.allowedColours(leader)
        if (allowed.isNotEmpty()) {
            val bad = DeckRules.coloursOf(card) - allowed
            if (bad.isNotEmpty()) {
                return TileBlock(
                    TileBlockReason.COLOUR_NOT_ALLOWED,
                    "${bad.joinToString("/")} not allowed by this Leader.",
                )
            }
        }
        return null
    }
}
