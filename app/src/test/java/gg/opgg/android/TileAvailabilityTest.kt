package gg.opgg.android

import gg.opgg.android.data.Card
import gg.opgg.android.data.DeckRules
import gg.opgg.android.data.LocalDeck
import gg.opgg.android.data.TileAvailability
import gg.opgg.android.data.TileBlockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tile availability. The behaviour that matters is not "which card can be
 * added" but "which reasons are about the whole deck", because those are the
 * ones that must never be printed on every tile.
 */
class TileAvailabilityTest {

    private fun card(
        id: String,
        type: String? = "CHARACTER",
        color: String = "Red",
    ) = Card(id = id, cardType = type, Color = color)

    private val db: Map<String, Card> = buildMap {
        put("L-RED", card("L-RED", type = "LEADER", color = "Red"))
        put("L-DUAL", card("L-DUAL", type = "LEADER", color = "Red/Blue"))
        put("R-00", card("R-00"))
        put("R-01", card("R-01"))
        put("B-01", card("B-01", color = "Blue"))
    }

    private fun lookup(id: String): Card? = db[id]

    private fun deck(vararg entries: Pair<String, Int>, leader: String? = "L-RED") = LocalDeck(
        id = "d",
        leaderId = leader,
        cards = ((entries.toMap() + listOfNotNull(leader?.let { it to 1 })).toMap())
            .filterValues { it > 0 },
    )

    /** 50 non-Leader cards spread over printings, within the 4-copy cap. */
    private fun fullDeck(): LocalDeck {
        val cards = HashMap<String, Int>()
        var placed = 0
        var i = 0
        while (placed < 50) {
            val id = "R-%02d".format(i)
            if ((cards[id] ?: 0) < 4) {
                cards[id] = (cards[id] ?: 0) + 1
                placed++
            }
            i++
        }
        return LocalDeck(id = "d", leaderId = "L-RED", cards = cards + ("L-RED" to 1))
    }

    private fun reasonOf(d: LocalDeck, id: String, ownedCap: Int? = null) =
        TileAvailability.blockFor(d, id, ::lookup, ownedCap)?.reason

    // ---- deck-level reasons carry no per-tile text -----------------------

    @Test
    fun aDeckWithNoLeaderBlocksEveryNonLeaderWithNoLabel() {
        val d = deck("R-00" to 1, leader = null)
        val b = TileAvailability.blockFor(d, "R-00", ::lookup)
        assertEquals(TileBlockReason.NO_LEADER, b?.reason)
        assertTrue("NO_LEADER is deck-level", b!!.deckLevel)
        assertNull(
            "a deck-level reason must not produce per-tile text",
            b.label,
        )
    }

    @Test
    fun aFullDeckBlocksWithNoLabel() {
        val b = TileAvailability.blockFor(fullDeck(), "R-00", ::lookup)
        assertEquals(TileBlockReason.DECK_FULL, b?.reason)
        assertTrue(b!!.deckLevel)
        assertNull(b.label)
    }

    @Test
    fun deckLevelReasonsAreAllMarkedDeckLevel() {
        listOf(
            TileBlockReason.NO_LEADER,
            TileBlockReason.MULTIPLE_LEADERS,
            TileBlockReason.DECK_FULL,
        ).forEach {
            assertTrue("$it should be deck-level", gg.opgg.android.data.TileBlock(it).deckLevel)
        }
        listOf(
            TileBlockReason.AT_COPY_LIMIT,
            TileBlockReason.AT_OWNED_CAP,
            TileBlockReason.COLOUR_NOT_ALLOWED,
        ).forEach {
            assertFalse("$it should be card-level", gg.opgg.android.data.TileBlock(it).deckLevel)
        }
    }

    // ---- card-level reasons do carry text --------------------------------

    @Test
    fun anUnderOwnedCardSaysHowManyYouOwn() {
        val d = deck("R-00" to 2)
        val b = TileAvailability.blockFor(d, "R-00", ::lookup, ownedCap = 2)
        assertEquals(TileBlockReason.AT_OWNED_CAP, b?.reason)
        assertFalse(b!!.deckLevel)
        // The tile prints "max 2" under its + button, so a caption here would
        // only repeat it.
        assertNull("the + button already states the owned cap", b.label)
    }

    @Test
    fun anOwnedCapOfZeroBlocksACardYouDoNotOwnAtAll() {
        val b = TileAvailability.blockFor(deck(), "R-00", ::lookup, ownedCap = 0)
        assertEquals(TileBlockReason.AT_OWNED_CAP, b?.reason)
        assertNull(b?.label)
    }

    @Test
    fun noOwnedCapMeansTheNormalFourCopyLimitApplies() {
        assertNull(TileAvailability.blockFor(deck("R-00" to 3), "R-00", ::lookup))
        val b = TileAvailability.blockFor(deck("R-00" to 4), "R-00", ::lookup)
        assertEquals(TileBlockReason.AT_COPY_LIMIT, b?.reason)
        // Must stay short so it cannot wrap to two lines and make the tile taller
        // than its neighbours.
        assertEquals("max 4", b?.label)
    }

    @Test
    fun aCardOutsideTheLeaderColoursIsBlocked() {
        val b = TileAvailability.blockFor(deck("R-00" to 1), "B-01", ::lookup)
        assertEquals(TileBlockReason.COLOUR_NOT_ALLOWED, b?.reason)
        assertFalse(b!!.deckLevel)
        assertEquals("Blue not allowed by this Leader.", b.label)
    }

    @Test
    fun aDualLeaderAllowsBothOfItsColours() {
        val d = deck("R-00" to 1, leader = "L-DUAL")
        assertNull(TileAvailability.blockFor(d, "B-01", ::lookup))
    }

    // ---- always allowed --------------------------------------------------

    @Test
    fun aLeaderTileIsNeverBlocked() {
        // Setting a Leader replaces the current one, so the second-leader rule
        // must not block the tile.
        val d = deck("R-00" to 1)
        assertNull(TileAvailability.blockFor(d, "L-DUAL", ::lookup))
    }

    @Test
    fun anAddableCardHasNoBlock() {
        assertNull(reasonOf(deck("R-00" to 1), "R-01"))
    }

    @Test
    fun aFullDeckDoesNotBlockRemovingCards() {
        // Full decks must still be editable downwards; only adding is affected.
        val b = TileAvailability.blockFor(fullDeck(), "R-00", ::lookup)
        assertEquals(TileBlockReason.DECK_FULL, b?.reason)
    }

    // ---- consistency with the rules engine -------------------------------

    @Test
    fun agreesWithCanAddOnWhetherTheCardIsAddable() {
        val cases = listOf(
            deck("R-00" to 1),
            deck("R-00" to 4),
            deck("R-00" to 1, leader = null),
            deck("R-00" to 1, leader = "L-DUAL"),
            fullDeck(),
        )
        for (d in cases) {
            for (id in listOf("R-00", "R-01", "B-01")) {
                val ours = TileAvailability.blockFor(d, id, ::lookup) != null
                val theirs = DeckRules.canAdd(d, id, ::lookup) != null
                assertEquals(
                    "disagreement on $id in a deck of ${d.mainDeckCount()}",
                    theirs,
                    ours,
                )
            }
        }
    }

    @Test
    fun anUnknownCardIdIsNotBlockedRatherThanCrashing() {
        assertNull(TileAvailability.blockFor(deck("R-00" to 1), "NO-SUCH-ID", ::lookup))
    }
}
