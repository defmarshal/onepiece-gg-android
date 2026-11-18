package gg.opgg.android

import gg.opgg.android.data.LocalDeck
import gg.opgg.android.data.LocalDeckOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rename and duplicate rules. Pure, so no device and no ViewModel. */
class LocalDeckOpsTest {

    private fun deck(
        id: String = "d1",
        name: String = "Beast",
        cards: Map<String, Int> = mapOf("L" to 1, "A" to 4, "B" to 2),
    ) = LocalDeck(
        id = id,
        name = name,
        leaderId = "L",
        cards = cards,
        createdAt = 100L,
        updatedAt = 200L,
    )

    // ---- cleanName -------------------------------------------------------

    @Test
    fun aNameIsTrimmed() {
        assertEquals("Beast", LocalDeckOps.cleanName("  Beast  "))
    }

    @Test
    fun aBlankNameFallsBackRatherThanBeingAccepted() {
        // A nameless deck is far harder to find in the list than a generic one.
        assertEquals("New deck", LocalDeckOps.cleanName(""))
        assertEquals("New deck", LocalDeckOps.cleanName("   "))
        assertEquals("New deck", LocalDeckOps.cleanName(null))
        assertEquals("Beast", LocalDeckOps.cleanName("", fallback = "Beast"))
    }

    // ---- copy naming -----------------------------------------------------

    @Test
    fun theFirstCopyIsNamedOriginalCopy() {
        assertEquals("Beast (copy)", LocalDeckOps.uniqueCopyName("Beast", emptyList()))
    }

    @Test
    fun aSecondCopyIsNumberedRatherThanStackingOrColliding() {
        val taken = listOf("Beast", "Beast (copy)")
        assertEquals("Beast (copy 2)", LocalDeckOps.uniqueCopyName("Beast", taken))
        val taken3 = taken + "Beast (copy 2)"
        assertEquals("Beast (copy 3)", LocalDeckOps.uniqueCopyName("Beast", taken3))
    }

    @Test
    fun copyNumberingSkipsNamesThatAreAlreadyTaken() {
        // "Beast (copy 2)" is taken but "Beast (copy)" is not, so the plain form
        // is used rather than jumping to 2.
        val taken = listOf("Beast", "Beast (copy 2)")
        assertEquals("Beast (copy)", LocalDeckOps.uniqueCopyName("Beast", taken))
    }

    @Test
    fun copyingAnAlreadyCopiedDeckDoesNotProduceASillyName() {
        val taken = listOf("Beast", "Beast (copy)")
        assertEquals(
            "Beast (copy) (copy)",
            LocalDeckOps.uniqueCopyName("Beast (copy)", taken),
        )
    }

    @Test
    fun aBlankBaseNameStillProducesSomethingUsable() {
        assertEquals("New deck (copy)", LocalDeckOps.uniqueCopyName("  ", emptyList()))
    }

    // ---- duplicate -------------------------------------------------------

    @Test
    fun aDuplicateGetsAFreshIdAndItsOwnCardMap() {
        val src = deck()
        val copy = LocalDeckOps.duplicate(src, "d2", now = 500L, existingNames = listOf("Beast"))
        assertEquals("d2", copy.id)
        assertNotSame("must not alias the original's map", src.cards, copy.cards)
        assertEquals(src.cards, copy.cards)
    }

    @Test
    fun editingACopyDoesNotDisturbTheOriginal() {
        val src = deck()
        val copy = LocalDeckOps.duplicate(src, "d2", now = 500L, existingNames = listOf("Beast"))
        // The realistic failure: both decks edited through a shared map.
        val edited = copy.withCount("A", 1)
        assertEquals(4, src.cards["A"])
        assertEquals(1, edited.cards["A"])
        assertEquals("the copy itself must be unchanged too", 4, copy.cards["A"])
    }

    @Test
    fun aDuplicateResetsBothTimestamps() {
        val copy = LocalDeckOps.duplicate(deck(), "d2", now = 500L, existingNames = listOf("Beast"))
        assertEquals("createdAt must be the copy's own time", 500L, copy.createdAt)
        assertEquals("updatedAt must be the copy's own time", 500L, copy.updatedAt)
    }

    @Test
    fun aDuplicateKeepsTheLeaderAndCardCounts() {
        val copy = LocalDeckOps.duplicate(deck(), "d2", now = 500L, existingNames = listOf("Beast"))
        assertEquals("L", copy.leaderId)
        assertEquals(mapOf("L" to 1, "A" to 4, "B" to 2), copy.cards)
        assertTrue(copy.cards.containsKey("L"))
    }

    @Test
    fun aDuplicateIsNamedAgainstTheCurrentListNotJustTheOriginal() {
        val taken = listOf("Beast", "Beast (copy)")
        val copy = LocalDeckOps.duplicate(deck(), "d2", now = 1L, existingNames = taken)
        assertEquals("Beast (copy 2)", copy.name)
        assertFalse(copy.name in taken)
    }
}
