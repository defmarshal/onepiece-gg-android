package gg.opgg.android

import gg.opgg.android.data.Card
import gg.opgg.android.data.DeckCode
import gg.opgg.android.data.LocalDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckCodeTest {

    private val printings = listOf(
        Card(id = "OP01-001", idNormal = "OP01-001", name = "Ace, Pirate", set = "OP01", rarity = "SR"),
        Card(id = "ST22-001", idNormal = "ST22-001", name = "Kinemon", set = "ST22", rarity = "C"),
        Card(id = "OP06-081_r1", idNormal = "OP06-081", name = "Absalom", set = "OP06", rarity = "R"),
        Card(id = "OP06-081", idNormal = "OP06-081", name = "Absalom", set = "OP06", rarity = "R"),
    )
    private val lookup = DeckCode.Lookup(printings)

    private fun deck(vararg e: Pair<String, Int>) =
        LocalDeck(id = "d", leaderId = "OP01-001", cards = e.toMap())

    // ---- export ---------------------------------------------------------

    @Test
    fun exportUsesTheSiteFormatAndIsStable() {
        val d = deck("ST22-001" to 2, "OP01-001" to 4)
        val text = DeckCode.export(d)
        assertEquals("4xOP01-001\n2xST22-001", text)
        // Same deck, same text - so an export can be diffed.
        assertEquals(text, DeckCode.export(deck("OP01-001" to 4, "ST22-001" to 2)))
    }

    @Test
    fun exportSkipsZeroCounts() {
        val d = LocalDeck(id = "d", cards = mapOf("OP01-001" to 2, "ST22-001" to 0))
        assertEquals("2xOP01-001", DeckCode.export(d))
    }

    // ---- the site's import shape (suffix dropped, set prefix kept) -------

    @Test
    fun exportForSiteDropsTheReprintSuffix() {
        val d = LocalDeck(
            id = "d",
            cards = mapOf("EB01-015_r1" to 1, "EB03-019" to 1, "ST02-007_p4" to 2),
        )
        val r = DeckCode.exportForSite(d)
        // EB01-015_r1 -> EB01-015, ST02-007_p4 -> ST02-007, EB03-019 unchanged.
        assertTrue(r.code.contains("1xEB01-015"))
        assertTrue(r.code.contains("1xEB03-019"))
        assertTrue(r.code.contains("2xST02-007"))
        assertFalse("no suffix may survive", r.code.contains("_r1"))
        assertFalse("no suffix may survive", r.code.contains("_p4"))
    }

    @Test
    fun thePlainExportKeepsTheSuffixBecauseItIsTheLosslessOne() {
        val d = LocalDeck(id = "d", cards = mapOf("EB01-015_r1" to 1))
        assertEquals("1xEB01-015_r1", DeckCode.export(d))
    }

    @Test
    fun siteExportKeepsTheSetPrefix() {
        val r = DeckCode.exportForSite(LocalDeck(id = "d", cards = mapOf("P-069_p3" to 1)))
        assertEquals("1xP-069", r.code)
    }

    @Test
    fun siteExportHandlesBothSuffixKinds() {
        val d = LocalDeck(id = "d", cards = mapOf("A-1_r2" to 1, "B-2_p5" to 1))
        val r = DeckCode.exportForSite(d)
        assertTrue(r.code.contains("1xA-1"))
        assertTrue(r.code.contains("1xB-2"))
    }

    @Test
    fun aCardWithNoSuffixIsUntouched() {
        val r = DeckCode.exportForSite(LocalDeck(id = "d", cards = mapOf("OP13-025" to 3)))
        assertEquals("3xOP13-025", r.code)
    }

    @Test
    fun twoPrintingsOfOneCardAreReportedRatherThanMerged() {
        // 4 of the base and 2 of the reprint. Merging would make 6 of one
        // printing, which breaks the 4-copy rule, so the caller is told instead.
        val d = LocalDeck(id = "d", cards = mapOf("EB01-015" to 4, "EB01-015_r1" to 2))
        val r = DeckCode.exportForSite(d)
        assertTrue(r.hasCollisions)
        assertEquals(listOf("EB01-015"), r.collapsedPrintings)
        // Both lines are still emitted, so nothing is silently dropped.
        assertTrue(r.code.contains("4xEB01-015"))
        assertTrue(r.code.contains("2xEB01-015"))
    }

    @Test
    fun aDeckWithNoCollisionsReportsNone() {
        val d = LocalDeck(id = "d", cards = mapOf("A-1" to 4, "B-2" to 2, "C-3" to 1))
        assertTrue(DeckCode.exportForSite(d).collapsedPrintings.isEmpty())
    }

    @Test
    fun siteExportIsSortedSoItStaysDiffable() {
        val d = LocalDeck(id = "d", cards = mapOf("C-3" to 1, "A-1" to 2, "B-2" to 3))
        assertEquals("2xA-1\n3xB-2\n1xC-3", DeckCode.exportForSite(d).code)
    }

    @Test
    fun siteExportSkipsZeroAndNegativeCounts() {
        val d = LocalDeck(id = "d", cards = mapOf("A-1" to 2, "B-2" to 0, "C-3" to -1))
        assertEquals("2xA-1", DeckCode.exportForSite(d).code)
    }

    @Test
    fun aRoundTripThroughTheSiteShapeIsAcceptedByOurOwnImporter() {
        // The site shape must still be parseable, so a deck that has been through
        // the site can come back in. Uses OP06-081_r1 because stripping its
        // suffix yields OP06-081, which this fixture's lookup knows.
        val d = LocalDeck(
            id = "d",
            leaderId = "OP01-001",
            cards = mapOf("OP01-001" to 1, "OP06-081_r1" to 2),
        )
        val site = DeckCode.exportForSite(d).code
        assertEquals("1xOP01-001\n2xOP06-081", site)
        val parsed = DeckCode.parse(site, lookup)
        assertTrue("nothing should be unresolved: ${parsed.unresolved}", parsed.unresolved.isEmpty())
        assertEquals(2, parsed.cards["OP06-081"])
    }

    // ---- round trip -----------------------------------------------------

    @Test
    fun roundTripPreservesTheDeck() {
        val d = deck("OP01-001" to 4, "ST22-001" to 3, "OP06-081_r1" to 1)
        val parsed = DeckCode.parse(DeckCode.export(d), lookup)
        assertEquals(d.cards, parsed.cards)
        assertTrue("nothing should be unresolved", parsed.unresolved.isEmpty())
        assertEquals(8, parsed.total)
    }

    // ---- the forgiving superset -----------------------------------------

    @Test
    fun parseAcceptsCommonSpacingVariants() {
        val text = """
            4xOP01-001
            2 x ST22-001
            OP06-081_r1 x 3
            ST22-001
        """.trimIndent()
        val p = DeckCode.parse(text, lookup)
        assertEquals(4, p.cards["OP01-001"])
        assertEquals(2 + 1, p.cards["ST22-001"])   // 2 from the spaced line, 1 bare
        assertEquals(3, p.cards["OP06-081_r1"])
        assertTrue(p.unresolved.isEmpty())
    }

    @Test
    fun parseIsCaseInsensitiveLikeTheSite() {
        val p = DeckCode.parse("4xop01-001", lookup)
        assertEquals(4, p.cards["OP01-001"])
    }

    @Test
    fun parseResolvesCanonicalIdsToTheBasePrinting() {
        // People paste the id from a search result, which is the canonical id.
        val p = DeckCode.parse("2xOP06-081", lookup)
        assertEquals(2, p.cards["OP06-081"])
    }

    @Test
    fun unresolvedIdsAreReportedNotSilentlyDropped() {
        val p = DeckCode.parse("4xOP01-001\n1xNOPE-999\n2xALSO-MISSING", lookup)
        assertEquals(4, p.cards["OP01-001"])
        assertEquals(listOf("NOPE-999", "ALSO-MISSING"), p.unresolved)
    }

    @Test
    fun blankLinesAndCommentsAreIgnored() {
        val p = DeckCode.parse("# my deck\n\n4xOP01-001\n\n  \n", lookup)
        assertEquals(4, p.cards["OP01-001"])
        assertTrue(p.unresolved.isEmpty())
    }

    @Test
    fun duplicateTokensForOnePrintingAreSummed() {
        val p = DeckCode.parse("2xOP01-001\n2xop01-001", lookup)
        assertEquals(4, p.cards["OP01-001"])
    }

    // ---- csv -------------------------------------------------------------

    @Test
    fun csvQuotesNamesContainingCommas() {
        val byId = printings.associateBy { it.id }
        val csv = DeckCode.toCsv(deck("OP01-001" to 4)) { byId[it] }
        assertTrue("comma must be quoted", csv.contains("\"Ace, Pirate\""))
        assertTrue(csv.startsWith("printing_id,card_id,name,set,rarity,count"))
        assertTrue(csv.contains("OP01-001,OP01-001,\"Ace, Pirate\",OP01,SR,4"))
    }
}
