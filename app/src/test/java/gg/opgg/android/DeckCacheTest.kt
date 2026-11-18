package gg.opgg.android

import gg.opgg.android.data.Deck
import gg.opgg.android.data.DeckCache
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The public deck list is cached for offline use, keyed by segment and query so
 * an offline search cannot show a snapshot from a different search.
 */
class DeckCacheTest {

    private lateinit var file: File

    @Before
    fun setUp() {
        file = File.createTempFile("opgg-decklist", ".json")
        file.delete()
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun decks(vararg names: String) = names.map { Deck(name = it, slug = it) }

    @Test
    fun aWrittenListRoundTrips() {
        val cache = DeckCache(file)
        cache.write(mine = false, query = "zoro", page = 2, decks = decks("A", "B"))
        val back = cache.forQuery(mine = false, query = "zoro")!!
        assertEquals(listOf("A", "B"), back.decks.map { it.name })
        assertEquals(2, back.page)
        assertEquals("zoro", back.query)
    }

    @Test
    fun aDifferentSearchDoesNotReuseTheSnapshot() {
        val cache = DeckCache(file)
        cache.write(mine = false, query = "zoro", page = 1, decks = decks("A"))
        assertNull(cache.forQuery(mine = false, query = "yamato"))
        assertNull("own decks are a different list", cache.forQuery(mine = true, query = "zoro"))
        assertNull("a blank query does not match a search snapshot", cache.forQuery(mine = false, query = ""))
    }

    @Test
    fun anEmptyWriteIsIgnored() {
        val cache = DeckCache(file)
        cache.write(mine = false, query = "", page = 1, decks = emptyList())
        assertNull(cache.read())
    }

    @Test
    fun aMissingFileReadsAsAbsent() {
        assertNull(DeckCache(file).read())
        assertNull(DeckCache(file).forQuery(mine = false, query = ""))
    }

    @Test
    fun corruptAndOldVersionsReadAsAbsent() {
        file.writeText("{ nope")
        assertNull(DeckCache(file).read())
        file.writeText("""{"version":0,"savedAt":0,"decks":[]}""")
        assertNull(DeckCache(file).read())
    }
}
