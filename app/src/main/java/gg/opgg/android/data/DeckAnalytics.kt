package gg.opgg.android.data

/**
 * Descriptive statistics for a deck being built. Pure functions over the card
 * database, no Android or ViewModel types, so the arithmetic is unit testable on
 * the JVM like [DeckRules].
 *
 * The bucket definitions are the game's own, not invented here: the site ships an
 * `extraCurves` config that defines both the counter curve and the power curve,
 * and the app should agree with it. See [DeckAnalytics.COUNTER_LABELS].
 *
 * What is deliberately NOT here: any judgement about whether the numbers are
 * *good*. Measured across 619 complete published decks (DECK_THEORY.md 7), the
 * cost curve sits around 12 cards at cost 1, a plateau across 2-6 and under 5
 * cards at 7-9 - but blocker count is strongly multi-modal, peaking at 4 and 8
 * with 9.5% of decks running none at all. Neither is a target, so this reports
 * shape and leaves the reading to the player.
 */
data class DeckAnalytics(
    /** Non-Leader, non-DON cards. The Leader is counted separately. */
    val mainDeckCount: Int = 0,
    /** Cards at each cost, index 0 = cost 1 ... index 9 = cost 10. */
    val costs: List<Int> = List(MAX_COST) { 0 },
    /** Mean cost of the cards that declare one, or null if none do. */
    val averageCost: Double? = null,
    val counters: Counters = Counters(),
    /** cardType -> copies, e.g. CHARACTER / EVENT / STAGE. DON is excluded. */
    val types: Map<String, Int> = emptyMap(),
    /**
     * Crew -> copies, e.g. "Straw Hat Crew", "Supernovas", "Navy".
     *
     * The server's `Type` field is multi-valued and `/`-separated
     * ("Fish-Man Island/Supernovas/Straw Hat Crew" is one card with three crews),
     * so it is split rather than treated as a single label. Verified across the
     * live database: 2,622 cards carry one crew, 2,305 carry two, 373 carry
     * three, over 205 distinct crews.
     *
     * Because of that, `crews.values.sum()` exceeds the deck size. A card is
     * counted once per distinct crew.
     */
    val crews: Map<String, Int> = emptyMap(),
    /** Each colour -> copies. A Red/Blue card counts once under each colour. */
    val colours: Map<String, Int> = emptyMap(),
    /** Copies carrying the [Blocker] keyword. */
    val blockers: Int = 0,
    /** Cards at each power band, index 0 = 0 power ... index 8 = 8000+. */
    val powers: List<Int> = List(MAX_POWER_BANDS) { 0 },
    /** Cards whose cost could not be parsed, so the curve does not sum to the deck. */
    val uncounted: Int = 0,
) {
    /**
     * Cards with no counter, +1000, or +2000 or more.
     *
     * Buckets exactly as `extraCurves` does: `Counter >= 2000 ? 2 :
     * Counter >= 1000 ? 1 : 0`, so +3000 and above land in the +2000 bucket.
     */
    data class Counters(val none: Int = 0, val plus1000: Int = 0, val plus2000: Int = 0) {
        val total: Int get() = none + plus1000 + plus2000
    }

    val totalCounters: Int get() = counters.total

    /**
     * The [n] most-played crews. Ties break alphabetically so the order does
     * not flicker between recompositions.
     */
    fun topCrews(n: Int): List<Pair<String, Int>> =
        crews.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(n)
            .map { it.key to it.value }

    companion object {
        const val MAX_COST = 10
        const val MAX_POWER_BANDS = 9
    }
}

object DeckAnalyticsCalc {

    /** The site's own `extraCurves` labels for the counter curve. */
    val COUNTER_LABELS = listOf("0", "1000", "2000")

    val POWER_LABELS = listOf("0", "1k", "2k", "3k", "4k", "5k", "6k", "7k", "8k+")

    private val COST_RE = Regex("\\d+")
    private val BLOCKER = "[Blocker]"

    private const val DON_TYPE = "DON"

    /**
     * Statistics for [deck]. The Leader is excluded from every main-deck figure
     * and reported on its own, because the rules put it outside the 50
     * (DECK_THEORY.md 2.1).
     */
    fun analyse(deck: LocalDeck, cardById: (String) -> Card?): DeckAnalytics {
        val costs = IntArray(DeckAnalytics.MAX_COST)
        val powers = IntArray(DeckAnalytics.MAX_POWER_BANDS)
        val types = LinkedHashMap<String, Int>()
        val colours = LinkedHashMap<String, Int>()
        val crews = LinkedHashMap<String, Int>()
        var none = 0
        var plus1 = 0
        var plus2 = 0
        var blockers = 0
        var uncounted = 0
        var costSum = 0
        var costCount = 0
        var mainTotal = 0

        for ((printingId, count) in deck.cards) {
            if (count <= 0) continue
            val card = cardById(printingId) ?: continue
            if (DeckRules.isLeader(card)) continue
            if (card.cardType.equals(DON_TYPE, ignoreCase = true)) continue

            mainTotal += count
            val type = card.cardType ?: "UNKNOWN"
            types[type] = (types[type] ?: 0) + count
            for (c in DeckRules.coloursOf(card)) colours[c] = (colours[c] ?: 0) + count
            for (crew in crewsOf(card)) crews[crew] = (crews[crew] ?: 0) + count

            repeat(count) {
                val cost = firstInt(card.Cost)
                if (cost == null) uncounted++ else if (cost in 1..DeckAnalytics.MAX_COST) {
                    costs[cost - 1]++
                    costSum += cost
                    costCount++
                } else {
                    uncounted++
                }
                powerBands(card.Power)?.let { powers[it]++ }
            }

            when (counterBucket(card.Counter)) {
                2 -> plus2 += count
                1 -> plus1 += count
                else -> none += count
            }

            if (card.Effect.orEmpty().contains(BLOCKER, ignoreCase = true) ||
                card.Trigger.orEmpty().contains(BLOCKER, ignoreCase = true)
            ) {
                blockers += count
            }
        }

        return DeckAnalytics(
            mainDeckCount = mainTotal,
            costs = costs.toList(),
            averageCost = if (costCount > 0) costSum.toDouble() / costCount else null,
            counters = DeckAnalytics.Counters(none, plus1, plus2),
            types = types.toSortedMap(),
            colours = colours.toSortedMap(),
            crews = crews.toSortedMap(),
            blockers = blockers,
            powers = powers.toList(),
            uncounted = uncounted,
        )
    }

    /**
     * The distinct crews on a card.
     *
     * `Type` arrives `/`-separated and one card can belong to up to three crews,
     * so it is split on `/` and de-duplicated - a repeated crew on the same card
     * would otherwise be counted twice.
     */
    fun crewsOf(card: Card?): Set<String> {
        val raw = card?.Type ?: return emptySet()
        return raw.split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** `0` for no counter, `1` for +1000, `2` for +2000 or more. */
    fun counterBucket(counter: String?): Int {
        val n = firstInt(counter) ?: 0
        return when {
            n >= 2000 -> 2
            n >= 1000 -> 1
            else -> 0
        }
    }

    /** Index into [DeckAnalytics.powers], or null when the power is unparseable. */
    fun powerBands(power: String?): Int? {
        val n = firstInt(power) ?: return null
        if (n < 0) return null
        return (n / 1000).coerceAtMost(DeckAnalytics.MAX_POWER_BANDS - 1)
    }

    /**
     * Cost, Counter and Power all arrive as strings, sometimes with decoration
     * ("10 DON!!", "+2000"). First integer is the number we want; null when
     * there is not one, which the caller must handle rather than treat as 0.
     */
    fun firstInt(value: String?): Int? {
        val m = COST_RE.find(value ?: return null) ?: return null
        return m.value.toIntOrNull()
    }
}
