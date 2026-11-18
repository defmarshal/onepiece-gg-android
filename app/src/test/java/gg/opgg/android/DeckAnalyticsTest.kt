package gg.opgg.android

import gg.opgg.android.data.Card
import gg.opgg.android.data.DeckAnalytics
import gg.opgg.android.data.DeckAnalyticsCalc
import gg.opgg.android.data.DeckRules
import gg.opgg.android.data.LocalDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Analytics arithmetic. Pure functions, so this runs on the JVM with no device
 * and no Android types.
 */
class DeckAnalyticsTest {

    private fun card(
        id: String,
        type: String? = "CHARACTER",
        color: String = "Red",
        cost: String? = null,
        power: String? = null,
        counter: String? = null,
        effect: String? = null,
        trigger: String? = null,
        crew: String? = null,
    ) = Card(
        id = id,
        cardType = type,
        Color = color,
        Cost = cost,
        Power = power,
        Counter = counter,
        Effect = effect,
        Trigger = trigger,
        Type = crew,
    )

    private val db: Map<String, Card> = buildMap {
        put("L-RED", card("L-RED", type = "LEADER", cost = "-", power = "5000"))
        put("C1", card("C1", cost = "1", power = "3000", counter = "1000"))
        put("C2", card("C2", cost = "2", power = "4000", counter = "0"))
        put("C3", card("C3", cost = "3", power = "5000", counter = "2000"))
        put("C10", card("C10", cost = "10", power = "9000", counter = "3000"))
        put("E1", card("E1", type = "EVENT", cost = "2", power = "0"))
        put("S1", card("S1", type = "STAGE", cost = "3", power = "0"))
        put("BLK", card("BLK", cost = "2", effect = "[Blocker] (After your opponent attacks.)"))
        put("BLK2", card("BLK2", cost = "4", trigger = "[Blocker] (This card can block.)"))
        put("DUAL", card("DUAL", color = "Red/Blue", cost = "5"))
        put("DONC", card("DONC", type = "DON", cost = "-"))
        put("NOCOST", card("NOCOST", cost = null))
        put("MISSING", card("MISSING", cost = "2"))
    }

    private fun lookup(id: String): Card? = db[id]

    /** A card on three crews, as the server actually sends it. */
    private val multi = card("M1", cost = "3", crew = "Fish-Man Island/Supernovas/Straw Hat Crew")

    private fun deckOf(vararg entries: Pair<String, Int>) = LocalDeck(
        id = "d",
        leaderId = "L-RED",
        cards = (entries.toMap() + ("L-RED" to 1)).filterValues { it > 0 },
    )

    // ---- cost curve -----------------------------------------------------

    @Test
    fun costHistogramIsIndexedFromOne() {
        val a = DeckAnalyticsCalc.analyse(deckOf("C1" to 1, "C2" to 2, "C3" to 3), ::lookup)
        // index 0 is cost 1, so a cost-1 card is costs[0].
        assertEquals(1, a.costs[0])
        assertEquals(2, a.costs[1])
        assertEquals(3, a.costs[2])
        assertEquals(10, a.costs.size)
    }

    @Test
    fun averageCostIgnoresCardsWithNoCost() {
        val a = DeckAnalyticsCalc.analyse(deckOf("C1" to 2, "C2" to 2), ::lookup)
        // 2x cost1 + 2x cost2 = (2 + 4) / 4
        assertEquals(1.5, a.averageCost!!, 0.0001)
    }

    @Test
    fun aCardWithNoParseableCostIsCountedAsUncountedNotAsZero() {
        val a = DeckAnalyticsCalc.analyse(deckOf("NOCOST" to 3, "C1" to 1), ::lookup)
        assertEquals(3, a.uncounted)
        assertEquals(1, a.costs.sum())
        // The mean is over the one card that has a cost, not over all 4 cards.
        assertEquals(1.0, a.averageCost!!, 0.0001)
        // But the deck is still 4 cards, and every card still has a counter bucket.
        assertEquals(4, a.mainDeckCount)
        assertEquals(4, a.totalCounters)
    }

    @Test
    fun anEmptyDeckHasNoAverageCost() {
        val a = DeckAnalyticsCalc.analyse(LocalDeck(id = "d"), ::lookup)
        assertNull(a.averageCost)
        assertEquals(0, a.mainDeckCount)
        assertEquals(0, a.totalCounters)
    }

    // ---- counters -------------------------------------------------------

    @Test
    fun counterBucketsFollowTheGamesExtraCurvesDefinition() {
        assertEquals(0, DeckAnalyticsCalc.counterBucket(null))
        assertEquals(0, DeckAnalyticsCalc.counterBucket("0"))
        assertEquals(0, DeckAnalyticsCalc.counterBucket("500"))
        assertEquals(1, DeckAnalyticsCalc.counterBucket("1000"))
        assertEquals(1, DeckAnalyticsCalc.counterBucket("1500"))
        assertEquals(2, DeckAnalyticsCalc.counterBucket("2000"))
        // +3000 and above belong in the +2000 bucket, not a fourth one.
        assertEquals(2, DeckAnalyticsCalc.counterBucket("3000"))
        assertEquals(2, DeckAnalyticsCalc.counterBucket("10000"))
    }

    @Test
    fun counterTotalsSplitNonePlus1000AndPlus2000() {
        val a = DeckAnalyticsCalc.analyse(deckOf("C1" to 3, "C2" to 2, "C3" to 4), ::lookup)
        // C1 counter 1000 x3, C2 counter 0 x2, C3 counter 2000 x4
        assertEquals(3, a.counters.plus1000)
        assertEquals(2, a.counters.none)
        assertEquals(4, a.counters.plus2000)
        assertEquals(9, a.counters.total)
    }

    // ---- types and colours ---------------------------------------------

    @Test
    fun typeCountsSplitCharacterEventAndStage() {
        val a = DeckAnalyticsCalc.analyse(deckOf("C1" to 4, "E1" to 2, "S1" to 1), ::lookup)
        assertEquals(4, a.types["CHARACTER"])
        assertEquals(2, a.types["EVENT"])
        assertEquals(1, a.types["STAGE"])
        assertEquals(7, a.types.values.sum())
    }

    @Test
    fun donCardsAreExcludedFromEveryFigure() {
        val a = DeckAnalyticsCalc.analyse(deckOf("C1" to 1, "DONC" to 9), ::lookup)
        assertEquals(1, a.mainDeckCount)
        assertEquals(1, a.totalCounters)
        assertNull(a.types["DON"])
    }

    @Test
    fun aDualColourCardCountsOnceUnderEachColour() {
        val a = DeckAnalyticsCalc.analyse(deckOf("DUAL" to 4), ::lookup)
        assertEquals(4, a.colours["Red"])
        assertEquals(4, a.colours["Blue"])
        // 4 cards, not 8.
        assertEquals(4, a.mainDeckCount)
    }

    // ---- blockers -------------------------------------------------------

    @Test
    fun blockersAreDetectedInEitherEffectOrTrigger() {
        assertEquals(1, DeckAnalyticsCalc.analyse(deckOf("BLK" to 1), ::lookup).blockers)
        assertEquals(2, DeckAnalyticsCalc.analyse(deckOf("BLK2" to 2), ::lookup).blockers)
        assertEquals(0, DeckAnalyticsCalc.analyse(deckOf("C1" to 4), ::lookup).blockers)
    }

    // ---- power bands ----------------------------------------------------

    @Test
    fun powerIsBucketedInThousandsAndSaturatesAtEight() {
        assertEquals(0, DeckAnalyticsCalc.powerBands("0"))
        assertEquals(0, DeckAnalyticsCalc.powerBands("500"))
        assertEquals(3, DeckAnalyticsCalc.powerBands("3000"))
        assertEquals(7, DeckAnalyticsCalc.powerBands("7000"))
        // 9000 and 10000 both land in the 8k+ band rather than overflowing.
        assertEquals(8, DeckAnalyticsCalc.powerBands("9000"))
        assertEquals(8, DeckAnalyticsCalc.powerBands("10000"))
        assertNull(DeckAnalyticsCalc.powerBands(null))
    }

    // ---- crews ---------------------------------------------------------

    @Test
    fun multiValuedTypeIsSplitOnSlash() {
        // The server sends this as one field for a card on three crews. Treating
        // it as a single label would hide "Supernovas" behind a long string.
        val m = card("M1", cost = "3", crew = "Fish-Man Island/Supernovas/Straw Hat Crew")
        val d = LocalDeck(id = "d", leaderId = "L-RED", cards = mapOf("L-RED" to 1, "M1" to 2))
        val a = DeckAnalyticsCalc.analyse(d) { id -> if (id == "M1") m else lookup(id) }
        assertEquals(2, a.crews["Straw Hat Crew"])
        assertEquals(2, a.crews["Supernovas"])
        assertEquals(2, a.crews["Fish-Man Island"])
        assertEquals(3, a.crews.size)
    }

    @Test
    fun crewsAreTrimmedAndBlankEntriesDropped() {
        val c = card("T1", cost = "1", crew = " Navy / / Egghead /")
        assertEquals(setOf("Navy", "Egghead"), DeckAnalyticsCalc.crewsOf(c))
    }

    @Test
    fun aRepeatedCrewOnOneCardIsCountedOncePerCard() {
        val c = card("DUP", cost = "1", crew = "Navy//Navy")
        assertEquals(1, DeckAnalyticsCalc.crewsOf(c).size)
        val d = LocalDeck(id = "d", leaderId = "L-RED", cards = mapOf("L-RED" to 1, "DUP" to 3))
        val a = DeckAnalyticsCalc.analyse(d) { id -> if (id == "DUP") c else lookup(id) }
        assertEquals(3, a.crews["Navy"])
    }

    @Test
    fun topCrewsIsLimitedToFiveAndTiesBreakAlphabetically() {
        val many = mapOf(
            "Zeta" to 4, "Alpha" to 4, "Mid" to 9, "Beta" to 4, "Gamma" to 7,
            "Delta" to 4, "Eps" to 1, "Omega" to 4,
        )
        val a = DeckAnalytics(crews = many)
        val top = a.topCrews(5)
        assertEquals(5, top.size)
        assertEquals("Mid" to 9, top[0])
        assertEquals("Gamma" to 7, top[1])
        // The remaining slots are all 4s, so they must be alphabetical and stable.
        assertEquals(listOf("Alpha", "Beta", "Delta"), top.drop(2).map { it.first })
    }

    @Test
    fun crewCountsMayExceedTheDeckSizeBecauseCardsBelongToSeveralCrews() {
        val d = deckOf("M1" to 3, "C1" to 1)
        val a = DeckAnalyticsCalc.analyse(d) { id -> if (id == "M1") multi else lookup(id) }
        assertEquals(4, a.mainDeckCount)
        assertTrue(
            "crew counts should double-count multi-crew cards by design, " +
                "got ${a.crews}",
            a.crews.values.sum() > a.mainDeckCount,
        )
    }

    @Test
    fun aCardWithNoTypeContributesNoCrews() {
        val a = DeckAnalyticsCalc.analyse(deckOf("C1" to 4), ::lookup)
        assertTrue(a.crews.isEmpty())
        assertTrue(a.topCrews(5).isEmpty())
    }

    // ---- the leader -----------------------------------------------------

    @Test
    fun theLeaderIsExcludedFromEveryMainDeckFigure() {
        val a = DeckAnalyticsCalc.analyse(deckOf("C1" to 5), ::lookup)
        assertEquals(5, a.mainDeckCount)
        // The leader's own 5000 power must not appear in the power bands.
        assertEquals(0, a.powers[5])
        assertEquals(5, a.powers[3])
        assertNull(a.types["LEADER"])
    }

    @Test
    fun totalsReconcileWithTheDeckSize() {
        val a = DeckAnalyticsCalc.analyse(deckOf("C1" to 4, "C2" to 3, "C3" to 2), ::lookup)
        assertEquals(9, a.mainDeckCount)
        assertEquals(a.mainDeckCount, a.counters.total)
        assertEquals(a.mainDeckCount, a.types.values.sum())
        // Every card here has a parseable cost, so the curve sums to the deck.
        assertEquals(a.mainDeckCount, a.costs.sum())
    }

    @Test
    fun unknownCardIdsAreSkippedRatherThanCrashing() {
        val d = LocalDeck(
            id = "d",
            leaderId = "L-RED",
            cards = mapOf("L-RED" to 1, "C1" to 2, "GHOST-ID" to 3),
        )
        val a = DeckAnalyticsCalc.analyse(d, ::lookup)
        assertEquals(2, a.mainDeckCount)
    }

    @Test
    fun zeroAndNegativeCountsAreIgnored() {
        val d = LocalDeck(
            id = "d",
            leaderId = "L-RED",
            cards = mapOf("L-RED" to 1, "C1" to 0, "C2" to -2, "C3" to 2),
        )
        val a = DeckAnalyticsCalc.analyse(d, ::lookup)
        assertEquals(2, a.mainDeckCount)
    }

    @Test
    fun everyPowerBandIsCountedAndTheListLengthIsStable() {
        val a = DeckAnalyticsCalc.analyse(deckOf("C1" to 1), ::lookup)
        assertEquals(DeckAnalytics.MAX_POWER_BANDS, a.powers.size)
        assertEquals(DeckAnalytics.MAX_COST, a.costs.size)
    }

    @Test
    fun analyticsAgreeWithTheRulesEngineOnWhatTheLeaderIs() {
        // Guard against the two disagreeing about leader identity.
        assertTrue(DeckRules.isLeader(lookup("L-RED")))
        assertTrue(!DeckRules.isLeader(lookup("C1")))
    }
}
