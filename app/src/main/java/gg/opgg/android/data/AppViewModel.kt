package gg.opgg.android.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

data class Owned(val normal: Int = 0, val foil: Int = 0, val wish: Int = 0, val wishFoil: Int = 0) {
    val total get() = normal + foil
    val wishTotal get() = wish + wishFoil
    /** Copies still wanted; the wishlist view lists cards with a positive need. */
    val need get() = (wishTotal - total).coerceAtLeast(0)
}

/** One row of the collection, collapsed across every printing of the card. */
data class OwnedGroup(
    val normal: String,
    val representative: Card,
    val total: Int,
    val standard: Int,
    val foil: Int,
    /** Wishlist counts, summed across the card's printings. */
    val wish: Int = 0,
    val wishFoil: Int = 0,
) {
    val wishTotal get() = wish + wishFoil
    /** Copies still wanted: wishes minus copies already owned. */
    val need get() = (wishTotal - total).coerceAtLeast(0)
}

/** One resolved line of a deck list, joined to the card database. */
data class DeckEntry(
    val printingId: String,
    val normalId: String,
    val count: Int,
    val card: Card?,
    val owned: Int,
)

data class UiState(
    val loadingCards: Boolean = false,
    /** 0..1 while the 3.1 MB card database downloads; null when not downloading. */
    val cardsProgress: Float? = null,
    val cardsError: String? = null,
    val cards: List<Card> = emptyList(),
    /** id_normal -> every printing (base, _p1, _r1, ...), for ownership collapsing. */
    val printingsByNormal: Map<String, List<Card>> = emptyMap(),
    /**
     * Exact printing id -> id_normal, taken from the server's own id_normal
     * field. Beats stripping a "_p1"/"_r1" suffix by regex: the server is the
     * authority on which printings collapse together.
     */
    val printingToNormal: Map<String, String> = emptyMap(),
    val sets: List<CardSet> = emptyList(),
    val query: String = "",
    val parsed: ParsedQuery = ParsedQuery(),
    val sort: SortSpec = SortSpec(),
    /** Number of cards covered by the meta related index; changes when it loads. */
    val relatedCoverage: Int = 0,
    /** The metagame snapshot, kept for the meta screen. ~157 KB, 30-day window. */
    val metaSnapshot: MetaSnapshot? = null,
    val metaLoading: Boolean = false,
    val metaError: String? = null,
    /**
     * Which meta archetype is expanded, or null.
     *
     * Held here rather than in a `remember` inside the list because opening a card
     * page swaps the whole metagame screen out of composition, and a `remember`
     * would come back collapsed. That is the same trap the editor's flags and the
     * filter chips fell into.
     */
    val metaExpandedArchetype: String? = null,
    /**
     * Pre-filtered result set. Stored rather than computed in a getter: the
     * getter ran a 3,063-card scan on every access, and it is read at least
     * twice per recomposition (title bar and grid).
     */
    val visible: List<Card> = emptyList(),
    val session: Session? = null,
    /** Keyed by PRINTING id, exactly as the collection endpoint returns it. */
    val owned: Map<String, Owned> = emptyMap(),
    /**
     * The same counts keyed by canonical id_normal.
     *
     * Necessary because the card list shows the *base* printing of every card
     * while the collection records whichever printing you actually own. Looking
     * up `owned["EB01-015"]` misses a holding of `EB01-015_r1`, which hid the
     * owned badge and let the detail sheet create a duplicate row for a card
     * already owned.
     */
    val ownedByNormal: Map<String, Owned> = emptyMap(),
    /**
     * Contents of the selected binder, keyed by printing id.
     *
     * A binder is a separate store: [owned] is always the collection, so the
     * Cards tab's badges and the wishlist keep meaning "what I own", while the
     * Collection tab can show a binder without rewriting those facts.
     */
    val binderOwned: Map<String, Owned> = emptyMap(),
    val binderOwnedByNormal: Map<String, Owned> = emptyMap(),
    /**
     * False once the API has told us it cannot accept collection writes for this
     * account. The +/- controls disable instead of pretending to work.
     */
    val collectionWritable: Boolean = true,
    /**
     * The last network call failed because there was no network.
     *
     * Distinct from "the server said no": onepiece.gg answers a dead token with
     * HTTP 200, so a network failure is the only unambiguous signal that the
     * device is offline, and it is worth saying so rather than printing a DNS
     * exception.
     */
    val offline: Boolean = false,
    /** User dismissed the "writes rejected" banner but the buttons stay off. */
    val dismissCollectionWarning: Boolean = false,
    val binderId: Long = 0,
    val binders: List<ApiBinder> = emptyList(),
    val serverUserId: Long? = null,
    /**
     * The server did not recognise the stored session.
     *
     * The site answers a rejected token with HTTP 200 and an empty result, so
     * this had to be worked out rather than read off a status code: the server
     * reports a user id of 0 while we hold a real one, and `listbinders` answers
     * "Not authorized!".
     */
    val sessionRejected: Boolean = false,
    val binderLoading: Boolean = false,
    val binderError: String? = null,
    /**
     * Non-null when the grid is showing rows restored from [StoreCache] because
     * the live read failed. Holds the timestamp of that snapshot.
     */
    val storeCacheAt: Long? = null,
    val decks: List<Deck> = emptyList(),
    /** True when the list is the signed-in account's own decks. */
    val decksMine: Boolean = false,
    val decksLoading: Boolean = false,
    val decksError: String? = null,
    val decksPage: Int = 0,
    val decksExhausted: Boolean = false,
    /** Server-side deck search (`getdecks.smartsrch`), applied on every page load. */
    val deckQuery: String = "",
    /** Timestamp of a cached deck list being shown after a failed read. */
    val decksCacheAt: Long? = null,
    val deckFilter: String = "",
    /** Richer per-deck payload from getdeck; the list row alone lacks description. */
    val deckDetail: DeckDetail? = null,
    val deckDetailLoading: Boolean = false,
    val deckDetailError: String? = null,
    /** Decks authored on this device. Not synced to onepiece.gg. */
    val localDecks: List<LocalDeck> = emptyList(),
    /**
     * Editor view state, held here rather than in the composable.
     *
     * Kept in the ViewModel because opening a card pushes a new screen, which
     * removes the editor from composition and discarded any plain `remember` -
     * so the Owned only / In deck / Leader filters silently reset on the way
     * back.
     */
    val editorLeadersOnly: Boolean = false,
    val editorOwnedOnly: Boolean = false,
    val editorInDeckOnly: Boolean = false,
    val editorSearchOpen: Boolean = false,
    /** The shared SortSheet. Filters use the Cards-tab sheet via [onOpenFilters]. */
    val editorSortOpen: Boolean = false,
    /**
     * The editor's own search, deliberately separate from [query]: typing here
     * must not touch the Cards tab's query and vice versa, and the editor
     * searches the full pool rather than the Cards tab's filtered [visible].
     */
    val editorQuery: String = "",
    val editorParsed: ParsedQuery = ParsedQuery(),
    /**
     * Deck analytics panel. Kept here for the same reason as the flags above: a
     * plain `remember` in the editor is discarded when a card page is pushed, so
     * the panel would silently collapse on the way back.
     */
    val editorAnalyticsExpanded: Boolean = false,
    /** Rule violations are collapsed by default; one per card can bury the grid. */
    val editorIssuesExpanded: Boolean = false,
    /** Which card pool the editor validates against. */
    val regulationMode: RegulationMode = RegulationMode.STANDARD,
    val message: String? = null,
) {
    val ownedCount: Int get() = owned.values.sumOf { it.total }

    /**
     * Resolves a deck's printing ids against the card database, using the exact
     * printing->canonical index. Also reports how many of each the signed-in
     * user owns, summed across the card's printings.
     */
    fun resolveEntries(deck: Map<String, String>): List<DeckEntry> =
        deck.mapNotNull { (printingId, rawCount) ->
            val count = rawCount.toIntOrNull() ?: return@mapNotNull null
            val normal = printingToNormal[printingId] ?: normalOfSuffix(printingId)
            val group = printingsByNormal[normal]
            val card = group?.firstOrNull { it.id == printingId } ?: group?.firstOrNull()
            val ownedCount = (group ?: emptyList()).sumOf { c ->
                val o = c.id?.let { owned[it] } ?: return@sumOf 0
                o.normal + o.foil
            }
            DeckEntry(printingId, normal, count, card, ownedCount)
        }.sortedWith(compareBy({ it.card?.cardType ?: "" }, { it.normalId }))



    /**
     * Collection rows: one per card the account actually owns, with reprint
     * counts summed. Cards in `owned` that no longer exist in the card database
     * are dropped so the binder never lists something unbuyable.
     *
     * Rows held only as wishes are excluded - they belong to [wishGroups] - so
     * an Owned view never shows a tile with a zero count.
     */
    val ownedGroups: List<OwnedGroup>
        get() = groupsFor(owned)

    /** The same rows for the selected binder, read from [binderOwned]. */
    val binderGroups: List<OwnedGroup>
        get() = groupsFor(binderOwned)

    /**
     * The same collapse, filtered to cards that are still short of copies.
     *
     * A wish is a target count, not a flag: wishing 4 while owning 2 leaves a
     * need of 2, and once you own 4 the card drops off - which is what "if I
     * already own it, it should not be on my wishlist" means in practice.
     */
    val wishGroups: List<OwnedGroup>
        get() = groupsFor(owned, wishOnly = true)

    /** Copies still wanted across the whole wishlist. */
    val wishCount: Int get() = wishGroups.sumOf { it.need }

    /** Copies in the store the Collection tab is showing, for the header count. */
    val activeStoreCount: Int get() =
        if (binderId == 0L) owned.values.sumOf { it.total }
        else binderOwned.values.sumOf { it.total }

    private fun groupsFor(
        source: Map<String, Owned>,
        wishOnly: Boolean = false,
    ): List<OwnedGroup> =
        groupTotals(source).mapNotNull { (normal, counts) ->
            if (wishOnly) {
                if (counts.wishTotal <= counts.total) return@mapNotNull null
            } else if (counts.total == 0) {
                return@mapNotNull null
            }
            printingsByNormal[normal] ?: return@mapNotNull null
            OwnedGroup(
                normal = normal,
                // Show the printing the account actually holds (or wishes for),
                // so a holding/ wish on a reprint displays its own art.
                representative = representativeOf(normal, source, preferWish = wishOnly),
                total = counts.normal + counts.foil,
                standard = counts.normal,
                foil = counts.foil,
                wish = counts.wish,
                wishFoil = counts.wishFoil,
            )
        }.sortedBy { it.normal }

    private fun groupTotals(source: Map<String, Owned>): Map<String, Owned> {
        val totals = HashMap<String, Owned>()
        for ((printingId, counts) in source) {
            if (counts.total == 0 && counts.wishTotal == 0) continue
            val normal = printingToNormal[printingId] ?: normalOfSuffix(printingId)
            printingsByNormal[normal] ?: continue
            val prev = totals[normal] ?: Owned()
            totals[normal] = prev.copy(
                normal = prev.normal + counts.normal,
                foil = prev.foil + counts.foil,
                wish = prev.wish + counts.wish,
                wishFoil = prev.wishFoil + counts.wishFoil,
            )
        }
        return totals
    }

    /**
     * Which printing to show for a collapsed card. Owned view prefers one the
     * account holds; wishlist view prefers one the account wishes for.
     */
    private fun representativeOf(
        normal: String,
        source: Map<String, Owned>,
        preferWish: Boolean,
    ): Card {
        val group = printingsByNormal[normal] ?: throw IllegalStateException("empty group")
        val held = group.firstOrNull { c ->
            val o = c.id?.let { source[it] } ?: return@firstOrNull false
            if (preferWish) o.wishTotal > 0 else o.total > 0
        }
        return held ?: group.first()
    }
}

/**
 * Fallback only, for a collection row whose printing is missing from the card
 * database. Both suffix families exist in the real data: 2,012 "_pN" and
 * 465 "_rN" reprints across the 5,540 rows.
 */
private val printingSuffix = Regex("^(.*)_[pr]\\d+$")

private fun normalOfSuffix(id: String): String =
    printingSuffix.find(id)?.groupValues?.get(1) ?: id

/**
 * Sums per-printing collection counts into per-card counts.
 *
 * The collection endpoint keys rows by printing id ("EB01-015_r1") while the
 * card list only ever displays the base printing ("EB01-015"). Without this
 * collapse, cards held solely as reprints show no owned badge, and editing one
 * writes a second row for a card already owned.
 */
internal fun collapseOwnedCounts(
    owned: Map<String, Owned>,
    printingToNormal: Map<String, String>,
): Map<String, Owned> {
    val out = HashMap<String, Owned>(owned.size)
    for ((printingId, counts) in owned) {
        // Wishlist counts keep the row alive too: a card the user wishes for
        // without owning must still collapse into the per-card map, or the
        // wishlist view would never see it.
        if (counts.total == 0 && counts.wishTotal == 0) continue
        val normal = printingToNormal[printingId] ?: normalOfSuffix(printingId)
        val prev = out[normal] ?: Owned()
        out[normal] = prev.copy(
            normal = prev.normal + counts.normal,
            foil = prev.foil + counts.foil,
            wish = prev.wish + counts.wish,
            wishFoil = prev.wishFoil + counts.wishFoil,
        )
    }
    return out
}

/**
 * Turns raw collection/binder rows into per-printing counts, summing duplicates.
 *
 * Duplicates now occur in the All-binders union: the collection and a binder can
 * both hold the same printing, and the read-only aggregate must show the total,
 * not whichever row was parsed last.
 */
internal fun mergeOwnedRows(rows: List<OwnedCard>): Map<String, Owned> {
    val out = HashMap<String, Owned>()
    for (row in rows) {
        val id = row.card ?: continue
        val prev = out[id] ?: Owned()
        out[id] = Owned(
            normal = prev.normal + (row.standard?.toIntOrNull() ?: 0),
            foil = prev.foil + (row.foil?.toIntOrNull() ?: 0),
            wish = prev.wish + (row.wish?.toIntOrNull() ?: 0),
            wishFoil = prev.wishFoil + (row.wish_foil?.toIntOrNull() ?: 0),
        )
    }
    return out
}

class AppViewModel(
    private val api: OpggApi,
    private val sessions: SessionStore,
    private val cache: CardCache,
    private val prefs: PrefsStore,
    private val appContext: Context,
) : ViewModel() {

    /**
     * Monotonic id for binder/collection loads.
     *
     * Switching stores quickly (or tapping Retry while a read is still in
     * flight) could otherwise let an older request finish last and overwrite the
     * newer screen - or resurrect a loading flag/error that no longer applies.
     */
    private var binderRequestId = 0L

    /** Same idea for the deck list: a new search must not be appended to by the
     * previous one's late response. */
    private var deckRequestId = 0L

    /** Known values per filter field, harvested once the cards are loaded, so the
     * parser can tell a complete value (c:red) from an unfinished one
     * (
arity:sp card). */
    private var vocab: Map<QField, Set<String>> = emptyMap()
    private var cardNames: List<String> = emptyList()

    /**
     * Related-card index, built from the meta snapshot. Optional: the snapshot
     * only covers the staples, and if the fetch fails the UI falls back to crew.
     */
    @Volatile
    private var relatedIndex: RelatedIndex? = null
    /** Snapshot persisted between launches so Meta/related survive offline. */
    private val metaCache = MetaCache(appContext)

    /** Last successful rows per store, so binder views open offline. */
    private val storeCache = StoreCache(appContext)

    /** Last public/own deck list, so the Decks tab opens offline. */
    private val deckCache = DeckCache(appContext)

    @Volatile
    private var metaArchetypeCount: Int = 0

    fun relatedFor(card: Card, limit: Int = 6): List<RelatedCard> =
        relatedIndex?.related(card.key, _state.value.cards, limit).orEmpty()

    /** Every related card, for the full-list screen. */
    fun relatedAll(card: Card): List<RelatedCard> =
        relatedIndex?.related(card.key, _state.value.cards, Int.MAX_VALUE).orEmpty()

    /**
     * How many related cards there are in total.
     *
     * Not `relatedFor(card).size`: that list is capped at 6 for the preview, so
     * its size is what is *shown* rather than what exists.
     */
    fun relatedCount(card: Card): Int =
        relatedIndex?.relatedCount(card.key, _state.value.cards) ?: 0

    fun relatedBasis(card: Card): RelatedCard.Basis =
        relatedFor(card, 1).firstOrNull()?.basis ?: RelatedCard.Basis.CREW

    fun relatedArchetypeCount(card: Card): Int =
        relatedIndex?.archetypeCount(card.key) ?: 0

    fun relatedInclusionRate(card: Card): Double? =
        relatedIndex?.inclusionRate(card.key)

    fun metaCoverage(): Int = metaArchetypeCount

    private fun loadRelatedIndex() {
        viewModelScope.launch {
            // Serve the saved snapshot first. Offline this is the only source;
            // online it keeps the Meta screen populated while the refresh runs.
            if (_state.value.metaSnapshot == null) {
                val cached = withContext(Dispatchers.IO) { metaCache.read() }
                if (cached != null && cached.archetypes.isNotEmpty()) {
                    applyMetaSnapshot(cached)
                }
            }
            try {
                val snapshot = api.getMetaSnapshot()
                if (snapshot.archetypes.isEmpty()) {
                    android.util.Log.w("OpggRelated", "meta snapshot had 0 archetypes - related stays empty")
                    return@launch
                }
                // If the fetched snapshot is the same generation as the cached
                // one, the index is already built; skip a second build.
                val current = _state.value.metaSnapshot
                if (current?.generatedAt != null &&
                    current.generatedAt == snapshot.generatedAt &&
                    relatedIndex != null
                ) {
                    _state.update { it.copy(metaLoading = false, metaError = null) }
                    return@launch
                }
                withContext(Dispatchers.IO) { metaCache.write(snapshot) }
                applyMetaSnapshot(snapshot)
            } catch (e: Exception) {
                // Non-fatal: the crew fallback still works without the snapshot,
                // and cached meta must not be replaced by an error box.
                val hasSnapshot = _state.value.metaSnapshot != null
                _state.update {
                    it.copy(
                        metaLoading = false,
                        metaError = if (hasSnapshot) {
                            null
                        } else {
                            NetworkStatus.friendlyMessage(e) ?: (e.message ?: "Meta unavailable")
                        },
                    )
                }
                android.util.Log.w(
                    "OpggRelated",
                    "meta snapshot unavailable: " + e.stackTraceToString().take(600),
                )
            }
        }
    }

    /** Publishes a snapshot and rebuilds the related index from it. */
    private suspend fun applyMetaSnapshot(snapshot: MetaSnapshot) {
        // Kept in state as well as indexed: the meta screen needs the archetype
        // detail, and re-fetching a ~157 KB snapshot to render it would be
        // wasteful when the index already holds it.
        _state.update {
            it.copy(metaSnapshot = snapshot, metaLoading = false, metaError = null)
        }
        val built = withContext(Dispatchers.Default) {
            RelatedIndex.build(snapshot, _state.value.printingToNormal, _state.value.cards)
        }
        relatedIndex = built
        metaArchetypeCount = built.coveredCards
        // Published so the UI can recompute: the index arrives after the first
        // composition, and a remember keyed only on the card id would cache the
        // empty result forever.
        _state.update { it.copy(relatedCoverage = built.coveredCards) }
        android.util.Log.i(
            "OpggRelated",
            "index built: archetypes=${snapshot.archetypes.size} cards=${built.coveredCards}",
        )
    }

    /** Re-fetches the snapshot, e.g. from the meta screen's refresh button. */
    fun refreshMeta() {
        _state.update { it.copy(metaLoading = true, metaError = null) }
        loadRelatedIndex()
    }

    /** Expands one meta archetype, or collapses it if it is already the open one. */
    fun toggleMetaArchetype(key: String) = _state.update {
        it.copy(metaExpandedArchetype = if (it.metaExpandedArchetype == key) null else key)
    }

    /**
     * Declared before `init` on purpose. Kotlin initialises properties in
     * declaration order, so a `by lazy` property declared further down the
     * class is still null when `init` runs - which crashed on the first launch     * after loadLocalDecks() was moved into `init`.
     */
    private val deckStore = LocalDeckStore(appContext)

    @Volatile
    private var decksLoaded = false

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Replayed in If-Modified-Since; empty means we hold no copy yet. */
    @Volatile
    private var cardsLastModified: String = ""
    @Volatile
    private var fetchedAt: Long = 0

    init {
        viewModelScope.launch {
            val session = sessions.load()
            _state.update { it.copy(session = session) }
            // Load the binder as soon as we know who is signed in, rather than
            // making the user press refresh. Skipped if this process already
            // has it, so it does not re-fetch on every recomposition.
            if (session != null && _state.value.owned.isEmpty()) loadBinder()
        }
        bootstrap()
        // Load local decks up front, not lazily when the Builder tab appears, so
        // a create/add never races an unread store.
        loadLocalDecks()
        viewModelScope.launch {
            // Restore the persisted sort and re-apply it to whatever is loaded.
            val saved = prefs.loadSort()
            _state.update { it.copy(sort = saved) }
            scheduleFilter(_state.value.query)
        }
    }

    /**
     * Cache first, network second. The card database is 5,230,704 bytes, so we
     * paint from disk immediately and only talk to the server when our copy is
     * older than the server's own max-age. An unchanged database costs a 304.
     */
    private fun bootstrap() {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { cache.read() }
            if (cached != null && cached.cards.isNotEmpty()) {
                cardsLastModified = cached.lastModified
                // A cache written by an older build has no printing rows, so it
                // cannot supply the printings or the printing->card index. It is
                // still good enough to paint the grid with, so it is used - but
                // the staleness clock is reset to force an immediate refresh
                // rather than leaving the app without printings for an hour.
                val current = cache.isCurrentFormat(cached)
                fetchedAt = if (current) cached.fetchedAt else 0L
                // The old cache still paints the grid, so use it while the full
                // database is fetched - offline users keep their cards.
                applyCards(cached.cards, cached.sets)
                _state.update { it.copy(loadingCards = false) }
                if (!current) {
                    // The Last-Modified must go too. Replaying it asks the server
                    // "has this changed?", and the server says no - it has no
                    // idea our copy kept only the deduped set. With the header
                    // still set the refresh came back 304 and the lossy cache
                    // stayed on disk forever. Clearing it makes the next fetch
                    // unconditional.
                    cardsLastModified = ""
                    android.util.Log.i(
                        "OpggCache",
                        "cache is an old format without printing rows - fetching the full database",
                    )
                }
            }
            if (cached == null || System.currentTimeMillis() - fetchedAt > STALE_AFTER_MS) {
                refreshInBackground()
            }
        }
    }

    private fun refreshInBackground() {
        viewModelScope.launch {
            try {
                // A single DNS blip used to leave the card database uncached for
                // the whole session, because this runs once at startup.
                val result = retryingNetwork { api.getCardsIndexedIfChanged(
                    cardsLastModified.ifEmpty { null },
                    onProgress = { received, total ->
                        if (total > 0) {
                            val pct = (received * 100.0 / total).toFloat().coerceIn(0f, 1f)
                            _state.update { it.copy(cardsProgress = pct) }
                        }
                    },
                ) }
                val (cards, lastModified) = result
                if (cards == null) {
                    // 304: our copy is current, nothing was downloaded. This is
                    // the whole point of the cache.
                    android.util.Log.i("OpggCache", "304 Not Modified - served from cache, 0 bytes downloaded")
                    fetchedAt = System.currentTimeMillis()
                    _state.update { it.copy(loadingCards = false, cardsProgress = null) }
                    return@launch
                }
                android.util.Log.i("OpggCache", "downloaded ${cards.size} printing rows from api.dotgg.gg")
                cardsLastModified = lastModified ?: cardsLastModified
                val sets = if (_state.value.sets.isEmpty()) {
                    api.getSetsIfChanged(null).first ?: emptyList()
                } else {
                    _state.value.sets
                }
                applyCards(cards, sets)
                fetchedAt = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    cache.write(
                        CardCacheEnvelope(
                            lastModified = cardsLastModified,
                            fetchedAt = fetchedAt,
                            // Store the RAW 5,540 printing rows, not the deduped
                            // 3,063-card set.
                            //
                            // Dedup is lossy: it keeps the base row per card and
                            // throws the `_pN`/`_rN` rows away, and they cannot
                            // be reconstructed from what is left. Caching the
                            // deduped set therefore cost, on every cold start
                            // after the first:
                            //   - the Printings strip, which vanished entirely,
                            //     so per-printing artwork was unreachable;
                            //   - `printingToNormal`, which then knew only base
                            //     ids, so meta-snapshot entries naming a printing
                            //     failed to resolve and the related list showed
                            //     "11 cards" above two tiles;
                            //   - `printingLookup`, so deck and collection
                            //     entries keyed by a printing id lost their card.
                            //
                            // `applyCards` re-derives the deduped set from this
                            // on load, exactly as it does for a fresh download.
                            cards = cards,
                            sets = sets,
                        )
                    )
                }
                // Proves connectivity just like the manual refresh does.
                _state.update { it.copy(cardsProgress = null, offline = false) }
            } catch (e: Exception) {
                // Offline or throttled: keep showing whatever we already have.
                // Log the whole stack as one message: Log.w(tag,msg,throwable)
                // printed only the message on this device's logcat filter.
                android.util.Log.e(
                    "OpggCards",
                    "background refresh failed: " + e.stackTraceToString().take(1200),
                )
                // A failed background refresh must not blank a working grid:
                // offline, the cache painted above is fully browsable, and
                // "The card list could not be loaded" was untrue. The error
                // screen is only for the cold start with nothing cached at all.
                if (_state.value.cards.isEmpty()) {
                    _state.update { it.copy(loadingCards = false, cardsProgress = null, cardsError = e.message) }
                } else {
                    _state.update { it.copy(loadingCards = false, cardsProgress = null, cardsError = null) }
                }
            }
        }
    }

    companion object {
        /** Copy types the server accepts; "standard" is non-foil. */
        const val TYPE_STANDARD = "standard"
        const val TYPE_FOIL = "foil"

        /** Matches the server's own Cache-Control: max-age=3600. */
        const val STALE_AFTER_MS = 60L * 60L * 1000L
    }

    /** Manual refresh: unconditional columnar fetch, bypassing cache and 304. */
    fun loadCards() {
        viewModelScope.launch {
            _state.update { it.copy(loadingCards = true, cardsError = null, cardsProgress = 0f) }
            try {
                val raw = withContext(Dispatchers.Default) {
                    api.getCardsIndexed { received, total ->
                        if (total > 0) {
                            val pct = (received * 100.0 / total).toFloat().coerceIn(0f, 1f)
                            _state.update { it.copy(cardsProgress = pct) }
                        }
                    }
                }
                val sets = runCatching { api.getSets() }.getOrDefault(emptyList())
                applyCards(raw, sets)
                fetchedAt = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    cache.write(
                        CardCacheEnvelope(
                            lastModified = cardsLastModified,
                            fetchedAt = fetchedAt,
                            // The raw printing rows, for the same reason as the
                            // background refresh: caching the deduped set throws
                            // the printings away and they cannot be rebuilt.
                            cards = raw,
                            sets = sets,
                        )
                    )
                }
                // A live card-database fetch proves the network is back, so the
                // Cards tab's OfflineNotice ("collection cannot be changed")
                // must not outlive it. The Collection tab's CachedStoreNotice
                // stays: its snapshot is still the saved copy until a binder
                // load succeeds.
                _state.update { it.copy(cardsProgress = null, offline = false) }
            } catch (e: Exception) {
                // Same rule as the background refresh: never blank a grid that
                // already has cards to show. A manual refresh failing while
                // cards are visible is a snackbar, not an error screen.
                if (_state.value.cards.isEmpty()) {
                    _state.update {
                        it.copy(loadingCards = false, cardsProgress = null, cardsError = e.message ?: "Failed to load cards")
                    }
                } else {
                    _state.update { it.copy(loadingCards = false, cardsProgress = null, cardsError = null) }
                    postMessage(NetworkStatus.friendlyMessage(e) ?: "Could not refresh the card list")
                }
            }
        }
    }

    /**
     * Dedup and index the raw printing rows, then publish to state.
     *
     * Runs the heavy work on Dispatchers.Default: decoding and building these
     * maps used to happen on Dispatchers.Main and froze the UI.
     */
    private suspend fun applyCards(raw: List<Card>, sets: List<CardSet>) {
        val prepared = withContext(Dispatchers.Default) {
            // Stamp each row with its set's release date before anything else
            // reads it: the dedup below keeps the stamped rows, so every Card
            // in state carries the date "Default order" sorts on.
            val dates = setReleaseDates(sets)
            val dated = raw.map { it.copy(releaseDate = dates[it.set]) }
            val byNormal = dated.groupBy { it.key }
            val index = HashMap<String, String>(dated.size)
            for (card in dated) {
                val printing = card.id ?: continue
                index[printing] = card.idNormal ?: printing
            }
            val seen = HashSet<String>()
            val firstSeen = ArrayList<Card>(byNormal.size)
            for (card in dated) {
                if (seen.add(card.key)) firstSeen.add(card)
            }
            // The first occurrence may be a reprint; prefer the base art where
            // one exists, keeping the first-seen ordering.
            val unique = firstSeen.map { c ->
                byNormal[c.key].orEmpty().firstOrNull { it.id == it.idNormal } ?: c
            }
            val vocabMap = CardSearch.vocabulary(unique)
            val nameList = unique.mapNotNull { it.name }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedBy { it.length }
            Prepared(unique, byNormal, index, vocabMap, nameList)
        }
        vocab = prepared.vocab
        cardNames = prepared.names
        _state.update {
            it.copy(
                loadingCards = false,
                cards = prepared.unique,
                // Apply the persisted sort here too. The restore path in init()
                // calls scheduleFilter before the cache has finished loading, so
                // it filters an empty list; without this the list came back in
                // API order and the saved sort silently did nothing.
                visible = sortCards(prepared.unique, it.sort),
                printingsByNormal = prepared.byNormal,
                printingToNormal = prepared.index,
                sets = sets,
            )
        }
        loadRelatedIndex()
    }

    private data class Prepared(
        val unique: List<Card>,
        val byNormal: Map<String, List<Card>>,
        val index: Map<String, String>,
        val vocab: Map<QField, Set<String>>,
        val names: List<String>,
    )

    /**
     * Set code -> release date (Unix seconds). The date lives on the set, not
     * the card, and several sets exist twice under hyphenated aliases
     * ("ST-01" beside "ST01", "PRB-02" beside "PRB02") whose rows carry date 0;
     * a real date wins over a 0 wherever they collide. Unknown set codes leave
     * no entry, and a card whose set has none sorts last in Default order.
     */
    private fun setReleaseDates(sets: List<CardSet>): Map<String, Long> {
        val map = HashMap<String, Long>(sets.size * 2)
        fun put(code: String?, d: Long) {
            if (code.isNullOrEmpty()) return
            val prev = map[code]
            if (prev == null || (prev == 0L && d != 0L)) map[code] = d
        }
        for (s in sets) {
            val d = s.ReleaseDate?.toLongOrNull() ?: continue
            put(s.code, d)
            put(s.id, d)
            // Hyphen-stripped alias; exact codes above, so this cannot
            // overwrite a real code's date.
            put(s.code?.replace("-", ""), d)
            put(s.id?.replace("-", ""), d)
        }
        return map
    }
    /**
     * Text field updates immediately so typing stays responsive, but the actual
     * filter is debounced and runs on Dispatchers.Default, so a keystroke never
     * waits on a 3,063-card scan.
     */
    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        scheduleFilter(q)
    }

    private var filterJob: Job? = null

    private fun scheduleFilter(q: String) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            delay(140)
            val parsed = CardSearch.parse(q, vocab)
            val source = _state.value.cards
            val sortSpec = _state.value.sort
            val result = withContext(Dispatchers.Default) {
                val filtered = if (parsed.isEmpty) source else source.filter { CardSearch.matches(it, parsed) }
                // Sort inside the debounced step, never in a getter: sorting on
                // every read would undo the per-frame cost fix.
                sortCards(filtered, sortSpec)
            }
            _state.update { it.copy(parsed = parsed, visible = result) }
        }
    }

    fun setSort(spec: SortSpec) {
        _state.update { it.copy(sort = spec) }
        viewModelScope.launch { prefs.saveSort(spec) }
        scheduleFilter(_state.value.query)
    }

    fun suggestionsFor(query: String): List<Suggestion> =
        CardSearch.suggest(query, vocab, cardNames)

    /** Replaces the token being typed with the chosen completion. */
    fun applySuggestion(suggestion: Suggestion) {
        val current = _state.value.query
        val start = CardSearch.partialTokenStart(current)
        val next = current.substring(0, start) + suggestion.insert
        setQuery(next)
    }

    /**
     * Chip taps edit the query text itself, so the search box and the filter
     * sheet are two views of one state rather than two competing filters.
     */
    fun toggleTerm(field: QField, value: String) =
        applyQuery(toggleTermIn(_state.value.parsed, field, value))

    fun clearTokens() =
        applyQuery(CardSearch.render(emptyList(), _state.value.parsed.words))

    /** Removes one field's selection, used by the filter dropdowns' "Clear". */
    fun clearTerm(field: QField) =
        applyQuery(clearTermIn(_state.value.parsed, field))

    fun clearQuery() {
        filterJob?.cancel()
        _state.update { it.copy(query = "", parsed = ParsedQuery(), visible = it.cards) }
    }

    private fun applyQuery(text: String) {
        _state.update { it.copy(query = text) }
        scheduleFilter(text)
    }

    // ---- the deck editor's independent copy of the same query machinery ----
    //
    // The editor must not share query state with the Cards tab: a filter set
    // there silently shrank the editor's pool, and an editor filter leaked back
    // into the Cards grid. Same token syntax, separate state, and no call to
    // scheduleFilter, whose result only the Cards tab consumes.

    /** The editor's search box. Nothing here touches the Cards tab's [visible]. */
    fun setEditorQuery(q: String) =
        _state.update { it.copy(editorQuery = q, editorParsed = CardSearch.parse(q, vocab)) }

    fun applyEditorSuggestion(suggestion: Suggestion) {
        val current = _state.value.editorQuery
        val start = CardSearch.partialTokenStart(current)
        setEditorQuery(current.substring(0, start) + suggestion.insert)
    }

    fun toggleEditorTerm(field: QField, value: String) =
        setEditorQuery(toggleTermIn(_state.value.editorParsed, field, value))

    fun clearEditorTerm(field: QField) =
        setEditorQuery(clearTermIn(_state.value.editorParsed, field))

    fun clearEditorTokens() =
        setEditorQuery(CardSearch.render(emptyList(), _state.value.editorParsed.words))

    fun clearEditorQuery() =
        _state.update { it.copy(editorQuery = "", editorParsed = ParsedQuery()) }

    private fun toggleTermIn(current: ParsedQuery, field: QField, value: String): String {
        val term = current.terms.firstOrNull { it.field == field && !it.negated }
        val values = term?.values?.toMutableList() ?: mutableListOf()
        if (values.any { it.equals(value, ignoreCase = true) }) {
            values.removeAll { it.equals(value, ignoreCase = true) }
        } else {
            values += value
        }
        val others = current.terms.filter { it.field != field || it.negated }
        val rebuilt = if (values.isEmpty()) others else others + Term(field, values)
        return CardSearch.render(rebuilt, current.words)
    }

    private fun clearTermIn(current: ParsedQuery, field: QField): String =
        CardSearch.render(current.terms.filter { it.field != field }, current.words)
    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Shows a snackbar. Anything the user should know but need not act on. */
    fun postMessage(text: String) = _state.update { it.copy(message = text) }

    /** Switches the deck list between the public catalogue and the account's own decks. */
    fun showMyDecks(mine: Boolean) {
        if (_state.value.decksMine == mine) return
        _state.update {
            it.copy(
                decksMine = mine,
                decks = emptyList(),
                decksPage = 0,
                decksExhausted = false,
                decksError = null,
            )
        }
        loadDecks(reset = true)
    }

    fun loadDecks(reset: Boolean = false) {
        val s = _state.value
        // A reset (new search, segment switch) supersedes whatever is in flight;
        // only "load more" has to wait for the current page.
        if (s.decksLoading && !reset) return
        if (!reset && s.decksExhausted) return
        val page = if (reset) 1 else s.decksPage + 1
        val mine = s.decksMine
        val requestId = ++deckRequestId
        viewModelScope.launch {
            _state.update { it.copy(decksLoading = true, decksError = null) }
            try {
                if (mine && _state.value.session == null) {
                    if (requestId == deckRequestId) {
                        _state.update {
                            it.copy(decksLoading = false, decksError = "Sign in to see your own decks.")
                        }
                    }
                    return@launch
                }
                // Offline fast path, same reasoning as the binder load.
                // Only a first page can be served from the cache: "load more"
                // pages were never persisted per page anyway.
                if (page == 1 && !NetworkStatus.isOnline(appContext)) {
                    applyCachedDecks(deckCache.forQuery(mine, s.deckQuery))
                    return@launch
                }
                val batch = api.getDecks(
                    page,
                    if (mine) s.session?.userId else null,
                    search = s.deckQuery.takeIf { it.isNotBlank() },
                )
                if (requestId != deckRequestId) return@launch
                _state.update { st ->
                    if (batch.isEmpty()) {
                        st.copy(
                            decksLoading = false,
                            decksExhausted = true,
                            decksCacheAt = null,
                            offline = false,
                        )
                    } else {
                        val seen = st.decks.mapNotNull { it.slug }.toSet()
                        val fresh = batch.filter { it.slug == null || !seen.contains(it.slug) }
                        st.copy(
                            decksLoading = false,
                            decks = st.decks + fresh,
                            decksPage = page,
                            // A refresh can reopen the list, so this must not
                            // stay true from a previous exhausted run.
                            decksExhausted = fresh.isEmpty(),
                            decksCacheAt = null,
                            offline = false,
                        )
                    }
                }
                // Persist the accumulated list for an offline open, keyed by
                // segment and query so a different search cannot reuse it.
                val now = _state.value
                if (now.decks.isNotEmpty() && now.decksMine == mine && now.deckQuery == s.deckQuery) {
                    deckCache.write(mine, s.deckQuery, page, now.decks)
                }
            } catch (e: Exception) {
                if (requestId != deckRequestId) return@launch
                // The list was readable a moment ago and public decks change
                // slowly; showing the last snapshot beats an error screen. The
                // screen's first load calls loadDecks() without reset, so the
                // test is "first page", not "reset".
                if (page == 1 && applyCachedDecks(deckCache.forQuery(mine, s.deckQuery))) {
                    return@launch
                }
                // A network failure is not a server complaint, and "Could not
                // resolve api.dotgg.gg: Unable to resolve host ..." is not
                // something to show a person.
                val friendly = NetworkStatus.friendlyMessage(e)
                _state.update {
                    it.copy(
                        decksLoading = false,
                        decksError = friendly ?: (e.message ?: "Could not load decks"),
                        offline = friendly != null || it.offline,
                    )
                }
            }
        }
    }

    /**
     * Server-side deck search.
     *
     * `smartsrch` inside `getdecks` is the site's own key (from its rq object);
     * the guesses tried earlier (`q`, `search`, `name`, `leader`, ...) are
     * silently ignored by the API, which is why the list had no search box.
     */
    fun setDeckQuery(query: String) {
        val q = query.trim()
        if (q == _state.value.deckQuery) return
        _state.update {
            it.copy(
                deckQuery = q,
                decks = emptyList(),
                decksPage = 0,
                decksExhausted = false,
                decksError = null,
            )
        }
        loadDecks(reset = true)
    }

    /**
     * Applies a cached deck-list snapshot (or none) to the state. The screen's
     * notice "Offline. Showing the copy saved at …" keys on [UiState.offline].
     * Returns false when [cached] is null so the caller can fall through.
     */
    private fun applyCachedDecks(cached: CachedDeckList?): Boolean {
        if (cached == null) return false
        _state.update {
            it.copy(
                decksLoading = false,
                decksError = null,
                decks = cached.decks,
                decksPage = cached.page,
                decksExhausted = false,
                decksCacheAt = cached.savedAt,
                offline = true,
            )
        }
        return true
    }

    fun loadDeckDetail(slug: String) {
        viewModelScope.launch {
            _state.update { it.copy(deckDetailLoading = true, deckDetailError = null, deckDetail = null) }
            try {
                val detail = api.getDeck(slug)
                _state.update { it.copy(deckDetailLoading = false, deckDetail = detail) }
            } catch (e: Exception) {
                _state.update { it.copy(deckDetailLoading = false, deckDetailError = e.message ?: "Could not load deck") }
            }
        }
    }

    fun clearDeckDetail() = _state.update { it.copy(deckDetail = null, deckDetailError = null) }

    // ---- local deck builder --------------------------------------------
    // The device is the source of truth. `saveDeckToSite` publishes a deck to
    // onepiece.gg and stores its machine name (`deckname`) so later saves
    // update the same copy; "Copy deck code" still exists for pasting the
    // list into the site by hand.

    private var codeLookup: DeckCode.Lookup? = null

    fun loadLocalDecks() {
        viewModelScope.launch {
            val decks = deckStore.read()
            decksLoaded = true
            _state.update { it.copy(localDecks = decks) }
        }
    }

    /**
     * The same search the Cards tab uses, so typed tokens like `c:red` and
     * `t:character` work identically in the deck editor.
     */
    suspend fun searchCards(cards: List<Card>, query: String): List<Card> {
        if (query.isBlank()) return cards
        val parsed = CardSearch.parse(query, vocab)
        if (parsed.isEmpty) return cards
        return withContext(Dispatchers.Default) { cards.filter { CardSearch.matches(it, parsed) } }
    }

    /**
     * Mutations must not run before the store has been read. `localDecks + deck`
     * against an empty in-memory list writes a one-element file and **overwrites
     * every existing deck** - which silently destroyed a real deck in testing.
     */
    private suspend fun decksReady(): List<LocalDeck> {
        if (!decksLoaded) {
            val decks = deckStore.read()
            decksLoaded = true
            _state.update { it.copy(localDecks = decks) }
            return decks
        }
        return _state.value.localDecks
    }

    /** False until the store has been read, so the UI can show a spinner
     *  instead of claiming a deck does not exist. */
    fun isDecksReady(): Boolean = decksLoaded

    fun newDeckId(): String = deckStore.newId()

    fun addLocalDeck(deck: LocalDeck) {
        viewModelScope.launch {
            val existing = decksReady()
            val next = if (existing.any { it.id == deck.id }) {
                existing.map { if (it.id == deck.id) deck else it }
            } else {
                existing + deck
            }
            _state.update { it.copy(localDecks = next) }
            deckStore.write(next)
        }
    }

    /**
     * Creates a deck and returns its id **synchronously**.
     *
     * The write is still async, but the caller needs the id immediately to
     * navigate to the editor. It is returned rather than round-tripped through
     * state, because a state read from the same event is always stale and
     * made opening a deck require two taps.
     */
    fun createLocalDeck(): String {
        val deck = LocalDeck(
            id = deckStore.newId(),
            name = "New deck",
            createdAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            val next = decksReady() + deck
            _state.update { it.copy(localDecks = next) }
            deckStore.write(next)
        }
        return deck.id
    }

    fun updateLocalDeck(deck: LocalDeck) {
        viewModelScope.launch {
            val current = decksReady()
            val next = current.map { if (it.id == deck.id) deck else it }
            android.util.Log.i(
                "OpggDecks",
                "update id=${deck.id} leader=${deck.leaderId} cards=${deck.cards.size} known=${current.size}",
            )
            _state.update { it.copy(localDecks = next) }
            deckStore.write(next)
        }
    }

    fun deleteLocalDeck(id: String) {
        viewModelScope.launch {
            val next = decksReady().filterNot { it.id == id }
            _state.update { it.copy(localDecks = next) }
            deckStore.write(next)
        }
    }

    /**
     * Publishes a local deck to onepiece.gg, or updates the copy already there.
     *
     * The site identifies decks by a machine name (`deckname`), not by id or
     * slug, so the first save stores that name on the local deck and every
     * later save reuses it. Renaming in the app therefore renames the site copy
     * instead of creating a second one.
     *
     * `savedeck` replaces the whole card list, which is what we want: the local
     * deck is the source of truth.
     */
    fun saveDeckToSite(id: String) {
        val deck = _state.value.localDecks.firstOrNull { it.id == id } ?: return
        if (_state.value.session == null) {
            postMessage("Sign in to save decks to onepiece.gg")
            return
        }
        val cards = deck.cards.filterValues { it > 0 }
        if (cards.isEmpty()) {
            postMessage("Add cards before saving this deck")
            return
        }
        val deckname = deck.siteDeckname ?: SiteDeckName.generate()
        viewModelScope.launch {
            runCatching { api.saveDeck(SaveDeckRequest.of(deckname, deck.name, cards)) }
                .onSuccess { saved ->
                    val current = _state.value.localDecks
                    val next = current.map {
                        if (it.id == id) {
                            it.copy(
                                siteDeckname = saved.deckname,
                                siteSlug = saved.slug,
                                updatedAt = System.currentTimeMillis(),
                            )
                        } else {
                            it
                        }
                    }
                    _state.update { it.copy(localDecks = next) }
                    deckStore.write(next)
                    postMessage(
                        if (deck.siteDeckname == null) {
                            "Saved to onepiece.gg as \u201C${deck.name}\u201D"
                        } else {
                            "Updated \u201C${deck.name}\u201D on onepiece.gg"
                        },
                    )
                }
                .onFailure {
                    postMessage("Could not save to onepiece.gg: ${it.message ?: "unknown error"}")
                }
        }
    }

    /**
     * Renames a deck. A blank name is rejected and the current one kept, so a
     * deck can never end up nameless in the list.
     */
    fun renameLocalDeck(id: String, rawName: String): Boolean {
        val current = _state.value.localDecks.firstOrNull { it.id == id } ?: return false
        val name = LocalDeckOps.cleanName(rawName, fallback = current.name)
        if (name == current.name) return true
        updateLocalDeck(current.copy(name = name, updatedAt = System.currentTimeMillis()))
        return true
    }

    /**
     * Copies a deck under a fresh id, with a name that does not collide with an
     * existing one. Returns the new deck so the caller can open it.
     */
    fun duplicateLocalDeck(id: String): LocalDeck? {
        val all = _state.value.localDecks
        val source = all.firstOrNull { it.id == id } ?: return null
        val copy = LocalDeckOps.duplicate(
            deck = source,
            newId = deckStore.newId(),
            now = System.currentTimeMillis(),
            existingNames = all.map { it.name },
        )
        val next = all + copy
        _state.update { it.copy(localDecks = next) }
        viewModelScope.launch { deckStore.write(next) }
        return copy
    }

    fun setEditorFlags(
        leadersOnly: Boolean = _state.value.editorLeadersOnly,
        ownedOnly: Boolean = _state.value.editorOwnedOnly,
        inDeckOnly: Boolean = _state.value.editorInDeckOnly,
        searchOpen: Boolean = _state.value.editorSearchOpen,
        sortOpen: Boolean = _state.value.editorSortOpen,
        analytics: Boolean = _state.value.editorAnalyticsExpanded,
        issues: Boolean = _state.value.editorIssuesExpanded,
        regulation: RegulationMode = _state.value.regulationMode,
    ) = _state.update {
        it.copy(
            editorLeadersOnly = leadersOnly,
            editorOwnedOnly = ownedOnly,
            editorInDeckOnly = inDeckOnly,
            editorSearchOpen = searchOpen,
            editorSortOpen = sortOpen,
            editorAnalyticsExpanded = analytics,
            editorIssuesExpanded = issues,
            regulationMode = regulation,
        )
    }


    /** Card lookup by printing id, for the rules engine and deck code. */
    fun printingLookup(): (String) -> Card? {
        val byId = HashMap<String, Card>()
        for (group in _state.value.printingsByNormal.values) {
            for (c in group) c.id?.let { byId[it] = c }
        }
        return { id -> byId[id] }
    }

    fun deckCodeLookup(): DeckCode.Lookup {
        codeLookup?.let { return it }
        val all = ArrayList<Card>()
        for (group in _state.value.printingsByNormal.values) all.addAll(group)
        return DeckCode.Lookup(all).also { codeLookup = it }
    }

    fun exportDeckCode(deck: LocalDeck): String = DeckCode.export(deck)

    /**
     * The site's *import* shape, which drops the reprint suffix
     * (`1xEB01-015_r1` -> `1xEB01-015`) while keeping the set prefix.
     *
     * Separate from [exportDeckCode] on purpose: the full printing id is what the
     * deck stores, so the plain export is the lossless one.
     */
    fun exportDeckCodeForSite(deck: LocalDeck): DeckCode.SiteExport =
        DeckCode.exportForSite(deck)

    fun exportDeckCsv(deck: LocalDeck): String = DeckCode.toCsv(deck, printingLookup())

    data class ImportResult(
        val deck: LocalDeck?,
        val unresolved: List<String>,
        val error: String?,
    )

    fun importDeckCode(text: String): ImportResult {
        val parsed = DeckCode.parse(text, deckCodeLookup())
        if (parsed.cards.isEmpty()) {
            return ImportResult(null, parsed.unresolved, "No cards in that deck code were recognised.")
        }
        val byId = printingLookup()
        // Deck code has no separate leader field, so infer it from the cards.
        val leader = parsed.cards.keys.firstOrNull { DeckRules.isLeader(byId(it)) }
        val deck = LocalDeck(
            id = deckStore.newId(),
            name = "Imported deck",
            leaderId = leader,
            cards = parsed.cards,
            createdAt = System.currentTimeMillis(),
        )
        val next = _state.value.localDecks + deck
        _state.update { it.copy(localDecks = next) }
        viewModelScope.launch { deckStore.write(next) }
        return ImportResult(deck, parsed.unresolved, null)
    }

    /** Called after SignInActivity handed us a token from the site's own session. */
    fun onSignedIn() {
        viewModelScope.launch {
            val s = sessions.load() ?: return@launch
            _state.update { it.copy(session = s) }
            loadBinder()
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessions.clear()
            _state.update { it.copy(session = null, owned = emptyMap(), binderError = null) }
        }
    }

    fun loadBinder(binderId: Long = _state.value.binderId) {
        loadBinderAttempt(binderId, allowRetry = true)
    }

    /**
     * Reads one store.
     *
     * [allowRetry] exists because a timed-out read is usually a dead keep-alive
     * connection rather than a dead server: the first attempt rides the stale
     * socket to its timeout, and an immediate second attempt lands on a fresh
     * connection. Without this, one stalled socket left the grid "loading"
     * (then on the error state) until the whole app was restarted.
     */
    private fun loadBinderAttempt(binderId: Long, allowRetry: Boolean) {
        if (_state.value.session == null) return
        val requestId = ++binderRequestId
        viewModelScope.launch {
            _state.update {
                // Clearing the previous store's rows matters: until the new
                // response lands the count row and grid would otherwise show the
                // binder you just left ("38 copies" under Supernova).
                val switching = it.binderId != binderId
                it.copy(
                    binderId = binderId,
                    binderLoading = true,
                    binderError = null,
                    binderOwned = if (switching) emptyMap() else it.binderOwned,
                    binderOwnedByNormal = if (switching) emptyMap() else it.binderOwnedByNormal,
                )
            }
            try {
                // Offline fast path: when the OS already reports no usable
                // network, the live read would ride the connect timeout (then
                // the read timeout, then the keep-alive retry) before the
                // catch block reached the same cache - 35 s of "Still
                // loading…" for a snapshot that was on disk all along. Apply
                // the cache now, in milliseconds. An empty cache still falls
                // through to one live attempt, so a wrong "offline" verdict
                // fails fast instead of showing nothing.
                if (!NetworkStatus.isOnline(appContext) && applyCachedStore(binderId)) {
                    return@launch
                }
                // The site gates collection sync on binder_id !== 0, so a real
                // binder has to be selected or the API reports nothing.
                //
                // A rejected session is not "no binders". The site answers it
                // with HTTP 200 and an error body, so it has to be recognised
                // here - otherwise the app tells the user to create a binder
                // they already have, and to a collection they already own.
                val binders = try {
                    api.listBinders()
                } catch (e: DotggException) {
                    if (e.message?.contains("authorized", ignoreCase = true) == true) {
                        if (requestId == binderRequestId) {
                            _state.update {
                                it.copy(binderLoading = false, sessionRejected = true, binderError = null)
                            }
                        }
                        return@launch
                    }
                    // The picker must still work offline: without the cached
                    // list the app looks like the account has no binders.
                    storeCache.binders()
                }
                // Do NOT promote binder 0 to the account's first binder.
                //
                // It used to, and that silently changed where writes landed:
                // this account has one binder called "Wishlist", so every +/-
                // went to savebindercard for that binder instead of
                // savebatchcollection, and the main collection never changed
                // (found while verifying the wishlist's shortfall rule). Binder
                // 0 is the default collection and is a valid target; the chips
                // let the user switch binders explicitly.
                val target = binderId

                // Binder 0 is the collection and only `getuserdata` knows about
                // it; every other binder is a separate store read through
                // `getbindercards`. The two answer with the same row shape, so
                // everything downstream is shared.
                val rows: List<OwnedCard>
                val serverUserId: Long?
                val rejected: Boolean
                val serverNickname: String?
                if (target == 0L) {
                    val data = api.getUserData(0)
                    if (data.error) throw DotggException(data.error_text ?: "Could not load collection")
                    rows = data.collection.orEmpty()
                    serverUserId = data.user?.user_id
                    serverNickname = data.user?.nickname
                    // The server reports the user id it recognised. Zero while
                    // we hold a real one means the token is dead, whatever the
                    // shape of the response.
                    rejected = isSessionRejected(
                        storedUserId = _state.value.session?.userId ?: 0L,
                        serverUserId = serverUserId,
                    )
                } else if (target < 0L) {
                    // The API's `binder_id=all` is binders only, but the app
                    // presents Collection as the default location. Union it in so
                    // "All binders" really means everything the account holds.
                    // Read-only: a row can span stores.
                    val collection = api.getUserData(0)
                    if (collection.error) throw DotggException(collection.error_text ?: "Could not load collection")
                    rows = collection.collection.orEmpty() + api.getAllBinderContents()
                    serverUserId = collection.user?.user_id
                    serverNickname = collection.user?.nickname
                    rejected = isSessionRejected(
                        storedUserId = _state.value.session?.userId ?: 0L,
                        serverUserId = serverUserId,
                    )
                } else {
                    rows = api.getBinderContents(target.toInt())
                    // A binder read cannot tell us the session's user id, so
                    // leave what the collection read established alone.
                    serverUserId = _state.value.serverUserId
                    serverNickname = null
                    rejected = _state.value.sessionRejected
                }

                val map = mergeOwnedRows(rows)
                // A newer load has already started; this result is stale.
                if (requestId != binderRequestId) return@launch
                // Persist for the next offline launch. `all binders` (-1) is a
                // read-only aggregate the parts can rebuild, so it is skipped.
                if (target >= 0L && !rejected) {
                    storeCache.saveBinders(binders)
                    storeCache.saveStore(target, rows)
                }
                // The nickname is what the site's own share links use. A
                // session imported without one would otherwise be unable to
                // build a link, so take the server's answer and keep it.
                val serverNick = serverNickname?.trim()?.takeIf { it.isNotEmpty() }
                val sessionNow = _state.value.session
                val session = if (serverNick != null && sessionNow != null && sessionNow.nick != serverNick) {
                    sessions.save(sessionNow.copy(nick = serverNick), null)
                    sessionNow.copy(nick = serverNick)
                } else {
                    sessionNow
                }
                _state.update {
                    val normalMap = collapseOwned(map, it.printingToNormal)
                    if (target == 0L) {
                        it.copy(
                            binderLoading = false,
                            binderId = target,
                            binders = binders,
                            owned = map,
                            ownedByNormal = normalMap,
                            serverUserId = serverUserId,
                            sessionRejected = rejected,
                            session = session,
                            storeCacheAt = null,
                            offline = false,
                        )
                    } else {
                        // Binder contents live beside the collection, never in
                        // its place: badges and the wishlist are collection facts.
                        it.copy(
                            binderLoading = false,
                            binderId = target,
                            binders = binders,
                            binderOwned = map,
                            binderOwnedByNormal = normalMap,
                            serverUserId = serverUserId,
                            sessionRejected = rejected,
                            session = session,
                            storeCacheAt = null,
                            offline = false,
                        )
                    }
                }
            } catch (e: Exception) {
                if (requestId != binderRequestId) return@launch
                if (allowRetry && with(NetworkStatus) { e.isTimeout() } && NetworkStatus.isOnline(appContext)) {
                    delay(500)
                    loadBinderAttempt(binderId, allowRetry = false)
                    return@launch
                }
                // The live read failed, but the last successful one may be on
                // disk. Showing it (marked with its save time) beats an error
                // screen when the metadata for every card is already local.
                // "All binders" has no cache of its own; it is rebuilt from the
                // cached stores, exactly like the online aggregate.
                if (requestId == binderRequestId && applyCachedStore(binderId)) return@launch
                val friendly = NetworkStatus.friendlyMessage(e)
                _state.update {
                    it.copy(
                        binderLoading = false,
                        binderError = friendly ?: (e.message ?: "Could not load binder"),
                        // A timeout means the server accepted the connection and
                        // stalled; that is not the same as being offline.
                        offline = NetworkStatus.isOfflineFailure(e) || it.offline,
                    )
                }
            }
        }
    }

    fun selectBinder(id: Long) = loadBinder(id)

    /**
     * Applies the disk-cached store for [binderId] to the state and reports
     * whether one existed. Called from the offline fast path at the top of
     * [loadBinderAttempt] and from its catch block; callers guard with their
     * own requestId. `binderId < 0` is the "All binders" aggregate, rebuilt
     * from the cached stores exactly like the online version.
     */
    private fun applyCachedStore(binderId: Long): Boolean {
        val cached = if (binderId < 0L) {
            storeCache.allRows().takeIf { it.isNotEmpty() } ?: return false
        } else {
            storeCache.store(binderId) ?: return false
        }
        val cachedMap = mergeOwnedRows(cached)
        val cachedAt = if (binderId < 0L) {
            storeCache.latestStoreSavedAt()
        } else {
            storeCache.storeSavedAt(binderId)
        }
        _state.update {
            val normalMap = collapseOwned(cachedMap, it.printingToNormal)
            if (binderId == 0L) {
                it.copy(
                    binderLoading = false,
                    binderError = null,
                    binderId = 0L,
                    binders = it.binders.ifEmpty { storeCache.binders() },
                    owned = cachedMap,
                    ownedByNormal = normalMap,
                    storeCacheAt = cachedAt,
                    offline = true,
                )
            } else {
                it.copy(
                    binderLoading = false,
                    binderError = null,
                    binderId = binderId,
                    binders = it.binders.ifEmpty { storeCache.binders() },
                    binderOwned = cachedMap,
                    binderOwnedByNormal = normalMap,
                    storeCacheAt = cachedAt,
                    offline = true,
                )
            }
        }
        return true
    }

    /**
     * Creates a binder and switches to it.
     *
     * A binder is a separate store, so this is the only way to get an empty
     * one; the + chip is what calls it.
     */
    fun createBinder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(message = "Give the binder a name") }
            return
        }
        if (_state.value.session == null) {
            _state.update { it.copy(message = "Sign in to create a binder") }
            return
        }
        viewModelScope.launch {
            try {
                loadBinder(api.createBinder(trimmed))
            } catch (e: Exception) {
                _state.update {
                    it.copy(message = NetworkStatus.friendlyMessage(e) ?: (e.message ?: "Could not create binder"))
                }
            }
        }
    }

    fun renameBinder(binderId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(message = "Give the binder a name") }
            return
        }
        viewModelScope.launch {
            try {
                api.renameBinder(binderId, trimmed)
                loadBinder(_state.value.binderId)
            } catch (e: Exception) {
                _state.update {
                    it.copy(message = NetworkStatus.friendlyMessage(e) ?: (e.message ?: "Could not rename binder"))
                }
            }
        }
    }

    /**
     * Deletes a binder and, if it was the selected store, returns to the
     * collection. The server deletes the binder's cards with it.
     */
    fun deleteBinder(binderId: Long) {
        viewModelScope.launch {
            try {
                api.deleteBinder(binderId)
                loadBinder(if (_state.value.binderId == binderId) 0L else _state.value.binderId)
            } catch (e: Exception) {
                _state.update {
                    it.copy(message = NetworkStatus.friendlyMessage(e) ?: (e.message ?: "Could not delete binder"))
                }
            }
        }
    }

    /**
     * Moves the whole row for [cardId] to another store.
     *
     * The API moves all copies of that printing, so the UI says so; there is no
     * partial move. Binder 0 is the collection in both directions.
     */
    fun moveCard(cardId: String, fromStore: Long, toStore: Long) =
        moveCards(listOf(cardId), fromStore, toStore)

    /** Bulk variant: `movecards` accepts a list of printing ids. */
    fun moveCards(cardIds: List<String>, fromStore: Long, toStore: Long) {
        if (cardIds.isEmpty()) return
        if (_state.value.session == null) {
            _state.update { it.copy(message = "Sign in to move cards") }
            return
        }
        if (!NetworkStatus.isOnline(appContext)) {
            _state.update { it.copy(message = "You're offline, so cards cannot be moved. Reconnect and try again.") }
            return
        }
        val targetName = if (toStore == 0L) {
            "Collection"
        } else {
            _state.value.binders.firstOrNull { it.id == toStore }?.name ?: "binder"
        }
        val noun = if (cardIds.size == 1) "card" else "${cardIds.size} cards"
        viewModelScope.launch {
            try {
                api.moveCards(fromStore, toStore, cardIds)
                // Refresh the store being viewed and the binder counts.
                loadBinder(_state.value.binderId)
                _state.update { it.copy(message = "Moved $noun to $targetName") }
            } catch (e: Exception) {
                android.util.Log.w("OpggMove", "move ${cardIds.size} card(s) $fromStore->$toStore failed", e)
                _state.update {
                    it.copy(message = NetworkStatus.friendlyMessage(e) ?: (e.message ?: "Could not move cards"))
                }
            }
        }
    }

    fun setBinderColor(binderId: Long, color: String) {
        viewModelScope.launch {
            try {
                api.setBinderColor(binderId, color)
                loadBinder(_state.value.binderId)
            } catch (e: Exception) {
                _state.update {
                    it.copy(message = NetworkStatus.friendlyMessage(e) ?: (e.message ?: "Could not update colour"))
                }
            }
        }
    }

    fun reorderBinders(order: List<Long>) {
        viewModelScope.launch {
            try {
                api.reorderBinders(order)
                loadBinder(_state.value.binderId)
            } catch (e: Exception) {
                _state.update {
                    it.copy(message = NetworkStatus.friendlyMessage(e) ?: (e.message ?: "Could not reorder binders"))
                }
            }
        }
    }

    fun setBinderPublic(binderId: Long, public: Boolean) {
        viewModelScope.launch {
            try {
                api.setBinderPublic(binderId, public)
                loadBinder(_state.value.binderId)
                _state.update { it.copy(message = if (public) "Binder is now public" else "Binder is private again") }
            } catch (e: Exception) {
                _state.update {
                    it.copy(message = NetworkStatus.friendlyMessage(e) ?: (e.message ?: "Could not update sharing"))
                }
            }
        }
    }

    /** [parentId] 0 means the top level. */
    fun setBinderParent(binderId: Long, parentId: Long) {
        viewModelScope.launch {
            try {
                api.setBinderParent(binderId, parentId)
                loadBinder(_state.value.binderId)
            } catch (e: Exception) {
                _state.update {
                    it.copy(message = NetworkStatus.friendlyMessage(e) ?: (e.message ?: "Could not move binder"))
                }
            }
        }
    }

    /** Sums printing-level counts into canonical card counts. */
    private fun collapseOwned(
        owned: Map<String, Owned>,
        printingToNormal: Map<String, String>,
    ): Map<String, Owned> = collapseOwnedCounts(owned, printingToNormal)

    /**
     * Which printing id to write to when the user edits a card.
     *
     * Must be the printing they already own, or editing a card held only as a
     * reprint would add a second row for the same card.
     */
    fun printingToEdit(normal: String, storeId: Long): String {
        val group = _state.value.printingsByNormal[normal] ?: return normal
        val store = if (storeId == 0L) _state.value.owned else _state.value.binderOwned
        val held = group.firstOrNull { c ->
            val o = c.id?.let { store[it] }
            o != null && (o.normal + o.foil) > 0
        }
        return held?.id
            ?: group.firstOrNull { it.id == it.idNormal }?.id
            ?: group.firstOrNull()?.id
            ?: normal
    }

    /**
     * Which printing id a wishlist edit writes to.
     *
     * Same principle as [printingToEdit]: the wish must be edited on the row it
     * already lives on, or adjusting a wish placed on a reprint would create a
     * second row and the collapsed total would double. Falls back to the base
     * printing when nothing is wished yet.
     */
    fun printingToWishEdit(normal: String): String {
        val group = _state.value.printingsByNormal[normal] ?: return normal
        val owned = _state.value.owned
        val wished = group.firstOrNull { c ->
            (c.id?.let { owned[it] }?.wishTotal ?: 0) > 0
        }
        return wished?.id
            ?: group.firstOrNull { it.id == it.idNormal }?.id
            ?: group.firstOrNull()?.id
            ?: normal
    }

    /**
     * Which copy type the tile's +/- controls act on. Cards have both a normal
     * and a foil count, so a bare "+" is ambiguous; the tile shows the choice
     * and lets the user switch it.
     */
    private val ownedTypeChoice = mutableStateMapOf<String, String>()

    fun ownedTypeFor(key: String): String = ownedTypeChoice[key] ?: TYPE_STANDARD

    /**
     * Whether [printingId] (one of [normal]'s printings) exists only as foil.
     *
     * Parallel printings are foil-only, so the tile must not offer a standard
     * counter for them. The check lives here because the tile already knows
     * which printing it edits; asking again by card key would redo that lookup.
     */
    fun isFoilOnlyPrinting(normal: String, printingId: String): Boolean =
        _state.value.printingsByNormal[normal]
            .orEmpty()
            .firstOrNull { it.id == printingId }
            ?.foilOnly == true

    /**
     * Which copy type the tile's +/- controls act on, for [card] in [storeId].
     *
     * Foil-only printings force foil. Otherwise an explicit toggle wins, and the
     * fallback follows what is owned: a card held only as foil used to open on
     * "STD 0", so the first "+" recorded a standard copy that does not exist.
     */
    fun ownedTypeFor(card: Card, storeId: Long): String {
        val printing = printingToEdit(card.key, storeId)
        if (isFoilOnlyPrinting(card.key, printing)) return TYPE_FOIL
        ownedTypeChoice[card.key]?.let { return it }
        val source = if (storeId == 0L) _state.value.owned else _state.value.binderOwned
        val counts = source[printing]
        return if (counts != null && counts.foil > 0 && counts.normal == 0) TYPE_FOIL else TYPE_STANDARD
    }

    fun cycleOwnedType(key: String) {
        ownedTypeChoice[key] = if (ownedTypeFor(key) == TYPE_STANDARD) TYPE_FOIL else TYPE_STANDARD
    }

    /**
     * "+/- pressed" on a tile. Clamps at zero and never sends a negative count.
     *
     * [storeId] is 0 for the collection and the binder's id when the Collection
     * tab is showing a binder. The Cards tab always passes 0: browsing the card
     * database is not a location, so editing there must not land in whichever
     * binder happens to be selected.
     */
    fun quickAdjust(card: Card, delta: Int, storeId: Long) {
        if (_state.value.session == null) {
            _state.update { it.copy(message = "Sign in to edit your collection") }
            return
        }
        val key = card.key
        // Read and write MUST use the same printing. The card list shows the base
        // printing, but the account may hold a reprint, and reading owned[card.key]
        // while writing owned[printingId] made the count spring back to 0.
        val printing = printingToEdit(key, storeId)
        // Must be the same type the tile is showing, including the foil-only
        // rule: otherwise the label says FOIL while +/- writes a standard row.
        val type = ownedTypeFor(card, storeId)
        val source = if (storeId == 0L) _state.value.owned else _state.value.binderOwned
        val current = source[printing]?.let { if (type == TYPE_FOIL) it.foil else it.normal } ?: 0
        val next = (current + delta).coerceAtLeast(0)
        if (next == current) return
        setOwned(printing, type, next, storeId)
    }

    fun retryCollectionWrite() {
        _state.update { it.copy(collectionWritable = true, dismissCollectionWarning = true) }
    }

    fun dismissCollectionWarning() = _state.update { it.copy(dismissCollectionWarning = true) }

    /**
     * Mirror [storeId]'s rows into the offline cache after a successful write.
     *
     * The cache is otherwise only refreshed by a full read, so an edit followed
     * by an app kill reopened offline on the pre-edit count.
     */
    private fun cacheStoreRows(storeId: Long) {
        val s = _state.value
        val source = if (storeId == 0L) s.owned else s.binderOwned
        val rows = source.map { (id, o) ->
            OwnedCard(
                card = id,
                standard = o.normal.toString(),
                foil = o.foil.toString(),
                wish = o.wish.toString(),
                wish_foil = o.wishFoil.toString(),
            )
        }
        storeCache.saveStore(storeId, rows)
        // Binder counts live on the binder list; keep the cached picker in step.
        if (storeId != 0L) storeCache.saveBinders(s.binders)
    }

    fun setOwned(cardId: String, type: String, count: Int, storeId: Long) {
        if (_state.value.session == null) {
            _state.update { it.copy(message = "Log in first") }
            return
        }
        // Offline, the write cannot succeed. Doing it anyway means an optimistic
        // update, a network failure, a rollback and an exception message - so the
        // count flickers and the user is told about DNS. Say what is actually
        // wrong instead.
        if (!NetworkStatus.isOnline(appContext)) {
            _state.update {
                it.copy(message = "You're offline, so your collection cannot be changed. Reconnect and try again.")
            }
            return
        }
        viewModelScope.launch {
            val before = _state.value
            val isBinder = storeId != 0L
            val source = if (isBinder) before.binderOwned else before.owned
            val previous = source[cardId] ?: Owned()
            // Counts "satisfied" the wish once the copies on this row reach it,
            // so the wish is dropped in the same write. A wish is a target:
            // owning the target should leave nothing on the wishlist.
            val ownedAfter = if (type == "foil") {
                previous.normal + count
            } else {
                count + previous.foil
            }
            val wishAfter = if (ownedAfter >= previous.wishTotal) 0 else previous.wish
            val newTotal = if (type == "foil") previous.normal + count else count + previous.foil
            val delta = newTotal - previous.total
            // Optimistic, and keyed the same way the state is, so the badge and
            // the collection row move together. A binder chip's count comes from
            // listbinders, so it is adjusted here too - otherwise the chip keeps
            // reporting the count from before the tap until the next reload.
            val optimisticPrintings = HashMap(source).apply {
                this[cardId] = if (type == "foil") {
                    previous.copy(foil = count, wish = wishAfter)
                } else {
                    previous.copy(normal = count, wish = wishAfter)
                }
            }
            _state.update {
                if (isBinder) {
                    it.copy(
                        binderOwned = optimisticPrintings,
                        binderOwnedByNormal = collapseOwned(optimisticPrintings, it.printingToNormal),
                        binders = it.binders.map { b ->
                            if (b.id == storeId) {
                                b.copy(count = ((b.count ?: 0) + delta).coerceAtLeast(0))
                            } else {
                                b
                            }
                        },
                    )
                } else {
                    it.copy(
                        owned = optimisticPrintings,
                        ownedByNormal = collapseOwned(optimisticPrintings, it.printingToNormal),
                    )
                }
            }
            try {
                // Routing follows the store being edited, not the selected
                // binder: binder 0 is the collection, which has no binder row
                // and must use savebatchcollection.
                if (!isBinder) {
                    // The wish count MUST be echoed: a savebatchcollection row
                    // overwrites everything it does not carry, and a probe proved
                    // an owned edit without `wish` zeroed a server-side wish.
                    // Omitted `wish_foil` is simply ignored by the server, so it
                    // needs no echo.
                    api.saveCollectionCard(cardId, type, count, wish = wishAfter)
                } else {
                    api.saveBinderCard(cardId, type, count, storeId.toInt())
                }
                // Keep the offline snapshot in step with the write; otherwise a
                // kill right after this tap reopens on the pre-edit count.
                cacheStoreRows(storeId)
                if (_state.value.offline) _state.update { it.copy(offline = false) }
            } catch (e: Exception) {
                // Roll back to what we had before this edit.
                val rolledBack = HashMap(source)
                val msg = e.message ?: "Could not save to binder"
                // The API refuses writes with "Binder not found" when the account
                // has no binder, while reads at binder 0 still work. Silently
                // rolling back just made the count bounce, so mark the collection
                // read-only and say why.
                val blocked = msg.contains("Binder not found", ignoreCase = true)
                // A network failure is not the server refusing; it is the request
                // never landing. Saying so is the difference between "the site
                // does not like your account" and "you are on a train".
                val friendly = NetworkStatus.friendlyMessage(e)
                _state.update {
                    val rolledNormal = collapseOwned(rolledBack, it.printingToNormal)
                    it.copy(
                        message = friendly ?: if (blocked) {
                            "This account has no binder, so onepiece.gg is rejecting collection edits. " +
                                "Add a card to your collection on the website once, then retry."
                        } else msg,
                        owned = if (isBinder) it.owned else rolledBack,
                        ownedByNormal = if (isBinder) it.ownedByNormal else rolledNormal,
                        binderOwned = if (isBinder) rolledBack else it.binderOwned,
                        binderOwnedByNormal = if (isBinder) rolledNormal else it.binderOwnedByNormal,
                        binders = if (isBinder) {
                            it.binders.map { b ->
                                if (b.id == storeId) {
                                    b.copy(count = ((b.count ?: 0) - delta).coerceAtLeast(0))
                                } else {
                                    b
                                }
                            }
                        } else {
                            it.binders
                        },
                        collectionWritable = if (blocked) false else it.collectionWritable,
                        offline = friendly != null || it.offline,
                    )
                }
                android.util.Log.w("OpggBinder", "savebindercard failed: $msg", e)
            }
        }
    }

    /** A wishlist tap on a tile: adjusts the card's wish count, clamped at zero. */
    fun quickWish(card: Card, delta: Int) {
        val s = _state.value
        if (s.session == null) {
            _state.update { it.copy(message = "Sign in to edit your wishlist") }
            return
        }
        // Edit whichever printing already carries the wish, or the base one.
        val printing = printingToWishEdit(card.key)
        val current = s.owned[printing]?.wish ?: 0
        val next = (current + delta).coerceAtLeast(0)
        if (next == current) return
        setWished(printing, next)
    }

    /**
     * Bookmark tap: add the card to the wishlist, or take it off.
     *
     * Adding aims for one copy more than the account already holds. A wish is a
     * target count and the list only shows the shortfall, so without that a
     * tapped owned card would be added already satisfied and nothing would
     * appear to happen.
     */
    fun toggleWish(card: Card) {
        val s = _state.value
        if (s.session == null) {
            _state.update { it.copy(message = "Sign in to edit your wishlist") }
            return
        }
        val printing = printingToWishEdit(card.key)
        val cardCounts = s.ownedByNormal[card.key] ?: Owned()
        if (cardCounts.wishTotal > cardCounts.total) {
            // On the list already: take it off.
            setWished(printing, 0)
        } else {
            // Wish counts live per printing while the list works per card, so
            // subtract any wish already parked on other printings to land on a
            // card-level need of exactly one.
            val otherWishes = s.owned
                .filterKeys { it != printing }
                .values.sumOf { it.wishTotal }
            val target = (cardCounts.total + 1 - otherWishes).coerceAtLeast(1)
            setWished(printing, target)
        }
    }

    /**
     * Writes a wishlist count.
     *
     * Routed to the *main collection* regardless of which binder is open: the
     * server has no wish field on binder rows - the website only keeps wishes
     * there, so a wish is visible to every binder and to its "In Wishlist"
     * filter. The currently owned standard count is echoed, mirroring the
     * website's own saves, which always send the full row rather than risking
     * the server blanking an omitted column.
     */
    fun setWished(cardId: String, count: Int) {
        if (_state.value.session == null) {
            _state.update { it.copy(message = "Sign in to edit your wishlist") }
            return
        }
        if (!NetworkStatus.isOnline(appContext)) {
            _state.update {
                it.copy(message = "You're offline, so your wishlist cannot be changed. Reconnect and try again.")
            }
            return
        }
        viewModelScope.launch {
            val before = _state.value
            val previous = before.owned[cardId] ?: Owned()
            val optimisticPrintings = HashMap(before.owned).apply {
                // Only the standard wish is editable from here; a wish_foil can
                // exist on rows created or imported on the website and is shown
                // as part of the total but never silently cleared.
                this[cardId] = previous.copy(wish = count)
            }
            _state.update {
                it.copy(
                    owned = optimisticPrintings,
                    ownedByNormal = collapseOwned(optimisticPrintings, it.printingToNormal),
                )
            }
            try {
                api.saveCollectionCard(cardId, "standard", previous.normal, wish = count)
                cacheStoreRows(0L)
                if (_state.value.offline) _state.update { it.copy(offline = false) }
            } catch (e: Exception) {
                val rolledBack = HashMap(before.owned)
                val msg = e.message ?: "Could not save wishlist"
                val blocked = msg.contains("Binder not found", ignoreCase = true)
                val friendly = NetworkStatus.friendlyMessage(e)
                _state.update {
                    it.copy(
                        message = friendly ?: if (blocked) {
                            "This account has no binder, so onepiece.gg is rejecting collection edits. " +
                                "Add a card to your collection on the website once, then retry."
                        } else msg,
                        owned = rolledBack,
                        ownedByNormal = collapseOwned(rolledBack, it.printingToNormal),
                        collectionWritable = if (blocked) false else it.collectionWritable,
                        offline = friendly != null || it.offline,
                    )
                }
                android.util.Log.w("OpggWishlist", "savecollection wish failed: $msg", e)
            }
        }
    }
}

/**
 * Whether the server has rejected the stored session.
 *
 * The site answers a dead token with HTTP 200 and a plausible-looking empty
 * result rather than a 401, so this has to be worked out from the payload: the
 * server reports a user id of 0 while we hold a real one.
 *
 * A missing server id counts as rejected too - it is what the server sends when
 * it does not recognise the token at all.
 */
internal fun isSessionRejected(storedUserId: Long, serverUserId: Long?): Boolean =
    storedUserId > 0 && (serverUserId ?: 0L) == 0L

/**
 * Display name for the selected card store.
 *
 * Binder 0 is the collection; any other id is a binder. A binder that vanished
 * (deleted from another device) falls back to "Binder" rather than rendering an
 * empty title while the next refresh is in flight.
 */
internal fun binderTitle(binders: List<ApiBinder>, binderId: Long): String =
    when {
        binderId == 0L -> "Collection"
        binderId < 0L -> "All binders"
        else -> binders.firstOrNull { it.id == binderId }?.name?.takeIf { it.isNotBlank() } ?: "Binder"
    }

/**
 * Value of one row: standards at the standard price, foils at the foil price,
 * falling back to the standard price when the foil one is missing or zero.
 * Prices arrive as strings and may be null; anything unparseable counts as 0.
 */
internal fun ownedValue(counts: Owned, standardPrice: String?, foilPrice: String?): Double {
    val std = standardPrice?.toDoubleOrNull() ?: 0.0
    val foil = foilPrice?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: std
    return counts.normal * std + counts.foil * foil
}

/**
 * Moves [id] one slot in [order], returning the original list when it is
 * already at the edge. Used for binder reordering, where the whole id list is
 * sent and position becomes the sort value.
 */
internal fun swapOrder(order: List<Long>, id: Long, up: Boolean): List<Long> {
    val i = order.indexOf(id)
    if (i < 0) return order
    val j = if (up) i - 1 else i + 1
    if (j !in order.indices) return order
    return order.toMutableList().apply {
        val tmp = this[i]
        this[i] = this[j]
        this[j] = tmp
    }
}

/**
 * Swaps a binder with its neighbour *under the same parent*.
 *
 * `reorderbinder` stores the array position as `sort`, and `sort` is only ever
 * compared between siblings, so exchanging the two ids in the flat array is
 * enough: everyone else keeps their index (and children keep theirs, which is
 * irrelevant to sibling order). Without this, moving a nested binder "up"
 * swapped across folder boundaries and visibly did nothing in the picker.
 */
internal fun siblingSwapOrder(binders: List<ApiBinder>, id: Long, up: Boolean): List<Long> {
    fun parentOf(b: ApiBinder): Long? = b.parent_id?.takeIf { it != 0L && it != b.id }
    val me = binders.firstOrNull { it.id == id } ?: return binders.map { it.id }
    val siblings = binders
        .filter { parentOf(it) == parentOf(me) }
        .sortedWith(compareBy({ it.sort ?: Int.MAX_VALUE }, { it.id }))
    val i = siblings.indexOfFirst { it.id == id }
    val j = if (up) i - 1 else i + 1
    if (i < 0 || j !in siblings.indices) return binders.map { it.id }
    val ids = binders.map { it.id }.toMutableList()
    val a = ids.indexOf(siblings[i].id)
    val b = ids.indexOf(siblings[j].id)
    ids[a] = siblings[j].id
    ids[b] = siblings[i].id
    return ids
}

/**
 * Public share link for a binder, matching the site's own builder:
 * `u/<nickname>/binder/<slug>`, falling back to the collection URL by id when a
 * binder has no slug. Null when there is no nickname to build it from.
 */
internal fun binderShareUrl(nickname: String?, binder: ApiBinder): String? {
    val nick = nickname?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    val slug = binder.slug?.takeIf { it.isNotBlank() }
    return if (slug != null) {
        "https://onepiece.gg/u/${enc(nick)}/binder/${enc(slug)}"
    } else {
        "https://onepiece.gg/u/${enc(nick)}/collection?binder=${binder.id}"
    }
}

/**
 * Binders in tree order with their nesting depth.
 *
 * Parents come before their children and each level is ordered by `sort`.
 * Missing parents and self-references are treated as top level, and a cycle
 * cannot recurse forever: each binder is emitted at most once.
 */
internal fun orderWithDepth(binders: List<ApiBinder>): List<Pair<ApiBinder, Int>> {
    val byId = binders.associateBy { it.id }
    val children = binders
        .filter { b -> b.parent_id != null && b.parent_id != 0L && b.parent_id != b.id && byId.containsKey(b.parent_id) }
        .groupBy { it.parent_id!! }
    val roots = binders.filter { b ->
        b.parent_id == null || b.parent_id == 0L || b.parent_id == b.id || !byId.containsKey(b.parent_id)
    }
    val out = ArrayList<Pair<ApiBinder, Int>>(binders.size)
    val seen = HashSet<Long>()
    fun walk(b: ApiBinder, depth: Int) {
        if (!seen.add(b.id)) return
        out += b to depth
        children[b.id]?.sortedBy { it.sort ?: Int.MAX_VALUE }?.forEach { walk(it, depth + 1) }
    }
    roots.sortedBy { it.sort ?: Int.MAX_VALUE }.forEach { walk(it, 0) }
    // Anything left is part of a cycle; append it rather than hide it.
    binders.sortedBy { it.sort ?: Int.MAX_VALUE }.forEach { if (it.id !in seen) walk(it, 0) }
    return out
}

/** Ids nested under [id], cycle-safe. Used to stop a folder being moved into itself. */
internal fun binderDescendantIds(binders: List<ApiBinder>, id: Long): Set<Long> {
    val children = binders.filter { it.parent_id != null && it.parent_id != 0L }.groupBy { it.parent_id!! }
    val out = HashSet<Long>()
    fun walk(parent: Long) {
        children[parent].orEmpty().forEach { child ->
            if (out.add(child.id)) walk(child.id)
        }
    }
    walk(id)
    return out
}

/**
 * Binders that are real, user-managed locations.
 *
 * `onepiece.gg` auto-creates a binder named "Wishlist". In the app it is a fixed
 * destination under Collection (the wishlist view), so it must not appear in the
 * store picker, move destinations, or the manage list.
 */
internal fun List<ApiBinder>.withoutSystemBinders(): List<ApiBinder> =
    filterNot { it.name?.equals("Wishlist", ignoreCase = true) == true }

/** Where a card can be moved to from [currentStore].
 *
 * The collection (0) is a valid destination from a binder, but not listed when
 * already there. System binders are never offered.
 */
internal fun moveDestinations(
    binders: List<ApiBinder>,
    currentStore: Long,
): List<Pair<Long, String>> {
    val out = ArrayList<Pair<Long, String>>()
    if (currentStore != 0L) out += 0L to "Collection"
    binders.withoutSystemBinders()
        .filter { it.id != currentStore }
        .forEach { b -> out += b.id to (b.name?.takeIf { it.isNotBlank() } ?: "Binder") }
    return out
}