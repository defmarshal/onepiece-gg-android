package gg.opgg.android.data

import android.os.SystemClock
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json

class OpggApi(private val tokenProvider: () -> Pair<Long, String>?) {

    private companion object {
        /** The card database needs the long client default. */
        const val DEFAULT_TIMEOUT_MS = 5 * 60_000L

        /** Collection/binder reads are tiny; do not hang the grid for minutes. */
        const val QUICK_TIMEOUT_MS = 20_000L

        /** Meta is ~157 KB; generous, but not the five-minute card-download default. */
        const val META_TIMEOUT_MS = 60_000L

        /** Writes are tiny too; a stuck edit should fail, not spin. */
        const val WRITE_TIMEOUT_MS = 30_000L
    }

    @Volatile
    private var http: HttpClient = buildClient()

    /**
     * A dead keep-alive connection can outlive the request that discovered it:
     * Ktor's timeout cancels the coroutine, but the pooled socket can be handed
     * to the next request, which then times out too - and so on until the app is
     * restarted. Three failures in a row is not a slow link, so drop the whole
     * client (closing every pooled connection) and start clean.
     */
    private val consecutiveTimeouts = java.util.concurrent.atomic.AtomicInteger(0)

    private fun noteOutcome(timedOut: Boolean) {
        if (!timedOut) {
            consecutiveTimeouts.set(0)
            return
        }
        if (consecutiveTimeouts.incrementAndGet() >= 3) {
            consecutiveTimeouts.set(0)
            val old = http
            http = buildClient()
            runCatching { old.close() }
            Log.w("OpggHttp", "HTTP client rebuilt after repeated timeouts")
        }
    }

    private fun buildClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        engine {
            config {
                // The device this runs on has no global IPv6 route, and the
                // system resolver fails the AAAA query outright rather than
                // falling back. See PreferIpv4Dns.
                dns(PreferIpv4Dns)
                retryOnConnectionFailure(true)
                // See connectTimeoutMillis below: an address that blackholes
                // must fail fast enough for the next one to be tried.
                connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                // HTTP/2 pings find a connection that died silently (expired
                // NAT mapping, Wi-Fi power save). Without them OkHttp keeps
                // reusing it and every request pays the full timeout.
                pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
                explicitNulls = false
            })
        }
        install(HttpTimeout) {
            // The card database is 5,230,704 bytes. Measured from the phone, a
            // link to api.dotgg.gg can run as slow as ~4 KB/s, so 60 s was not
            // enough and the download simply timed out. Generous, because
            // caching means this is paid at most once an hour.
            requestTimeoutMillis = 5 * 60_000
            // Was 30 s, which is longer than the 20 s request timeout a quick
            // read uses - so a blackholed address (DNS hands back several
            // Cloudflare IPs, and one can stop answering while the app stays
            // open) swallowed the whole budget and the fallback address was
            // never tried. Every read then timed out until the app restarted.
            // Six seconds leaves room for two more addresses inside 20 s.
            connectTimeoutMillis = 6_000
            socketTimeoutMillis = 5 * 60_000
        }
    }

    private val browserHeaders = mapOf(
        HttpHeaders.UserAgent to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
        HttpHeaders.Referrer to "https://onepiece.gg/",
        HttpHeaders.Origin to "https://onepiece.gg",
        HttpHeaders.Accept to "application/json, text/plain, */*",
    )

    private suspend fun io.ktor.client.request.HttpRequestBuilder.auth() {
        browserHeaders.forEach { (k, v) -> header(k, v) }
        val creds = tokenProvider()
        if (creds != null) header("Dotgguserauth", "${creds.first}:${creds.second}")
    }

    /**
     * api.dotgg.gg answers unknown routes with HTTP 200 and the plain-text body
     * "Hacker! Go home!" instead of a 404, and also does so for requests without
     * browser-ish headers. Both look like a successful response, so guard here.
     */
    private fun assertNotBlocked(text: String) {
        val t = text.trim()
        if (t.startsWith("Hacker!") || t.startsWith("<")) {
            throw DotggException("Blocked by api.dotgg.gg (bot guard). The app needs a network path the server accepts.")
        }
    }

    private fun httpError(status: Int, url: String): DotggException = when (status) {
        // Cloudflare rate limiting. The deck list pages 30 at a time, so scrolling
        // fast can trip this; it clears on its own after a short pause.
        429 -> DotggException("Rate limited by api.dotgg.gg (HTTP 429). Wait a moment and try again.")
        403 -> DotggException("api.dotgg.gg refused the request (HTTP 403).")
        404 -> DotggException("Not found (HTTP 404): $url")
        else -> DotggException("HTTP $status from $url")
    }

    private suspend fun getText(url: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String {
        val started = SystemClock.elapsedRealtime()
        try {
            val r: HttpResponse = http.get(url) {
                auth()
                // Small API reads use a short timeout: the 5-minute default exists
                // for the multi-megabyte card database, and applying it here left the
                // collection grid on a skeleton for minutes when the link stalled.
                timeout {
                    requestTimeoutMillis = timeoutMs
                    socketTimeoutMillis = timeoutMs
                }
            }
            val text = r.bodyAsText()
            assertNotBlocked(text)
            if (!r.status.isSuccess()) throw httpError(r.status.value, url)
            noteOutcome(timedOut = false)
            return text
        } catch (e: Exception) {
            // A blank grid cannot tell a stall from a rejection. Leave a trace
            // so a "keeps loading" report can be told apart from a bad address.
            Log.w(
                "OpggHttp",
                "GET $url failed after ${SystemClock.elapsedRealtime() - started} ms: " +
                    "${e::class.simpleName}: ${e.message}"
            )
            noteOutcome(timedOut = with(NetworkStatus) { e.isTimeout() })
            throw e
        }
    }

    private suspend fun postJson(
        url: String,
        body: Any?,
        timeoutMs: Long = WRITE_TIMEOUT_MS,
    ): String {
        val started = SystemClock.elapsedRealtime()
        try {
            val r: HttpResponse = http.post(url) {
                auth()
                contentType(ContentType.Application.Json)
                if (body != null) setBody(body)
                timeout {
                    requestTimeoutMillis = timeoutMs
                    socketTimeoutMillis = timeoutMs
                }
            }
            val text = r.bodyAsText()
            assertNotBlocked(text)
            if (!r.status.isSuccess()) throw httpError(r.status.value, url)
            noteOutcome(timedOut = false)
            return text
        } catch (e: Exception) {
            Log.w(
                "OpggHttp",
                "POST $url failed after ${SystemClock.elapsedRealtime() - started} ms: " +
                    "${e::class.simpleName}: ${e.message}"
            )
            noteOutcome(timedOut = with(NetworkStatus) { e.isTimeout() })
            throw e
        }
    }

    private fun cgfw(action: String) = "$API_BASE/cgfw/$action?game=$GAME"

    /**
     * Conditional fetch helpers. The card database sends Last-Modified but no
     * ETag, so we replay If-Modified-Since: an unchanged database returns 304
     * with no body instead of 5.2 MB of JSON.
     *
     * Returns (null, lastModified) when the server says 304 Not Modified.
     */
    suspend fun getCardsIndexedIfChanged(
        lastModified: String?,
        onProgress: ((received: Long, total: Long) -> Unit)? = null,
    ): Pair<List<Card>?, String?> {
        val (text, lm) = getConditional("${cgfw("getcards")}&mode=indexed", lastModified, onProgress)
        if (text == null) return null to (lm ?: lastModified)
        return decodeIndexedCards(text) to lm
    }

    /** Unconditional columnar fetch, for manual refresh. */
    suspend fun getCardsIndexed(onProgress: ((received: Long, total: Long) -> Unit)? = null): List<Card> {
        val (text, _) = getConditional("${cgfw("getcards")}&mode=indexed", null, onProgress)
        return decodeIndexedCards(text ?: error("unexpected 304 on an unconditional fetch"))
    }

    /**
     * Zips each positional row against `names` **by name**, then hands the
     * result to Card's own serializer. Deliberately not index-based: the format
     * is self-describing, so reordering or adding a field upstream still works,
     * and Card's @SerialName mapping stays in one place.
     */
    private fun decodeIndexedCards(text: String): List<Card> {
        val indexed = AppJson.decodeFromString(IndexedCards.serializer(), text)
        if (indexed.names.isEmpty() || indexed.data.isEmpty()) return emptyList()
        val names = indexed.names
        val out = ArrayList<Card>(indexed.data.size)
        for (row in indexed.data) {
            // JsonObject is immutable, so build a mutable map and wrap it once.
            val map = HashMap<String, JsonElement>(names.size)
            for (i in names.indices) {
                map[names[i]] = row.getOrNull(i) ?: JsonNull
            }
            runCatching { AppJson.decodeFromJsonElement(Card.serializer(), JsonObject(map)) }
                .getOrNull()?.let(out::add)
        }
        return out
    }

    suspend fun getCardsIfChanged(
        lastModified: String?,
        onProgress: ((received: Long, total: Long) -> Unit)? = null,
    ): Pair<List<Card>?, String?> {
        val (text, lm) = getConditional(cgfw("getcards"), lastModified, onProgress)
        if (text == null) return null to (lm ?: lastModified)
        return AppJson.decodeFromString(ListSerializer(Card.serializer()), text) to lm
    }

    suspend fun getMetaSnapshot(format: String = "standard", window: String = "30d"): MetaSnapshot {
        val (text, _) = getConditional("${cgfw("getmetasnapshot")}&format=&window=", null, timeoutMs = META_TIMEOUT_MS)
        return AppJson.decodeFromString(MetaSnapshot.serializer(), text ?: "{}")
    }

    suspend fun getSetsIfChanged(lastModified: String?): Pair<List<CardSet>?, String?> {
        val (text, lm) = getConditional(cgfw("getsets"), lastModified, timeoutMs = QUICK_TIMEOUT_MS)
        if (text == null) return null to (lm ?: lastModified)
        return AppJson.decodeFromString(ListSerializer(CardSet.serializer()), text) to lm
    }

    private suspend fun getConditional(
        url: String,
        since: String?,
        onProgress: ((received: Long, total: Long) -> Unit)? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Pair<String?, String?> {
        val r: HttpResponse = http.get(url) {
            auth()
            if (!since.isNullOrBlank()) header(HttpHeaders.IfModifiedSince, since)
            if (onProgress != null) {
                onDownload { received, total -> onProgress(received, total ?: -1L) }
            }
            timeout {
                requestTimeoutMillis = timeoutMs
                socketTimeoutMillis = timeoutMs
            }
        }
        if (r.status == HttpStatusCode.NotModified) {
            // No body, and no resending of Last-Modified, so keep ours.
            return null to (r.headers[HttpHeaders.LastModified] ?: since)
        }
        val text = r.bodyAsText()
        assertNotBlocked(text)
        if (!r.status.isSuccess()) throw httpError(r.status.value, url)
        return text to r.headers[HttpHeaders.LastModified]
    }

    suspend fun getCards(): List<Card> {
        val raw = getText(cgfw("getcards"))
        return AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Card.serializer()), raw)
    }

    /** Exposed for the live-API unit test. */
    suspend fun getTextForTest(action: String): String = getText(cgfw(action))

    suspend fun getSets(): List<CardSet> {
        val raw = getText(cgfw("getsets"), QUICK_TIMEOUT_MS)
        return AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(CardSet.serializer()), raw)
    }

    /** The endpoint the site itself uses for the signed-in collection. */
    suspend fun getUserData(binderId: Int): UserDataResponse {
        val raw = getText("${cgfw("getuserdata")}&binder_id=$binderId", QUICK_TIMEOUT_MS)
        return AppJson.decodeFromString(UserDataResponse.serializer(), raw)
    }

    suspend fun listBinders(): List<ApiBinder> {
        val raw = getText(cgfw("listbinders"), QUICK_TIMEOUT_MS)
        val parsed = AppJson.decodeFromString(BinderListResponse.serializer(), raw)
        // A rejected session comes back as HTTP 200 with an error body. Decoding
        // it into an empty list made the app report "you have no binder" when
        // the truth was "your token is no longer valid", and the user was told
        // to go and create a binder they already had.
        if (parsed.error) throw DotggException(parsed.error_text ?: "Not authorized!")
        return parsed.binders.orEmpty()
    }

    /**
     * Builds the `rq` query object.
     *
     * `my` must be the numeric user id, not 1: the site reads `my` as a literal
     * `1` client-side, then replaces it with `window.DotGGUser` before sending.
     * Posting `my:1` is silently ignored and returns the public deck list.
     */
    internal fun buildDeckRequest(page: Int, myUserId: Long?, search: String? = null): String {
        val parts = ArrayList<String>(3)
        if (page > 1) parts += "\"page\":$page"
        if (myUserId != null && myUserId > 0) parts += "\"my\":$myUserId"
        // The site's own request shape nests the filters: getdecks.smartsrch is
        // the text search. Flat keys (q, search, name, ...) are silently
        // ignored, which is why deck search looked unsupported.
        val query = search?.trim().orEmpty()
        if (query.isNotEmpty()) {
            parts += "\"getdecks\":{\"smartsrch\":" +
                AppJson.encodeToString(String.serializer(), query) + "}"
        }
        // Joined, not concatenated: a trailing comma makes the whole object
        // invalid JSON, the server discards rq entirely and silently returns
        // page 1 - which looked like "Load more" doing nothing.
        return parts.joinToString(",", "{", "}")
    }

    /**
     * Public decks, or one account's decks, or a text search over them.
     *
     * `page` and `my` are confirmed to affect the result set, and
     * `getdecks.smartsrch` is the server-side search the site's deck browser
     * uses. The query is repeated on every page, so "Load more" keeps filtering.
     *
     * @param myUserId pass the signed-in user id to list only their own decks.
     * @param search text to match against deck name, leader and cards.
     */
    suspend fun getDecks(page: Int = 1, myUserId: Long? = null, search: String? = null): List<Deck> {
        val rq = buildDeckRequest(page, myUserId, search)
        val url = "${cgfw("getdecks")}&rq=${java.net.URLEncoder.encode(rq, "UTF-8")}"
        val raw = getText(url, QUICK_TIMEOUT_MS)
        return AppJson.decodeFromString(ListSerializer(Deck.serializer()), unwrapDeckList(raw))
    }

    suspend fun getDeck(slug: String): DeckDetail {
        val raw = getText("${cgfw("getdeck")}&slug=${java.net.URLEncoder.encode(slug, "UTF-8")}", QUICK_TIMEOUT_MS)
        return AppJson.decodeFromString(DeckDetail.serializer(), raw)
    }

    /**
     * Binder contents, in the same row shape as the collection.
     *
     * `binder_id = 0` is the default collection and is fetched through
     * `getuserdata` instead - `getbindercards` needs a real binder.
     */
    suspend fun getBinderContents(binderId: Int): List<OwnedCard> {
        val raw = getText("${cgfw("getbindercards")}&binder_id=$binderId", QUICK_TIMEOUT_MS)
        val parsed = AppJson.decodeFromString(BinderContentsResponse.serializer(), raw)
        if (parsed.error) throw DotggException(parsed.error_text ?: "Could not load binder")
        return parsed.collection.orEmpty()
    }

    /** Every binder's contents aggregated by printing (`binder_id=all`). */
    suspend fun getAllBinderContents(): List<OwnedCard> {
        val raw = getText("${cgfw("getbindercards")}&binder_id=all", QUICK_TIMEOUT_MS)
        val parsed = AppJson.decodeFromString(BinderContentsResponse.serializer(), raw)
        if (parsed.error) throw DotggException(parsed.error_text ?: "Could not load binders")
        return parsed.collection.orEmpty()
    }

    suspend fun createBinder(name: String): Long {
        val raw = postJson(cgfw("createbinder"), CreateBinderRequest(name = name))
        val parsed = AppJson.decodeFromString(CreateBinderResponse.serializer(), raw)
        if (parsed.error) throw DotggException(parsed.error_text ?: "Could not create binder")
        return parsed.id ?: throw DotggException(parsed.error_text ?: "onepiece.gg did not return a binder id")
    }

    suspend fun renameBinder(binderId: Long, name: String) {
        val raw = postJson(cgfw("renamebinder"), RenameBinderRequest(binderId, name))
        val parsed = AppJson.decodeFromString(BinderActionResponse.serializer(), raw)
        if (parsed.error) throw DotggException(parsed.error_text ?: "Could not rename binder")
    }

    suspend fun deleteBinder(binderId: Long) {
        val raw = postJson(cgfw("deletebinder"), BinderIdRequest(binderId))
        val parsed = AppJson.decodeFromString(BinderActionResponse.serializer(), raw)
        if (parsed.error) throw DotggException(parsed.error_text ?: "Could not delete binder")
    }

    suspend fun setBinderColor(binderId: Long, color: String) {
        val raw = postJson(cgfw("setbindercolor"), BinderColorRequest(binderId, color))
        val parsed = AppJson.decodeFromString(BinderActionResponse.serializer(), raw)
        if (parsed.error) throw DotggException(parsed.error_text ?: "Could not update colour")
    }

    /** `order` is the full list of binder ids; sort becomes the list index. */
    suspend fun reorderBinders(order: List<Long>) {
        val raw = postJson(cgfw("reorderbinder"), ReorderBindersRequest(order))
        val parsed = AppJson.decodeFromString(BinderActionResponse.serializer(), raw)
        if (parsed.error) throw DotggException(parsed.error_text ?: "Could not reorder binders")
    }

    suspend fun setBinderPublic(binderId: Long, public: Boolean) {
        val raw = postJson(cgfw("setbinderpublic"), BinderPublicRequest(binderId, if (public) 1 else 0))
        val parsed = AppJson.decodeFromString(BinderActionResponse.serializer(), raw)
        if (parsed.error) throw DotggException(parsed.error_text ?: "Could not update sharing")
    }

    /** `parentId` 0 moves the binder back to the top level. */
    suspend fun setBinderParent(binderId: Long, parentId: Long) {
        val raw = postJson(cgfw("setbinderparent"), BinderParentRequest(binderId, parentId))
        val parsed = AppJson.decodeFromString(BinderActionResponse.serializer(), raw)
        if (parsed.error) throw DotggException(parsed.error_text ?: "Could not move binder")
    }

    /**
     * Moves a card's whole row between stores. Verified against the live API:
     * `moved:1, copies:3` for a row holding three copies, and binder `0` means
     * the collection in both directions.
     */
    suspend fun moveCards(fromBinder: Long, toBinder: Long, cards: List<String>): MoveCardsResponse {
        val raw = postJson(cgfw("movecards"), MoveCardsRequest(fromBinder, toBinder, cards))
        val parsed = AppJson.decodeFromString(MoveCardsResponse.serializer(), raw)
        if (parsed.error) throw DotggException(parsed.error_text ?: "Could not move card")
        return parsed
    }

    /**
     * Adds or sets one card in the default (binder-less) collection.
     *
     * `savebindercard` is the wrong endpoint for the default collection: it
     * requires a real binder id and answers
     * `{"error":true,"error_text":"Binder not found."}` for binder 0, which most
     * accounts do not have. `savebatchcollection` takes no binder id, is what the
     * website itself calls for adding a card, and is additive - verified by
     * adding two cards to an empty collection and confirming both persisted.
     */
    /**
     * @param wish wishlist count, sent alongside the owned count exactly as the
     * website's own collection page does. Must be null for plain owned edits,
     * which never carry a wish field.
     */
    suspend fun saveCollectionCard(card: String, type: String, count: Int, wish: Int? = null): String {
        val req = SaveBatchCollectionRequest(
            cards = listOf(CollectionCardEntry(card = card, type = type, count = count, wish = wish)),
            source = "manual",
        )
        val raw = postJson(cgfw("savebatchcollection"), req)
        val result = runCatching {
            AppJson.decodeFromString(SaveBatchCollectionResult.serializer(), raw)
        }.getOrNull()
        if (result != null) {
            if (result.error) throw DotggException(result.error_text ?: "Could not save card")
            // error:false with a per-card error is still a failure for that card.
            result.errors?.firstOrNull { it.isNotBlank() }?.let {
                throw DotggException("Could not save card: $it")
            }
            if (result.processed != null && result.processed == 0) {
                throw DotggException("onepiece.gg did not accept that card")
            }
        }
        return raw
    }

    /**
     * count is the desired absolute total. The site sends type="standard" for
     * non-foil and "foil" for foil -- "normal" is silently accepted as a no-op.
     */
    suspend fun saveBinderCard(cardId: String, type: String, count: Int, binderId: Int): String {
        val req = SaveBinderCardRequest(card = cardId, type = type, count = count, binder_id = binderId)
        val raw = postJson(cgfw("savebindercard"), req)
        runCatching { AppJson.decodeFromString(ApiError.serializer(), raw) }.getOrNull()?.let {
            if (it.error) throw DotggException(it.error_text ?: "Could not save card")
        }
        return raw
    }
}

internal val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * The deck endpoint double-encodes an empty result: a search with no matches
 * answers with the JSON *string* `"[]"` rather than an array, so decoding it as
 * `List<Deck>` failed and "no results" looked like a broken request. Unwrap the
 * extra layer; everything else is passed through untouched.
 */
internal fun unwrapDeckList(raw: String): String {
    val text = raw.trim()
    if (!text.startsWith("\"")) return text
    return runCatching { AppJson.decodeFromString(String.serializer(), text) }.getOrDefault(text)
}

class DotggException(message: String) : Exception(message)
