package gg.opgg.android

import gg.opgg.android.data.AppJson
import gg.opgg.android.data.Card
import gg.opgg.android.data.Owned
import gg.opgg.android.data.OwnedCard
import gg.opgg.android.data.UiState
import gg.opgg.android.data.collapseOwnedCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wishlist plumbing. The wishlist is a `wish` count on the same collection
 * rows the owned counts come from, so almost every rule tested here is the
 * wishlist twin of an owned-count rule: rows collapse across printings, cards
 * held only as wishes must still collapse, and the owned/wishlist views must
 * not leak rows into each other.
 */
class WishlistTest {

    // ---- Owned counts carry wishes ---------------------------------------

    @Test
    fun ownedSumsWishesSeparatelyFromCopies() {
        val o = Owned(normal = 2, foil = 1, wish = 3, wishFoil = 1)
        assertEquals(3, o.total)
        assertEquals(4, o.wishTotal)
    }

    @Test
    fun ownedCardJsonParsesWishFields() {
        val row = AppJson.decodeFromString(
            OwnedCard.serializer(),
            """{"card":"P-078_p6","standard":"0","foil":"0","wish":"3","wish_foil":"1"}""",
        )
        assertEquals(3, row.wish?.toIntOrNull())
        assertEquals(1, row.wish_foil?.toIntOrNull())
        // Absent - the common case today, before any wishlist exists.
        val empty = AppJson.decodeFromString(
            OwnedCard.serializer(),
            """{"card":"EB01-015"}""",
        )
        assertNull(empty.wish)
        assertNull(empty.wish_foil)
    }

    // ---- collapse across printings ---------------------------------------

    @Test
    fun collapseSumsWishCountsAcrossPrintings() {
        val collapsed = collapseOwnedCounts(
            owned = mapOf(
                "EB01-015" to Owned(wish = 2),
                "EB01-015_r1" to Owned(wish = 1, wishFoil = 1),
                "EB02-001" to Owned(normal = 3),
            ),
            printingToNormal = mapOf("EB01-015_r1" to "EB01-015"),
        )
        val eb01 = collapsed["EB01-015"]!!
        assertEquals(3, eb01.wish)
        assertEquals(1, eb01.wishFoil)
        assertEquals(0, eb01.total)
        assertEquals(3, collapsed["EB02-001"]!!.normal)
    }

    @Test
    fun collapseKeepsARowWishedWithoutOwning() {
        // A wish-only row used to be dropped by the collapse, because the old
        // filter looked at owned counts only - the wishlist view would never
        // have seen a card the user wished for but does not own.
        val collapsed = collapseOwnedCounts(
            owned = mapOf(
                "OP02-013" to Owned(wish = 1, wishFoil = 2),
            ),
            printingToNormal = emptyMap(),
        )
        assertEquals(1, collapsed["OP02-013"]!!.wish)
        assertEquals(2, collapsed["OP02-013"]!!.wishFoil)
    }

    @Test
    fun collapseStillDropsEmptyRows() {
        val collapsed = collapseOwnedCounts(
            owned = mapOf("EB01-015" to Owned(), "EB02-001" to Owned(normal = 1)),
            printingToNormal = emptyMap(),
        )
        assertFalse(collapsed.containsKey("EB01-015"))
        assertTrue(collapsed.containsKey("EB02-001"))
    }

    // ---- the two views ----------------------------------------------------

    private val base = Card(id = "EB01-015", idNormal = "EB01-015", name = "Shanks")
    private val reprint = Card(id = "EB01-015_r1", idNormal = "EB01-015", name = "Shanks")
    private val other = Card(id = "EB02-001", idNormal = "EB02-001", name = "Luffy")

    private fun state(
        owned: Map<String, Owned>,
    ) = UiState(
        owned = owned,
        ownedByNormal = collapseOwnedCounts(
            owned,
            mapOf("EB01-015_r1" to "EB01-015"),
        ),
        printingToNormal = mapOf("EB01-015_r1" to "EB01-015"),
        printingsByNormal = mapOf(
            "EB01-015" to listOf(base, reprint),
            "EB02-001" to listOf(other),
        ),
    )

    @Test
    fun ownedViewHidesWishOnlyCards() {
        val s = state(
            owned = mapOf(
                "EB01-015" to Owned(wish = 2),
                "EB02-001" to Owned(normal = 1),
            ),
        )
        assertFalse("a wish-only card must not appear in the Owned view", s.ownedGroups.any { it.normal == "EB01-015" })
        assertEquals(1, s.ownedGroups.single().total)
    }

    @Test
    fun wishViewShowsOnlyWishedCards() {
        val s = state(
            owned = mapOf(
                "EB01-015" to Owned(wish = 2),
                "EB02-001" to Owned(normal = 4),
            ),
        )
        val wished = s.wishGroups.single()
        assertEquals("EB01-015", wished.normal)
        assertEquals(2, wished.wishTotal)
        // An owned card with no wish is invisible here, whatever it adds to the
        // Owned view.
        assertEquals(1, s.wishGroups.size)
    }

    @Test
    fun wishGroupsCollapsePrintingsAndPreferTheWishedArt() {
        // Wishing a reprint must display that reprint, not the base art - the
        // same representative rule the Owned view applies to holdings.
        val s = state(
            owned = mapOf(
                "EB01-015" to Owned(normal = 1),
                "EB01-015_r1" to Owned(wish = 2),
            ),
        )
        val wished = s.wishGroups.single()
        assertEquals("EB01-015_r1", wished.representative.id)
        assertEquals(2, wished.wishTotal)
        // Still owned as well, so the Owned view must keep listing it too.
        assertTrue(s.ownedGroups.any { it.normal == "EB01-015" })
    }

    @Test
    fun ownedAndWishedBothSurviveOnTheSameCard() {
        val s = state(
            owned = mapOf(
                "EB01-015" to Owned(normal = 2, wish = 1),
                "EB02-001" to Owned(wish = 3),
            ),
        )
        // EB01-015 is owned, so both views list it; EB02-001 is wish-only, so
        // only the wishlist view does.
        assertEquals(1, s.ownedGroups.size)
        assertEquals(1, s.wishGroups.size)
        // The own row keeps the wish target even though it is satisfied, so the
        // data round-trips; the view is what hides it.
        assertEquals(1, s.ownedGroups.first { it.normal == "EB01-015" }.wishTotal)
        // The counter is the shortfall across the list: 3 wanted, 0 owned.
        assertEquals(3, s.wishCount)
    }

    @Test
    fun aSatisfiedWishLeavesTheList() {
        val s = state(
            owned = mapOf("EB01-015" to Owned(normal = 2, wish = 2)),
        )
        assertTrue("owning as many as wished means nothing is needed", s.wishGroups.isEmpty())
        assertEquals(0, s.wishCount)
        // The card is still owned, so the collection view keeps it.
        assertEquals(1, s.ownedGroups.size)
    }

    @Test
    fun owningMoreThanWishedAlsoLeavesTheList() {
        val s = state(
            owned = mapOf("EB01-015" to Owned(normal = 4, wish = 1)),
        )
        assertTrue(s.wishGroups.isEmpty())
        assertEquals(0, s.wishCount)
    }

    @Test
    fun aPartialWishListsOnlyTheShortfall() {
        val s = state(
            owned = mapOf("EB01-015" to Owned(normal = 2, wish = 4)),
        )
        val group = s.wishGroups.single()
        assertEquals(4, group.wishTotal)
        assertEquals(2, group.need)
        assertEquals(2, s.wishCount)
    }
}
