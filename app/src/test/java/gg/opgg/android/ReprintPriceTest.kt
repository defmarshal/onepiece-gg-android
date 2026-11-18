package gg.opgg.android

import gg.opgg.android.ui.reprintPriceLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reprint strip used to print "-" whenever the standard price was missing,
 * even when the row carried a foil price - which was true for most parallels and
 * reprints. These cases pin the fallback and the "missing" rules.
 */
class ReprintPriceTest {

    @Test
    fun theStandardPriceWinsWhenPresent() {
        assertEquals("$0.11", reprintPriceLabel("0.11", "4.00"))
    }

    @Test
    fun aMissingStandardPriceFallsBackToFoil() {
        // Real shape: standard zero on a printing that only has a foil price.
        assertEquals("$0.33 foil", reprintPriceLabel("0", "0.33"))
        assertEquals("$0.33 foil", reprintPriceLabel(null, "0.33"))
        assertEquals("$0.33 foil", reprintPriceLabel("0.000000", "0.33"))
    }

    @Test
    fun zeroAndGarbageCountAsMissing() {
        assertEquals("-", reprintPriceLabel("0", "0"))
        assertEquals("-", reprintPriceLabel("0.000000", "0.000000"))
        assertEquals("-", reprintPriceLabel(null, null))
        assertEquals("-", reprintPriceLabel("n/a", "n/a"))
    }

    @Test
    fun pricesAreShownToTwoDecimals() {
        assertEquals("$1.40", reprintPriceLabel("1.400000", null))
        assertEquals("$0.10", reprintPriceLabel("0.1", null))
    }
}
