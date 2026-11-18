package gg.opgg.android

import gg.opgg.android.data.AppJson
import gg.opgg.android.data.Card
import gg.opgg.android.data.CardCacheEnvelope
import gg.opgg.android.data.CardSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cache is written to disk on every cold start, so anything that inflates
 * it is paid twice: once to write and once to parse. `searchBlob` is a derived
 * convenience field and must never be serialised.
 */
class CacheEnvelopeTest {

    private fun card() = Card(
        id = "EB01-050",
        idNormal = "EB01-050",
        name = "...I Want to Live!!",
        cardType = "EVENT",
        Color = "Black",
        Type = "Straw Hat Crew",
        Effect = "[Counter] If you have 30 or more cards in your trash, add up to 1 card.",
        set = "EB01",
    )

    @Test
    fun searchBlobIsNotWrittenToDisk() {
        val env = CardCacheEnvelope(
            lastModified = "Sun, 27 Sep 2026 08:13:26 GMT",
            fetchedAt = 1_790_496_819_718L,
            cards = listOf(card()),
            sets = listOf(CardSet(id = "EB01", code = "EB01", name = "Memorial Collection")),
        )
        val json = AppJson.encodeToString(CardCacheEnvelope.serializer(), env)
        assertFalse("searchBlob must not be serialised: $json", json.contains("searchBlob"))
        assertFalse(json.contains("i want to live!!"))
    }

    @Test
    fun envelopeRoundTripsAndRestoresTheBlob() {
        val env = CardCacheEnvelope(
            lastModified = "Sun, 27 Sep 2026 08:13:26 GMT",
            fetchedAt = 1_790_496_819_718L,
            cards = listOf(card()),
            sets = listOf(CardSet(id = "EB01", code = "EB01", name = "Memorial Collection")),
        )
        val json = AppJson.encodeToString(CardCacheEnvelope.serializer(), env)
        val back = AppJson.decodeFromString(CardCacheEnvelope.serializer(), json)

        assertEquals("Sun, 27 Sep 2026 08:13:26 GMT", back.lastModified)
        assertEquals(1_790_496_819_718L, back.fetchedAt)
        assertEquals(1, back.cards.size)
        assertEquals(1, back.sets.size)

        val c = back.cards.single()
        assertEquals("EB01-050", c.id)
        assertEquals("...I Want to Live!!", c.name)
        // The derived field is recomputed on decode rather than restored.
        assertTrue("searchBlob should be rebuilt after decoding", c.searchBlob.isNotEmpty())
        assertTrue(c.searchBlob.contains("i want to live!!"))
        assertTrue(c.searchBlob.contains("[counter]"))
    }

    @Test
    fun nullFieldsSurviveTheRoundTrip() {
        val bare = Card(id = "P-044", idNormal = "P-044", name = "Unknown")
        val env = CardCacheEnvelope(cards = listOf(bare))
        val back = AppJson.decodeFromString(
            CardCacheEnvelope.serializer(),
            AppJson.encodeToString(CardCacheEnvelope.serializer(), env),
        )
        val c = back.cards.single()
        assertEquals("P-044", c.id)
        assertEquals(null, c.price)
        assertEquals(null, c.Effect)
    }

    @Test
    fun printingRowsSurviveTheCacheRoundTrip() {
        // The cache used to store the deduped set, which dropped the printing
        // rows outright - so on every cold start after the first the Printings
        // strip vanished, the printing->card index was incomplete, and the
        // related list dropped cards. The printing rows must survive a round
        // trip through the cache intact.
        val base = Card(id = "P-058", idNormal = "P-058", name = "Where the Wind Blows")
        val parallel = Card(
            id = "P-058_p1",
            idNormal = "P-058",
            name = "Where the Wind Blows",
            rarity = "P",
        )
        val env = CardCacheEnvelope(cards = listOf(base, parallel))
        val back = AppJson.decodeFromString(
            CardCacheEnvelope.serializer(),
            AppJson.encodeToString(CardCacheEnvelope.serializer(), env),
        )

        assertEquals(2, back.cards.size)
        val printing = back.cards.first { it.id == "P-058_p1" }
        assertEquals("P-058", printing.idNormal)
        // The whole point: the printing's own id survives, so it can be turned
        // back into a distinct artwork URL.
        assertTrue(printing.imageUrl.endsWith("/P-058_p1.webp"))
    }

    @Test
    fun anOldCacheIsRecognisedAsOutOfDate() {
        // Version 1 stored the deduped set. Such a cache is still readable and
        // still paints the grid, so it must not be treated as absent - but it
        // cannot supply the printings, so it has to force a refresh.
        val old = CardCacheEnvelope(cards = listOf(card()))
        assertEquals(1, old.version)
        assertTrue(old.cards.isNotEmpty())
    }
}
