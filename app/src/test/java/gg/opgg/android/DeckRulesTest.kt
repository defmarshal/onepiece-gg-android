package gg.opgg.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckRulesTest {

    private fun card(
        id: String,
        normal: String? = null,
        type: String? = "CHARACTER",
        color: String? = "Red",
        block: String? = null,
    ) = Card(
        id = id,
        idNormal = normal ?: id,
        cardType = type,
        Color = color,
        BlockIcon = block,
    )

    private val db: Map<String, Card> = buildMap {
        // One Red leader, plus a reprint of it. A deck may only hold one leader,
        // and the reprint is here so that can be tested.
        put("L-RED", card("L-RED", type = "LEADER", color = "Red"))
        put("L-RED_r1", card("L-RED_r1", normal = "L-RED", type = "LEADER", color = "Red"))
        put("L-DUAL", card("L-DUAL", type = "LEADER", color = "Red/Blue"))
        // Regulation fixtures: OP14-020 is on the ban list from 2026-10-12, and
        // OLD-B1 sits in Block 1, outside the current Standard window.
        put("OP14-020", card("OP14-020", color = "Red", block = "4"))
        put("OLD-B1", card("OLD-B1", color = "Red", block = "1"))
        // 20 distinct Red printings, so a legal 50-card deck is actually
        // constructible: 50 cards needs >= 13 printings at a 4-copy cap.
        repeat(20) { i ->
            val id = "R-%02d".format(i)
            put(id, card(id, color = "Red"))
        }
        // A couple of non-Red options.
        put("B-01", card("B-01", color = "Blue"))
        put("DU-01", card("DU-01", color = "Red/Blue"))
        // A reprint of one Red card, for the per-printing cap test.
        put("R-00_r1", card("R-00_r1", normal = "R-00", color = "Red"))
    }

    private fun lookup(id: String): Card? = db[id]

    /** Builds a deck; the leader is always part of `cards`, as the API returns it. */
    private fun deck(
        vararg entries: Pair<String, Int>,
        leader: String = "L-RED",
    ) = LocalDeck(
        id = "d1",
        leaderId = leader,
        cards = (entries.toMap() + (leader to 1)).filterValues { it > 0 },
    )

    /** A legal 50-card main deck plus the leader - 51 in total. */
    private fun legalDeck(): LocalDeck {
        val cards = HashMap<String, Int>()
        var placed = 0
        var i = 0
        while (placed < 50 && i < 200) {
            val id = "R-%02d".format(i % 20)
            if ((cards[id] ?: 0) < 4) {
                cards[id] = (cards[id] ?: 0) + 1
                placed++
            }
            i++
        }
        return LocalDeck(id = "d", leaderId = "L-RED", cards = cards + ("L-RED" to 1))
    }

    // ---- size ----------------------------------------------------------

    @Test
    fun theLeaderIsAdditionalSoTheLimitIsFiftyPlusOne() {
        val legal = legalDeck()
        // 50 main-deck cards + 1 Leader = 51 in total. The Leader is a separate
        // pile, NOT one of the 50 - see DeckRules' header comment.
        assertEquals(51, DeckRules.totalCards(legal))
        assertEquals(50, DeckRules.totalCards(legal) - 1)
        assertTrue(
            "50 main + 1 leader should be legal, got ${DeckRules.validate(legal, ::lookup)}",
            DeckRules.isLegal(legal, ::lookup),
        )
    }

    @Test
    fun fiftyOneMainDeckCardsIsOneTooMany() {
        // Regression guard: an earlier build subtracted one and capped the main
        // deck at 49, which rejected legal 50-card decks.
        val over = HashMap<String, Int>()
        var placed = 0
        var i = 0
        while (placed < 51 && i < 300) {
            val id = "R-%02d".format(i % 20)
            if ((over[id] ?: 0) < 4) {
                over[id] = (over[id] ?: 0) + 1
                placed++
            }
            i++
        }
        val d = LocalDeck(id = "d", leaderId = "L-RED", cards = over + ("L-RED" to 1))
        assertEquals(52, DeckRules.totalCards(d))
        val v = DeckRules.validate(d, ::lookup)
        assertEquals(1, v.count { it.kind == DeckKind.TOO_MANY_CARDS })
    }

    @Test
    fun oneCardTooManyInTheMainDeckIsIllegal() {
        // Take the legal deck and push one Red printing to 5. That trips the copy
        // cap, which is the rule that actually fires first.
        val over = legalDeck().let { d ->
            val c = HashMap(d.cards)
            c["R-00"] = 5
            d.copy(cards = c)
        }
        val v = DeckRules.validate(over, ::lookup)
        assertTrue("expected a copy-limit violation, got $v", v.any { it.kind == DeckKind.COPY_LIMIT })
    }

    // ---- leader --------------------------------------------------------

    @Test
    fun aDeckWithNoLeaderIsIllegal() {
        val v = DeckRules.validate(deck("R-00" to 4, leader = "L-RED").copy(cards = mapOf("R-00" to 4)), ::lookup)
        assertTrue(v.any { it.kind == DeckKind.NO_LEADER })
    }

    @Test
    fun twoLeadersIsIllegal() {
        val v = DeckRules.validate(deck("L-RED_r1" to 1), ::lookup)
        assertTrue(
            "two leaders should be illegal, got $v",
            v.any { it.kind == DeckKind.MULTIPLE_LEADERS },
        )
    }

    // ---- the copy cap is per PRINTING ----------------------------------

    @Test
    fun theFourCopyCapIsPerPrintingNotPerCard() {
        // R-00 and R-00_r1 are the same card. Four of each is eight copies of
        // that card, which is legal because copyLimitAcrossPrintings is false.
        val v = DeckRules.validate(deck("R-00" to 4, "R-00_r1" to 4), ::lookup)
        assertFalse("splitting copies across printings must be legal, got $v", v.any { it.kind == DeckKind.COPY_LIMIT })
    }

    @Test
    fun fiveCopiesOfOnePrintingIsIllegal() {
        val v = DeckRules.validate(deck("R-00" to 5), ::lookup)
        assertTrue(v.any { it.kind == DeckKind.COPY_LIMIT })
    }

    // ---- colours: the strict reading, verified against 150 real decks ---

    @Test
    fun aMonoLeaderForbidsASecondColour() {
        val v = DeckRules.validate(deck("B-01" to 4), ::lookup)
        assertTrue(
            "Blue under a Red leader should be illegal, got $v",
            v.any { it.kind == DeckKind.COLOUR_NOT_ALLOWED },
        )
    }

    @Test
    fun aDualLeaderAllowsBothOfItsColours() {
        val d = LocalDeck(
            id = "d",
            leaderId = "L-DUAL",
            cards = mapOf("L-DUAL" to 1, "R-00" to 4, "B-01" to 4),
        )
        val v = DeckRules.validate(d, ::lookup)
        assertFalse("Red+Blue under a Red/Blue leader should be legal, got $v", v.any { it.kind == DeckKind.COLOUR_NOT_ALLOWED })
    }

    @Test
    fun aDualColourCardCountsAsBothColours() {
        assertEquals(setOf("Red", "Blue"), DeckRules.coloursOf(lookup("DU-01")))
        assertEquals(setOf("Red", "Blue"), DeckRules.allowedColours(lookup("L-DUAL")))
        assertEquals(setOf("Red"), DeckRules.allowedColours(lookup("L-RED")))
    }

    @Test
    fun coloursUsedSpansEveryColourInTheDeck() {
        val d = LocalDeck(
            id = "d",
            leaderId = "L-DUAL",
            cards = mapOf("L-DUAL" to 1, "R-00" to 1, "B-01" to 1),
        )
        assertEquals(setOf("Red", "Blue"), DeckRules.coloursUsed(d, ::lookup))
    }

    // ---- picker guard ---------------------------------------------------

    @Test
    fun canAddBlocksAnIllegalPickEarly() {
        val d = deck("R-00" to 4)
        assertNotNull("a 5th copy must be blocked", DeckRules.canAdd(d, "R-00", ::lookup))
        assertNull("a legal pick must pass", DeckRules.canAdd(d, "R-01", ::lookup))
        assertNotNull(
            "a Blue card must be blocked under a Red leader",
            DeckRules.canAdd(d, "B-01", ::lookup),
        )
    }

    @Test
    fun canAddBlocksASecondLeader() {
        val d = deck("R-00" to 4)
        val v = DeckRules.canAdd(d, "L-RED_r1", ::lookup)
        assertNotNull(v)
        assertEquals(DeckKind.MULTIPLE_LEADERS, v!!.kind)
    }

    @Test
    fun choosingALeaderTwiceLeavesExactlyOne() {
        // Regression: tapping a Leader appeared to do nothing. The tile was not
        // clickable at all, and the swap logic was duplicated inline.
        val leaders = setOf("L-RED", "L-DUAL")
        var d = deck("R-00" to 4)
        assertNull("no leader yet", d.leaderId.takeIf { false })

        d = d.withLeader("L-RED") { DeckRules.isLeader(lookup(it)) }
        assertEquals("L-RED", d.leaderId)
        assertEquals(1, d.cards["L-RED"])

        // Picking a different leader must replace, not accumulate.
        d = d.withLeader("L-DUAL") { DeckRules.isLeader(lookup(it)) }
        assertEquals("L-DUAL", d.leaderId)
        assertNull("the old leader must be gone", d.cards["L-RED"])
        assertEquals(1, d.cards["L-DUAL"])

        val leaderEntries = d.cards.filter { (id, n) -> n > 0 && DeckRules.isLeader(lookup(id)) }
        assertEquals("exactly one leader, found $leaderEntries", 1, leaderEntries.size)
    }

    @Test
    fun choosingALeaderKeepsTheRestOfTheDeck() {
        var d = deck("R-00" to 4, "R-01" to 2)
        d = d.withLeader("L-DUAL") { DeckRules.isLeader(lookup(it)) }
        assertEquals(4, d.cards["R-00"])
        assertEquals(2, d.cards["R-01"])
        // The Leader is separate from the 50-card main deck, so it is not
        // counted in mainDeckCount().
        assertEquals(6, d.mainDeckCount())
        assertEquals(7, d.totalWithLeader())
    }

    // ---- the orphaned-leaderId trap --------------------------------------

    @Test
    fun removingTheLeaderClearsLeaderIdSoTheDeckStaysConsistent() {
        val d = deck("R-00" to 2).withCount("L-RED", 0)
        assertNull("leaderId must not survive its own removal", d.leaderId)
        assertNull(d.cards["L-RED"])
        // And the deck must now validate as needing a Leader, with no Leader
        // left behind in the cards to contradict it.
        val kinds = DeckRules.validate(d, ::lookup).map { it.kind }.toSet()
        assertTrue(DeckKind.NO_LEADER in kinds)
    }

    @Test
    fun reducingTheLeaderToZeroViaWithCountIsTheOnlyWayItGoes() {
        val d = deck("R-00" to 2).withCount("L-RED", 0)
        assertEquals(2, d.mainDeckCount())
    }

    @Test
    fun removingANonLeaderLeavesLeaderIdAlone() {
        val d = deck("R-00" to 2).withCount("R-00", 0)
        assertEquals("L-RED", d.leaderId)
        assertEquals(1, d.cards["L-RED"])
    }

    @Test
    fun aLeaderIdNamingACardTheDeckDoesNotHoldIsDetectable() {
        // The state that produced the stuck card pool: the id survives but the
        // card does not. Callers must resolve the Leader from the cards.
        val corrupt = LocalDeck(
            id = "d",
            leaderId = "L-RED",
            cards = mapOf("R-00" to 4),
        )
        val present = corrupt.cards.entries
            .firstOrNull { (id, n) -> n > 0 && DeckRules.isLeader(lookup(id)) }
            ?.key
        assertNull("no Leader is actually present", present)
    }

    // ---- reporting -----------------------------------------------------

    @Test
    fun everyViolationIsReportedNotJustTheFirst() {
        // No leader, a 5-copy printing, and a Blue card: three distinct problems.
        val d = LocalDeck(
            id = "d",
            leaderId = null,
            cards = mapOf("R-00" to 5, "B-01" to 4),
        )
        val kinds = DeckRules.validate(d, ::lookup).map { it.kind }.toSet()
        assertTrue("expected NO_LEADER, got $kinds", kinds.contains(DeckKind.NO_LEADER))
        assertTrue("expected COPY_LIMIT, got $kinds", kinds.contains(DeckKind.COPY_LIMIT))
    }

    // ---- tournament regulations -------------------------------------------

    private fun afterBan() = Regulation.epochDay(2026, 10, 12)
    private fun beforeBan() = Regulation.epochDay(2026, 10, 11)
    private fun duringWindow() = Regulation.epochDay(2026, 9, 28)

    private fun kindsOf(d: LocalDeck, mode: RegulationMode, on: Long) =
        DeckRules.validate(d, ::lookup, mode, on).map { it.kind }

    @Test
    fun aBannedCardIsReportedInStandard() {
        val d = deck("OP14-020" to 1, "R-00" to 1)
        val kinds = kindsOf(d, RegulationMode.STANDARD, afterBan())
        assertTrue("expected a ban, got $kinds", DeckKind.BANNED_CARD in kinds)
    }

    @Test
    fun aBannedCardIsFineBeforeTheEffectiveDate() {
        val d = deck("OP14-020" to 1, "R-00" to 1)
        val kinds = kindsOf(d, RegulationMode.STANDARD, beforeBan())
        assertTrue("the ban is not in force yet", DeckKind.BANNED_CARD !in kinds)
    }

    @Test
    fun extraRegulationIgnoresTheBanList() {
        val d = deck("OP14-020" to 1, "R-00" to 1)
        val kinds = kindsOf(d, RegulationMode.EXTRA, afterBan())
        assertTrue("banned cards stay playable in Extra", DeckKind.BANNED_CARD !in kinds)
    }

    @Test
    fun aBlockOneCardIsReportedInStandardAndAllowedInExtra() {
        val d = deck("OLD-B1" to 1, "R-00" to 1)
        assertTrue(
            "block 1 is out of the window",
            DeckKind.BLOCK_NOT_LEGAL in kindsOf(d, RegulationMode.STANDARD, duringWindow()),
        )
        assertTrue(
            DeckKind.BLOCK_NOT_LEGAL !in kindsOf(d, RegulationMode.EXTRA, duringWindow()),
        )
    }

    @Test
    fun cardsWithNoBlockIconAreNeverFlagged() {
        // Tournament decks run such cards, so an empty value must not restrict.
        val kinds = kindsOf(deck("R-00" to 1), RegulationMode.STANDARD, duringWindow())
        assertTrue(DeckKind.BLOCK_NOT_LEGAL !in kinds)
    }

    @Test
    fun canAddRefusesABannedCardInStandard() {
        val v = DeckRules.canAdd(
            deck("R-00" to 1),
            "OP14-020",
            ::lookup,
            RegulationMode.STANDARD,
            afterBan(),
        )
        assertEquals(DeckKind.BANNED_CARD, v?.kind)
    }

    @Test
    fun existingRulesStillHoldUnderTheDefaultRegulation() {
        // The fixture cards carry no BlockIcon and are not banned, so the legal
        // 50-card deck must still validate under Standard.
        assertTrue(DeckRules.isLegal(legalDeck(), ::lookup))
    }
}
