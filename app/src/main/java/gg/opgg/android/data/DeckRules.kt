package gg.opgg.android.data

/**
 * One Piece deck rules, taken from the game's own `deckBuildingRules` config
 * and confirmed against the printed rules and 620 published decks:
 *
 *   MAIN_DECK_SIZE    : 50   non-Leader cards
 *   LEADER            : exactly 1, required, and NOT counted in the 50
 *   TOTAL_DECK_SIZE   : 51   50 main-deck cards + 1 Leader
 *   maxCardsCopies    : 4    per PRINTING - `copyLimitAcrossPrintings: false`
 *   maxColorsInDeck   : 2
 *   LEADER            : `deckDefiner` + `colorLimiter` means the leader's
 *                       colours are the deck's entire palette
 *
 * The 50 vs 51 distinction is easy to get wrong and the site's config is
 * misleading here. The printed rules (Comprehensive Rules 5-1-2, 2026-08-28)
 * read "exactly 1 Leader card, a 50-card deck, and a 10-card DON!! deck" - the
 * 50-card deck is a *separate* pile from the Leader, not a pile that includes
 * it. Confirmed against the public deck list: of 750 decks, 670 contain 51
 * cards, and 620 of those contain exactly 1 Leader plus exactly 50 non-Leader
 * cards. All 104 decks flagged as tournament decks are 51 cards. An earlier
 * version of this file subtracted one and allowed only 49 main-deck cards,
 * which rejected legal decks.
 *
 * The colour rule is the one worth being explicit about. Of 150 sampled decks
 * with a resolvable leader: 69 mono leaders, 63 dual. Every card in all 132 sat
 * inside its leader's colour set - zero violations - and all 63 decks that used
 * two colours had dual-colour leaders. A mono leader therefore means a mono deck.
 *
 * This module is deliberately free of Android and of ViewModel types so it can
 * be unit tested on the JVM.
 */
object DeckRules {

    /** Non-Leader cards. The Leader is additional - see [TOTAL_DECK_SIZE]. */
    const val MAIN_DECK_SIZE = 50

    /** Leaders per deck. Always exactly 1. */
    const val LEADER_COUNT = 1

    /** 50 main-deck cards plus 1 Leader. */
    const val TOTAL_DECK_SIZE = MAIN_DECK_SIZE + LEADER_COUNT

    const val MAX_COPIES_PER_PRINTING = 4
    const val MAX_COLOURS = 2
    const val LEADER_TYPE = "LEADER"

    fun isLeader(card: Card?): Boolean =
        card?.cardType?.equals(LEADER_TYPE, ignoreCase = true) == true

    fun coloursOf(card: Card?): Set<String> =
        card?.Color?.split('/')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    /** Colours a leader permits. A multi-colour leader unlocks both of its own. */
    fun allowedColours(leader: Card?): Set<String> = coloursOf(leader)

    fun totalCards(deck: LocalDeck): Int = deck.cards.values.sumOf { it.coerceAtLeast(0) }

    fun coloursUsed(deck: LocalDeck, cardById: (String) -> Card?): Set<String> {
        val out = HashSet<String>()
        for (id in deck.cards.keys) {
            if (deck.cards[id]?.let { it <= 0 } == true) continue
            out += coloursOf(cardById(id))
        }
        return out
    }

    fun validate(
        deck: LocalDeck,
        cardById: (String) -> Card?,
        mode: RegulationMode = RegulationMode.STANDARD,
        today: Long = Regulation.todayEpochDay(),
    ): List<RuleViolation> {
        val v = ArrayList<RuleViolation>()

        val leaderEntries = deck.cards.filter { (id, n) ->
            n > 0 && isLeader(cardById(id))
        }
        val leader = leaderEntries.keys.firstOrNull()?.let(cardById)
        if (leaderEntries.isEmpty()) {
            v += RuleViolation(DeckKind.NO_LEADER, "This deck has no Leader.")
        } else if (leaderEntries.size > 1) {
            v += RuleViolation(
                DeckKind.MULTIPLE_LEADERS,
                "A deck may only contain one Leader (found ${leaderEntries.size}).",
            )
        }

        val allowed = allowedColours(leader)
        val nonLeaderTotal = deck.cards.entries
            .filter { (id, n) -> n > 0 && !isLeader(cardById(id)) }
            .sumOf { it.value }

        if (nonLeaderTotal > MAIN_DECK_SIZE) {
            v += RuleViolation(
                DeckKind.TOO_MANY_CARDS,
                "Deck has $nonLeaderTotal cards; the limit is $MAIN_DECK_SIZE " +
                    "plus $LEADER_COUNT Leader, $TOTAL_DECK_SIZE in total.",
            )
        }

        for ((id, count) in deck.cards) {
            if (count <= 0) continue
            if (count > MAX_COPIES_PER_PRINTING) {
                v += RuleViolation(
                    DeckKind.COPY_LIMIT,
                    "${id}: ${count} copies, maximum is $MAX_COPIES_PER_PRINTING per printing.",
                    id,
                )
            }
            val card = cardById(id) ?: continue
            if (isLeader(card)) continue
            if (allowed.isNotEmpty()) {
                val bad = coloursOf(card) - allowed
                if (bad.isNotEmpty()) {
                    v += RuleViolation(
                        DeckKind.COLOUR_NOT_ALLOWED,
                        "${card.name ?: id} is ${bad.joinToString("/")}, but the Leader " +
                            "${leader?.name ?: ""} only allows ${allowed.joinToString("/")}.",
                        id,
                    )
                }
            }
            // Tournament regulations, reported through the same channel as the
            // construction rules so there is one place legality is decided.
            // The Leader is exempt: a banned Leader is worth knowing about, but
            // the deck is already reported as having no usable Leader.
            if (!isLeader(card)) {
                if (Regulation.isBanned(card.idNormal ?: id, today, mode)) {
                    val ban = Regulation.banFor(card.idNormal ?: id, today, mode)
                    v += RuleViolation(
                        DeckKind.BANNED_CARD,
                        "${card.name ?: id} is banned in Standard Regulation. ${ban?.note ?: ""}".trim(),
                        id,
                    )
                } else if (!Regulation.isBlockAllowed(card.BlockIcon, today, mode)) {
                    val blocks = Regulation.standardBlocks(today)
                    v += RuleViolation(
                        DeckKind.BLOCK_NOT_LEGAL,
                        "${card.name ?: id} is Block ${card.BlockIcon}; Standard Regulation " +
                            "currently allows Blocks ${blocks.first}-${blocks.last}.",
                        id,
                    )
                }
            }
        }

        val used = coloursUsed(deck, cardById)
        if (used.size > MAX_COLOURS) {
            v += RuleViolation(
                DeckKind.DECK_TOO_MANY_COLOURS,
                "Deck uses ${used.size} colours; maximum is $MAX_COLOURS.",
            )
        }
        return v
    }

    fun isLegal(
        deck: LocalDeck,
        cardById: (String) -> Card?,
        mode: RegulationMode = RegulationMode.STANDARD,
        today: Long = Regulation.todayEpochDay(),
    ): Boolean = validate(deck, cardById, mode, today).isEmpty()

    /**
     * Can this printing be added without breaking a rule? Used to grey out
     * options in the picker rather than letting the user build an illegal deck
     * and discovering it afterwards.
     */
    fun canAdd(
        deck: LocalDeck,
        printingId: String,
        cardById: (String) -> Card?,
        mode: RegulationMode = RegulationMode.STANDARD,
        today: Long = Regulation.todayEpochDay(),
    ): Violation? {
        val current = deck.cards[printingId] ?: 0
        val card = cardById(printingId)
        if (isLeader(card)) {
            val hasLeader = deck.cards.any { (id, n) -> n > 0 && isLeader(cardById(id)) }
            if (hasLeader && !deck.cards.containsKey(printingId)) {
                return Violation("A deck may only contain one Leader.", DeckKind.MULTIPLE_LEADERS)
            }
        }
        if (current + 1 > MAX_COPIES_PER_PRINTING) {
            return Violation(
                "Only $MAX_COPIES_PER_PRINTING copies of a printing are allowed.",
                DeckKind.COPY_LIMIT,
            )
        }
        val leader = deck.cards.keys.firstOrNull { isLeader(cardById(it)) }?.let(cardById)
        val allowed = allowedColours(leader)
        if (!isLeader(card) && allowed.isNotEmpty()) {
            val bad = coloursOf(card) - allowed
            if (bad.isNotEmpty()) {
                return Violation(
                    "${bad.joinToString("/")} is not allowed by this deck's Leader.",
                    DeckKind.COLOUR_NOT_ALLOWED,
                )
            }
        }
        val probe = deck.copy(cards = deck.cards + (printingId to current + 1))
        return validate(probe, cardById, mode, today).firstOrNull()
            ?.let { Violation(it.message, it.kind) }
    }
}

/** Every way a deck can break the rules. All violations are reported, not just the first. */
enum class DeckKind {
    NO_LEADER,
    MULTIPLE_LEADERS,
    TOO_MANY_CARDS,
    COPY_LIMIT,
    COLOUR_NOT_ALLOWED,
    DECK_TOO_MANY_COLOURS,
    /** Banned in the selected regulation; see [Regulation]. */
    BANNED_CARD,
    /** Outside the rolling Block Number window; see [Regulation.standardBlocks]. */
    BLOCK_NOT_LEGAL,
}

data class Violation(val message: String, val kind: DeckKind)

data class RuleViolation(
    val kind: DeckKind,
    val message: String,
    val cardId: String? = null,
)

/** A deck authored on this device. Not synced to onepiece.gg. */
@kotlinx.serialization.Serializable
data class LocalDeck(
    val id: String,
    val name: String = "New deck",
    /** Printing id of the Leader, or null while choosing. */
    val leaderId: String? = null,
    /** Printing id -> copies. Keys are printings, not canonical ids. */
    val cards: Map<String, Int> = emptyMap(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    /**
     * Sets or removes copies of a printing.
     *
     * Clearing `leaderId` when the Leader itself drops to zero is not cosmetic.
     * Removing the Leader card while leaving the id set produced a deck that
     * validated as "no Leader" while the editor still resolved the colour
     * palette from the old Leader - so the card pool stayed stuck on that
     * Leader's colours and a wider Leader could not be chosen back. The model
     * is now self-consistent by construction.
     */
    fun withCount(printingId: String, count: Int): LocalDeck {
        val next = cards.toMutableMap()
        if (count <= 0) next.remove(printingId) else next[printingId] = count
        val stillPresent = if (printingId == leaderId && count <= 0) null else leaderId
        return copy(leaderId = stillPresent, cards = next, updatedAt = System.currentTimeMillis())
    }
    /** Cards excluding the Leader, which is `excludeFromDeckCount`. */
    fun mainDeckCount(): Int =
        cards.entries
            .filter { it.value > 0 && it.key != leaderId }
            .sumOf { it.value }

    /** Total copies including the Leader. */
    fun totalWithLeader(): Int = cards.entries.filter { it.value > 0 }.sumOf { it.value }

    /**
     * Sets this printing as the deck's only Leader.
     *
     * A deck may hold exactly one, so any previous leader is stripped first
     * rather than a second one accumulating.
     */
    fun withLeader(printingId: String, isLeader: (String) -> Boolean): LocalDeck {
        val kept = cards.filterKeys { !isLeader(it) }
        return copy(leaderId = printingId, cards = kept + (printingId to 1))
    }
}
