package gg.opgg.android.ui

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Coil setup for card art.
 *
 * This exists because of a real bug: card images would sit blank and then
 * appear all at once, worst after a filter change or a fast scroll. Logcat gave
 * the cause away -
 *
 *     Long monitor contention with owner DefaultDispatcher-worker-1
 *       at coil.disk.DiskLruCache.initialize() ... for 796ms   waiters=7
 *
 * Coil opens its disk cache lazily, on the first request that needs it, and
 * holds a lock while it does. The first screenful of grid tiles is many requests
 * arriving at once, so they all pile onto that lock: nothing resolves, every
 * tile shows its blank `SurfaceRaised` background, and then the whole screen
 * pops in together once the lock clears. It looked like missing images rather
 * than a lock, which is why it was not obvious.
 *
 * Two fixes, and the second only works because of the first:
 *
 * 1. **[loader] is installed in `MainActivity.onCreate`**, before `setContent`,
 *    with an explicit cache directory. Configuring it up front means the disk
 *    cache is opened once, early, off the critical path, rather than being
 *    discovered mid-scroll.
 * 2. **[warm] is called the moment the card database arrives**, before the grid
 *    has drawn. It requests the first screenful of thumbnails at low priority,
 *    which both opens the disk cache and fills the memory cache, so the first
 *    real grid renders from memory.
 *
 * Without (1) there is no stable place to warm from, and without (2) the warm
 * would just be the grid's own requests arriving earlier - which is what
 * already happens, too late.
 */
object CardImages {

    /**
     * 25% of available heap for the memory cache, which is Coil's own default
     * but stated here so the disk cache directory and it are configured together.
     */
    fun loader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("card_images"))
                // The catalogue is 5,671 printings at 160-300 KB each: about
                // 1.2 GB of encoded art, which Coil cannot downscale before it
                // caches (the disk cache stores the response bytes). The old
                // 160 MB cap held roughly 600 of them, so once it filled every
                // tile was a miss and every write ran an eviction: scrolling
                // then went to the CDN continuously and a stalled request left
                // a permanently blank tile. Size the cache for the catalogue.
                .maxSizeBytes(1024L * 1024 * 1024)
                .build()
        }
        // Card art is immutable for a given printing id - a different picture is
        // a different id - but the CDN sends `max-age=86400`. Respecting that
        // made Coil treat every cached image as stale a day later and re-download
        // the whole grid.
        .respectCacheHeaders(false)
        .callFactory {
            OkHttpClient.Builder()
                // Coil's default is 5 requests per host. A grid asks for 9-12
                // images at once, so the extra queue just delayed the last row.
                .dispatcher(Dispatcher().apply {
                    maxRequests = 48
                    maxRequestsPerHost = 8
                })
                // A 250 KB image on mobile data can take longer than OkHttp's
                // 10 s default; a socket timeout here is a blank tile.
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
        // Crossfade is opt-in per request. A 150ms fade on every thumbnail in a
        // fast-scrolling grid restarts whenever a request is re-issued, which
        // reads as "the image is late" even when it is already in memory. The
        // one large image on the card page asks for it explicitly.
        .crossfade(false)
        .build()

    /**
     * Open the caches and pull the first screenful into memory.
     *
     * There is no `Priority` in Coil 2 - it was removed, and the only remaining
     * `Priority` symbols in the library are Android log levels - so these cannot
     * be deprioritised against the grid's own requests. That is fine: they are
     * small `INEXACT` decodes, and running them slightly before the grid needs
     * them is the entire point.
     *
     * Failures are ignored on purpose. A warm-up that throws is a warm-up that
     * must not crash the app, and a missing warm-up only costs the pop-in this
     * whole file exists to remove.
     */
    suspend fun warm(context: Context, loader: ImageLoader, urls: List<String>) {
        urls.take(FIRST_SCREEN)
            .filter { it.isNotBlank() }
            .forEach { url ->
                runCatching {
                    // Deliberately NOT `.dispose()`d: dispose() cancels the
                    // request, so the old version cancelled every warm-up it
                    // started and nothing was ever cached by it. The request
                    // releases itself when it finishes.
                    loader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            // Small: these are for populating the caches, not
                            // images anyone looks at closely.
                            .size(WARM_PX)
                            .precision(Precision.INEXACT)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    )
                }
            }
    }

    /** About one screenful of tiles at 8 across; deliberately generous. */
    private const val FIRST_SCREEN = 40
    private const val WARM_PX = 300
}

/**
 * Holds the one configured [ImageLoader] and installs it as Coil's singleton.
 *
 * A holder rather than a top-level `val` because the loader needs a `Context`,
 * and a top-level initialiser would either leak an Activity or need the
 * application context before the process is ready to give it.
 */
object CardImageLoaderHolder {

    @Volatile
    private var instance: ImageLoader? = null

    @Volatile
    private var appContext: Context? = null

    /** Idempotent, so a configuration change does not build a second loader. */
    fun install(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    instance = CardImages.loader(app)
                }
            }
        }
        Coil.setImageLoader(instance!!)
    }

    /**
     * The configured loader, building it on first use if [install] somehow has
     * not run. Never Coil's unconfigured default, because that is the case this
     * whole file exists to avoid.
     */
    fun get(): ImageLoader {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val app = appContext ?: error(
                "CardImageLoaderHolder.install() must be called before get()"
            )
            return CardImages.loader(app).also { instance = it }
        }
    }
}
