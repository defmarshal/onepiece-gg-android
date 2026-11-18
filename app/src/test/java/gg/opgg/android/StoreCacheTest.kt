package gg.opgg.android

import gg.opgg.android.data.ApiBinder
import gg.opgg.android.data.OwnedCard
import gg.opgg.android.data.StoreCache
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Store cache: the last good read per binder, plus the binder list, so the
 * Collection and binder views open offline instead of showing a network error.
 * A corrupt or future-version file must read as absent, never crash a launch.
 */
class StoreCacheTest {

    private lateinit var file: File

    @Before
    fun setUp() {
        file = File.createTempFile("opgg-stores", ".json")
        file.delete() // start from "no cache"
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun aWrittenStoreRoundTrips() {
        val cache = StoreCache(file)
        cache.saveBinders(listOf(ApiBinder(id = 10, name = "Yamato")))
        cache.saveStore(
            10,
            listOf(OwnedCard(card = "OP01-001", standard = "2", foil = "1", wish = "3")),
        )

        assertEquals("Yamato", cache.binders().single().name)
        val row = cache.store(10)!!.single()
        assertEquals("OP01-001", row.card)
        assertEquals("2", row.standard)
        assertEquals("1", row.foil)
        assertEquals("3", row.wish)
        assertTrue("the store's own timestamp is recorded", cache.storeSavedAt(10) > 0)
    }

    @Test
    fun timestampsArePerStore() {
        val cache = StoreCache(file)
        cache.saveStore(0, listOf(OwnedCard(card = "A")))
        val first = cache.storeSavedAt(0)
        Thread.sleep(5)
        cache.saveStore(5, listOf(OwnedCard(card = "B")))
        assertTrue("the second store is newer", cache.storeSavedAt(5) >= first)
        assertEquals("earlier store keeps its time", first, cache.storeSavedAt(0))
        assertEquals(cache.storeSavedAt(5), cache.latestStoreSavedAt())
    }

    @Test
    fun allRowsUnionsEveryCachedStore() {
        val cache = StoreCache(file)
        cache.saveStore(0, listOf(OwnedCard(card = "A")))
        cache.saveStore(5, listOf(OwnedCard(card = "B"), OwnedCard(card = "C")))
        assertEquals(setOf("A", "B", "C"), cache.allRows().mapNotNull { it.card }.toSet())
    }

    @Test
    fun savingOneStoreKeepsTheOthers() {
        val cache = StoreCache(file)
        cache.saveStore(0, listOf(OwnedCard(card = "A")))
        cache.saveStore(5, listOf(OwnedCard(card = "B")))
        assertEquals("A", cache.store(0)?.single()?.card)
        assertEquals("B", cache.store(5)?.single()?.card)
    }

    @Test
    fun bindersSurviveAStoreWrite() {
        val cache = StoreCache(file)
        cache.saveBinders(listOf(ApiBinder(id = 1, name = "Supernova")))
        cache.saveStore(1, emptyList())
        assertEquals(1, cache.binders().size)
        assertEquals(0, cache.store(1)?.size)
    }

    @Test
    fun anEmptyBinderListDoesNotWipeTheCache() {
        val cache = StoreCache(file)
        cache.saveBinders(listOf(ApiBinder(id = 1)))
        cache.saveBinders(emptyList())
        assertEquals(1, cache.binders().size)
    }

    @Test
    fun aMissingFileReadsAsAbsent() {
        assertNull(StoreCache(file).read())
        assertNull(StoreCache(file).store(0))
        assertEquals(0, StoreCache(file).binders().size)
    }

    @Test
    fun aCorruptFileReadsAsAbsent() {
        file.writeText("{ this is not json")
        assertNull(StoreCache(file).read())
    }

    @Test
    fun anOlderVersionIsIgnored() {
        file.writeText("""{"version":0,"savedAt":0,"binders":[],"stores":{}}""")
        assertNull(StoreCache(file).read())
    }

    @Test
    fun anUnknownStoreIsNull() {
        assertNull(StoreCache(file).store(99))
    }
}
