package gg.opgg.android

import gg.opgg.android.data.DeckSegment
import gg.opgg.android.data.DeckSegmentState
import gg.opgg.android.data.tap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decks tab segment switching.
 *
 * This exists because the behaviour was wrong and untestable by tapping: two
 * independent booleans plus a handler that toggled both meant "Public" while on
 * Meta showed the Builder. The whole thing is now one enum, and pinned here.
 */
class DeckSegmentTest {

    private fun state(segment: DeckSegment, decksMine: Boolean = false) =
        DeckSegmentState(segment, decksMine)

    // ---- the bug that prompted this -------------------------------------

    @Test
    fun tappingPublicWhileOnMetaGoesToPublicNotTheBuilder() {
        val from = state(DeckSegment.META)
        val after = DeckSegment.META.tap(DeckSegment.PUBLIC, from.decksMine)
        assertEquals(DeckSegment.PUBLIC, after.segment)
        assertFalse(after.builderOpen)
        assertFalse(after.metaOpen)
    }

    @Test
    fun tappingPublicTwiceFromMetaIsStable() {
        var s = state(DeckSegment.META)
        repeat(2) { s = DeckSegment.META.tap(DeckSegment.PUBLIC, s.decksMine) }
        assertEquals(DeckSegment.PUBLIC, s.segment)
        assertFalse(s.builderOpen)
        assertFalse(s.metaOpen)
    }

    @Test
    fun tappingMetaWhileOnTheBuilderGoesToMetaNotPublic() {
        val from = state(DeckSegment.BUILDER)
        val after = DeckSegment.BUILDER.tap(DeckSegment.META, from.decksMine)
        assertEquals(DeckSegment.META, after.segment)
        assertFalse("the Builder must not stay on", after.builderOpen)
        assertTrue(after.metaOpen)
    }

    @Test
    fun builderAndMetaAreNeverBothOpen() {
        // With one enum, "both open" and "neither open" are unrepresentable, so
        // this is a guard on the model rather than on a tap handler.
        DeckSegment.entries.forEach { s ->
            val st = state(s)
            assertFalse("${s.name}: both open", st.builderOpen && st.metaOpen)
            assertEquals(s == DeckSegment.BUILDER, st.builderOpen)
            assertEquals(s == DeckSegment.META, st.metaOpen)
        }
    }

    // ---- tapping the segment you are already on --------------------------

    @Test
    fun tappingTheCurrentBuilderSegmentKeepsTheBuilder() {
        val after = DeckSegment.BUILDER.tap(DeckSegment.BUILDER, decksMine = false)
        assertEquals(DeckSegment.BUILDER, after.segment)
    }

    @Test
    fun tappingTheCurrentMetaSegmentKeepsMeta() {
        val after = DeckSegment.META.tap(DeckSegment.META, decksMine = false)
        assertEquals(DeckSegment.META, after.segment)
    }

    @Test
    fun tappingPublicWhileOnPublicStaysPublic() {
        val after = DeckSegment.PUBLIC.tap(DeckSegment.PUBLIC, decksMine = true)
        assertEquals(DeckSegment.PUBLIC, after.segment)
        assertFalse("Public must clear the my-decks flag", after.decksMine)
    }

    // ---- the decksMine flag ----------------------------------------------

    @Test
    fun publicClearsTheMyDecksFlagAndMineSetsIt() {
        assertFalse(DeckSegment.MINE.tap(DeckSegment.PUBLIC, decksMine = true).decksMine)
        assertTrue(DeckSegment.PUBLIC.tap(DeckSegment.MINE, decksMine = false).decksMine)
    }

    @Test
    fun theBuilderAndMetaLeaveTheMyDecksFlagAlone() {
        // Otherwise visiting the Builder and coming back would silently show
        // your own decks instead of the public list.
        assertTrue(DeckSegment.BUILDER.tap(DeckSegment.META, decksMine = true).decksMine)
        assertFalse(DeckSegment.MINE.tap(DeckSegment.BUILDER, decksMine = false).decksMine)
    }

    // ---- classification --------------------------------------------------

    @Test
    fun onlyPublicAndMineShowTheDeckList() {
        assertTrue(DeckSegment.PUBLIC.isDecks)
        assertTrue(DeckSegment.MINE.isDecks)
        assertFalse(DeckSegment.BUILDER.isDecks)
        assertFalse(DeckSegment.META.isDecks)
    }

    @Test
    fun onlyMineAsksForTheAccountsDecks() {
        assertTrue(DeckSegment.MINE.usesMyDecks)
        DeckSegment.entries.filter { it != DeckSegment.MINE }.forEach {
            assertFalse(it.name, it.usesMyDecks)
        }
    }

    // ---- a full walk, the way a user would drive it ----------------------

    @Test
    fun walkingEverySegmentAndBackAlwaysLandsWhereTapped() {
        var s = state(DeckSegment.PUBLIC)
        // The order a user would plausibly try, including the reported bug.
        val walk = listOf(
            DeckSegment.META, DeckSegment.PUBLIC, DeckSegment.META,
            DeckSegment.BUILDER, DeckSegment.MINE, DeckSegment.PUBLIC,
            DeckSegment.BUILDER, DeckSegment.META, DeckSegment.MINE,
        )
        walk.forEach { target ->
            s = s.segment.tap(target, s.decksMine)
            assertEquals("tapped $target but landed on ${s.segment}", target, s.segment)
        }
    }

    /**
     * The receiver is the *current* segment and the argument is the *tapped*
     * one. A call site wrote them the other way round, so it evaluated
     * `current.tap(current)` and every chip became a silent no-op while the
     * onClick still fired - which is exactly what it looked like from the UI.
     *
     * This walks the same transitions the call site performs, so a swap here
     * fails the test rather than only the screen.
     */
    @Test
    fun theReceiverIsCurrentAndTheArgumentIsTapped() {
        val targets = DeckSegment.entries.filter { it != DeckSegment.PUBLIC }
        targets.forEach { current ->
            targets.forEach { tapped ->
                val viaCallSite = current.tap(tapped, decksMine = false)
                assertEquals(
                    "from $current tapping $tapped must land on $tapped, not ${viaCallSite.segment}",
                    tapped,
                    viaCallSite.segment,
                )
            }
        }
    }

    @Test
    fun tappingTheSegmentYouAreOnIsAStableNoOp() {
        DeckSegment.entries.forEach { s ->
            val after = s.tap(s, decksMine = false)
            assertEquals(s, after.segment)
        }
    }
}
