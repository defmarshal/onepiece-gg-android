package gg.opgg.android.data

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One store's cached rows, as returned by `getuserdata` / `getbindercards`. */
@Serializable
data class CachedStoreData(
    val version: Int = STORE_CACHE_VERSION,
    /** When the binder list was captured. */
    val savedAt: Long = 0,
    /** Per-store capture times; `"0"` is the collection. */
    val storeSavedAt: Map<String, Long> = emptyMap(),
    /** Binder list as of [savedAt]; keeps the picker usable offline. */
    val binders: List<ApiBinder> = emptyList(),
    /** Keyed by binder id as a string; `"0"` is the collection. */
    val stores: Map<String, List<OwnedCard>> = emptyMap(),
)

private const val STORE_CACHE_VERSION = 2

/**
 * Disk cache for the collection and every binder's rows.
 *
 * The card database and the meta snapshot were cached, but the stores themselves
 * were not: offline, the grid died with a network error even though all the card
 * metadata was on the device. This keeps the last successful read per store plus
 * the binder list, written atomically and version-checked so a corrupt file
 * reads as absent instead of crashing a launch.
 *
 * Writes (online or not) also update this cache, so an edit followed by a kill
 * does not reopen on stale rows. Timestamps are per store: saving binder A must
 * not make binder B's older snapshot look fresh.
 */
class StoreCache(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    // encodeDefaults so `version` and the timestamps are actually written; with
    // the shared AppJson (which omits defaults) a file could not be versioned.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun read(): CachedStoreData? {
        if (!file.exists()) return null
        return runCatching {
            val data = json.decodeFromString(CachedStoreData.serializer(), file.readText())
            if (data.version != STORE_CACHE_VERSION) null else data
        }.getOrNull()
    }

    fun binders(): List<ApiBinder> = read()?.binders.orEmpty()

    fun store(binderId: Long): List<OwnedCard>? = read()?.stores?.get(binderId.toString())

    /** Every cached store's rows, for the read-only "All binders" aggregate. */
    fun allRows(): List<OwnedCard> = read()?.stores?.values?.flatten().orEmpty()

    fun storeSavedAt(binderId: Long): Long =
        read()?.storeSavedAt?.get(binderId.toString()) ?: 0L

    /** Newest of the per-store timestamps, for the "All binders" aggregate. */
    fun latestStoreSavedAt(): Long = read()?.storeSavedAt?.values?.maxOrNull() ?: 0L

    /** Keeps the previous stores and their timestamps; only the list changes. */
    fun saveBinders(binders: List<ApiBinder>) {
        if (binders.isEmpty()) return
        val current = read() ?: CachedStoreData()
        write(current.copy(savedAt = System.currentTimeMillis(), binders = binders))
    }

    fun saveStore(binderId: Long, rows: List<OwnedCard>) {
        val current = read() ?: CachedStoreData()
        val key = binderId.toString()
        write(
            current.copy(
                stores = current.stores + (key to rows),
                storeSavedAt = current.storeSavedAt + (key to System.currentTimeMillis()),
            ),
        )
    }

    private fun write(data: CachedStoreData) {
        runCatching {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(CachedStoreData.serializer(), data))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    private companion object {
        const val FILE_NAME = "opgg-stores.json"
    }
}
