package gg.opgg.android

import gg.opgg.android.data.MetaArchetype
import gg.opgg.android.data.MetaCache
import gg.opgg.android.data.MetaSnapshot
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The meta cache is what keeps the Meta screen and the related-cards index alive
 * offline. A corrupt or future-version file must read as absent rather than
 * crash the app or return a half-decoded snapshot.
 */
class MetaCacheTest {

    private lateinit var file: File

    @Before
    fun setUp() {
        file = File.createTempFile("opgg-meta", ".json")
        file.delete() // start from "no cache"
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun aWrittenSnapshotRoundTrips() {
        val cache = MetaCache(file)
        cache.write(
            MetaSnapshot(
                generatedAt = 123L,
                archetypes = listOf(MetaArchetype(id = 7, name = "Enel", rank = 1)),
            ),
        )
        val back = cache.read()
        assertEquals("Enel", back?.archetypes?.single()?.name)
        assertEquals(7L, back?.archetypes?.single()?.id)
        assertEquals(123L, back?.generatedAt)
    }

    @Test
    fun aMissingFileReadsAsAbsent() {
        assertNull(MetaCache(file).read())
    }

    @Test
    fun aCorruptFileReadsAsAbsent() {
        file.writeText("{ this is not json")
        assertNull(MetaCache(file).read())
    }

    @Test
    fun anOlderVersionIsIgnored() {
        file.writeText("""{"version":0,"savedAt":0,"snapshot":{"archetypes":[]}}""")
        assertNull(MetaCache(file).read())
    }
}
