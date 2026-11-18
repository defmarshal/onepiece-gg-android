package gg.opgg.android

import gg.opgg.android.data.ApiBinder
import gg.opgg.android.data.AppJson
import gg.opgg.android.data.BinderContentsResponse
import gg.opgg.android.data.CreateBinderResponse
import gg.opgg.android.data.MoveCardsResponse
import gg.opgg.android.data.Owned
import gg.opgg.android.data.OwnedCard
import gg.opgg.android.data.binderDescendantIds
import gg.opgg.android.data.binderShareUrl
import gg.opgg.android.data.binderTitle
import gg.opgg.android.data.mergeOwnedRows
import gg.opgg.android.data.moveDestinations
import gg.opgg.android.data.orderWithDepth
import gg.opgg.android.data.ownedValue
import gg.opgg.android.data.siblingSwapOrder
import gg.opgg.android.data.swapOrder
import gg.opgg.android.data.withoutSystemBinders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Binder plumbing. The interesting parts are the title (a binder can vanish or
 * be renamed from another device) and the contents shape: binders answer with
 * collection-shaped rows, not the thin `{card,type,count}` rows the website's
 * own client uses, so parsing has to follow the real payload.
 */
class BinderTest {

    private val binders = listOf(
        ApiBinder(id = 4259, name = "Wishlist", count = 0),
        ApiBinder(id = 4355, name = "Trade Binder", count = 12),
    )

    @Test
    fun binderZeroIsTheCollection() {
        assertEquals("Collection", binderTitle(binders, 0))
    }

    @Test
    fun aSelectedBinderUsesItsName() {
        assertEquals("Trade Binder", binderTitle(binders, 4355))
    }

    @Test
    fun aVanishedBinderFallsBackInsteadOfBlankingTheTitle() {
        assertEquals("Binder", binderTitle(binders, 999))
    }

    @Test
    fun theAggregateStoreHasItsOwnTitle() {
        assertEquals("All binders", binderTitle(binders, -1))
    }

    // ---- value ----------------------------------------------------------

    @Test
    fun valuePricesStandardsAndFoilsSeparately() {
        val v = ownedValue(Owned(normal = 2, foil = 1), standardPrice = "2.50", foilPrice = "4.00")
        assertEquals(9.0, v, 0.001)
    }

    @Test
    fun foilPriceFallsBackToTheStandardPrice() {
        // Zero and missing foil prices are common in the feed; without the
        // fallback the foil copies would silently be worth nothing.
        val zero = ownedValue(Owned(foil = 2), standardPrice = "3.00", foilPrice = "0")
        val missing = ownedValue(Owned(foil = 2), standardPrice = "3.00", foilPrice = null)
        assertEquals(6.0, zero, 0.001)
        assertEquals(6.0, missing, 0.001)
    }

    @Test
    fun missingOrUnparseablePricesCountAsZero() {
        assertEquals(0.0, ownedValue(Owned(normal = 3, foil = 1), null, null), 0.001)
        assertEquals(0.0, ownedValue(Owned(normal = 3), "n/a", "n/a"), 0.001)
    }

    // ---- aggregate merge ------------------------------------------------

    @Test
    fun theAggregateSumsTheSamePrintingFromCollectionAndBinders() {
        val merged = mergeOwnedRows(
            listOf(
                OwnedCard(card = "ST24-003", standard = "2", foil = "1"),
                // The same printing in a binder: the union must add, not replace.
                OwnedCard(card = "ST24-003", standard = "3", foil = "0"),
                OwnedCard(card = "OP14-010", standard = "1"),
            ),
        )
        assertEquals(5, merged["ST24-003"]!!.normal)
        assertEquals(1, merged["ST24-003"]!!.foil)
        assertEquals(1, merged["OP14-010"]!!.normal)
    }

    @Test
    fun rowsWithoutACardIdAreIgnored() {
        val merged = mergeOwnedRows(
            listOf(OwnedCard(card = null, standard = "3"), OwnedCard(card = "ST24-003", standard = "1")),
        )
        assertEquals(setOf("ST24-003"), merged.keys)
    }

    @Test
    fun aNamelessBinderAlsoFallsBack() {
        assertEquals("Binder", binderTitle(listOf(ApiBinder(id = 1, name = "  ")), 1))
    }

    // ---- payload shapes ------------------------------------------------

    @Test
    fun binderContentsParseAsCollectionRows() {
        // Real getbindercards row, captured from the live API.
        val json = """
            {"error":false,"binder_id":4259,"collection":[
              {"card":"ST24-003","foil":"0","standard":"3","total":"3",
               "extra_data":null,"trade":"0","wish":"0","trade_foil":"0","wish_foil":"0"}
            ]}
        """.trimIndent()
        val parsed = AppJson.decodeFromString(BinderContentsResponse.serializer(), json)
        assertEquals(false, parsed.error)
        val row = parsed.collection!!.single()
        assertEquals("ST24-003", row.card)
        assertEquals(3, row.standard?.toIntOrNull())
        assertEquals(0, row.foil?.toIntOrNull())
    }

    @Test
    fun anEmptyBinderIsAnEmptyListNotAnError() {
        val parsed = AppJson.decodeFromString(
            BinderContentsResponse.serializer(),
            """{"error":false,"binder_id":4259,"collection":[]}""",
        )
        assertTrue(parsed.collection!!.isEmpty())
        assertNull(parsed.error_text)
    }

    @Test
    fun createResponseCarriesTheNewIdAndCanFailCleanly() {
        val ok = AppJson.decodeFromString(
            CreateBinderResponse.serializer(),
            """{"error":false,"id":4354,"name":"OPGG Test","slug":"opgg-test","sort":2}""",
        )
        assertEquals(4354L, ok.id)
        assertEquals("opgg-test", ok.slug)

        val bad = AppJson.decodeFromString(
            CreateBinderResponse.serializer(),
            """{"error":true,"error_text":"Binder name already exists"}""",
        )
        assertTrue(bad.error)
        assertNull(bad.id)
    }

    // ---- move destinations ---------------------------------------------

    private val moveBinders = listOf(
        ApiBinder(id = 4259, name = "Wishlist"),
        ApiBinder(id = 4355, name = "Trade Binder"),
        ApiBinder(id = 4360, name = "Yamato"),
    )

    @Test
    fun movingFromTheCollectionOffersEveryBinderExceptHiddenOnes() {
        val dest = moveDestinations(moveBinders, currentStore = 0)
        assertEquals(listOf("Trade Binder", "Yamato"), dest.map { it.second })
        // No "Collection" entry: already there.
        assertTrue(dest.none { it.first == 0L })
    }

    @Test
    fun systemBindersAreNotUserManaged() {
        // The site-created "Wishlist" binder is a fixed destination under
        // Collection, so the manage list must not offer it.
        assertEquals(
            listOf("Trade Binder", "Yamato"),
            moveBinders.withoutSystemBinders().map { it.name },
        )
    }

    @Test
    fun movingFromABinderOffersTheCollectionFirst() {
        val dest = moveDestinations(moveBinders, currentStore = 4355)
        assertEquals("Collection", dest.first().second)
        assertEquals(0L, dest.first().first)
        // The current binder is not a destination; the hidden Wishlist is not
        // offered either.
        assertEquals(listOf("Collection", "Yamato"), dest.map { it.second })
    }

    @Test
    fun moveResponseReportsWholeRowCopies() {
        // Real response for a row holding three copies.
        val parsed = AppJson.decodeFromString(
            MoveCardsResponse.serializer(),
            """{"error":false,"message":"OK","moved":1,"copies":3,"total":1}""",
        )
        assertEquals(1, parsed.moved)
        assertEquals(3, parsed.copies)
    }

    // ---- reorder --------------------------------------------------------

    @Test
    fun movingABinderUpSwapsItWithThePreviousOne() {
        assertEquals(listOf(2L, 1L, 3L), swapOrder(listOf(1L, 2L, 3L), id = 2, up = true))
    }

    @Test
    fun movingABinderDownSwapsItWithTheNextOne() {
        assertEquals(listOf(1L, 3L, 2L), swapOrder(listOf(1L, 2L, 3L), id = 2, up = false))
    }

    @Test
    fun movingPastEitherEdgeIsANoOp() {
        assertEquals(listOf(1L, 2L, 3L), swapOrder(listOf(1L, 2L, 3L), id = 1, up = true))
        assertEquals(listOf(1L, 2L, 3L), swapOrder(listOf(1L, 2L, 3L), id = 3, up = false))
    }

    @Test
    fun anUnknownBinderLeavesTheOrderAlone() {
        assertEquals(listOf(1L, 2L, 3L), swapOrder(listOf(1L, 2L, 3L), id = 99, up = true))
    }

    // ---- folders --------------------------------------------------------

    @Test
    fun foldersRenderParentsBeforeTheirChildren() {
        val tree = listOf(
            ApiBinder(id = 4, name = "Root", sort = 0),
            ApiBinder(id = 1, name = "Folder", sort = 1),
            ApiBinder(id = 2, name = "Child", sort = 0, parent_id = 1),
            ApiBinder(id = 3, name = "Child 2", sort = 1, parent_id = 1),
        )
        assertEquals(
            listOf("Root" to 0, "Folder" to 0, "Child" to 1, "Child 2" to 1),
            orderWithDepth(tree).map { it.first.name to it.second },
        )
    }

    @Test
    fun aParentCycleEmitsEveryBinderExactlyOnce() {
        val tree = listOf(
            ApiBinder(id = 1, name = "A", parent_id = 2),
            ApiBinder(id = 2, name = "B", parent_id = 1),
        )
        val order = orderWithDepth(tree)
        assertEquals(2, order.size)
        assertEquals(setOf(1L, 2L), order.map { it.first.id }.toSet())
    }

    @Test
    fun descendantsAreFoundAndCyclesDoNotHang() {
        val tree = listOf(
            ApiBinder(id = 1, name = "A"),
            ApiBinder(id = 2, name = "B", parent_id = 1),
            ApiBinder(id = 3, name = "C", parent_id = 2),
        )
        assertEquals(setOf(2L, 3L), binderDescendantIds(tree, 1))
    }

    // ---- sharing --------------------------------------------------------

    @Test
    fun aShareLinkUsesTheNicknameAndSlug() {
        val binder = ApiBinder(id = 9, slug = "trade-box")
        assertEquals(
            "https://onepiece.gg/u/Some%20One/binder/trade-box",
            binderShareUrl("Some One", binder),
        )
    }

    @Test
    fun aBinderWithoutASlugFallsBackToTheCollectionUrl() {
        assertEquals(
            "https://onepiece.gg/u/nick/collection?binder=9",
            binderShareUrl("nick", ApiBinder(id = 9)),
        )
        assertNull("no nickname means no link", binderShareUrl(null, ApiBinder(id = 9)))
        assertNull(binderShareUrl("   ", ApiBinder(id = 9)))
    }

    // ---- sibling reordering ---------------------------------------------

    @Test
    fun reorderingSwapsNeighboursAtTheSameLevel() {
        val roots = listOf(
            ApiBinder(id = 1, sort = 0),
            ApiBinder(id = 2, sort = 1),
        )
        assertEquals(listOf(2L, 1L), siblingSwapOrder(roots, id = 2, up = true))
        assertEquals(listOf(2L, 1L), siblingSwapOrder(roots, id = 1, up = false))
        assertEquals(listOf(1L, 2L), siblingSwapOrder(roots, id = 1, up = true))
    }

    @Test
    fun aNestedBinderMovesAmongItsSiblingsOnly() {
        // Flat order is server order: both roots first, then the children.
        val binders = listOf(
            ApiBinder(id = 1, name = "Folder", sort = 0),
            ApiBinder(id = 2, name = "Other", sort = 1),
            ApiBinder(id = 3, name = "A", sort = 0, parent_id = 1),
            ApiBinder(id = 4, name = "B", sort = 1, parent_id = 1),
        )
        // B moves up past A; the parents keep their positions.
        assertEquals(listOf(1L, 2L, 4L, 3L), siblingSwapOrder(binders, id = 4, up = true))
        // A is already first among its siblings: nothing happens, and in
        // particular it does not jump above its parent.
        assertEquals(listOf(1L, 2L, 3L, 4L), siblingSwapOrder(binders, id = 3, up = true))
        // B is already last among its siblings.
        assertEquals(listOf(1L, 2L, 3L, 4L), siblingSwapOrder(binders, id = 4, up = false))
    }

    @Test
    fun anUnknownBinderLeavesTheOrderAloneForSiblingsToo() {
        val binders = listOf(ApiBinder(id = 1, sort = 0), ApiBinder(id = 2, sort = 1))
        assertEquals(listOf(1L, 2L), siblingSwapOrder(binders, id = 99, up = true))
    }
}
