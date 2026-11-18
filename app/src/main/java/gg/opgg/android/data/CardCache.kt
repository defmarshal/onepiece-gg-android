package gg.opgg.android.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CardCacheEnvelope(
    /** Value to replay in If-Modified-Since. */
    val lastModified: String = "",
    val fetchedAt: Long = 0,
    /**
     * Cache format version.
     *
     * 1 stored only the deduped 3,063-card set, which lost the printing rows
     * entirely - so the printings, the per-printing artwork and the
     * printing->card index were all missing on every cold start after the
     * first. 2 stores the raw rows.
     *
     * A version-1 cache is still readable and still paints the card grid, so it
     * is used rather than thrown away; it just forces a refresh, because it
     * cannot supply the printings.
     */
    val version: Int = 1,
    val cards: List<Card> = emptyList(),
    val sets: List<CardSet> = emptyList(),
)

/**
 * On-disk cache for the card database.
 *
 * The raw `getcards` response is 5,230,704 bytes, re-downloaded on every cold
 * start. We store the **5,540 printing rows**, and replay `If-Modified-Since` so
 * an unchanged database costs a few hundred bytes and a 304 instead of a 5 MB
 * body.
 *
 * Storing the *deduped* 3,063-card set instead would save 45% of the file, and
 * it was what this used to do - which turned out to be a bad trade. Dedup keeps
 * the base row per card and discards the `_pN`/`_rN` rows, and they cannot be
 * reconstructed from what is left. Losing them cost, on every cold start after
 * the first:
 * - the Printings strip, which vanished entirely, so per-printing artwork was
 *   unreachable;
 * - `printingToNormal`, so meta-snapshot entries naming a printing failed to
 *   resolve and the related list showed "11 cards" above two tiles;
 * - `printingLookup`, so deck and collection entries keyed by a printing id
 *   lost their card.
 *
 * 4.7 MB of disk for a card game is a fair price for that. `applyCards`
 * re-derives the deduped set from these rows on load, exactly as it does for a
 * fresh download.
 */
class CardCache(private val context: Context) {

    private val file: File get() = File(context.filesDir, "opgg-cards.json")

    fun read(): CardCacheEnvelope? {
        if (!file.exists()) return null
        val text = file.readText()
        val meta = Regex("\"fetchedAt\":(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull()
        Log.i(TAG, "cache hit: ${file.length()} bytes, $meta")
        return runCatching {
            CacheJson.decodeFromString(CardCacheEnvelope.serializer(), text)
        }.onFailure { Log.w(TAG, "cache read failed, treating as absent", it) }.getOrNull()
    }

    /**
     * Whether a cache was written by a build that stored the printing rows.
     *
     * A cache that did not is still good enough to paint the card grid with, so
     * it is not discarded - but it cannot supply the printings, so the app
     * refreshes straight away rather than waiting for the staleness window.
     */
    fun isCurrentFormat(envelope: CardCacheEnvelope?): Boolean =
        envelope != null && envelope.version >= VERSION

    fun write(envelope: CardCacheEnvelope) {
        runCatching {
            val tmp = File(context.filesDir, "opgg-cards.json.tmp")
            tmp.writeText(CacheJson.encodeToString(CardCacheEnvelope.serializer(), envelope.copy(version = VERSION)))
            // Replace atomically so a kill mid-write cannot leave a truncated file.
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }.onSuccess {
            Log.i(TAG, "cache written: ${file.length()} bytes, ${envelope.cards.size} cards")
        }.onFailure { Log.e(TAG, "cache write failed", it) }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    private companion object {
        const val TAG = "OpggCache"

        /**
         * The format this build writes. Bumped when the stored contents change
         * shape; a cache written by an older build is read for painting but
         * forces a refresh.
         */
        const val VERSION = 2
    }
}

/**
 * The card DB is a plain array with no version wrapper, so we cannot require
 * unknown keys to be absent. Lenient, and we treat a cache we cannot parse the
 * same as a cache that does not exist.
 */
private val CacheJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
