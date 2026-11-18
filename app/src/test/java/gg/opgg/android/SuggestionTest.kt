package gg.opgg.android

import gg.opgg.android.data.Card
import gg.opgg.android.data.CardSearch
import gg.opgg.android.data.QField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionTest {

    private val cards = listOf(
        mk("OP01-001", "Monkey D. Luffy", "CHARACTER", "Red", "SR", "OP01"),
        mk("OP01-002", "Ace", "CHARACTER", "Black", "SR", "OP01"),
        mk("ST22-001", "Ace & Newgate", "LEADER", "Blue", "L", "ST22"),
        mk("EB01-050", "...I Want to Live!!", "EVENT", "Black", "C", "EB01"),
    )

    private val vocab = CardSearch.vocabulary(cards)
    private val names = cards.mapNotNull { it.name }

    private fun mk(id: String, name: String, type: String, color: String, rarity: String, set: String) =
        Card(
            id = id, idNormal = id, name = name, cardType = type, Color = color,
            rarity = rarity, set = set, Cost = "1", Attribute = "Strike", Type = "Straw Hat Crew",
        )

    private fun s(query: String) = CardSearch.suggest(query, vocab, names).map { it.insert }

    @Test
    fun typingAPrefixSuggestsFieldNames() {
        // "t" is ambiguous, so both CARDTYPE and TYPE are offered.
        assertEquals(listOf("t:", "type:"), s("t"))
        // "c" is the canonical colour prefix, so it must rank first even though
        // the "ct" and "crew" aliases also start with c.
        assertEquals(listOf("c:", "cost:", "counter:", "t:", "type:"), s("c"))
        assertTrue(s("cos").contains("cost:"))
    }

    @Test
    fun fieldColonSuggestsEveryValueForThatField() {
        val values = s("t:").map { it.removePrefix("t:") }
        assertEquals(setOf("CHARACTER", "EVENT", "LEADER"), values.toSet())
    }

    @Test
    fun partialValueNarrowsTheList() {
        assertEquals(listOf("t:LEADER"), s("t:lea"))
    }

    @Test
    fun completionUsesTheCanonicalPrefixNotTheAliasTyped() {
        // Typed via the alias "cardtype", completed as the canonical "t:".
        assertEquals(listOf("t:LEADER"), s("cardtype:lea"))
    }

    @Test
    fun negatedTokensSuggestValuesAndKeepTheMinus() {
        assertEquals(listOf("-rarity:C"), s("-rarity:c"))
    }

    @Test
    fun quotedPartialStillMatches() {
        assertEquals(listOf("c:Red"), s("c:\"re"))
    }

    @Test
    fun freeTextFieldOffersNoValueSuggestions() {
        // No vocabulary for crew/effect text, so stay quiet rather than guess.
        assertTrue(s("type:stra").isEmpty())
        assertTrue(s("effects:draw").isEmpty())
    }

    @Test
    fun bareWordsSuggestCardNames() {
        assertTrue(s("luf").contains("Monkey D. Luffy"))
        assertTrue(s("newgate").contains("Ace & Newgate"))
    }

    @Test
    fun unknownFieldIsNotSuggested() {
        assertTrue(s("zz:").isEmpty())
    }

    @Test
    fun suggestionsOnlyCoverTheTokenBeingTyped() {
        // "luffy c:" - the caret is on the colour token, so no card names.
        val out = s("luffy c:")
        assertEquals(setOf("Red", "Black", "Blue"), out.map { it.removePrefix("c:") }.toSet())
    }

    @Test
    fun partialStartFindsTheTokenUnderTheCaret() {
        assertEquals(0, CardSearch.partialTokenStart("c:red"))
        assertEquals(6, CardSearch.partialTokenStart("luffy "))
        assertEquals(6, CardSearch.partialTokenStart("luffy c"))
        // Inside a quoted phrase, spaces do not end the token.
        assertEquals(0, CardSearch.partialTokenStart("effects:\"draw 1"))
    }
}
