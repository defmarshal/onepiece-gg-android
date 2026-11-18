package gg.opgg.android.data

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The last public/own deck page set, for the Decks tab offline.
 *
 * Local decks already live on disk; the *public* list did not, so the tab died
 * with a network error while the rest of the app worked. The snapshot is keyed
 * by `(mine, query)` so an offline search cannot show unrelated decks.
 */
@Serializable
data class CachedDeckList(
    val version: Int = DECK_CACHE_VERSION,
    val savedAt: Long = 0,
    val mine: Boolean = false,
    val query: String = "",
    val page: Int = 0,
    val decks: List<Deck> = emptyList(),
)

private const val DECK_CACHE_VERSION = 1

class DeckCache(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    // encodeDefaults writes `version` and `savedAt`; AppJson omits defaults.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun read(): CachedDeckList? {
        if (!file.exists()) return null
        return runCatching {
            val data = json.decodeFromString(CachedDeckList.serializer(), file.readText())
            if (data.version != DECK_CACHE_VERSION) null else data
        }.getOrNull()
    }

    /** The snapshot for this list, or null when it was a different search. */
    fun forQuery(mine: Boolean, query: String): CachedDeckList? =
        read()?.takeIf { it.mine == mine && it.query == query && it.decks.isNotEmpty() }

    fun write(mine: Boolean, query: String, page: Int, decks: List<Deck>) {
        if (decks.isEmpty()) return
        runCatching {
            val data = CachedDeckList(
                savedAt = System.currentTimeMillis(),
                mine = mine,
                query = query,
                page = page,
                decks = decks,
            )
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(CachedDeckList.serializer(), data))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    private companion object {
        const val FILE_NAME = "opgg-decklist.json"
    }
}
