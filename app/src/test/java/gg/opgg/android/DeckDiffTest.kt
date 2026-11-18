package gg.opgg.android

import gg.opgg.android.data.Card
import gg.opgg.android.data.DeckDiff
import gg.opgg.android.data.LocalDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deck vs collection. The two judgement calls are that printings collapse and
 * that the Leader counts as a requirement, so both are pinned here.
 */
class DeckDiffTest {

    private fun card(id: String, normal: String? = null, type: String? = "CHARACTER") =
        Card(id = id, idNormal = normal, cardType = type, name = nameOf(id))

    private fun nameOf(id: String) = "Card $id"

    // L = leader, A = a card, A_p1 = a reprint of A, B = another card.
    private val db: Map<String, Card> = buildMap {
        put("L", card("L", type = "LEADER"))
        put("A", card("A"))
        put("A_p1", card("A_p1", normal = "A"))
        put("B", card("B"))
    }

    private fun lookup(id: String): Card? = db[id]

    private fun deck(vararg entries: Pair<String, Int>) = LocalDeck(
        id = "d",
        leaderId = "L",
        cards = (entries.toMap() + ("L" to 1)).filterValues { it > 0 },
    )

    private fun diff(d: LocalDeck, owned: Map<String, Int>) =
        DeckDiff.diff(d, { owned[it] ?: 0 }, ::lookup)

    // ---- printings collapse ----------------------------------------------

    @Test
    fun owningADifferentPrintingSatisfiesTheRequirement() {
        // The deck asks for 4x A; the collection holds 4x the parallel.
        // Reporting "4 missing" would be a false answer to "what do I buy".
        val r = diff(deck("A" to 4), mapOf("A" to 4, "L" to 1))
        assertTrue(r.isComplete)
        assertEquals(0, r.missingCopies)
    }

    @Test
    fun aReprintHeldIsCountedTowardsTheSameCanonicalCard() {
        // 2 standard + 2 foil of any printing still covers 4.
        val r = diff(deck("A" to 4), mapOf("A" to 4))
        assertEquals(1, r.covered)
    }

    @Test
    fun requiredCountsAreSummedAcrossTheDecksOwnPrintings() {
        // The deck runs both printings of A, 2 each = 4 required.
        val d = deck("A" to 2, "A_p1" to 2)
        val r = diff(d, mapOf("L" to 1))
        assertEquals(1, r.missing.size)
        assertEquals(4, r.missing[0].required)
        assertEquals(4, r.missing[0].missing)
        assertEquals(setOf("A", "A_p1"), r.missing[0].printingsInDeck.toSet())
    }

    @Test
    fun anUnknownPrintingIsTreatedAsItsOwnCard() {
        // The database may not have it yet; the deck must not silently vanish.
        val d = LocalDeck(
            id = "d",
            leaderId = "L",
            cards = mapOf("L" to 1, "MYSTERY" to 2),
        )
        val r = diff(d, mapOf("L" to 1))
        assertEquals("only MYSTERY is short", 2, r.missingCopies)
    }

    // ---- the Leader is a requirement -------------------------------------

    @Test
    fun theLeaderCountsAsSomethingYouNeed() {
        val r = diff(deck("A" to 1), mapOf("A" to 1))
        assertEquals(1, r.missing.size)
        assertTrue("the missing card should be flagged as the Leader", r.missing[0].isLeader)
        assertEquals("L", r.missing[0].normalId)
    }

    @Test
    fun owningTheLeaderClearsIt() {
        val r = diff(deck("A" to 1), mapOf("A" to 1, "L" to 1))
        assertTrue(r.isComplete)
    }

    @Test
    fun requiredTotalIncludesTheLeader() {
        val r = diff(deck("A" to 2, "B" to 1), mapOf("A" to 2, "B" to 1, "L" to 1))
        assertEquals(4, r.requiredCards)
    }

    // ---- the actual gap --------------------------------------------------

    @Test
    fun aShortfallIsReportedWithBothCounts() {
        val r = diff(deck("A" to 4), mapOf("A" to 2, "L" to 1))
        assertEquals(1, r.missing.size)
        val m = r.missing[0]
        assertEquals(4, m.required)
        assertEquals(2, m.owned)
        assertEquals(2, m.missing)
        assertFalse(r.isComplete)
    }

    @Test
    fun extraCopiesDoNotProduceANegativeShortfall() {
        val r = diff(deck("A" to 2), mapOf("A" to 7, "L" to 1))
        assertTrue(r.missing.isEmpty())
    }

    @Test
    fun theBiggestGapIsListedFirst() {
        val r = diff(
            deck("A" to 4, "B" to 3),
            mapOf(),
        )
        assertEquals(4, r.missing[0].missing)
        assertEquals(3, r.missing[1].missing)
    }

    @Test
    fun anEmptyCollectionMeansEverythingIsMissing() {
        // Three cards short, and the Leader is one of them: it is a real
        // requirement even though it sits outside the 50.
        val r = diff(deck("A" to 4, "B" to 2), mapOf())
        assertEquals(3, r.missing.size)
        assertEquals(7, r.missingCopies)
        assertEquals(1, r.missing.count { it.isLeader })
    }

    @Test
    fun aCompleteCollectionIsRecognised() {
        val r = diff(deck("A" to 4, "B" to 1), mapOf("A" to 4, "B" to 1, "L" to 1))
        assertTrue(r.isComplete)
        assertEquals(0, r.missingCopies)
        assertEquals(3, r.covered)
    }

    @Test
    fun anEmptyDeckNeedsNothing() {
        val r = diff(LocalDeck(id = "d"), mapOf())
        assertTrue(r.isComplete)
        assertEquals(0, r.requiredCards)
    }

    @Test
    fun nonPositiveCountsInTheDeckAreIgnored() {
        val d = LocalDeck(
            id = "d",
            leaderId = "L",
            cards = mapOf("L" to 1, "A" to 0, "B" to -3),
        )
        val r = diff(d, mapOf())
        assertEquals(1, r.requiredCards)
    }

    @Test
    fun namesComeFromTheDatabaseWhenAvailable() {
        val r = diff(deck("A" to 1), mapOf())
        assertEquals("Card A", r.missing[0].name)
    }
}
