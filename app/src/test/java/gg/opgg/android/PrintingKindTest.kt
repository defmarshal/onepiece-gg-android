package gg.opgg.android

import gg.opgg.android.data.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Printing kinds.
 *
 * The API has no flag for this. There is no art column among the 34 in
 * `getcards` and no art-variant endpoint, so the kind has to be inferred from
 * the id suffix and the site's own name label.
 */
class PrintingKindTest {

    private fun card(id: String, normal: String?, name: String? = null) =
        Card(id = id, idNormal = normal, name = name)

    @Test
    fun theBasePrintingIsStandard() {
        assertEquals("Standard", card("P-058", "P-058").printingKind)
    }

    @Test
    fun aParallelSuffixIsAParallel() {
        assertEquals("Parallel", card("P-058_p1", "P-058").printingKind)
        assertEquals("Parallel", card("P-058_p10", "P-058").printingKind)
    }

    @Test
    fun aReprintSuffixIsAReprint() {
        assertEquals("Reprint", card("OP09-098_r1", "OP09-098").printingKind)
        assertEquals("Reprint", card("OP09-098_r3", "OP09-098").printingKind)
    }

    @Test
    fun theSitesOwnLabelWins() {
        // 91 rows are named "... (Parallel)", always in the _p family, so the
        // label and the suffix agree - but the label is the more precise answer.
        assertEquals("Parallel", card("OP06-081_p1", "OP06-081", "Absalom (Parallel)").printingKind)
    }

    @Test
    fun aBasePrintingCanItselfBeTheAlternateArt() {
        // These rows are all base rows: for those cards the base is the special
        // one and the ordinary version is a reprint.
        assertEquals(
            "Alternate Art",
            card("DON-498519", "DON-498519", "DON!! Card (Alternate Art)").printingKind,
        )
    }

    @Test
    fun aGoldLabelIsRecognised() {
        assertEquals(
            "Gold",
            card("DON-587957", "DON-587957", "DON!! Card (Ace) (Gold)").printingKind,
        )
    }

    @Test
    fun bracketsThatArePartOfTheNameAreNotKinds() {
        // Most trailing brackets are just part of the card's name. Treating
        // "Chadros.Higelyges (Brownbeard)" as a kind would be nonsense.
        assertEquals("Standard", card("OP10-010", "OP10-010", "Chadros.Higelyges (Brownbeard)").printingKind)
        assertEquals("Standard", card("DON-544805", "DON-544805", "DON!! Card (3D Text)").printingKind)
        assertEquals("Standard", card("DON-593801", "DON-593801", "DON!! Card (Ace)").printingKind)
        assertEquals("Standard", card("X-001", "X-001", "Some Card (Galdino)").printingKind)
    }

    @Test
    fun aRowWithNoIdIsStandard() {
        assertEquals("Standard", card("", null).printingKind)
    }

    @Test
    fun parallelPrintingsAreFoilOnly() {
        // A parallel is printed as foil only: offering a standard counter would
        // ask the user to record copies that cannot exist.
        assertTrue(card("P-058_p1", "P-058").foilOnly)
        assertTrue(card("OP06-081_p1", "OP06-081", "Absalom (Parallel)").foilOnly)
        assertFalse("the base printing has a standard version", card("P-058", "P-058").foilOnly)
        assertFalse("a reprint is the standard version", card("OP09-098_r1", "OP09-098").foilOnly)
    }
}
