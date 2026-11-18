package gg.opgg.android

import gg.opgg.android.data.Card
import gg.opgg.android.data.CardText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pack names.
 *
 * `CardSets` is the only field that says which product a printing's artwork
 * shipped in. The `set` code cannot: it is the card's *series* and is identical
 * for every printing of that card - across the 1,172 cards with more than one
 * printing, `set` differs on none of them and `CardSets` differs on 783.
 */
class CardPackNameTest {

    private fun card(sets: String?, set: String? = "OP03", id: String = "OP03-094") = Card(
        id = id,
        idNormal = id,
        CardSets = sets,
        set = set,
    )

    @Test
    fun packNameIsTheCardSetsValue() {
        assertEquals(
            "ONE PIECE FILM edition [ST-05]",
            card("ONE PIECE FILM edition [ST-05]").packName,
        )
    }

    @Test
    fun htmlEntitiesAreDecoded() {
        // The pack names carry the same entities as the rules text, and the
        // raw value reads badly: "BOOSTER PACK -The World&#039;s Strongest
        // Warriors- [OP-17]".
        assertEquals(
            "BOOSTER PACK -The World's Strongest Warriors- [OP-17]",
            card("BOOSTER PACK -The World&#039;s Strongest Warriors- [OP-17]").packName,
        )
        assertEquals(
            "-Zoro & Sanji- [ST-12]",
            card("-Zoro &amp; Sanji- [ST-12]").packName,
        )
        assertEquals(
            "STARTER DECK -YELLOW Eustass\"Captain\"Kid- [ST-36]",
            card("STARTER DECK -YELLOW Eustass&quot;Captain&quot;Kid- [ST-36]").packName,
        )
    }

    @Test
    fun strayWhitespaceIsTidied() {
        assertEquals(
            "Offline Regional Participation Pack 2025 Vol.1",
            card("  Offline Regional Participation Pack 2025   Vol.1  ").packName,
        )
    }

    @Test
    fun aMissingPackIsNullRatherThanEmpty() {
        assertNull(card(null).packName)
        assertNull(card("   ").packName)
    }

    @Test
    fun decodeEntitiesLeavesOrdinaryTextAlone() {
        val plain = "Nothing to decode here"
        assertEquals(plain, CardText.decodeEntities(plain))
        // An unknown entity is left exactly as it was, not swallowed.
        assertEquals("a &bogus; b", CardText.decodeEntities("a &bogus; b"))
    }

    @Test
    fun theSetCodeIsNotThePack() {
        // The same card number in two packs, same series code. This is the
        // distinction the app was getting wrong.
        val a = card("Offline Regional Participation Pack 2025 Vol.1", set = "P")
        val b = card("Offline Regional Champion Card Set 25-26 Season 1", set = "P")
        assertEquals(a.set, b.set)
        assertEquals(false, a.packName == b.packName)
    }
}
