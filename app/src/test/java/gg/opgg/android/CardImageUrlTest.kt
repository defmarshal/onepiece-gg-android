package gg.opgg.android

import gg.opgg.android.data.CARD_IMG_BASE
import gg.opgg.android.data.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Card artwork URLs.
 *
 * The card database carries one row per *printing*, and printings of the same
 * card number have different artwork - P-058 "Where the Wind Blows" is one
 * illustration in the base printing and a completely different one in `_p1`.
 * The CDN serves one file per printing id.
 *
 * The bug this pins: the URL was built from `idNormal`, so every printing of a
 * card resolved to the base card's image. 1,172 of the 3,063 cards have more
 * than one printing, so 38% of the database was showing the wrong picture, and
 * the reprint strip's thumbnails were all identical.
 */
class CardImageUrlTest {

    private fun card(id: String?, normal: String?) = Card(id = id, idNormal = normal)

    @Test
    fun `base row uses its own id`() {
        // A base row has id == idNormal, so the two rules cannot disagree.
        assertEquals(
            "$CARD_IMG_BASE/P-058.webp",
            card("P-058", "P-058").imageUrl,
        )
    }

    @Test
    fun `a parallel printing gets its own artwork`() {
        assertEquals(
            "$CARD_IMG_BASE/P-058_p1.webp",
            card("P-058_p1", "P-058").imageUrl,
        )
    }

    @Test
    fun `a reprint printing gets its own artwork`() {
        assertEquals(
            "$CARD_IMG_BASE/OP09-098_r1.webp",
            card("OP09-098_r1", "OP09-098").imageUrl,
        )
    }

    @Test
    fun `printings of one card resolve to different artwork`() {
        val base = card("P-058", "P-058")
        val parallel = card("P-058_p1", "P-058")
        assertNotEquals(base.imageUrl, parallel.imageUrl)
    }

    @Test
    fun `falls back to the normal id when the row has no id`() {
        assertEquals(
            "$CARD_IMG_BASE/P-058.webp",
            card(id = null, normal = "P-058").imageUrl,
        )
    }

    @Test
    fun `falls back to the normal id when the id is blank`() {
        // A blank id would otherwise request ".../card/.webp".
        assertEquals(
            "$CARD_IMG_BASE/P-058.webp",
            card(id = "", normal = "P-058").imageUrl,
        )
    }

    @Test
    fun `key still groups printings under their card`() {
        // The image is per-printing; the key must not be, or ownership and the
        // rules engine would stop collapsing printings together.
        assertEquals("P-058", card("P-058_p1", "P-058").key)
        assertEquals("P-058", card("P-058", "P-058").key)
    }
}
