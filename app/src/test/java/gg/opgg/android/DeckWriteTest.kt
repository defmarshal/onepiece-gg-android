package gg.opgg.android

import gg.opgg.android.data.SaveDeckRequest
import gg.opgg.android.data.SiteDeckName
import gg.opgg.android.data.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the `savedeck` body against a request captured from the site's own
 * builder on 2026-11-19. The original failure was not a wrong endpoint: it was
 * assuming `id`/`slug`/`name` identified a deck, when the wire field is
 * `deckname` and the title is `humanname`.
 */
class DeckWriteTest {

    private fun encode(req: SaveDeckRequest): JsonObject =
        AppJson.parseToJsonElement(AppJson.encodeToString(SaveDeckRequest.serializer(), req)) as JsonObject

    @Test
    fun bodyCarriesEveryFieldTheSiteSends() {
        val body = encode(
            SaveDeckRequest.of(
                deckname = "qxqgktmi49",
                humanname = "Ace & Newgate Deck",
                cards = mapOf("ST22-001" to 1, "ST17-001" to 1, "ST25-001" to 2),
            ),
        )
        val expected = setOf(
            "savedeck", "savedeck_extra", "deckname", "description", "format",
            "humanname", "manually_private", "public", "title_image",
            "unrestricted", "youtube",
        )
        assertEquals(expected, body.keys)
    }

    @Test
    fun flagFieldsAreStringsNotBooleans() {
        // The site sends "0"/"1" strings. A JSON boolean is a different request.
        val body = encode(SaveDeckRequest.of("abc1234567", "Deck", mapOf("OP01-001" to 4)))
        assertEquals("0", body["public"]?.jsonPrimitive?.content)
        assertEquals("0", body["unrestricted"]?.jsonPrimitive?.content)
        assertEquals("", body["manually_private"]?.jsonPrimitive?.content)
        assertEquals("Standard", body["format"]?.jsonPrimitive?.content)
    }

    @Test
    fun cardsArePrintingIdToCount() {
        val body = encode(
            SaveDeckRequest.of("abc1234567", "Deck", mapOf("ST25-001" to 2, "ST22-001" to 1)),
        )
        val cards = body["savedeck"] as JsonObject
        assertEquals("2", cards["ST25-001"]?.jsonPrimitive?.content)
        assertEquals("1", cards["ST22-001"]?.jsonPrimitive?.content)
    }

    @Test
    fun generatedDeckNamesLookLikeTheSites() {
        repeat(50) {
            val name = SiteDeckName.generate()
            assertEquals(10, name.length)
            assertTrue("unexpected characters in $name", name.all { it in 'a'..'z' || it in '0'..'9' })
        }
    }
}
