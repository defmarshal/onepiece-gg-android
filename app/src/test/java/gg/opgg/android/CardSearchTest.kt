package gg.opgg.android

import gg.opgg.android.data.Card
import gg.opgg.android.data.CardSearch
import gg.opgg.android.data.Owned
import gg.opgg.android.data.QField
import gg.opgg.android.data.Term
import gg.opgg.android.data.collapseOwnedCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure parser/matcher tests. No network, no Android. */
class CardSearchTest {

    private fun card(
        id: String = "OP01-001",
        name: String? = "Monkey D. Luffy",
        cardType: String? = "CHARACTER",
        color: String? = "Red",
        attribute: String? = "Strike",
        rarity: String? = "SR",
        set: String? = "OP01",
        cost: String? = "5",
        crew: String? = "Straw Hat Crew",
        effect: String? = "[Activate: Main] Draw 1 card.",
        price: String? = "1.25",
        block: String? = "3",
    ) = Card(
        id = id, idNormal = id, name = name, cardType = cardType, Color = color,
        Attribute = attribute, rarity = rarity, set = set, Cost = cost, Type = crew,
        Effect = effect, price = price, BlockIcon = block,
    )

    private fun run(query: String, c: Card = card()) = CardSearch.matches(c, parseWithVocab(query))

    // ---- counter buckets -------------------------------------------------

    private fun withCounter(value: String?) = card().copy(Counter = value)

    @Test
    fun counterBucketNoneMatchesCardsWithNoCounterValue() {
        // A card whose Counter is absent, "0" or empty all belong in "no counter".
        assertTrue(run("counter:none", withCounter("0")))
        assertTrue(run("counter:none", withCounter(null)))
        assertFalse(run("counter:none", withCounter("1000")))
    }

    @Test
    fun counterBucketPlus1000CoversThe1000To1999Range() {
        assertTrue(run("counter:plus1000", withCounter("1000")))
        assertTrue(run("counter:plus1000", withCounter("1500")))
        assertFalse(run("counter:plus1000", withCounter("2000")))
        assertFalse(run("counter:plus1000", withCounter("0")))
    }

    @Test
    fun counterBucketPlus2000CoversTwoThousandAndAbove() {
        assertTrue(run("counter:plus2000", withCounter("2000")))
        assertTrue(run("counter:plus2000", withCounter("3000")))
        assertTrue(run("counter:plus2000", withCounter("10000")))
        assertFalse(run("counter:plus2000", withCounter("1500")))
    }

    @Test
    fun aNumericCounterQueryStillMeansExactlyThatNumber() {
        // The buckets are named rather than numeric precisely so that
        // `counter:2000` keeps meaning "exactly 2000" and is not widened to
        // "2000 or more" by the new bucket support.
        assertTrue(run("counter:2000", withCounter("2000")))
        assertFalse(run("counter:2000", withCounter("3000")))
    }

    @Test
    fun bucketNamesAreInTheVocabularySoTheParserCompletesThem() {
        assertTrue(CardSearch.COUNTER_BUCKETS.all { it in vocab.getValue(QField.COUNTER) })
    }

    @Test
    fun aCounterBucketCanBeNegated() {
        assertFalse(run("-counter:none", withCounter("0")))
        assertTrue(run("-counter:none", withCounter("2000")))
    }

    private val vocab = CardSearch.vocabulary(
        listOf(
            card(rarity = "SR", color = "Red", set = "OP01", cost = "5", attribute = "Strike"),
            card(id = "OP02-100", rarity = "SP CARD", color = "Blue", set = "OP02", cost = "3", attribute = "Slash"),
        )
    )

    private fun parseWithVocab(text: String) = CardSearch.parse(text, vocab)

    @Test
    fun completeEnumValueDoesNotSwallowTheNextWord() {
        // "red" is a real colour, so "luffy" must stay a search word rather than
        // becoming part of the colour value.
        val q = parseWithVocab("c:red luffy")
        assertEquals(listOf("red"), q.terms.single().values)
        assertEquals(listOf("luffy"), q.words)
    }

    @Test
    fun incompleteEnumValueAbsorbsUntilItMatchesTheVocabulary() {
        val q = parseWithVocab("rarity:sp card")
        assertEquals(listOf("sp card"), q.terms.single().values)
        assertTrue(q.words.isEmpty())
    }

    @Test
    fun bareWordsSearchNameIdAndEffect() {
        assertTrue(run("luffy"))
        assertTrue(run("OP01-001"))
        assertTrue(run("draw 1"))          // ANDed words, both present
        assertFalse(run("luffy zoro"))
    }

    @Test
    fun cardTypeAliasWorksAndOrs() {
        assertTrue(run("t:leader", card(cardType = "LEADER")))
        assertTrue(run("cardtype:LEADER", card(cardType = "LEADER")))
        assertTrue(run("t:leader,character"))
        assertFalse(run("t:event"))
        // Prefix matching is allowed so "t:lead" still finds LEADER.
        assertTrue(run("t:lead", card(cardType = "LEADER")))
    }

    @Test
    fun colorMatchesAnyPartOfAMultiColourCard() {
        val redBlue = card(color = "Red/Blue")
        assertTrue(run("c:red", redBlue))
        assertTrue(run("c:blue", redBlue))
        assertTrue(run("color:Red", redBlue))
        assertFalse(run("c:green", redBlue))
    }

    @Test
    fun attributeHandlesSpacedSeparators() {
        val mixed = card(attribute = "Slash / Wisdom")
        assertTrue(run("attr:slash", mixed))
        assertTrue(run("attr:wisdom", mixed))
    }

    @Test
    fun crewUsesContainsSoLongNamesAreUsable() {
        val crew = card(crew = "Alabasta/Straw Hat Crew")
        assertTrue(run("type:straw hat", crew))
        assertTrue(run("crew:alabasta", crew))
    }

    @Test
    fun exactFieldsAreCaseInsensitive() {
        assertTrue(run("rarity:sr"))
        assertTrue(run("set:op01"))
        assertTrue(run("cost:5"))
        assertFalse(run("rarity:secret"))
        // "SP CARD" contains a space: unquoted multi-word values must work.
        assertTrue(run("rarity:sp card", card(rarity = "SP CARD")))
    }

    @Test
    fun effectsAcceptsQuotedPhrase() {
        val c = card(effect = "[Activate: Main] Draw 1 card.")
        assertTrue(run("effects:\"draw 1 card\"", c))
        assertTrue(run("effects:activate", c))
        // Quotes must not leak into the searched value.
        val q = CardSearch.parse("effects:\"draw 1 card\"")
        assertEquals(listOf("draw 1 card"), q.terms.single().values)
    }

    @Test
    fun usdpriceIsGreaterThan() {
        assertTrue(run("usdprice:1", card(price = "1.25")))
        assertFalse(run("usdprice:2", card(price = "1.25")))
    }

    @Test
    fun termsForDifferentFieldsAreAnded() {
        assertTrue(run("c:red rarity:SR"))
        assertFalse(run("c:red rarity:UC", card(rarity = "SR")))
    }

    @Test
    fun valuesWithinOneFieldAreOred() {
        assertFalse(run("c:green", card(color = "Red")))
        assertTrue(run("c:green,red", card(color = "Red")))
    }

    @Test
    fun multiWordValuesDoNotLeakIntoBareWords() {
        // "sp card" and "draw 1 card" must be one value, not a value plus an
        // extra AND-ed search word.
        val q = parseWithVocab("rarity:sp card")
        assertEquals(1, q.terms.size)
        assertEquals(listOf("sp card"), q.terms.single().values)
        assertTrue(q.words.isEmpty())
        assertTrue(run("rarity:sp card", card(rarity = "SP CARD")))

        val q2 = parseWithVocab("type:straw hat c:red")
        assertEquals(2, q2.terms.size)
        assertEquals(listOf("straw hat"), q2.terms[0].values)
        assertEquals(listOf("red"), q2.terms[1].values)
        assertTrue(run("type:straw hat", card(crew = "Straw Hat Crew")))
    }

    @Test
    fun minusNegatesATerm() {
        assertFalse(run("-c:red", card(color = "Red")))
        assertTrue(run("-c:green", card(color = "Red")))
    }

    @Test
    fun unknownTokenFallsBackToTextAndIsReported() {
        val q = CardSearch.parse("bogus:thing")
        assertTrue(q.terms.isEmpty())
        assertEquals(listOf("bogus:thing"), q.unknownTokens)
        assertEquals(listOf("bogus:thing"), q.words)
    }

    @Test
    fun renderRoundTripsThroughParse() {
        val terms = listOf(
            Term(QField.COLOR, listOf("Red", "Blue")),
            Term(QField.RARITY, listOf("SR"), negated = true),
        )
        val text = CardSearch.render(terms, listOf("luffy"))
        val reparsed = CardSearch.parse(text)
        assertEquals(terms.size, reparsed.terms.size)
        assertEquals(listOf("Red", "Blue"), reparsed.terms[0].values)
        assertTrue(reparsed.terms[1].negated)
        assertEquals(listOf("luffy"), reparsed.words)
    }

    @Test
    fun quotedValuesWithSpacesSurviveRender() {
        val text = CardSearch.render(listOf(Term(QField.EFFECTS, listOf("draw 1 card"))))
        assertEquals(listOf("draw 1 card"), CardSearch.parse(text).terms.single().values)
    }

    /**
     * Regression: ownership is recorded against a PRINTING id, but the card list
     * shows the BASE printing. Looking up owned["EB01-015"] misses a holding of
     * "EB01-015_r1", which hid the owned badge and let the detail sheet write a
     * duplicate row. Ids below are real holdings from a signed-in account.
     */
    @Test
    fun reprintHoldingsAreVisibleOnTheBaseCard() {
        val printings = listOf("EB01-015", "EB01-015_r1", "OP07-031", "OP07-031_p1")
        val printingToNormal = printings.associateWith { it.substringBefore("_") }

        // The account holds only the reprints.
        val owned = mapOf(
            "EB01-015_r1" to Owned(normal = 0, foil = 4),
            "OP07-031_p1" to Owned(normal = 1, foil = 0),
        )

        val collapsed = collapseOwnedCounts(owned, printingToNormal)

        assertEquals(4, collapsed["EB01-015"]?.foil)
        assertEquals(0, collapsed["EB01-015"]?.normal)
        assertEquals(1, collapsed["OP07-031"]?.normal)

        // The badge lookup is by canonical id, so it must now find them.
        assertTrue(collapsed.containsKey("EB01-015"))
        assertTrue(collapsed.containsKey("OP07-031"))
    }

    @Test
    fun holdingsAcrossSeveralPrintingsOfOneCardAreSummed() {
        val owned = mapOf(
            "OP07-031" to Owned(normal = 1, foil = 0),
            "OP07-031_p1" to Owned(normal = 2, foil = 1),
        )
        val printingToNormal = mapOf("OP07-031" to "OP07-031", "OP07-031_p1" to "OP07-031")
        val collapsed = collapseOwnedCounts(owned, printingToNormal)
        assertEquals(3, collapsed["OP07-031"]?.normal)
        assertEquals(1, collapsed["OP07-031"]?.foil)
    }

    @Test
    fun unknownPrintingFallsBackToStrippingTheSuffix() {
        // A holding whose printing is missing from the card database must still
        // land in a group rather than being dropped.
        val collapsed = collapseOwnedCounts(
            mapOf("ZZ99-001_r7" to Owned(normal = 2, foil = 0)),
            emptyMap(),
        )
        assertEquals(2, collapsed["ZZ99-001"]?.normal)
    }

}
