package gg.opgg.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.prefsStore by preferencesDataStore("opgg_prefs")

/**
 * Grid sorting. Mirrors the option set the site exposes in its own
 * `cards.sortConfig`, so results are ordered the same way as onepiece.gg.
 */
enum class SortField(val key: String, val label: String) {
    NONE("none", "Default order"),
    NAME("name", "Name"),
    SET("set", "Set"),
    COST("cost", "Cost"),
    POWER("power", "Power"),
    COUNTER("counter", "Counter"),
    PRICE("price", "Price"),
    FOIL_PRICE("pricefoil", "Foil price"),
    GAIN_1D_PCT("1dpercentgains", "1d % gain"),
    GAIN_7D_PCT("7dpercentgains", "7d % gain"),
    GAIN_1D_USD("1ddollargains", "1d $ gain"),
    GAIN_7D_USD("7ddollargains", "7d $ gain");

    companion object {
        // Unknown keys fall back to Default order - which also covers the
        // removed "number" (Card number) field in an old persisted pref.
        fun fromKey(k: String): SortField = entries.firstOrNull { it.key == k } ?: NONE
    }
}

data class SortSpec(val field: SortField = SortField.NONE, val descending: Boolean = false)

/** User preferences that must survive a restart. */
class PrefsStore(private val context: Context) {

    private val kSort = stringPreferencesKey("sort_field")
    private val kSortDesc = booleanPreferencesKey("sort_desc")

    suspend fun loadSort(): SortSpec {
        val p = context.prefsStore.data.first()
        return SortSpec(SortField.fromKey(p[kSort] ?: "none"), p[kSortDesc] ?: false)
    }

    suspend fun saveSort(spec: SortSpec) {
        context.prefsStore.edit {
            it[kSort] = spec.field.key
            it[kSortDesc] = spec.descending
        }
    }
}

/**
 * Sorts a card list. Top-level and Android-free so it is directly testable.
 *
 * Nulls always sort last, whatever the direction: otherwise every card with a
 * null Power would top a "power descending" list, and every unpriced card
 * would lead a "price descending" one. Ties break on set code then id so the
 * order is stable across recompositions.
 */
internal fun sortCards(cards: List<Card>, spec: SortSpec): List<Card> {
    fun idOf(c: Card) = c.idNormal ?: c.id.orEmpty()

    /**
     * Release date of the row's set. A date of 0 counts as unknown: the API
     * stamps 0 on every promo/variant set (P, OP-PR, OPDD, the hyphenated
     * duplicates), which is where the DON!! cards live - they must not lead
     * the list just because "0" is the smallest number.
     */
    fun dateOf(c: Card) = c.releaseDate?.takeIf { it != 0L }

    /**
     * Shared tail: group by set code, then by the id's trailing number, then
     * id, all following the direction. With no dates in play this reproduces
     * the old set-then-id order - and it fixes the DON!! cards leading their
     * set group: they ship inside OP01 but carry ids like "DON-456059", which
     * sorted before "OP01-001" on the id string alone.
     */
    fun setThenId(a: Card, b: Card, dir: Int): Int {
        val s = (a.set ?: "").compareTo(b.set ?: "")
        if (s != 0) return s * dir
        val ia = idOf(a)
        val ib = idOf(b)
        val an = numericSuffix(ia)
        val bn = numericSuffix(ib)
        if (an == null && bn == null) return ia.compareTo(ib) * dir
        if (an == null) return 1
        if (bn == null) return -1
        val n = an.compareTo(bn) * dir
        if (n != 0) return n
        return ia.compareTo(ib) * dir
    }

    /** One stage of the comparator: date, undated last, direction applied. */
    fun byDate(a: Card, b: Card, dir: Int): Int? {
        val ad = dateOf(a)
        val bd = dateOf(b)
        if (ad == null && bd == null) return null
        if (ad == null) return 1
        if (bd == null) return -1
        val p = ad.compareTo(bd) * dir
        return if (p != 0) p else null
    }

    if (spec.field == SortField.NONE) {
        // "Default order" is the card's release date: newest set first, then
        // card number ascending within a set. The old default was whatever row
        // order the API returned, which had no pattern and read as shuffled.
        // Undated sets land after every dated set. Not multiplied by `dir` -
        // the sheet hides the Descending switch for Default order. The -1
        // makes the date comparison newest-first.
        return cards.sortedWith { a, b ->
            byDate(a, b, -1) ?: idOf(a).compareTo(idOf(b))
        }
    }

    if (spec.field == SortField.SET) {
        // Chronological, not alphabetical: set codes sort "-03" before "01"
        // and "P" before "ST", so the string order scattered the promo sets -
        // with their DON!! cards - across the top of the list. Release date
        // first (undated sets last in both directions), then set code, then id.
        val dir = if (spec.descending) -1 else 1
        return cards.sortedWith { a, b -> byDate(a, b, dir) ?: setThenId(a, b, dir) }
    }

    val dir = if (spec.descending) -1 else 1

    val numeric: ((Card) -> Double?)? = when (spec.field) {
        SortField.COST -> { c -> c.Cost?.toDoubleOrNull() }
        SortField.POWER -> { c -> c.Power?.toDoubleOrNull() }
        SortField.COUNTER -> { c -> c.Counter?.toDoubleOrNull() }
        SortField.PRICE -> { c -> c.price?.toDoubleOrNull() }
        SortField.FOIL_PRICE -> { c -> c.foilPrice?.toDoubleOrNull() }
        SortField.GAIN_1D_PCT, SortField.GAIN_1D_USD -> { c -> c.deltaPrice?.toDoubleOrNull() }
        SortField.GAIN_7D_PCT, SortField.GAIN_7D_USD -> { c -> c.delta7dPrice?.toDoubleOrNull() }
        else -> null
    }

    val byText: ((Card) -> String)? = when (spec.field) {
        SortField.NAME -> { c -> c.name?.lowercase() ?: "" }
        else -> null
    }

    return cards.sortedWith { a, b ->
        // The tie-break follows the direction, so a descending list really is
        // descending. With a fixed ascending tie-break, "Set descending"
        // rendered ST36-001, ST36-002, ... which reads as unsorted.
        val tie = setThenId(a, b, dir)

        if (byText != null) {
            val p = byText(a).compareTo(byText(b))
            return@sortedWith if (p != 0) p * dir else tie
        }
        val av = numeric?.invoke(a)
        val bv = numeric?.invoke(b)
        // Nulls sort last whatever the direction. These returns are deliberately
        // NOT multiplied by `dir` - doing that puts every null at the top of a
        // descending list.
        if (av == null && bv == null) return@sortedWith tie
        if (av == null) return@sortedWith 1
        if (bv == null) return@sortedWith -1
        val p = av.compareTo(bv)
        if (p != 0) p * dir else tie
    }
}

/** Trailing digits of a card id, e.g. "OP06-081" -> 81.0 */
private fun numericSuffix(id: String?): Double? {
    if (id.isNullOrBlank()) return null
    val digits = id.takeLastWhile { it.isDigit() }
    return digits.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
}

