package gg.opgg.android

import gg.opgg.android.data.OpggApi
import gg.opgg.android.data.unwrapDeckList
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Deck search rides on the site's own request shape: the text lives in
 * `getdecks.smartsrch`, nested under the same `rq` object as paging and `my`.
 *
 * Flat keys (`q`, `search`, `name`, `leader`, ...) are silently ignored by the
 * API - that is why the deck list had no search box for so long.
 */
class DeckSearchRequestTest {

    private val api = OpggApi { null }

    @Test
    fun aSearchIsNestedUnderGetdecks() {
        assertEquals(
            "{\"getdecks\":{\"smartsrch\":\"zoro\"}}",
            api.buildDeckRequest(1, null, "zoro"),
        )
    }

    @Test
    fun theQueryIsRepeatedOnLaterPagesAndWithMine() {
        assertEquals(
            "{\"page\":2,\"getdecks\":{\"smartsrch\":\"zoro\"}}",
            api.buildDeckRequest(2, null, "zoro"),
        )
        assertEquals(
            "{\"page\":2,\"my\":42,\"getdecks\":{\"smartsrch\":\"zoro\"}}",
            api.buildDeckRequest(2, 42, "zoro"),
        )
    }

    @Test
    fun aBlankSearchAddsNothing() {
        assertEquals("{}", api.buildDeckRequest(1, null, "   "))
        assertEquals("{\"page\":2}", api.buildDeckRequest(2, null, ""))
        assertEquals("{}", api.buildDeckRequest(1, null, null))
    }

    @Test
    fun quotesAreEscaped() {
        val rq = api.buildDeckRequest(1, null, "a\"b")
        assertEquals("{\"getdecks\":{\"smartsrch\":\"a\\\"b\"}}", rq)
        assertEquals("the object must stay valid JSON", rq, Json.parseToJsonElement(rq).toString())
    }

    @Test
    fun anEmptyResultIsDoubleEncodedAndMustBeUnwrapped() {
        // The server answers a no-match search with the JSON string "[]",
        // not an array; decoding that as List<Deck> used to fail and looked
        // like a broken request.
        assertEquals("[]", unwrapDeckList("\"[]\""))
        // A real page must pass through byte-for-byte.
        val page = "[{\"name\":\"x\"}]"
        assertEquals(page, unwrapDeckList(page))
    }
}
