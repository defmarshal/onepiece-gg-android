package gg.opgg.android

import gg.opgg.android.data.Card
import gg.opgg.android.data.SortField
import gg.opgg.android.data.sortCards
import gg.opgg.android.data.SortSpec
import gg.opgg.android.data.sortCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure sort tests. No network, no Android. */
class SortTest {

    private val store = ::sortCards

    private fun c(
        id: String,
        name: String? = null,
        set: String? = null,
        cost: String? = null,
        power: String? = null,
        price: String? = null,
        delta7d: String? = null,
        date: Long? = null,
    ) = Card(
        id = id, idNormal = id, name = name, set = set, Cost = cost, Power = power,
        price = price, delta7dPrice = delta7d, releaseDate = date,
    )

    private fun sorted(cards: List<Card>, field: SortField, desc: Boolean = false) =
        store(cards, SortSpec(field, desc)).mapNotNull { it.id }

    @Test
    fun noneSortsByReleaseDateNewestFirst() {
        val cards = listOf(
            c("OP01-001", date = 1669939200),
            c("EB03-009", date = 1769904000),
            c("OP16-077", date = 1780250400),
        )
        assertEquals(listOf("OP16-077", "EB03-009", "OP01-001"), sorted(cards, SortField.NONE))
    }

    @Test
    fun noneOrdersCardsWithinASetByNumber() {
        val cards = listOf(
            c("OP16-010", date = 1780250400),
            c("OP16-002", date = 1780250400),
            c("OP16-001", date = 1780250400),
        )
        assertEquals(listOf("OP16-001", "OP16-002", "OP16-010"), sorted(cards, SortField.NONE))
    }

    @Test
    fun nonePutsPromosAfterDatedSetsAndUnknownSetsLast() {
        val cards = listOf(
            c("OPDD-001", date = 0L),          // promo: date known but zero
            c("P-058", date = null),           // set unknown to the sets list
            c("OP02-003", date = 1678406400),
        )
        assertEquals(listOf("OP02-003", "OPDD-001", "P-058"), sorted(cards, SortField.NONE))
    }

    @Test
    fun nameSortsAlphabetically() {
        val cards = listOf(c("1", name = "Zoro"), c("2", name = "ace"), c("3", name = "Luffy"))
        assertEquals(listOf("2", "3", "1"), sorted(cards, SortField.NAME))
    }

    @Test
    fun numericSortIsAscendingByDefault() {
        val cards = listOf(c("A", cost = "10"), c("B", cost = "2"), c("C", cost = "7"))
        assertEquals(listOf("B", "C", "A"), sorted(cards, SortField.COST))
    }

    @Test
    fun descendingReversesTheOrder() {
        val cards = listOf(c("A", cost = "10"), c("B", cost = "2"), c("C", cost = "7"))
        assertEquals(listOf("A", "C", "B"), sorted(cards, SortField.COST, desc = true))
    }

    /** The rule that matters most: a null must never outrank a real value. */
    @Test
    fun nullsSortLastInBothDirections() {
        val cards = listOf(c("none1", cost = null), c("low", cost = "1"), c("none2", cost = null), c("high", cost = "9"))

        val asc = sorted(cards, SortField.COST)
        assertEquals("ascending put a null first", "low", asc.first())
        assertEquals(listOf("low", "high", "none1", "none2"), asc)

        val desc = sorted(cards, SortField.COST, desc = true)
        assertEquals("descending put a null first", "high", desc.first())
        // The two nulls tie, and the tie-break follows the direction too, so a
        // fully descending list is fully descending.
        assertEquals(listOf("high", "low", "none2", "none1"), desc)
    }

    @Test
    fun tiesBreakOnIdSoOrderIsStable() {
        val cards = listOf(
            c("OP02-005", set = "OP02", cost = "3"),
            c("OP01-009", set = "OP01", cost = "3"),
            c("OP01-001", set = "OP01", cost = "3"),
        )
        assertEquals(listOf("OP01-001", "OP01-009", "OP02-005"), sorted(cards, SortField.COST))
    }

    /** No set code at all: the tie falls through to the id's number. */
    @Test
    fun tiesWithoutSetCodesBreakOnNumber() {
        val cards = listOf(c("OP02-005", cost = "3"), c("OP01-009", cost = "3"), c("OP01-001", cost = "3"))
        assertEquals(listOf("OP01-001", "OP02-005", "OP01-009"), sorted(cards, SortField.COST))
    }

    @Test
    fun setSortWithoutDatesFallsBackToSetThenId() {
        val cards = listOf(c("x", set = "OP02"), c("y", set = "OP01"), c("z", set = "ST22"))
        assertEquals(listOf("y", "x", "z"), sorted(cards, SortField.SET))
    }

    @Test
    fun setSortIsChronologicalByReleaseDate() {
        val cards = listOf(
            c("OP18-001", set = "OP18", date = 1795132800),   // newest
            c("OP01-001", set = "OP01", date = 1669939200),  // oldest
            c("EB02-004", set = "EB02", date = 1746748800),  // in between
        )
        assertEquals(listOf("OP01-001", "EB02-004", "OP18-001"), sorted(cards, SortField.SET))
    }

    /**
     * The report behind this rule: the DON!! cards live in the date-0 promo
     * sets (OP-PR, EB-03, ST-01 PRE, ...), whose codes sort before the digits,
     * so a string sort put DON on top of "Set" - now they are undated and sit
     * at the bottom, in both directions.
     */
    @Test
    fun setSortPutsDateZeroSetsLastInBothDirections() {
        val don = c("DON-549342", set = "OP02", date = 0L)
        val promo = c("P-058", set = "P", date = 0L)
        val unknown = c("X-1", set = null, date = null)
        val dated = c("OP01-001", set = "OP01", date = 1669939200)

        val asc = sorted(listOf(don, promo, unknown, dated), SortField.SET)
        assertEquals("DON led the ascending list", "OP01-001", asc.first())
        // Within the undated tail the set code decides, and a missing set
        // counts as "" - first there, like every other empty-key text sort.
        assertEquals(listOf("OP01-001", "X-1", "DON-549342", "P-058"), asc)

        val desc = sorted(listOf(don, promo, unknown, dated), SortField.SET, desc = true)
        assertEquals("DON led the descending list", "OP01-001", desc.first())
        assertEquals(listOf("OP01-001", "P-058", "DON-549342", "X-1"), desc)
    }

    @Test
    fun setSortTiesOnDateBreakBySetCode() {
        // The launch wave shares one date; the set code keeps OP01 before ST01.
        val cards = listOf(
            c("ST01-001", set = "ST01", date = 1669939200),
            c("OP01-001", set = "OP01", date = 1669939200),
        )
        assertEquals(listOf("OP01-001", "ST01-001"), sorted(cards, SortField.SET))
    }

    /**
     * DON!! cards ship inside OP01 (set code OP01, id "DON-456059") and used
     * to lead the OP01 group on the id string. The number tie-break puts them
     * after the numbered cards of their own set.
     */
    @Test
    fun setSortPutsDonCardsAtTheEndOfTheirSetGroup() {
        val cards = listOf(
            c("DON-456059", set = "OP01", date = 1669939200),
            c("OP01-009", set = "OP01", date = 1669939200),
            c("OP01-002", set = "OP01", date = 1669939200),
        )
        assertEquals(listOf("OP01-002", "OP01-009", "DON-456059"), sorted(cards, SortField.SET))
    }

    @Test
    fun removedNumberKeyFallsBackToDefaultOrder() {
        // "Card number" was removed from the sheet; an old persisted pref
        // must load as Default order, not crash or sort oddly.
        assertEquals(SortField.NONE, SortField.fromKey("number"))
    }

    @Test
    fun priceSortHandlesZeroAndMissing() {
        val cards = listOf(c("none", price = null), c("free", price = "0"), c("paid", price = "3.50"))
        val asc = sorted(cards, SortField.PRICE)
        assertEquals("free", asc.first())
        assertEquals("none", asc.last())
    }

    @Test
    fun sevenDayGainSortUsesTheDelta() {
        val cards = listOf(c("up", delta7d = "0.50"), c("down", delta7d = "-0.20"), c("flat", delta7d = "0"))
        assertEquals(listOf("down", "flat", "up"), sorted(cards, SortField.GAIN_7D_PCT))
    }

    @Test
    fun unknownSortKeyFallsBackToDefault() {
        assertEquals(SortField.NONE, SortField.fromKey("nonsense"))
        assertEquals(SortField.PRICE, SortField.fromKey("price"))
    }

    @Test
    fun tiesFollowTheSortDirection() {
        // Regression, from a real report: "Set descending" produced
        // ST36-001, ST36-002, ... because the tie-break was hard-coded
        // ascending, and that reads as unsorted.
        val a = c("ST36-001", set = "ST36")
        val b = c("ST36-002", set = "ST36")
        val d = c("ST35-001", set = "ST35")

        val desc = sortCards(listOf(a, b, d), SortSpec(SortField.SET, descending = true))
            .map { it.id }
        assertEquals(listOf("ST36-002", "ST36-001", "ST35-001"), desc)

        val asc = sortCards(listOf(a, b, d), SortSpec(SortField.SET, descending = false))
            .map { it.id }
        assertEquals(listOf("ST35-001", "ST36-001", "ST36-002"), asc)
    }

    @Test
    fun everySortFieldHasALabelAndAKey() {
        assertTrue(SortField.entries.all { it.label.isNotBlank() && it.key.isNotBlank() })
    }
}
