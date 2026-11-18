package gg.opgg.android

import gg.opgg.android.data.Regulation
import gg.opgg.android.data.RegulationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regulation rules: the rolling Block window, the hand-maintained ban list, and
 * the hand-rolled date arithmetic it rests on.
 */
class RegulationTest {

    private fun day(y: Int, m: Int, d: Int) = Regulation.epochDay(y, m, d)

    // ---- date arithmetic --------------------------------------------------

    @Test
    fun epochDayMatchesKnownValues() {
        // Anchors that a wrong implementation would miss.
        assertEquals(0L, day(1970, 1, 1))
        assertEquals(1L, day(1970, 1, 2))
        assertEquals(31L, day(1970, 2, 1))
        assertEquals(365L, day(1971, 1, 1))
        // 2000 was a leap year, so Feb 28 -> Mar 1 is TWO days, not one.
        assertEquals(day(2000, 2, 28) + 2, day(2000, 3, 1))
        assertEquals(day(2000, 2, 29) + 1, day(2000, 3, 1))
        // 1900 was not a leap year (divisible by 100, not 400), so it is one day.
        assertEquals(day(1900, 2, 28) + 1, day(1900, 3, 1))
    }

    @Test
    fun civilIsTheInverseOfEpochDay() {
        listOf(
            Triple(1970, 1, 1),
            Triple(2000, 2, 29),   // leap day
            Triple(2026, 10, 12),  // the ban date
            Triple(2027, 4, 1),    // the next window boundary
            Triple(1999, 12, 31),
        ).forEach { (y, m, d) ->
            assertEquals("$y-$m-$d", Triple(y, m, d), Regulation.civil(day(y, m, d)))
        }
    }

    @Test
    fun epochDayRoundTripsAcrossAFourYearSpan() {
        // Cheap exhaustive check: 1500 consecutive days, including a leap day.
        var e = day(2025, 1, 1)
        repeat(1500) {
            val (y, m, d) = Regulation.civil(e)
            assertEquals("day $e", e, day(y, m, d))
            e += 1
        }
    }

    // ---- the Block window -------------------------------------------------

    @Test
    fun theWindowMatchesBandaisPublishedSchedule() {
        // Apr 2026 - Mar 2027 -> blocks 2-5, as published.
        assertEquals(2..5, Regulation.standardBlocks(day(2026, 4, 1)))
        assertEquals(2..5, Regulation.standardBlocks(day(2026, 9, 28)))
        assertEquals(2..5, Regulation.standardBlocks(day(2027, 3, 31)))
        // Apr 2027 - Mar 2028 -> blocks 3-6.
        assertEquals(3..6, Regulation.standardBlocks(day(2027, 4, 1)))
        assertEquals(3..6, Regulation.standardBlocks(day(2028, 3, 31)))
        // And it keeps rolling.
        assertEquals(4..7, Regulation.standardBlocks(day(2028, 4, 1)))
    }

    @Test
    fun januaryToMarchStillBelongToTheWindowThatOpenedLastApril() {
        // The easy mistake: treating January 2027 as a new season.
        assertEquals(Regulation.standardBlocks(day(2026, 4, 1)), Regulation.standardBlocks(day(2027, 1, 15)))
        assertEquals(Regulation.standardBlocks(day(2027, 4, 1)), Regulation.standardBlocks(day(2028, 2, 15)))
    }

    @Test
    fun theBoundaryIsExactlyTheFirstOfApril() {
        assertEquals(2..5, Regulation.standardBlocks(day(2027, 3, 31)))
        assertEquals(3..6, Regulation.standardBlocks(day(2027, 4, 1)))
    }

    // ---- block legality ---------------------------------------------------

    @Test
    fun blockOneIsNotLegalInTheCurrentWindow() {
        assertFalse(Regulation.isBlockAllowed("1", day(2026, 9, 28), RegulationMode.STANDARD))
    }

    @Test
    fun blocksTwoToFiveAreLegalNow() {
        for (b in listOf("2", "3", "4", "5")) {
            assertTrue("block $b", Regulation.isBlockAllowed(b, day(2026, 9, 28), RegulationMode.STANDARD))
        }
    }

    @Test
    fun anEmptyBlockIconIsLegal() {
        // Verified against tournament decks: 1.8% of their copies were cards with
        // no BlockIcon, so an empty value cannot mean restricted.
        assertTrue(Regulation.isBlockAllowed("", day(2026, 9, 28), RegulationMode.STANDARD))
        assertTrue(Regulation.isBlockAllowed(null, day(2026, 9, 28), RegulationMode.STANDARD))
        assertTrue(Regulation.isBlockAllowed("   ", day(2026, 9, 28), RegulationMode.STANDARD))
    }

    @Test
    fun blockXIsAlwaysLegal() {
        assertTrue(Regulation.isBlockAllowed("X", day(2026, 9, 28), RegulationMode.STANDARD))
        assertTrue(Regulation.isBlockAllowed("x", day(2026, 9, 28), RegulationMode.STANDARD))
    }

    @Test
    fun anUnparseableBlockIconIsTreatedAsLegalRatherThanBanningACard() {
        // Guessing "restricted" here would wrongly outlaw real cards.
        assertTrue(Regulation.isBlockAllowed("?", day(2026, 9, 28), RegulationMode.STANDARD))
        assertTrue(Regulation.isBlockAllowed("ban", day(2026, 9, 28), RegulationMode.STANDARD))
    }

    @Test
    fun extraAllowsEveryBlock() {
        assertTrue(Regulation.isBlockAllowed("1", day(2026, 9, 28), RegulationMode.EXTRA))
        assertTrue(Regulation.isBlockAllowed("9", day(2026, 9, 28), RegulationMode.EXTRA))
    }

    // ---- the ban list -----------------------------------------------------

    @Test
    fun mihawkIsNotBannedBeforeTheTwelfthOfOctober() {
        val before = day(2026, 10, 11)
        assertFalse(Regulation.isBanned("OP14-020", before, RegulationMode.STANDARD))
    }

    @Test
    fun mihawkIsBannedFromTheTwelfthOfOctober() {
        val on = day(2026, 10, 12)
        assertTrue(Regulation.isBanned("OP14-020", on, RegulationMode.STANDARD))
        assertTrue(Regulation.isBanned("OP14-020", day(2026, 12, 25), RegulationMode.STANDARD))
    }

    @Test
    fun theBanReachesEveryPrintingBecauseItIsStoredCanonically() {
        val on = day(2026, 10, 12)
        assertTrue(Regulation.isBanned("OP14-020", on, RegulationMode.STANDARD))
        // The same holds for the parallels, which the notice calls out. A caller
        // passing a printing id must not accidentally exempt them.
        assertTrue(Regulation.isBanned("OP14-020_p1", on, RegulationMode.STANDARD))
        assertTrue(Regulation.isBanned("OP14-020_p2", on, RegulationMode.STANDARD))
        assertTrue(Regulation.isBanned("OP14-020_r1", on, RegulationMode.STANDARD))
    }

    @Test
    fun aBannedCardsNeighbourIsNotCaughtBySuffixStripping() {
        val on = day(2026, 12, 1)
        assertFalse(Regulation.isBanned("OP14-021", on, RegulationMode.STANDARD))
        assertFalse(Regulation.isBanned("OP14-0201", on, RegulationMode.STANDARD))
    }

    @Test
    fun extraIgnoresBansSoBannedCardsStayPlayable() {
        val on = day(2026, 12, 1)
        assertTrue(Regulation.isBanned("OP14-020", on, RegulationMode.STANDARD))
        assertFalse(Regulation.isBanned("OP14-020", on, RegulationMode.EXTRA))
    }

    @Test
    fun otherCardsAreNotBanned() {
        val on = day(2026, 12, 1)
        assertFalse(Regulation.isBanned("OP13-001", on, RegulationMode.STANDARD))
        assertFalse(Regulation.isBanned("OP14-021", on, RegulationMode.STANDARD))
    }

    @Test
    fun theBanListIsDatedSoItCannotBeTrustedBlindly() {
        // The list is hand-maintained because the API has no ban status, so its
        // age has to be visible.
        val (y, m, d) = Regulation.civil(Regulation.banListAsOf)
        assertTrue("list must not claim to be checked in the future", Regulation.banListAsOf <= day(2026, 10, 12))
        assertEquals(2026, y)
        assertTrue(m in 1..12 && d in 1..31)
    }

    @Test
    fun everyBanEntryNamesACardAndASource() {
        Regulation.banList.forEach {
            assertTrue("blank canonical id", it.canonicalId.isNotBlank())
            assertTrue("blank source", it.source.isNotBlank())
            assertTrue("blank note", it.note.isNotBlank())
        }
    }

    // ---- the summary line -------------------------------------------------

    @Test
    fun theDescriptionSaysWhatIsBeingEnforcedAndHowOldTheListIs() {
        val s = Regulation.describe(day(2026, 9, 28), RegulationMode.STANDARD)
        assertTrue(s, s.contains("Standard"))
        assertTrue(s, s.contains("2-5"))
        assertTrue(s, s.contains("as of"))
        val e = Regulation.describe(day(2026, 9, 28), RegulationMode.EXTRA)
        assertTrue(e, e.contains("Extra"))
        assertTrue(e, e.contains("not applied"))
    }
}
