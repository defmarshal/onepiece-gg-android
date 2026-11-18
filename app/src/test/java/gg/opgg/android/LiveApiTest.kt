package gg.opgg.android

import gg.opgg.android.data.AppJson
import gg.opgg.android.data.Card
import kotlinx.serialization.json.Json
import gg.opgg.android.data.OpggApi
import gg.opgg.android.data.RelatedIndex
import gg.opgg.android.data.UserDataResponse
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume
import org.junit.Before
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hits the live api.dotgg.gg backend using the same client the app ships.
 * Run with:  gradlew :app:testDebugUnitTest
 * Needs network access; not a hermetic unit test.
 */
class LiveApiTest {

    private val api = OpggApi { null }

    /**
     * These tests hit the real api.dotgg.gg, which rate-limits per IP (roughly
     * 50 requests). One cheap probe up front, and if we are being throttled the
     * whole class skips instead of reporting six failures that have nothing to
     * do with the code.
     */
    @Before
    fun assumeApiIsNotThrottled() = runBlocking {
        val reachable = runCatching { api.getSets() }.isSuccess
        Assume.assumeTrue("api.dotgg.gg is rate limiting or unreachable; skipping live tests", reachable)
    }

    @Test
    fun getCardsParsesRealPayload() = runBlocking {
        val cards = api.getCards()
        println("cards=${cards.size}")
        assertTrue("expected thousands of cards, got ${cards.size}", cards.size > 3000)

        val ace = cards.first { it.idNormal == "ST22-001" }
        assertEquals("Ace & Newgate", ace.name)
        assertEquals("LEADER", ace.cardType)
        assertEquals("L", ace.rarity)
        assertEquals("5000", ace.Power)
        assertEquals("https://static.dotgg.gg/onepiece/card/ST22-001.webp", ace.imageUrl)

        // Every record must survive deserialisation without losing its id.
        assertEquals(cards.size, cards.count { !it.key.isNullOrBlank() })
    }

    @Test
    fun getSetsParsesRealPayload() = runBlocking {
        val sets = api.getSets()
        println("sets=${sets.size} first=${sets.firstOrNull()?.code}")
        assertTrue("expected sets, got ${sets.size}", sets.isNotEmpty())
        assertTrue(sets.any { it.code == "OP17" })
    }

    @Test
    fun collectionPayloadParsesForTheSignedInShape() = runBlocking {
        // Anonymous, so this returns an empty collection, but it proves the
        // {user, collection} envelope and our OwnedCard fields deserialize.
        val data = api.getUserData(0)
        println("serverUserId=${data.user?.user_id} collection=${data.collection?.size} error=${data.error}")
        assertTrue("getuserdata should not error for an anonymous read", !data.error)
        assertTrue("user envelope should parse", data.user != null)
        assertNotNull(data.collection)
    }

    @Test
    fun signedInUserDataShapeParses() {
        // Trimmed from a real getuserdata response. The important detail is
        // "capabilities" being an OBJECT: typing it as a list used to throw
        // "expected start of the array '[' but had '{'" at offset 74, which made
        // a 361-card collection render as an empty screen.
        val raw = """
            {"user":{"user_id":"158557252","nickname":"jojo-158557252",
            "capabilities":{"subscriber":true},"user_registered":"2026-09-23 01:27:33",
            "needs_nick":false,"needs_email":false},
            "collection":[{"card":"DON-660744","foil":"1","standard":"0","total":"1",
            "extra_data":null,"trade":"0","wish":"0","trade_foil":"0","wish_foil":"0"}],
            "settings":{},"deckCardCounts":{},"binders":[],"activeBinderId":0}
        """.trimIndent().replace("\n", "")

        val parsed = AppJson.decodeFromString(UserDataResponse.serializer(), raw)
        assertEquals(158557252L, parsed.user?.user_id)
        assertEquals("jojo-158557252", parsed.user?.nickname)
        assertEquals(1, parsed.collection?.size)
        val row = parsed.collection!!.first()
        assertEquals("DON-660744", row.card)
        // Counts arrive as strings, which is why they must be parsed, not cast.
        assertEquals(0, row.standard?.toIntOrNull())
        assertEquals(1, row.foil?.toIntOrNull())
    }

    @Test
    fun reprintsCollapseToOneRowPerCard() = runBlocking {
        val raw = api.getCards()
        val groups = raw.groupBy { it.key }
        val seen = HashSet<String>()
        val unique = ArrayList<Card>(groups.size)
        for (c in raw) if (seen.add(c.key)) unique.add(c)
        val final = unique.map { c -> groups[c.key].orEmpty().firstOrNull { it.id == it.idNormal } ?: c }

        println("printings=${raw.size} uniqueCards=${final.size} groups=${groups.size}")
        assertEquals(groups.size, final.size)
        assertEquals(final.size, final.map { it.key }.toSet().size)
        // Every card must survive dedup with an id and an image.
        assertEquals(final.size, final.count { !it.key.isBlank() && it.id != null })
        // Reprint groups exist in the real data, so this is not a no-op check.
        assertTrue("expected reprint groups", groups.any { it.value.size > 1 })
    }

    @Test
    fun everyReprintMapsBackToAnExistingGroup() = runBlocking {
        val raw = api.getCards()
        val groups = raw.groupBy { it.key }
        val index = HashMap<String, String>(raw.size)
        for (c in raw) {
            val printing = c.id ?: continue
            index[printing] = c.idNormal ?: printing
        }

        val reprints = raw.filter { it.id != it.idNormal }
        val pCount = reprints.count { c -> c.id?.let { Regex("_p\\d+$").containsMatchIn(it) } == true }
        val rCount = reprints.count { c -> c.id?.let { Regex("_r\\d+$").containsMatchIn(it) } == true }
        println("reprints=${reprints.size} _pN=$pCount _rN=$rCount groups=${groups.size}")

        // Both suffix families must be present, or this test is vacuous.
        assertTrue("expected _pN reprints", pCount > 0)
        assertTrue("expected _rN reprints", rCount > 0)

        // Every printing must resolve to a group that actually exists, which is
        // exactly what silently dropped the _rN cards from the collection.
        val orphans = index.filter { (printing, normal) -> groups[normal]?.none { it.id == printing } == true }
        assertTrue("printings with no group: ${orphans.keys.take(5)}", orphans.isEmpty())
    }

    @Test
    fun collectionRowsUsingReprintIdsResolveToTheirGroup() = runBlocking {
        val raw = api.getCards()
        val groups = raw.groupBy { it.key }
        val index = HashMap<String, String>(raw.size)
        for (c in raw) {
            val printing = c.id ?: continue
            index[printing] = c.idNormal ?: printing
        }

        // Ids taken from a real signed-in collection and a real deck list.
        val collectionIds = listOf("EB01-015_r1", "OP10-105_r1", "P-078_r1", "P-069_p3", "ST02-007_p4")
        for (id in collectionIds) {
            val normal = index[id]
            assertNotNull("no index entry for $id", normal)
            assertTrue("$id (normal=$normal) is not in the card database", groups.containsKey(normal))
        }
    }

    @Test
    fun conditionalGetAvoidsRedownloadingAnUnchangedDatabase() = runBlocking {
        // This is the mechanism the whole cache depends on: the API sends
        // Last-Modified but no ETag, and replaying If-Modified-Since must give
        // 304 with no body rather than another 5.2 MB.
        val (first, lastModified) = api.getCardsIfChanged(null)
        assertNotNull("initial conditional fetch returned nothing", first)
        assertNotNull("server sent no Last-Modified header", lastModified)
        println("initial fetch cards=" + first!!.size + " lastModified=" + lastModified)

        val (second, _) = api.getCardsIfChanged(lastModified)
        assertNull("expected 304 Not Modified for an unchanged database", second)
    }

    @Test
    fun columnarFormatMatchesTheObjectFormat() = runBlocking {
        // The app fetches mode=indexed (3,148,059 bytes) instead of the default
        // array-of-objects (5,230,704). This proves the positional rows decode to
        // exactly the same cards, so the 40% saving costs nothing.
        val objects = api.getCards()
        val columnar = api.getCardsIndexed()

        println("objects=${objects.size} columnar=${columnar.size}")
        assertEquals("row counts differ", objects.size, columnar.size)

        val objById = objects.associateBy { it.id }
        var compared = 0
        for (c in columnar) {
            val o = objById[c.id] ?: continue
            assertEquals("name mismatch for ${c.id}", o.name, c.name)
            assertEquals("id_normal mismatch for ${c.id}", o.idNormal, c.idNormal)
            assertEquals("rarity mismatch for ${c.id}", o.rarity, c.rarity)
            assertEquals("cardType mismatch for ${c.id}", o.cardType, c.cardType)
            assertEquals("set mismatch for ${c.id}", o.set, c.set)
            assertEquals("Color mismatch for ${c.id}", o.Color, c.Color)
            assertEquals("Effect mismatch for ${c.id}", o.Effect, c.Effect)
            // Deliberately NOT asserting price: it is live data and drifted a
            // cent between the two requests, which made this test flaky. Only
            // assert that the field survives the columnar round trip.
            assertEquals("price lost for ${c.id}", o.price != null, c.price != null)
            compared++
        }
        println("compared $compared cards field by field")
        assertTrue("compared too few cards to be meaningful", compared > 5000)
    }

    @Test
    fun columnarFormatAlsoHonoursConditionalGet() = runBlocking {
        val (first, lastModified) = api.getCardsIndexedIfChanged(null)
        assertNotNull(first)
        assertNotNull(lastModified)
        val (second, _) = api.getCardsIndexedIfChanged(lastModified)
        assertNull("expected 304 for an unchanged database", second)
    }

    @Test
    fun metaSnapshotParsesAndFeedsTheRelatedIndex() = runBlocking {
        // Guards the DTO field names: a wrong @SerialName would silently give
        // empty lists, and every related card would quietly disappear.
        val snap = api.getMetaSnapshot()
        println("archetypes=${snap.archetypes.size} set=${snap.setCode} format=${snap.format}")
        assertTrue("no archetypes parsed", snap.archetypes.size > 10)
        val withCards = snap.archetypes.filter { it.cards.isNotEmpty() }
        assertTrue("no archetype had cards", withCards.isNotEmpty())
        val entry = withCards.first().cards.first()
        assertNotNull("entry id missing", entry.id)
        println("sample entry: ${entry.id} in_deck=${entry.inDeck} weight=${entry.weight}")

        val cards = api.getCards()
        val index = HashMap<String, String>(cards.size)
        for (c in cards) c.id?.let { index[it] = c.idNormal ?: it }
        val related = RelatedIndex.build(snap, index, cards)
        println("index covers ${related.coveredCards} cards")
        assertTrue("index covered nothing", related.coveredCards > 100)

        // At least one real card must actually produce related cards.
        val seed = cards.firstOrNull { c -> c.key.isNotEmpty() && related.archetypeCount(c.key) > 0 }
        assertNotNull("no card in the snapshot is also in the card database", seed)
        val seedKey = seed!!.key
        val recs = related.related(seedKey, cards, limit = 6)
        println("seed=$seedKey -> ${recs.size} related: ${recs.map { it.normalId }}")
        assertTrue("no related cards produced", recs.isNotEmpty())
        assertFalse("a card was related to itself", recs.any { it.normalId == seedKey })
    }

    @Test
    fun deckRequestCarriesTheUserIdNotTheLiteralOne() {
        // Regression: sending "my":1 is silently ignored by the server and
        // returns the PUBLIC deck list, so "My decks" looked identical to
        // "Public decks". The site replaces my:1 with window.DotGGUser.
        assertEquals("{}", OpggApi { null }.buildDeckRequest(1, null))
        assertEquals("{\"page\":2}", OpggApi { null }.buildDeckRequest(2, null))
        assertEquals("{\"my\":158557252}", OpggApi { null }.buildDeckRequest(1, 158557252))
        assertEquals("{\"page\":3,\"my\":42}", OpggApi { null }.buildDeckRequest(3, 42))
        // A missing or bogus session must not emit a "my" filter at all.
        assertFalse(OpggApi { null }.buildDeckRequest(1, 0).contains("my"))
        // Must be valid JSON: a trailing comma made the server discard rq
        // entirely and silently return page 1, so "Load more" did nothing.
        for ((p, id) in listOf(1 to null, 2 to null, 1 to 5L, 9 to 7L)) {
            val rq = OpggApi { null }.buildDeckRequest(p, id)
            assertFalse("$rq must not have a trailing comma", rq.contains(",}"))
            assertEquals("$rq should parse", rq, Json.parseToJsonElement(rq).toString())
        }
    }

    @Test
    fun deckPayloadsDecodeWithTheRealFieldTypes() = runBlocking {
        // Regression: the deck detail screen crashed on
        // "expected beginning of the string but got {" because `history` is an
        // OBJECT in the real payload, not a string. The archetype_* fields vary
        // too. These are public endpoints, so this needs no credentials.
        val decks = api.getDecks(page = 1)
        assertTrue("no decks returned", decks.isNotEmpty())
        val d = decks.first()
        assertTrue("deck name missing", !d.displayName.isNullOrBlank())
        assertTrue("deck card counts did not sum", d.cardCount > 0)
        println("list deck ${d.slug}: unique=${d.uniqueCards} total=${d.cardCount} price=${d.price}")

        val detail = api.getDeck(d.slug!!)
        assertTrue("detail name missing", !detail.displayName.isNullOrBlank())
        assertTrue("detail deck empty", detail.deck.isNotEmpty())
        // Counts arrive as strings; this is the parse the detail list relies on.
        assertTrue(detail.deck.values.all { it.toIntOrNull() != null })
        println("detail ${detail.slug}: cards=${detail.deck.size} total=${detail.cardCount}")
    }

    @Test
    fun unknownRouteIsRejectedNotSilentlyEmpty() = runBlocking {
        // A bogus action must raise, not come back as an empty success.
        var threw = false
        try {
            api.getTextForTest("cards")
        } catch (e: Exception) {
            threw = true
            println("blocked as expected: ${e.message}")
        }
        assertTrue("bot-guard response was not detected", threw)
    }
}
