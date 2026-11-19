package gg.opgg.android.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
private data class DeckFile(
    /** Bumped when the shape changes so old files migrate instead of vanishing. */
    val schema: Int = 1,
    val decks: List<LocalDeck> = emptyList(),
)

/**
 * On-device deck storage.
 *
 * The device is the source of truth. A deck can be published to onepiece.gg
 * from the builder menu (`savedeck`, which keys on the stored `siteDeckname`),
 * but its local copy is never overwritten by the site.
 *
 * Stored unencrypted, like the existing session token, so decks survive a
 * reinstall only if they have been saved to the site or exported.
 */
class LocalDeckStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, "opgg-decks.json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun read(): List<LocalDeck> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            val parsed = json.decodeFromString(DeckFile.serializer(), file.readText())
            migrate(parsed)
        }.onFailure {
            Log.w(TAG, "could not read ${file.name}, treating as empty", it)
        }.getOrNull()?.decks.orEmpty()
    }

    suspend fun write(decks: List<LocalDeck>) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(context.filesDir, "opgg-decks.json.tmp")
            tmp.writeText(json.encodeToString(DeckFile.serializer(), DeckFile(decks = decks)))
            // Atomic-ish: a kill mid-write must not leave a truncated file.
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
            Log.i(TAG, "wrote ${decks.size} local deck(s), ${file.length()} bytes")
        }.onFailure { Log.e(TAG, "write failed", it) }
        Unit
    }

    private fun migrate(parsed: DeckFile): DeckFile = when {
        parsed.schema >= SCHEMA -> parsed
        else -> parsed.copy(schema = SCHEMA)
    }

    fun newId(): String = "d" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(6)

    private companion object {
        const val TAG = "OpggDecks"
        const val SCHEMA = 1
    }
}
