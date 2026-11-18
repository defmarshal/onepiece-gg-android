package gg.opgg.android.data

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable

/**
 * Disk cache for the metagame snapshot.
 *
 * The card database has been cached for a long time, but the ~157 KB meta
 * snapshot was refetched on every launch and simply unavailable offline - which
 * also took the related-cards index down with it. This stores the decoded
 * snapshot (not the raw payload) behind a small versioned envelope, written
 * atomically so a kill mid-write cannot leave a truncated file.
 */
class MetaCache(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    @Serializable
    private data class Envelope(
        val version: Int = VERSION,
        val savedAt: Long = 0,
        val snapshot: MetaSnapshot? = null,
    )

    fun read(): MetaSnapshot? {
        if (!file.exists()) return null
        return runCatching {
            val envelope = AppJson.decodeFromString(Envelope.serializer(), file.readText())
            if (envelope.version != VERSION) null else envelope.snapshot
        }.getOrNull()
    }

    fun write(snapshot: MetaSnapshot) {
        runCatching {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(
                AppJson.encodeToString(
                    Envelope.serializer(),
                    Envelope(savedAt = System.currentTimeMillis(), snapshot = snapshot),
                ),
            )
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    private companion object {
        const val FILE_NAME = "opgg-meta.json"
        const val VERSION = 1
    }
}
