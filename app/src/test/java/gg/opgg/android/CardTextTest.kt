package gg.opgg.android

import gg.opgg.android.data.CardText
import gg.opgg.android.data.CardTextPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Card text parsing. The fixtures are real strings taken from the live card
 * database, not invented ones, so a change in the site's markup convention
 * shows up here rather than on screen.
 */
class CardTextTest {

    private fun types(raw: String?) = CardText.parse(raw).filterIsInstance<CardTextPart.Type>()
    private fun keywords(raw: String?) = CardText.parse(raw).filterIsInstance<CardTextPart.Keyword>()
    private fun attrs(raw: String?) = CardText.parse(raw).filterIsInstance<CardTextPart.Attribute>()
    private fun breaks(raw: String?) = CardText.parse(raw).count { it is CardTextPart.Break }

    /** Everything joined back together, delimiters dropped. */
    private fun plain(raw: String?): String =
        CardText.parse(raw).filterIsInstance<CardTextPart.Plain>()
            .joinToString("") { it.text }

    // ---- real payloads ---------------------------------------------------

    @Test
    fun aBreakSeparatesTwoKeywordBlocks() {
        val raw = "[Blocker] (After your opponent declares an attack, you may rest this " +
            "card to make it the new target of the attack.)<br>[On K.O.] Draw 1 card."
        val parts = CardText.parse(raw)
        assertEquals(1, breaks(raw))
        assertEquals(listOf("Blocker", "On K.O."), keywords(raw).map { it.text })
        // No angle-bracket or brace markup leaks into the plain text.
        assertFalse(plain(raw).contains("<"))
        assertFalse(plain(raw).contains(">"))
    }

    @Test
    fun anAttributeTokenIsRecognisedNotTreatedAsHtml() {
        // Real: OP03-008
        val raw = "This Character cannot be K.O.'d in battle by <Slash> attribute cards." +
            "<br>[On Play] Look at 5 cards from the top of your deck"
        assertEquals(listOf("Slash"), attrs(raw).map { it.text })
        assertEquals(1, breaks(raw))
        assertFalse("the angle brackets must not survive", plain(raw).contains("Slash>"))
    }

    @Test
    fun aTypeTokenIsRecognised() {
        // Real: ST22-001 style payload
        val raw = "[On Play] If your Leader has the {ODYSSEY} type, set up to 3 of your " +
            "DON!! cards as active."
        assertEquals(listOf("ODYSSEY"), types(raw).map { it.text })
        assertFalse(plain(raw).contains("{"))
    }

    @Test
    fun aCardWithABreakATypeAndAnAttributeAllParse() {
        // Real: OP13-025, shape simplified but token-for-token accurate.
        val raw = "[Blocker] (After your opponent declares an attack.)<br>" +
            "[On Play] If your Leader has the {FILM} type or the <Strike> attribute, draw 1."
        val parts = CardText.parse(raw)
        assertEquals(1, breaks(raw))
        assertEquals(listOf("Blocker", "On Play"), keywords(raw).map { it.text })
        assertEquals(listOf("FILM"), types(raw).map { it.text })
        assertEquals(listOf("Strike"), attrs(raw).map { it.text })
    }

    @Test
    fun theApostropheEntityBecomesAnApostrophe() {
        // Real: OP06-096 / OP03-008
        val raw = "This Character cannot be K.O.&#039;d in battle by <Slash> attribute cards."
        assertTrue(plain(raw).contains("K.O.'d"))
        assertFalse(plain(raw).contains("&#039;"))
    }

    // ---- no data loss ----------------------------------------------------

    @Test
    fun nothingIsLostWhenMarkupIsStripped() {
        // Deliberately no entities: this asserts the *structural* round trip, and
        // entity unescaping is a lossy-but-intended change covered separately.
        val raw = "[On Play] Play up to 1 {Straw Hat Crew} type Character with a cost of 3 " +
            "or less from your hand.<br>[On K.O.] Draw 1 card. It is done."
        val joined = CardText.parse(raw).joinToString("") {
            when (it) {
                is CardTextPart.Plain -> it.text
                is CardTextPart.Break -> "<br>"
                is CardTextPart.Keyword -> "[${it.text}]"
                is CardTextPart.Type -> "{${it.text}}"
                is CardTextPart.Attribute -> "<${it.text}>"
            }
        }
        assertEquals(raw, joined)
    }

    // ---- robustness ------------------------------------------------------

    @Test
    fun nullAndEmptyProduceNothing() {
        assertTrue(CardText.parse(null).isEmpty())
        assertTrue(CardText.parse("").isEmpty())
        assertEquals(listOf("Plain"), CardText.parse("x").map { it::class.simpleName })
    }

    @Test
    fun anUnclosedTagIsLeftAloneRatherThanSwallowed() {
        // A stray "<" must not eat the rest of the string.
        val parts = CardText.parse("5 or less <not a tag")
        assertEquals(1, parts.size)
        assertEquals("5 or less <not a tag", (parts[0] as CardTextPart.Plain).text)
    }

    @Test
    fun anUnknownEntityIsLeftAlone() {
        // "&foo;" is not in the table, so the ampersand and the text survive.
        val parts = CardText.parse("&foo;bar")
        assertEquals(1, parts.size)
        assertEquals("&foo;bar", (parts[0] as CardTextPart.Plain).text)
    }

    @Test
    fun breakVariantsAreAllAccepted() {
        assertEquals(1, breaks("a<br>b"))
        assertEquals(1, breaks("a<BR>b"))
        assertEquals(1, breaks("a<br />b"))
        assertEquals(1, breaks("a< br >b"))
    }

    @Test
    fun carriageReturnNewlineCountsAsOneBreak() {
        assertEquals(1, breaks("a\r\nb"))
    }

    @Test
    fun hasMarkupDetectsTheRealCases() {
        assertTrue(CardText.hasMarkup("a<br>b"))
        assertTrue(CardText.hasMarkup("a {FILM} b"))
        assertTrue(CardText.hasMarkup("a [On Play] b"))
        assertTrue(CardText.hasMarkup("K.O.&#039;d"))
        assertFalse(CardText.hasMarkup("Draw 1 card."))
        assertFalse(CardText.hasMarkup(null))
    }

    @Test
    fun anEmptyTokenIsNotProduced() {
        // "{}" and "[]" carry no information and must not become blank pills.
        assertTrue(types("{}").isEmpty())
        assertTrue(keywords("[]").isEmpty())
    }
}
