package gg.opgg.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import gg.opgg.android.data.SortField
import gg.opgg.android.data.SortSpec
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import gg.opgg.android.data.sortCards
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.network.HttpException
import coil.request.ImageRequest
import coil.size.Size
import gg.opgg.android.data.AppViewModel
import gg.opgg.android.data.ApiBinder
import gg.opgg.android.data.CardSearch
import gg.opgg.android.data.ParsedQuery
import gg.opgg.android.data.binderTitle
import gg.opgg.android.data.binderDescendantIds
import gg.opgg.android.data.binderShareUrl
import gg.opgg.android.data.moveDestinations
import gg.opgg.android.data.orderWithDepth
import gg.opgg.android.data.ownedValue
import gg.opgg.android.data.siblingSwapOrder
import gg.opgg.android.data.withoutSystemBinders
import gg.opgg.android.data.DeckSegment
import gg.opgg.android.data.tap
import gg.opgg.android.data.ICON_BASE
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import gg.opgg.android.data.Card
import gg.opgg.android.data.CardSet
import gg.opgg.android.data.RelatedCard
import gg.opgg.android.data.QField
import gg.opgg.android.data.Suggestion
import gg.opgg.android.data.Owned
import gg.opgg.android.data.UiState
import gg.opgg.android.ui.theme.OpggTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Material 3 scheme, delegating every colour to [AppColors].
 *
 * Kept as a named val because it is referenced across the UI, but the values
 * live in one place only - they used to be duplicated here and in the call
 * sites, which is how a colour could be changed in one file and not another.
 */
internal val Palette = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.AccentOn,
    secondary = AppColors.AccentAlt,
    background = AppColors.Background,
    surface = AppColors.Surface,
    onSurface = AppColors.OnSurface,
)

/**
 * Rotation recreates the activity, so a plain rememberLazyGridState() jumps back
 * to the top. This Compose version has no rememberSaveableLazyGridState, so the
 * first visible index is saved and restored manually.
 */
@Composable
private fun PersistGridScroll(state: androidx.compose.foundation.lazy.grid.LazyGridState) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.collect { index = it }
    }
    LaunchedEffect(state) {
        if (index > 0) runCatching { state.scrollToItem(index) }
    }
}

/**
 * Navigation screens. A real stack rather than a pile of booleans: booleans
 * cannot express "back from this card should return to the related list", which
 * is exactly what kept happening.
 */
sealed interface OpggScreen {
    data object Tabs : OpggScreen

    /** [list] is what horizontal swipe walks. */
    data class CardDetail(
        val card: Card,
        val list: List<Card>,
        /**
         * Which store this page edits: 0 for the collection, a binder id when
         * opened from a binder. Cards reached from the tab/database default to 0,
         * so browsing the database never writes into whichever binder is selected.
         */
        val storeId: Long = 0L,
    ) : OpggScreen

    /** Full list of cards related to [seed]. */
    data class RelatedList(val seed: Card) : OpggScreen

    /** Deck detail is a screen, not a sheet: tapping a card pushes a new
     *  CardDetail, and a pushed screen would render behind a modal dialog. */
    data class DeckDetail(val deck: gg.opgg.android.data.Deck) : OpggScreen

    /** The on-device deck editor. */
    data class DeckEditor(val deckId: String) : OpggScreen

    /** Cards a local deck runs that the collection does not cover. */
    data class DeckMissing(val deckId: String) : OpggScreen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel, onSignIn: () -> Unit, initialScreen: String? = null) {
    OpggTheme {
        Box(Modifier.fillMaxSize()) {
        val state by vm.state.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableStateOf(0) }
        var filtersOpen by rememberSaveable { mutableStateOf(false) }
        var sortOpen by rememberSaveable { mutableStateOf(false) }
        var searchOpen by rememberSaveable { mutableStateOf(false) }
        // One enum for the four Decks segments, not two booleans: with flags,
        // "builder and meta both open" was representable and reachable by a tap.
        var deckSegment by rememberSaveable { mutableStateOf(DeckSegment.PUBLIC) }
        // Collection tab: 0 = owned, 1 = wishlist. An int, not a boolean, so a
        // third segment (e.g. "for trade") can be added later without touching
        // the call sites.
        var collectionSegment by rememberSaveable { mutableIntStateOf(0) }
        // Store picker and binder management live up here now: the picker is the
        // app-bar title, not a permanent row of chips.
        var storeMenuOpen by remember { mutableStateOf(false) }
        var showCreateBinder by remember { mutableStateOf(false) }
        var binderActionFor by remember { mutableStateOf<ApiBinder?>(null) }
        var renameBinderFor by remember { mutableStateOf<ApiBinder?>(null) }
        var deleteBinderFor by remember { mutableStateOf<ApiBinder?>(null) }
        var manageBinders by remember { mutableStateOf(false) }
        var shareBinderFor by remember { mutableStateOf<ApiBinder?>(null) }
        var moveBinderFor by remember { mutableStateOf<ApiBinder?>(null) }
        // onepiece.gg auto-creates an empty binder called "Wishlist"; it is a
        // fixed destination under Collection, not a user-managed binder, so it
        // never appears in the picker, destinations or the manage list.
        fun visibleBinders() = state.binders.withoutSystemBinders()
        fun pickStore(binderId: Long, wishlist: Boolean) {
            storeMenuOpen = false
            collectionSegment = if (wishlist) 1 else 0
            if (state.binderId != binderId) vm.selectBinder(binderId)
        }
        // Owned here because the Import action now lives in this tab's app bar.
        var builderImport by remember { mutableStateOf(false) }
        var builderImportText by remember { mutableStateOf("") }
        // Grid scroll is hoisted so it survives both opening a detail page and a
        // rotation. rememberSaveableLazyGridState does not exist in this Compose
        // version, so the first visible index is persisted by hand.
        val cardsGridState = rememberLazyGridState()
        val collectionGridState = rememberLazyGridState()
        // Hoisted for the same reason as the other two: the editor leaves
        // composition when a card page is pushed, and a grid state created inside
        // it was destroyed, so scrolling reset to the top on the way back.
        val editorGridState = rememberLazyGridState()
        // Hoisted like the grids: opening a card from the Meta list replaces the
        // whole screen, which would otherwise discard the list's scroll state
        // and send you back to the top on return.
        val metaListState = rememberLazyListState()
    PersistGridScroll(cardsGridState)
    PersistGridScroll(collectionGridState)
    PersistGridScroll(editorGridState)

    // Open Coil's disk cache and pull the first screenful of thumbnails into
    // memory, as soon as there are cards to fetch - and not one frame later than
    // that. Left to the grid's own requests, the first screenful of tiles all
    // blocks on the lazy disk-cache lock and the whole grid pops in at once; see
    // CardImages for the logcat that gave this away.
    // Read before the coroutine: LocalContext is only valid while composing.
    val imageCtx = LocalContext.current.applicationContext
    LaunchedEffect(state.cards.size) {
        val urls = state.visible.mapNotNull { it.imageUrl }
        if (urls.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            CardImages.warm(imageCtx, CardImageLoaderHolder.get(), urls)
        }
    }

        // Rotation recreates the activity, so every remember{} here was reset:
        // back to the Cards tab, sort sheet closed, filters cleared. The stack
        // is saved as tags and rebuilt against the card database on restore.
        val stackSaver = remember(vm) {
            listSaver<List<OpggScreen>, String>(
                save = { stack ->
                    stack.map { s ->
                        when (s) {
                            is OpggScreen.Tabs -> "tabs"
                            is OpggScreen.CardDetail -> "card:${s.card.key}"
                            is OpggScreen.RelatedList -> "related:${s.seed.key}"
                        is OpggScreen.DeckDetail -> "deck:${s.deck.slug}"
                        is OpggScreen.DeckEditor -> "editor:${s.deckId}"
                        is OpggScreen.DeckMissing -> "missing:${s.deckId}"
                        }
                    }
                },
                restore = { tags ->
                    val s = vm.state.value
                    val restored = tags.mapNotNull { tag ->
                        val parts = tag.split(':', limit = 2)
                        val arg = parts.getOrNull(1)
                        when (parts[0]) {
                            "tabs" -> OpggScreen.Tabs
                            "card" -> s.cards.firstOrNull { it.key == arg }
                                ?.let { OpggScreen.CardDetail(it, s.visible) }
                            "related" -> s.cards.firstOrNull { it.key == arg }
                                ?.let { OpggScreen.RelatedList(it) }
                            "deck" -> s.decks.firstOrNull { it.slug == arg }
                                ?.let { OpggScreen.DeckDetail(it) }
                            "editor" -> if (s.localDecks.any { it.id == arg }) {
                                OpggScreen.DeckEditor(arg.orEmpty())
                            } else {
                                null
                            }
                            "missing" -> if (s.localDecks.any { it.id == arg }) {
                                OpggScreen.DeckMissing(arg.orEmpty())
                            } else {
                                null
                            }
                            else -> null
                        }
                    }
                    if (restored.isEmpty()) listOf(OpggScreen.Tabs) else restored
                },
            )
        }
        var stack by rememberSaveable(stateSaver = stackSaver) {
            mutableStateOf(listOf(OpggScreen.Tabs))
        }

        val current = stack.last()
        val snackbarHostState = remember { SnackbarHostState() }
        fun push(screen: OpggScreen) { stack = stack + screen }
        fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
        fun pushDeckFirst() { state.decks.firstOrNull()?.let { push(OpggScreen.DeckDetail(it)) } }

        // Debug deep link, applied once the data it needs has arrived.
        // A plain LaunchedEffect keyed on state sizes re-ran every time the card
        // or deck count changed, creating a new deck each time and racing the
        // editor it had just opened. `handled` makes it fire exactly once.
        var handled by remember { mutableStateOf(false) }
        LaunchedEffect(initialScreen, state.cards.size, state.decks.size) {
            val spec = initialScreen ?: return@LaunchedEffect
            if (handled) return@LaunchedEffect
            if (stack.size > 1) return@LaunchedEffect
        when {
            spec.startsWith("tab:") -> {
                handled = true
                tab = spec.substringAfter(':').toIntOrNull() ?: 0
            }
            // The search box is behind a tap, which cannot be synthesised, so
            // every dead-end search - and therefore every empty state - needed
            // its own spec before it could be looked at on a device.
            //
            // No separator between the prefix and the query: the query itself is
            // full of colons (`c:red`), and anything else has to be typed through
            // `adb shell`, where `|` and spaces are eaten by the device's shell.
            spec.startsWith("query:") -> {
                // Wait for the card database. Filtering against an empty source
                // yields an empty grid, and the database load that follows resets
                // `visible` to everything, silently discarding the query. Same
                // reason `card:` and `editor:` do not mark themselves handled
                // until their target resolves.
                if (state.cards.isEmpty()) return@LaunchedEffect
                handled = true
                tab = 0
                searchOpen = true
                vm.setQuery(spec.substringAfter(':'))
            }
                // The Builder segment lives behind a tap on the Decks tab, which
                // cannot be synthesised, so its list needs its own spec.
                spec == "builder" -> {
                    handled = true
                    tab = 2
                    deckSegment = DeckSegment.BUILDER
                }
                spec == "meta" -> {
                    handled = true
                    tab = 2
                    deckSegment = DeckSegment.META
                }
                spec.startsWith("card:") -> {
                    val c = state.cards.firstOrNull { it.key == spec.substringAfter(':') } ?: return@LaunchedEffect
                    handled = true
                    push(OpggScreen.CardDetail(c, state.visible))
                }
                spec.startsWith("related:") -> {
                    val c = state.cards.firstOrNull { it.key == spec.substringAfter(':') } ?: return@LaunchedEffect
                    handled = true
                    push(OpggScreen.RelatedList(c))
                }
                spec.startsWith("editor:") -> {
                    val wanted = spec.substringAfter(':')
                    // Only "new" may create. Creating for an id that simply has
                    // not loaded yet used to wipe the store.
                    val deck = if (wanted == "new") {
                        val d = gg.opgg.android.data.LocalDeck(
                            id = vm.newDeckId(),
                            name = "New deck",
                            createdAt = System.currentTimeMillis(),
                        )
                        vm.addLocalDeck(d)
                        d
                    } else {
                        state.localDecks.firstOrNull { it.id == wanted } ?: return@LaunchedEffect
                    }
                    // Marked handled only once the deck actually resolved. Setting
                    // it before the lookup meant a spec naming a deck that had not
                    // finished loading was dropped for good, and the launch quietly
                    // fell back to the Cards tab instead of the editor.
                    handled = true
                    tab = 2
                    deckSegment = DeckSegment.BUILDER
                    push(OpggScreen.DeckEditor(deck.id))
                }
                spec.startsWith("missing:") -> {
                    val wanted = spec.substringAfter(':')
                    val deck = state.localDecks.firstOrNull { it.id == wanted } ?: return@LaunchedEffect
                    handled = true
                    push(OpggScreen.DeckMissing(deck.id))
                }
                spec.startsWith("deckfirst") -> {
                    handled = true
                    tab = 2
                    if (state.decks.isEmpty()) vm.loadDecks(reset = true) else pushDeckFirst()
                }
                spec.startsWith("deck:") -> {
                    val slug = spec.substringAfter(':')
                    // The deck list is only fetched when the Decks tab is on
                    // screen, so a spec naming a deck has to ask for it - and
                    // this branch does not mark itself handled until the deck
                    // is actually found, so the effect runs again when the list
                    // arrives.
                    if (state.decks.isEmpty()) vm.loadDecks(reset = true)
                    val d = state.decks.firstOrNull { it.slug == slug } ?: return@LaunchedEffect
                    handled = true
                    push(OpggScreen.DeckDetail(d))
                }
            }
        }

        // Previously the message was consumed and thrown away, so "log in first"
        // and save failures were invisible.
        LaunchedEffect(state.message) {
            val m = state.message
            if (m != null) {
                snackbarHostState.showSnackbar(m)
                vm.consumeMessage()
            }
        }

        // Swiping moves *within* a card page, so it must REPLACE the top entry.
        // Pushing here made every swipe add a screen, so back had to be pressed
        // once per card swiped through before reaching the tabs.
        fun replaceTopWithCard(card: Card, list: List<Card>) {
            val top = stack.last()
            if (top is OpggScreen.CardDetail && top.card.key == card.key) return
            // Paging must not lose the store the page was opened from.
            val storeId = (top as? OpggScreen.CardDetail)?.storeId ?: 0L
            stack = stack.dropLast(1) + OpggScreen.CardDetail(card, list, storeId)
        }

        if (builderImport) {
            ImportDeckDialog(
                text = builderImportText,
                onTextChange = { builderImportText = it },
                onDismiss = { builderImport = false },
                onImport = { raw ->
                    val r = vm.importDeckCode(raw)
                    builderImport = false
                    if (r.deck != null) {
                        val created = r.deck
                        builderImportText = ""
                        if (r.unresolved.isNotEmpty()) {
                            val shown = r.unresolved.take(6).joinToString()
                            val more = r.unresolved.size - 6
                            vm.postMessage(
                                "Imported ${created.cards.size} of " +
                                    "${created.cards.size + r.unresolved.size} cards. " +
                                    "Not recognised: $shown" +
                                    (if (more > 0) " and $more more" else "") + ".",
                            )
                        }
                        push(OpggScreen.DeckEditor(created.id))
                    } else {
                        vm.postMessage(r.error ?: "That deck code could not be read.")
                    }
                },
            )
        }

        // Binder dialogs live at the app root now, because the picker that opens
        // them is the app-bar title. Composing them here means they are never
        // skipped by a screen's empty-state early return.
        if (showCreateBinder) {
            BinderNameDialog(
                title = "New binder",
                initial = "",
                onConfirm = { name ->
                    showCreateBinder = false
                    vm.createBinder(name)
                },
                onDismiss = { showCreateBinder = false },
            )
        }
        renameBinderFor?.let { b ->
            BinderNameDialog(
                title = "Rename binder",
                initial = b.name.orEmpty(),
                onConfirm = { name ->
                    renameBinderFor = null
                    vm.renameBinder(b.id, name)
                },
                onDismiss = { renameBinderFor = null },
            )
        }
        deleteBinderFor?.let { b ->
            DeleteBinderDialog(
                binder = b,
                onConfirm = {
                    deleteBinderFor = null
                    vm.deleteBinder(b.id)
                },
                onDismiss = { deleteBinderFor = null },
            )
        }
        binderActionFor?.let { b ->
            BinderActionsDialog(
                binder = b,
                onRename = { binderActionFor = null; renameBinderFor = b },
                onDelete = { binderActionFor = null; deleteBinderFor = b },
                onColor = { hex ->
                    binderActionFor = null
                    vm.setBinderColor(b.id, hex)
                },
                onShare = { binderActionFor = null; shareBinderFor = b },
                onMoveUnder = { binderActionFor = null; moveBinderFor = b },
                onDismiss = { binderActionFor = null },
            )
        }
        shareBinderFor?.let { b ->
            val live = state.binders.firstOrNull { it.id == b.id } ?: b
            ShareBinderDialog(
                binder = live,
                nickname = state.session?.nick,
                onToggle = { vm.setBinderPublic(live.id, it) },
                onDismiss = { shareBinderFor = null },
            )
        }
        moveBinderFor?.let { b ->
            val all = state.binders
            val excluded = binderDescendantIds(all, b.id) + b.id
            val options = buildList {
                add(Triple(0L, "Top level", 0))
                orderWithDepth(all.filterNot { it.id in excluded }.withoutSystemBinders()).forEach { (cb, depth) ->
                    add(Triple(cb.id, cb.name ?: "Binder", depth))
                }
            }
            MoveBinderDialog(
                binder = b,
                options = options,
                onMove = { parent ->
                    moveBinderFor = null
                    vm.setBinderParent(b.id, parent)
                },
                onDismiss = { moveBinderFor = null },
            )
        }
        if (manageBinders) {
            // Wishlist (and any future system binder) is pinned under Collection:
            // it is not part of the tree the user manages and cannot be dragged.
            ManageBindersDialog(
                binders = state.binders.withoutSystemBinders(),
                onMove = { id, up -> vm.reorderBinders(siblingSwapOrder(state.binders, id, up)) },
                onDismiss = { manageBinders = false },
            )
        }

        // Back walks the stack. The modal sheets are dialog windows and consume
        // back themselves, so they are not part of this.
        BackHandler(enabled = stack.size > 1 || searchOpen) {
            if (stack.size > 1) pop() else searchOpen = false
        }

        when (val screen = current) {
            is OpggScreen.CardDetail -> CardDetailPage(
                card = screen.card,
                list = screen.list,
                storeId = screen.storeId,
                state = state,
                vm = vm,
                onBack = { pop() },
                onNavigate = { replaceTopWithCard(it, screen.list) },
                onOpenRelated = { push(OpggScreen.RelatedList(screen.card)) },
            )

            is OpggScreen.RelatedList -> RelatedListScreen(
                seed = screen.seed,
                state = state,
                vm = vm,
                onBack = { pop() },
                onOpenCard = { card, all -> push(OpggScreen.CardDetail(card, all)) },
            )

            is OpggScreen.DeckDetail -> DeckDetailPage(
                deck = screen.deck,
                state = state,
                vm = vm,
                onBack = { pop() },
                onOpenCard = { card, all -> push(OpggScreen.CardDetail(card, all)) },
            )

            is OpggScreen.DeckEditor -> DeckEditorScreen(
                deckId = screen.deckId,
                vm = vm,
                onBack = { pop() },
                onOpenFilters = { filtersOpen = true },
                onOpenCard = { card, all -> push(OpggScreen.CardDetail(card, all)) },
                gridState = editorGridState,
            )

            is OpggScreen.DeckMissing -> DeckMissingScreen(
                deckId = screen.deckId,
                vm = vm,
                onBack = { pop() },
                onOpenCard = { card, all -> push(OpggScreen.CardDetail(card, all)) },
            )

            OpggScreen.Tabs -> Unit
        }

        if (current is OpggScreen.Tabs) {

        Scaffold(
            containerColor = Palette.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppColors.Surface,
                        titleContentColor = Color.White,
                        actionIconContentColor = AppColors.IconInactive,
                    ),
                      title = {
                          val builder = tab == 2 && deckSegment == DeckSegment.BUILDER
                          val meta = tab == 2 && deckSegment == DeckSegment.META
                          val titleText = when {
                              meta -> "Metagame"
                              builder -> "Builder"
                              else -> when (tab) {
                                  0 -> "Cards (${state.visible.size})"
                                  1 -> if (collectionSegment == 1 && state.binderId == 0L) {
                                      "Wishlist (${state.wishGroups.size})"
                                  } else {
                                      "${binderTitle(state.binders, state.binderId)} (${state.activeStoreCount})"
                                  }
                                  2 -> "Decks (${state.decks.size})"
                                  else -> "Account"
                              }
                          }
                          if (tab == 1) {
                              // The collection's store picker IS the title. The
                              // quantity is deliberately not here: it sits in a
                              // row under the app bar, where it does not compete
                              // with the four action icons for width.
                              val wishlistNow = collectionSegment == 1 && state.binderId == 0L
                              val storeName = if (wishlistNow) "Wishlist" else binderTitle(state.binders, state.binderId)
                              Box {
                                  Row(
                                      Modifier
                                          .clip(RoundedCornerShape(8.dp))
                                          .clickable { storeMenuOpen = true }
                                          .padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                      verticalAlignment = Alignment.CenterVertically,
                                  ) {
                                      Text(
                                          storeName,
                                          fontWeight = FontWeight.Bold,
                                          fontSize = 18.sp,
                                          maxLines = 1,
                                          overflow = TextOverflow.Ellipsis,
                                          modifier = Modifier.weight(1f, fill = false),
                                      )
                                      Icon(
                                          Icons.Default.ArrowDropDown,
                                          contentDescription = "Choose collection or binder",
                                          tint = AppColors.IconInactive,
                                      )
                                  }
                                  DropdownMenu(
                                      expanded = storeMenuOpen,
                                      onDismissRequest = { storeMenuOpen = false },
                                  ) {
                                      MenuRow(onClick = { pickStore(0, wishlist = false) }) {
                                          Text("Collection")
                                      }
                                      MenuRow(onClick = { pickStore(0, wishlist = true) }) {
                                          Text("Wishlist")
                                      }
                                      MenuRow(onClick = { pickStore(-1L, wishlist = false) }) {
                                          Text("All binders")
                                      }
                                      if (visibleBinders().isNotEmpty()) HorizontalDivider()
                                      // Tree order with indentation: children under
                                      // their parent, each level by sort. The indent
                                      // lives *inside* the menu item so the whole row
                                      // stays tappable.
                                      orderWithDepth(visibleBinders()).forEach { (b, depth) ->
                                          Row(verticalAlignment = Alignment.CenterVertically) {
                                              MenuRow(
                                                  onClick = { pickStore(b.id, wishlist = false) },
                                                  modifier = Modifier.weight(1f),
                                              ) {
                                                  TreeIndent(depth)
                                                  BinderDot(b.color)
                                                  Text(
                                                      listOfNotNull(b.name, b.count?.let { "($it)" })
                                                          .joinToString(" "),
                                                  )
                                              }
                                              IconButton(
                                                  onClick = {
                                                      storeMenuOpen = false
                                                      binderActionFor = b
                                                  },
                                                  modifier = Modifier.size(36.dp),
                                              ) {
                                                  Icon(
                                                      Icons.Default.MoreVert,
                                                      contentDescription = "Binder actions",
                                                      tint = AppColors.IconInactive,
                                                      modifier = Modifier.size(20.dp),
                                                  )
                                              }
                                          }
                                      }
                                      HorizontalDivider()
                                      MenuRow(onClick = {
                                          storeMenuOpen = false
                                          showCreateBinder = true
                                      }) { Text("New binder") }
                                      MenuRow(onClick = {
                                          storeMenuOpen = false
                                          manageBinders = true
                                      }) { Text("Manage binders") }
                                  }
                              }
                          } else {
                              Text(titleText, fontWeight = FontWeight.Bold)
                          }
                      },
                      actions = {
                          if (tab == 2 && deckSegment == DeckSegment.META) {
                              // The meta screen has its own refresh next to its
                              // header; this keeps the top bar honest about what
                              // it is refreshing.
                              IconButton(onClick = { vm.refreshMeta() }) {
                                  Icon(
                                      Icons.Default.Refresh,
                                      contentDescription = "Refresh metagame",
                                  )
                              }
                              return@TopAppBar
                          }
                          if (tab == 2 && deckSegment == DeckSegment.BUILDER) {
                              // The Builder's own bar was a second one stacked
                              // under this one, so the Import / New deck actions
                              // live here instead and the list starts sooner.
                              // Import pastes a deck code, so it gets a paste
                              // icon rather than another "+".
                              IconButton(onClick = { builderImport = true }) {
                                  Icon(
                                      Icons.Default.ContentPaste,
                                      contentDescription = "Import deck code",
                                  )
                              }
                              IconButton(
                                  onClick = {
                                      val d = vm.createLocalDeck()
                                      push(OpggScreen.DeckEditor(d))
                                  },
                              ) {
                                  Icon(Icons.Default.Add, contentDescription = "New deck")
                              }
                              return@TopAppBar
                          }
                          if (tab == 0 || tab == 1) {
                            // Search and filters apply to both the Cards and the
                            // Collection grid, so they share one control set.
                            IconButton(onClick = { searchOpen = !searchOpen }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = if (searchOpen) "Hide search" else "Search",
                                    tint = if (searchOpen) Palette.primary else AppColors.IconInactive,
                                )
                            }
                            IconButton(onClick = { sortOpen = true }) {
                                Icon(
                                    Icons.Default.Sort,
                                    contentDescription = "Sort",
                                    tint = if (state.sort.field != SortField.NONE) Palette.primary else AppColors.IconInactive,
                                )
                            }
                            IconButton(onClick = { filtersOpen = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filters")
                            }
                        }
                        IconButton(
                            onClick = {
                                when (tab) {
                                    0 -> vm.loadCards()
                                    1 -> vm.loadBinder()
                                    2 -> vm.loadDecks(reset = true)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                )
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {

                // The content below uses fillMaxSize(), which inside a Column claims
                // the full height and would squeeze BottomTabs to nothing. weight(1f)
                // bounds the content and leaves the tab bar its own row.
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    if (tab == 0) {
                        // A Column, not a Box: a Box stacks children, so the
                        // fillMaxSize() card grid was drawing over the search field.
                        if (searchOpen) {
                            SearchBar(
                                query = state.query,
                                onQueryChange = vm::setQuery,
                                onOpenFilters = { filtersOpen = true },
                                unknown = state.parsed.unknownTokens,
                                activeTerms = state.parsed.terms.size,
                                suggestionsFor = vm::suggestionsFor,
                                applySuggestion = vm::applySuggestion,
                            )
                        }
                        ActiveFilterChips(state, vm)
                        // Must appear on the Cards tab too: that is where the +/-
                        // controls live, so a notice shown only on Collection
                        // would never be seen by the person who clicked +.
                        if (!state.collectionWritable && !state.dismissCollectionWarning) {
                            CollectionWriteBlockedBanner(
                                onRetry = { vm.retryCollectionWrite() },
                                onDismiss = { vm.dismissCollectionWarning() },
                            )
                        }
                        // Offline, the card list is still perfectly usable - it is
                        // cached - but the +/- on each tile cannot be saved, and
                        // the user should be told that before they try.
                        if (state.offline) {
                            OfflineNotice()
                        }
                        val progress = state.cardsProgress
                        if (progress != null) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    "Downloading card database  ${(progress * 100).toInt()}%  ·  3.1 MB, first run only",
                                    color = AppColors.TextDim,
                                    fontSize = AppType.meta,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        } else if (state.loadingCards) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        if (state.cards.isEmpty() && (state.loadingCards || state.cardsProgress != null)) {
                            // A skeleton, not a spinner: this grid is the first thing
                            // on screen after launch, and holding its shape means the
                            // cards land into place instead of appearing under a jump.
                            SkeletonCardGrid()
                        } else {
                            CardsGrid(
                                cards = state.visible,
                                state = state,
                                vm = vm,
                                // The most common dead end in the app, so it is the one
                                // that gets a real action: the filter sheet works by
                                // editing the search text, so clearing the query clears
                                // the filters with it.
                                emptyState = {
                                    EmptyState(
                                        icon = Icons.Outlined.SearchOff,
                                        title = "No cards match",
                                        // Formatted, because the raw 3063 reads as a
                                        // typo next to the "Cards (3063)" title.
                                        body = "Nothing in the ${"%,d".format(state.cards.size)} card database matches your search and filters.",
                                        action = {
                                            TextButton(onClick = { vm.clearQuery() }) {
                                                Text(
                                                    "Clear search and filters",
                                                    color = AppColors.Accent,
                                                    fontSize = AppType.body,
                                                )
                                            }
                                        },
                                    )
                                },
                                gridState = cardsGridState,
                                onClick = { card -> push(OpggScreen.CardDetail(card, state.visible)) },
                            )
                        }
                    } else if (tab == 1) {
                        // Same search field and filter chips as the Cards tab.
                        if (searchOpen) {
                            SearchBar(
                                query = state.query,
                                onQueryChange = vm::setQuery,
                                onOpenFilters = { filtersOpen = true },
                                unknown = state.parsed.unknownTokens,
                                activeTerms = state.parsed.terms.size,
                                suggestionsFor = vm::suggestionsFor,
                                applySuggestion = vm::applySuggestion,
                            )
                        }
                        ActiveFilterChips(state, vm)
                        if (!state.collectionWritable && !state.dismissCollectionWarning) {
                            CollectionWriteBlockedBanner(
                                onRetry = { vm.retryCollectionWrite() },
                                onDismiss = { vm.dismissCollectionWarning() },
                            )
                        }
                        // There is deliberately no separate Owned/Wishlist row
                        // here: that was a content filter on the collection
                        // stacked above a store picker, so "Owned" looked like a
                        // binder. Store and Wishlist are now one destination row
                        // inside CollectionScreen.
                        CollectionScreen(
                            state = state,
                            vm = vm,
                            onSignIn = onSignIn,
                            gridState = collectionGridState,
                            wishlist = collectionSegment == 1 && state.binderId == 0L,
                        ) { card, shown ->
                            // Page through exactly the list that is on screen, and
                            // keep editing the store it came from.
                            push(OpggScreen.CardDetail(card, shown, state.binderId))
                        }
                    } else if (tab == 2) {
                        // Five tabs did not fit: "Account" was cut off the bar.
                        // The builder is a view of the same feature, so it lives
                        // here as a third segment rather than a fifth tab.
                        DecksScreen(
                            state = state,
                            vm = vm,
                            segment = deckSegment,
                            onSelectSegment = { target ->
                                // Receiver is the CURRENT segment, the argument is
                                // the one tapped. These were swapped, so this
                                // computed current.tap(current) and every tap was
                                // silently a no-op - the chips fired, the state
                                // never moved.
                                val next = deckSegment.tap(target, state.decksMine)
                                deckSegment = next.segment
                                if (next.decksMine != state.decksMine) vm.showMyDecks(next.decksMine)
                            },
                            onOpenDeck = { d -> push(OpggScreen.DeckDetail(d)) },
                            // Card pages are handed the archetype's own staples as
                            // the swipe list, so paging through a deck's core walks
                            // that deck rather than the whole database.
                            onOpenCard = { card, list -> push(OpggScreen.CardDetail(card, list)) },
                            metaListState = metaListState,
                        ) {
                            BuilderScreen(
                        vm = vm,
                        onEdit = { id -> push(OpggScreen.DeckEditor(id)) },
                        onMissing = { id -> push(OpggScreen.DeckMissing(id)) },
                    )
                        }
                    } else {
                        AccountScreen(state, vm, onSignIn)
                    }
                }

                BottomTabs(tab) { tab = it }
            }
        }
        }

        // Deliberately OUTSIDE the "is Tabs" block above: these were composed
        // inside it once, which is why the deck editor's Filter icon set
        // `filtersOpen` but the sheet never appeared - the block is skipped
        // whenever a pushed screen is current. They are read-only state
        // consumers, so composing them on any screen is safe.
        if (filtersOpen) {
            // Opened from the deck editor, the sheet edits the editor's own
            // query; anywhere else it edits the Cards tab's.
            FilterSheet(
                state,
                vm,
                editor = current is OpggScreen.DeckEditor,
            ) { filtersOpen = false }
        }
        if (sortOpen) {
            SortSheet(state, vm) { sortOpen = false }
        }

        // Every failure used to be swallowed: the state existed and the effect
        // called showSnackbar, but no host was ever composed, so the tray
        // suspended forever and the message was dropped when state cleared.
        // That made "New binder" (duplicate name, timeout) look like a dead tap.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (current is OpggScreen.Tabs) 80.dp else 24.dp),
            snackbar = { data ->
                // Material's default is inverseSurface, which the dark theme
                // resolves to a near-white bar; use the app's own raised panel.
                Snackbar(
                    snackbarData = data,
                    containerColor = AppColors.SurfaceRaised,
                    contentColor = AppColors.OnSurface,
                )
            },
        )
        }
    }
}

@Composable
private fun BottomTabs(current: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Surface)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf("Cards", "Collection", "Decks", "Account").forEachIndexed { i, label ->
            Text(
                label,
                color = if (i == current) Palette.primary else AppColors.IconInactive,
                fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
internal fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    unknown: List<String> = emptyList(),
    activeTerms: Int = 0,
    suggestionsFor: (String) -> List<Suggestion> = { emptyList() },
    applySuggestion: (Suggestion) -> Unit = {},
) {
    val suggestions = remember(query) { suggestionsFor(query) }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activeTerms > 0) {
                        Text(
                            "$activeTerms",
                            color = Palette.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onOpenFilters() }.padding(horizontal = 4.dp),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                }
            },
        )
        if (suggestions.isNotEmpty()) {
            SuggestionList(suggestions) { applySuggestion(it) }
        }
        if (unknown.isNotEmpty()) {
            Text(
                "Not a filter, searched as text: ${unknown.joinToString(" ")}",
                color = AppColors.Warning,
                fontSize = AppType.meta,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}

@Composable
private fun SuggestionList(suggestions: List<Suggestion>, onPick: (Suggestion) -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .heightIn(max = 210.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceRaised),
    ) {
        LazyColumn {
            items(suggestions, key = { it.insert }) { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(s) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.label, fontSize = AppType.title, color = AppColors.OnSurface, modifier = Modifier.weight(1f))
                    Text(s.hint, fontSize = AppType.meta, color = AppColors.TextDim)
                }
            }
        }
    }
}

@Composable
internal fun ActiveFilterChips(
    state: UiState,
    vm: AppViewModel,
    /** The editor passes its own parsed query; defaults to the Cards tab's. */
    parsed: ParsedQuery = state.parsed,
    onClear: () -> Unit = { vm.clearQuery() },
) {
    val chips = parsed.terms.map { term ->
        val prefix = if (term.negated) "-" else ""
        "$prefix${term.field.canonical}:${term.values.joinToString(",")}"
    }
    val words = parsed.words
    if (chips.isEmpty() && words.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (words.isNotEmpty()) {
            Text(
                "\"${words.joinToString(" ")}\"",
                color = AppColors.TextDim,
                fontSize = AppType.meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        chips.take(2).forEach { c ->
            Text(
                c,
                color = Palette.primary,
                fontSize = AppType.meta,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.SurfaceSelected)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (chips.size > 2) {
            Text("+${chips.size - 2}", color = AppColors.IconInactive, fontSize = AppType.meta)
        }
        TextButton(onClick = onClear) { Text("Clear", fontSize = AppType.meta) }
    }
}

/**
 * The card grid, shared by the Cards and Collection tabs so both look
 * identical. Only the supplied list differs: the Cards tab passes the whole
 * database (or its search results), Collection passes just the owned cards.
 */
@Composable
private fun CardsGrid(
    cards: List<Card>,
    state: UiState,
    vm: AppViewModel,
    /**
     * True inside the wishlist view: the tiles show wish counts and their +/-
     * move the wish instead of the owned count. The owned badge still renders,
     * because "you already own 3 of the 4 you want" is what a wishlist tells you.
     */
    wishMode: Boolean = false,
    /**
     * What to draw when there is nothing to show.
     *
     * A composable lambda rather than a message string, because an empty state
     * needs an icon, a title and usually an action, and a bare string cannot
     * carry any of that. Each caller also knows a different reason for being
     * empty, so the copy cannot be shared either.
     */
    emptyState: @Composable () -> Unit,
    gridState: LazyGridState,
    onClick: (Card) -> Unit,
    /**
     * Which store the ± edits: 0 for the collection, a binder id when the
     * Collection tab is showing a binder. The Cards tab always uses 0.
     */
    storeId: Long = 0L,
    /** Bulk-move selection. Only the Collection tab uses this. */
    selectionMode: Boolean = false,
    selectedKeys: Set<String> = emptySet(),
    onToggleSelect: ((Card) -> Unit)? = null,
    /** False for the read-only all-binders aggregate. */
    editable: Boolean = true,
) {
    // Press-and-hold preview. Unlike the detail page's loupe, this is a peek:
    // it grows while the finger is down and shrinks away on release, so the
    // grid keeps its place and nothing has to be dismissed.
    var peekCard by remember { mutableStateOf<Card?>(null) }
    val peekProgress = remember { Animatable(0f) }
    val peekScope = rememberCoroutineScope()

    fun startPeek(card: Card) {
        if (peekCard != null) return
        peekCard = card
        peekScope.launch { peekProgress.animateTo(1f, animationSpec = tween(120)) }
    }

    fun endPeek() {
        if (peekCard == null) return
        peekScope.launch {
            peekProgress.animateTo(0f, animationSpec = tween(150))
            peekCard = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.cardsError != null -> ErrorState(
                state.cardsError,
                body = "The card list could not be loaded. Pull to refresh, or check the server is reachable.",
            )
            cards.isEmpty() -> emptyState()
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cards, key = { it.key }) { card ->
                    if (wishMode) {
                        // The wish is edited on the printing it already lives on -
                        // the same rule the owned ± follow - so a wish held on a
                        // reprint never spawns a second row.
                        val printing = vm.printingToWishEdit(card.key)
                        val counts = state.owned[printing] ?: Owned()
                        // The list is a shortfall list, so the tile speaks in copies
                        // still needed, not in the raw wish target.
                        val cardCounts = state.ownedByNormal[card.key] ?: Owned()
                        val need = (cardCounts.wishTotal - cardCounts.total).coerceAtLeast(0)
                        CardTile(
                            card = card,
                            owned = counts,
                            wishAdjust = { d -> vm.quickWish(card, d) },
                            wishWritable = state.collectionWritable,
                            wishLabel = "NEED $need",
                            onClick = onClick,
                            onPeekStart = { startPeek(card) },
                            onPeekEnd = { endPeek() },
                        )
                    } else {
                        val type = vm.ownedTypeFor(card, storeId)
                        // Everything on the tile refers to the same printing the buttons
                        // edit, so the pill, the badge and +/- can never disagree.
                        val printing = vm.printingToEdit(card.key, storeId)
                        val foilOnly = vm.isFoilOnlyPrinting(card.key, printing)
                        val store = if (storeId == 0L) state.owned else state.binderOwned
                        val counts = store[printing] ?: Owned()
                        val selected = selectionMode && card.key in selectedKeys
                        Box {
                            CardTile(
                                card = card,
                                owned = counts,
                                // Selection mode owns the tap and disables editing:
                                // a stray + would change counts while picking cards.
                                onAdjust = if (selectionMode || !editable) {
                                    null
                                } else {
                                    { d -> vm.quickAdjust(card, d, storeId) }
                                },
                                writable = state.collectionWritable && !selectionMode && editable,
                                typeLabel = (if (type == "foil") "FOIL " else "STD ") +
                                    (if (type == "foil") counts.foil else counts.normal),
                                onCycleType = if (selectionMode || !editable || foilOnly) null else ({ vm.cycleOwnedType(card.key) }),
                                onClick = if (selectionMode && onToggleSelect != null) {
                                    { c -> onToggleSelect(c) }
                                } else {
                                    onClick
                                },
                                // Wishes are collection-only, so a binder view shows
                                // neither the badge nor the toggle: both would write
                                // to the collection while the screen shows a binder.
                                wishNeed = if (storeId == 0L) {
                                    (state.ownedByNormal[card.key]?.need ?: 0)
                                } else {
                                    0
                                },
                                onToggleWish = if (storeId == 0L && !selectionMode && editable) {
                                    { vm.toggleWish(card) }
                                } else {
                                    null
                                },
                                onPeekStart = if (selectionMode) null else ({ startPeek(card) }),
                                onPeekEnd = if (selectionMode) null else ({ endPeek() }),
                            )
                            if (selected) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .clip(TileShape)
                                        .border(2.dp, AppColors.Accent, TileShape)
                                        .background(AppColors.Accent.copy(alpha = 0.12f)),
                                )
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(AppColors.Accent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = AppColors.AccentOn,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        peekCard?.let { card ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f * peekProgress.value)),
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CardImage(
                    url = card.imageUrl,
                    description = card.name,
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .aspectRatio(CardArtRatio)
                        // Starts at 86% and grows to full size, so the press has
                        // a direction rather than appearing as a cut.
                        .graphicsLayer {
                            val s = 0.86f + 0.14f * peekProgress.value
                            scaleX = s
                            scaleY = s
                            alpha = peekProgress.value
                        }
                        .clip(TileShape)
                        .border(1.dp, AppColors.Hairline, TileShape),
                    nativeSize = true,
                )
            }
        }
    }
}

/**
 * Shown when a store read failed and its last successful snapshot was restored
 * from [StoreCache]. Different from [OfflineNotice], which explains why the
 * card database is still browsable.
 */
@Composable
private fun CachedStoreNotice(savedAt: Long) {
    CachedNotice(
        "Offline. Showing the copy saved at ${cachedTime(savedAt)}; " +
            "changes are disabled until you reconnect."
    )
}

/** The same, for the public deck list restored from [DeckCache]. */
@Composable
private fun CachedDeckNotice(savedAt: Long) {
    CachedNotice("Offline. Showing the deck list saved at ${cachedTime(savedAt)}.")
}

@Composable
private fun cachedTime(savedAt: Long): String {
    val context = LocalContext.current
    return remember(savedAt) {
        if (savedAt <= 0L) "earlier"
        else android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(savedAt))
    }
}

@Composable
private fun CachedNotice(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceRaised)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = AppColors.IconInactive,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = AppColors.TextDim, fontSize = AppType.meta)
    }
}

/**
 * A quiet line saying the device is offline.
 *
 * The card list works offline - it is cached - so this is not an error. But the
 * +/- on each tile cannot be saved, and without saying so the user finds out by
 * tapping a button that appears to do nothing.
 */
@Composable
private fun OfflineNotice() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceRaised)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = AppColors.IconInactive,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Offline. Cards and decks on this device are still here, but your collection cannot be changed.",
            color = AppColors.TextDim,
            fontSize = AppType.meta,
        )
    }
}

/**
 * Why the +/- controls are greyed out. The reason used to only live in a
 * snackbar that vanished after a few seconds, leaving the buttons apparently
 * broken with no explanation.
 */
@Composable
private fun CollectionWriteBlockedBanner(onRetry: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.NoticeBg)
            .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "onepiece.gg is rejecting collection edits for this account (no binder). Adding is disabled; your collection still reads normally.",
            fontSize = AppType.meta,
            color = AppColors.Warning,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Retry", fontSize = AppType.meta, color = AppColors.Warning)
        }
        TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Hide", fontSize = AppType.meta, color = AppColors.IconInactive)
        }
    }
}

@Composable
internal fun TileButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) AppColors.TileButtonBg else AppColors.TileButtonBgOff)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = AppType.title,
            fontWeight = FontWeight.Bold,
            color = if (enabled) AppColors.Accent else AppColors.IconDisabled,
        )
    }
}

/**
 * The frame around a card tile. Slightly larger than the art's old 8dp radius.
 *
 * `internal` rather than private so `Loading.kt` can build a placeholder with
 * exactly the same silhouette - a skeleton that does not match the real tile
 * makes the swap more jarring than the spinner it replaced.
 */
internal val TileShape = RoundedCornerShape(12.dp)

/** The frame around the large card image on the detail page. Matches [TileShape]. */
private val DetailImageShape = RoundedCornerShape(12.dp)

/**
 * A card's printed proportions, 59mm x 86mm.
 *
 * Used to size the detail page's frame so the art fills it instead of being
 * letterboxed by `ContentScale.Fit`.
 */
private const val CardArtRatio = 59f / 86f

/**
 * A card tile: framed art on top, name and set line below.
 *
 * The frame is the point. Without it the grid was a set of illustrations floating
 * on the app background with nothing to say they were discrete, tappable objects.
 *
 * `minLines = 2` on the name is not decoration either. Tiles are laid out by
 * `GridCells.Adaptive`, so a two-line name made its tile taller than the ones
 * beside it and every row came out slightly ragged. Reserving both lines gives
 * every tile the same height, which is what makes the grid read as a grid.
 */
@Composable
private fun CardTile(
    card: Card,
    owned: Owned?,
    onAdjust: ((Int) -> Unit)? = null,
    writable: Boolean = true,
    typeLabel: String? = null,
    onCycleType: (() -> Unit)? = null,
    onClick: (Card) -> Unit,
    // Wishlist controls. Null (the default) means the tile is used somewhere
    // wishes do not apply, e.g. the deck screens, and draws no wish UI at all.
    wishAdjust: ((Int) -> Unit)? = null,
    wishWritable: Boolean = true,
    wishLabel: String? = null,
    /** Copies still needed; drives the bookmark badge. */
    wishNeed: Int = 0,
    onToggleWish: (() -> Unit)? = null,
    /**
     * Press-and-hold preview. Called when the long-press threshold is reached,
     * and again when the finger lifts (or the gesture is cancelled). The tile
     * itself does not change; the grid animates a large copy while it is held.
     */
    onPeekStart: (() -> Unit)? = null,
    onPeekEnd: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val peekEnabled = onPeekStart != null
    Column(
        Modifier
            .clip(TileShape)
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Hairline, TileShape)
            .pointerInput(card.key, peekEnabled) {
                // onPress runs at touch-down and can wait for the release, while
                // onLongPress fires at the threshold. Pairing them gives the
                // hold-to-peek lifecycle. Using onPress for the whole thing
                // would also suppress taps: without a real onLongPress, a long
                // hold counts as a tap and would open the card on release.
                var peeking = false
                detectTapGestures(
                    onTap = { onClick(card) },
                    onLongPress = if (!peekEnabled) {
                        null
                    } else {
                        {
                            peeking = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPeekStart!!.invoke()
                        }
                    },
                    onPress = {
                        peeking = false
                        // Returns on up or cancellation; either way the peek, if
                        // it started, must shrink away.
                        tryAwaitRelease()
                        if (peeking) {
                            peeking = false
                            onPeekEnd?.invoke()
                        }
                    },
                )
            }
    ) {
        Box {
            CardImage(
                url = card.imageUrl,
                description = card.name,
                // Top corners only: the tile's own clip rounds the bottom, and
                // rounding the art's bottom edge too would notch the frame.
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                targetPx = 300,
            )
            // The row is always shown so the owned count stays visible; only the
            // buttons grey out. Hiding it entirely made the tile lose information
            // the moment a write failed.
            if (typeLabel != null) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TileButton("−", enabled = writeableEnabled(writable, onAdjust)) { onAdjust!!(-1) }
                    Text(
                        typeLabel,
                        fontSize = AppType.caption,
                        fontWeight = FontWeight.Bold,
                        color = if (writable) AppColors.OnSurface else AppColors.TextDim,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.BadgeScrim)
                            // No click node at all when the type cannot change
                            // (foil-only printings); a disabled one still reads
                            // as an interactive control.
                            .then(
                                if (onCycleType != null) {
                                    Modifier.clickable { onCycleType.invoke() }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                    TileButton("+", enabled = writeableEnabled(writable, onAdjust)) { onAdjust!!(1) }
                }
            }
            if (wishLabel != null) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TileButton("−", enabled = wishWritable) { wishAdjust?.invoke(-1) }
                    Text(
                        wishLabel,
                        fontSize = AppType.caption,
                        fontWeight = FontWeight.Bold,
                        color = if (wishWritable) AppColors.Accent else AppColors.TextDim,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.BadgeScrim)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                    TileButton("+", enabled = wishWritable) { wishAdjust?.invoke(1) }
                }
            }
            if (owned != null && owned.total > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Palette.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("x${owned.total}", fontSize = AppType.meta, fontWeight = FontWeight.Bold, color = AppColors.AccentOn)
                }
            }
            // The bookmark badge. Shown whenever a card tile has a wish toggle
            // at all - on the Cards tab that is how the user finds out the
            // wishlist feature exists. Tapping it toggles the card on/off the
            // list. The number is the shortfall, so a card the account owns
            // enough of draws an empty bookmark even if a wish row survives.
            if (onToggleWish != null) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.BadgeScrim)
                        .clickable { onToggleWish?.invoke() }
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (wishNeed > 0) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (wishNeed > 0) "On wishlist" else "Add to wishlist",
                            tint = if (wishNeed > 0) AppColors.Accent else AppColors.TextDim,
                            modifier = Modifier.size(12.dp),
                        )
                        if (wishNeed > 0) {
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "$wishNeed",
                                fontSize = AppType.meta,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.Accent,
                            )
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
            Spacer(Modifier.height(4.dp))
            Text(
                card.name.orEmpty(),
                fontSize = AppType.body,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = AppColors.OnSurface,
            )
            Text(
                listOfNotNull(card.id, card.rarity).joinToString(" · "),
                fontSize = AppType.caption,
                color = AppColors.TextDim,
            )
        }
    }
}

/** Both the enabled test and the non-null assert live here, once. */
private fun writeableEnabled(writable: Boolean, onAdjust: ((Int) -> Unit)?) = writable && onAdjust != null

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
/**
 * The one filter sheet for every screen. With [editor] set it reads and writes
 * the deck editor's independent query state instead of the Cards tab's, so the
 * two screens' filters never share values.
 */
private fun FilterSheet(state: UiState, vm: AppViewModel, editor: Boolean = false, onClose: () -> Unit) {
    val parsed = if (editor) state.editorParsed else state.parsed
    fun onToggle(field: QField, value: String) {
        if (editor) vm.toggleEditorTerm(field, value) else vm.toggleTerm(field, value)
    }
    fun onClearField(field: QField) {
        if (editor) vm.clearEditorTerm(field) else vm.clearTerm(field)
    }
    // "Red/Blue" and "Slash / Wisdom" are single card values but separate filter
    // options, so offer the individual parts.
    fun multiValues(field: QField): List<String> =
        state.cards.mapNotNull {
            when (field) {
                QField.COLOR -> it.Color
                QField.ATTRIBUTE -> it.Attribute
                else -> null
            }
        }.flatMap { v -> v.split('/') }
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "?" }
            .distinct()
            .sorted()

    fun singleValues(field: QField): List<String> =
        state.cards.mapNotNull {
            when (field) {
                QField.RARITY -> it.rarity
                QField.CARDTYPE -> it.cardType
                QField.COST -> it.Cost
                QField.BLOCK -> it.BlockIcon
                else -> null
            }
        }.filter { it.isNotBlank() && it != "?" }.distinct().sortedBy { it.toIntOrNull() ?: 999 }

    // The deck editor's counter buckets, now that the sheet is the one filter
    // UI for both screens. Ids, not numbers: `counter:2000` already means
    // "exactly 2000", so the buckets need distinct tokens.
    val counterDisplay = mapOf(
        "none" to "no counter",
        "plus1000" to "+1000",
        "plus2000" to "+2000+",
    )

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Filters", fontWeight = FontWeight.Bold, fontSize = AppType.display)
            Text(
                "These edit the search box, so you can mix them with typed tokens.",
                color = AppColors.TextDim,
                fontSize = AppType.body,
            )
            Spacer(Modifier.height(8.dp))
            listOf(
                Triple("Set", "set", state.sets.mapNotNull { it.code }.distinct().sorted()),
                Triple("Rarity", "rarity", singleValues(QField.RARITY)),
                Triple("Card type", "t", singleValues(QField.CARDTYPE)),
                Triple("Color", "c", multiValues(QField.COLOR)),
                Triple("Attribute", "attr", multiValues(QField.ATTRIBUTE)),
                Triple("Cost", "cost", singleValues(QField.COST)),
                Triple("Block", "block", singleValues(QField.BLOCK)),
                Triple("Counter", "counter", CardSearch.COUNTER_BUCKETS),
            ).forEach { (label, token, options) ->
                val field = QField.entries.first { it.canonical == token }
                MultiSelectDropdown(
                    label = label,
                    token = token,
                    options = options,
                    selected = parsed.activeValues(field),
                    onToggle = { onToggle(field, it) },
                    onClear = { onClearField(field) },
                    // Bucket ids are not user-facing text.
                    display = if (token == "counter") counterDisplay else emptyMap(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { if (editor) vm.clearEditorTokens() else vm.clearTokens() }) {
                    Text("Reset all")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * One dropdown per filter with checkbox multi-select. Writes through
 * toggleTerm so the dropdowns and typed tokens stay a single mechanism.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MultiSelectDropdown(
    label: String,
    token: String,
    options: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    /** Friendly text for values that are not user-facing, e.g. counter bucket ids. */
    display: Map<String, String> = emptyMap(),
) {
    if (options.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }

    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { open = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = if (open) filter else summarise(selected, token),
            onValueChange = { filter = it },
            readOnly = true,
            enabled = !open,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            if (options.size > 12) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        singleLine = true,
                        placeholder = { Text("Type to filter", fontSize = AppType.body) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val shown = if (filter.isBlank()) options else options.filter { it.contains(filter, true) }
            shown.forEach { opt ->
                val isOn = selected.any { it.equals(opt, true) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(opt) }
                        .padding(horizontal = 14.dp, vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isOn, onCheckedChange = { onToggle(opt) })
                    Text(display[opt] ?: opt, fontSize = AppType.body, color = AppColors.OnSurface)
                }
            }
            if (selected.isNotEmpty()) {
                HorizontalDivider()
                TextButton(onClick = { onClear(); open = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear ${label.lowercase()}", fontSize = AppType.body)
                }
            }
        }
    }
}

private fun summarise(selected: List<String>, token: String): String = when {
    selected.isEmpty() -> ""
    selected.size == 1 -> "$token:${selected.first()}"
    else -> "$token:${selected.first()} +${selected.size - 1}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// internal: the deck editor opens the same sheet from its app bar.
internal fun SortSheet(state: UiState, vm: AppViewModel, onClose: () -> Unit) {
    val sort = state.sort
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Sort by", fontWeight = FontWeight.Bold, fontSize = AppType.display)
            Spacer(Modifier.height(4.dp))
            // Direction first, above the field list: it used to sit below all
            // 13 radio rows, off the bottom of the 560 dp sheet, so a
            // Descending flag left on from an earlier session made Name sort
            // read as reversed with no visible way to fix it - the report that
            // "sort feels not working".
            if (sort.field != SortField.NONE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Descending", fontSize = AppType.title, modifier = Modifier.weight(1f))
                    Switch(
                        checked = sort.descending,
                        onCheckedChange = { vm.setSort(sort.copy(descending = it)) },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
            }
            SortField.entries.forEach { f ->
                val selected = sort.field == f
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.setSort(sort.copy(field = if (selected) SortField.NONE else f)) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { vm.setSort(sort.copy(field = if (selected) SortField.NONE else f)) },
                    )
                    Text(
                        f.label,
                        fontSize = AppType.title,
                        color = if (selected) Palette.primary else AppColors.OnSurface,
                    )
                }
            }
            if (sort.field != SortField.NONE) {
                Text(
                    "Saved automatically and restored on next launch.",
                    color = AppColors.TextDim,
                    fontSize = AppType.body,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardDetailPage(
    card: Card,
    list: List<Card>,
    storeId: Long,
    state: UiState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onNavigate: (Card) -> Unit,
    onOpenRelated: (Card) -> Unit,
) {
    val swipable = list.size > 1
    val startIndex = remember(list, card.key) {
        val i = list.indexOfFirst { it.key == card.key }
        if (i >= 0) i else 0
    }
    val pager = rememberPagerState(initialPage = startIndex, pageCount = { list.size })

    // Keep the visible page in AppRoot's state so Back lands on the right card.
    // Keyed on the pager ONLY: the list is a fresh instance on some
    // recompositions, and keying on it restarted this collector every time,
    // re-firing onNavigate and fighting the pager.
    val currentList by rememberUpdatedState(list)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            currentList.getOrNull(page)?.let(onNavigate)
        }
    }

    // A Surface sets LocalContentColor, which otherwise defaults to Color.Black.
    // Without it every Text here that relies on inheritance is invisible on our
    // dark background - which is exactly what happened when this page was first
    // built outside a Scaffold.
    Surface(color = Palette.background, contentColor = AppColors.OnSurface) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Surface,
                    titleContentColor = Color.White,
                    navigationIconContentColor = AppColors.IconInactive,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        list.getOrNull(pager.currentPage)?.name ?: card.name.orEmpty(),
                        fontSize = AppType.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (swipable) {
                        Text(
                            "${pager.currentPage + 1} / ${list.size}",
                            fontSize = AppType.body,
                            color = AppColors.TextDim,
                            modifier = Modifier.padding(end = 14.dp),
                        )
                    }
                },
            )
            if (swipable) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    list.getOrNull(page)?.let { CardDetailBody(it, storeId, state, vm, onOpenRelated) }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) { CardDetailBody(card, storeId, state, vm, onOpenRelated) }
            }
        }
    }
}

@Composable
private fun CardDetailBody(
    card: Card,
    storeId: Long,
    state: UiState,
    vm: AppViewModel,
    onOpenRelated: (Card) -> Unit,
) {
    val key = card.key
    val printings = state.printingsByNormal[key].orEmpty()
    val storeOwned = if (storeId == 0L) state.owned else state.binderOwned
    val storeByNormal = if (storeId == 0L) state.ownedByNormal else state.binderOwnedByNormal
    // Default to the printing this store actually holds, else the base printing.
    // Keyed on the card, so paging on resets to that card's own printing.
    var selectedId by remember(key, storeId) { mutableStateOf(vm.printingToEdit(key, storeId)) }
    // The loupe. Reset per card, so paging to another card cannot leave a zoom
    // dialog open over the new art.
    var zoomOpen by remember(key) { mutableStateOf(false) }
    val selected = printings.firstOrNull { it.id == selectedId } ?: card
    val editPrinting = selected.id ?: key
    val owned = storeByNormal[key] ?: Owned()
    val held = storeOwned[selectedId]

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // The art used to sit centred in a full-width `SurfaceRaised` slab with
        // square corners, so it read as a picture floating on the page. Pinning
        // the frame to the card's own aspect ratio removes the empty side panels
        // and gives the art somewhere to be rounded and bordered - a 320dp-tall
        // full-width box is much wider than a card, so `ContentScale.Fit` was
        // letterboxing it with grey on both sides.
        //
        // 59mm x 86mm is the printed card, which is what the site scans are. The
        // band is 8.dp taller than the art on each side so the art is still
        // 320.dp tall - the size it was before - but is no longer jammed against
        // the app bar.
        Box(
            Modifier.fillMaxWidth().height(336.dp).padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(CardArtRatio)
                    .clip(DetailImageShape)
                    .background(AppColors.SurfaceRaised)
                    .border(1.dp, AppColors.Hairline, DetailImageShape)
                    .clickable { zoomOpen = true },
            ) {
                CardImage(
                    url = selected.imageUrl,
                    description = selected.name,
                    modifier = Modifier.fillMaxSize(),
                    // Native decode: the source is 600x838, and asking for a
                    // 900px square box made Coil upscale it to 644x900.
                    nativeSize = true,
                    // The one place a fade earns its keep: a single large image
                    // arriving into an otherwise empty frame.
                    crossfadeMs = 150,
                )
                // The art is tappable, but a picture is not obviously a button.
                // This corner glyph says so without covering the card, and the
                // top-right corner is free (the printing badge is top-start).
                Icon(
                    Icons.Outlined.ZoomIn,
                    contentDescription = "Zoom image",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.BadgeScrim)
                        .padding(4.dp)
                        .size(18.dp),
                )
                if (selected.id != selected.idNormal) {
                    Text(
                        selected.id.orEmpty(),
                        color = AppColors.Accent,
                        fontSize = AppType.meta,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            // Clears the frame's 12dp corner radius, which a
                            // square-cornered badge would poke out of.
                            .padding(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.BadgeScrim)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Text(
            selected.name.orEmpty(),
            fontSize = AppType.display,
            fontWeight = FontWeight.Bold,
            color = AppColors.OnSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        Text(
            listOfNotNull(selected.id, selected.rarity, selected.cardType, selected.set).joinToString(" · "),
            color = AppColors.TextDim,
            fontSize = AppType.body,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        // Which pack this artwork came from. The set code above is the card's
        // *series* and is identical for every printing of it, so it cannot say
        // which pack a particular artwork shipped in - the pack name can, and it
        // differs for 783 of the 1,172 cards that have more than one printing.
        selected.packName?.let { pack ->
            Text(
                "From  $pack",
                color = AppColors.TextDim,
                fontSize = AppType.meta,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        if (printings.size > 1) {
            ReprintStrip(
                printings = printings,
                selectedId = selectedId,
                state = state,
                onSelect = { selectedId = it },
            )
        }

        StatBlock(card = selected)

        if (!selected.Effect.isNullOrBlank()) {
            DetailSection("Effect") {
                CardTextBody(selected.Effect, fontSize = AppType.body, colour = AppColors.OnSurface)
            }
        }
        if (!selected.Trigger.isNullOrBlank()) {
            DetailSection("Trigger") {
                CardTextBody(selected.Trigger, fontSize = AppType.body, colour = AppColors.OnSurface)
            }
        }

        PricePanel(selected)

        RelatedSection(card, state, vm, onOpenRelated)

        CollectionBlock(
            state = state,
            storeId = storeId,
            readOnly = storeId < 0L,
            printings = printings,
            selectedId = selectedId,
            ownedTotal = owned,
            heldForSelected = held,
            onSelect = { selectedId = it },
            onSet = { type, n -> vm.setOwned(editPrinting, type, n, storeId) },
            onSetWish = { n -> vm.setWished(editPrinting, n) },
            onMove = { target -> vm.moveCard(editPrinting, storeId, target) },
        )

        Spacer(Modifier.height(24.dp))
    }

    // Outside the scrolling Column: a Dialog does not participate in layout,
    // and this keeps every reopened loupe starting from the same clean state.
    if (zoomOpen) {
        CardImageZoomOverlay(
            url = selected.imageUrl,
            description = selected.name,
            onDismiss = { zoomOpen = false },
        )
    }
}

/**
 * Price shown under a printing.
 *
 * The standard price wins. When the feed has no usable standard price but does
 * have a foil one - common on parallel and reprint rows - show that with a
 * "foil" suffix rather than a bare dash. Zero and non-numeric strings mean
 * missing, not free.
 */
internal fun reprintPriceLabel(price: String?, foilPrice: String?): String {
    fun positive(raw: String?): Double? = raw?.toDoubleOrNull()?.takeIf { it > 0.0 }
    positive(price)?.let { return "$" + "%.2f".format(it) }
    positive(foilPrice)?.let { return "$" + "%.2f".format(it) + " foil" }
    return "-"
}

@Composable
private fun ReprintStrip(
    printings: List<Card>,
    selectedId: String,
    state: UiState,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            "Printings  ${printings.size}",
            fontWeight = FontWeight.Bold,
            fontSize = AppType.body,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(printings, key = { it.id ?: it.key }) { p ->
                val pid = p.id ?: p.key
                val isSelected = pid == selectedId
                val o = state.owned[pid]
                val held = o != null && (o.normal + o.foil) > 0
                Column(
                    Modifier
                        // 78dp, and not wider: the art is 96dp tall and card art
                        // is portrait (59:86), so a wider box only letterboxes it.
                        // A wider tile was tried when each printing showed its
                        // pack name; without that, the narrow one fits the art
                        // better.
                        .width(78.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AppColors.SurfaceSelected else AppColors.SurfaceRaised)
                        .clickable { onSelect(pid) }
                        .padding(4.dp),
                ) {
                    Box {
                        CardImage(
                            url = p.imageUrl,
                            description = p.id,
                            modifier = Modifier.fillMaxWidth().height(96.dp),
                            targetPx = 200,
                        )
                        if (held) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(9.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(AppColors.Positive),
                            )
                        }
                    }
                    // The kind of printing, not the id.
                    //
                    // "P-058_p1" tells a collector nothing; "Parallel" tells them
                    // whether they are looking at a different picture of the same
                    // card or just a reprint of it. The artwork identifies the
                    // printing on its own, and the id is still the image's
                    // accessibility label.
                    Text(
                        p.printingKind,
                        fontSize = AppType.caption,
                        color = if (isSelected) AppColors.Accent else AppColors.TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        reprintPriceLabel(p.price, p.foilPrice),
                        fontSize = AppType.caption,
                        color = AppColors.TextDim,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBlock(card: Card) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatChip("Cost", card.Cost)
            StatChip("Power", card.Power)
            StatChip("Life", card.Life)
            StatChip("Counter", card.Counter)
            StatChip("Block", card.BlockIcon?.let { if (it == "X") "X" else it })
        }
        if (!card.Color.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("Color  ${card.Color}", fontSize = AppType.body, color = AppColors.OnSurface)
        }
        if (!card.Type.isNullOrBlank()) {
            Text("Type  ${card.Type}", fontSize = AppType.body, color = AppColors.OnSurface)
        }
        card.Attribute?.takeIf { it.isNotBlank() && it != "?" }?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                AsyncImage(
                    model = "$ICON_BASE/$it.webp",
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(it, fontSize = AppType.body, color = AppColors.OnSurface)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String?) {
    if (value.isNullOrBlank() || value == "?") return
    Column(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.SurfaceRaised)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = AppType.caption, color = AppColors.TextDim)
        Text(value, fontSize = AppType.title, fontWeight = FontWeight.Bold, color = AppColors.OnSurface)
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = AppType.body, color = AppColors.TextDim)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

/**
 * Entry point to the related-card list. A button rather than an inline strip:
 * the full list is often long, and nesting a LazyRow inside the page's vertical
 * scroll (itself inside a HorizontalPager) is asking for trouble.
 */
@Composable
private fun RelatedSection(card: Card, state: UiState, vm: AppViewModel, onOpenRelated: (Card) -> Unit) {
    // Keyed on the index coverage as well: the meta snapshot loads after the
    // first composition, so keying only on the card cached an empty list.
    //
    // The count is the *total*, not the preview's size. `relatedFor` caps its
    // result at six, so using that size made every card with a big crew read
    // "See 6 cards from this crew" however many crew-mates it actually had.
    val relatedAndCount = remember(card.key, state.relatedCoverage) {
        vm.relatedFor(card) to vm.relatedCount(card)
    }
    val related = relatedAndCount.first
    if (related.isEmpty()) return
    val relatedCount = relatedAndCount.second
    val basis = vm.relatedBasis(card)
    val archetypes = vm.relatedArchetypeCount(card)
    val rate = vm.relatedInclusionRate(card)

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(
            onClick = { onOpenRelated(card) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (basis == RelatedCard.Basis.META) "See $relatedCount related cards" else "See $relatedCount cards of this type and colour",
                fontSize = AppType.body,
            )
        }
        if (archetypes > 0) {
            Text(
                buildString {
                    append("In $archetypes ")
                    append(if (archetypes == 1) "archetype" else "archetypes")
                    rate?.let { append("  ·  in ${(it * 100).toInt()}% of those decks") }
                },
                fontSize = AppType.meta,
                color = AppColors.TextDim,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Full screen listing every card related to a seed card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelatedListScreen(
    seed: Card,
    state: UiState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenCard: (Card, List<Card>) -> Unit,
) {
    val related = remember(seed.key, state.relatedCoverage) { vm.relatedAll(seed) }
    val cards = related.mapNotNull { it.card }
    val basis = related.firstOrNull()?.basis ?: RelatedCard.Basis.CREW

    Surface(color = Palette.background, contentColor = AppColors.OnSurface) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Surface,
                    titleContentColor = Color.White,
                    navigationIconContentColor = AppColors.IconInactive,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        "Related to ${seed.name.orEmpty()}",
                        fontSize = AppType.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            Text(
                // Suppressed when empty: the old copy read "0 cards from the
                // same crew", which contradicts itself.
                when {
                    cards.isEmpty() -> "No related cards yet."
                    basis == RelatedCard.Basis.META ->
                        "${related.size} cards that share tournament archetypes with this one."
                    else ->
                        "${related.size} cards of the same type and colour. This card is not in the current meta data, " +
                            "so these are type-mates rather than recommendations."
                },
                fontSize = AppType.body,
                color = AppColors.TextDim,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (cards.isEmpty()) {
                // Was an ErrorBox, which is fillMaxSize and floated mid-screen.
                Text(
                    if (state.relatedCoverage == 0) {
                        "Meta data is still loading."
                    } else {
                        "Nothing matched. This card may have a crew of one and no tournament appearances."
                    },
                    fontSize = AppType.body,
                    color = AppColors.TextDim,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(cards, key = { it.key }) { c ->
                        // Swiping from here walks the related list, not the
                        // database the seed happened to be opened from.
                        CardTile(c, state.ownedByNormal[c.key], onClick = { onOpenCard(c, cards) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun CardImage(
    url: String?,
    description: String?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    targetPx: Int = 720,
    /**
     * Fade-in duration in ms, or 0 for none.
     *
     * Only the card detail page asks for one. On a grid thumbnail a crossfade
     * restarts every time the request is re-issued - which happens constantly
     * while scrolling or after a filter change - so the tile spends its life
     * half-faded and reads as "the image has not arrived yet". See CardImages.
     */
    crossfadeMs: Int = 0,
    /**
     * Decode at the file's own resolution instead of a square target box.
     *
     * The zoom overlay needs every pixel the 600x838 source has; `size(px)`
     * means "fit inside a px-by-px box", which would downscale a portrait card.
     */
    nativeSize: Boolean = false,
) {
    var attempt by remember(url) { mutableIntStateOf(0) }
    var retrying by remember(url) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val request = rememberCardImage(url, targetPx, crossfadeMs, nativeSize, attempt)
    if (request == null) {
        Box(modifier.background(AppColors.SurfaceRaised))
        return
    }
    AsyncImage(
        model = request,
        contentDescription = description,
        contentScale = contentScale,
        modifier = modifier.background(AppColors.SurfaceRaised),
        onError = { state ->
            // Coil does not retry. A stalled CDN connection or a dropped socket
            // therefore left the tile blank for the rest of the session - the
            // "images sometimes refuse to load" report. Two retries cover the
            // transient cases; a 404 means this printing genuinely has no art,
            // so it is left alone.
            val throwable = state.result.throwable
            val missing = throwable is HttpException && throwable.response.code == 404
            if (!missing && attempt < 2 && !retrying) {
                retrying = true
                scope.launch {
                    delay(if (attempt == 0) 1_500L else 6_000L)
                    attempt += 1
                    retrying = false
                }
            }
        },
    )
}

/**
 * Coil request for a card image with an explicit target size.
 *
 * AsyncImage otherwise derives the request size from measured constraints. Inside
 * a verticalScroll inside a HorizontalPager the first composition can measure
 * before it is stable, so the request goes out at the wrong size and the image
 * stays blank until something forces a recomposition - scrolling being the
 * obvious one. Pinning the size makes the request independent of layout timing.
 *
 * Built against [CardImageLoaderHolder] rather than Coil's default so the disk
 * cache lives at a known directory and is opened once at startup.
 */
@Composable
private fun rememberCardImage(
    url: String?,
    targetPx: Int = 720,
    crossfadeMs: Int = 0,
    nativeSize: Boolean = false,
    attempt: Int = 0,
): ImageRequest? {
    val ctx = LocalContext.current
    val loader = CardImageLoaderHolder.get()
    return remember(url, targetPx, crossfadeMs, nativeSize, attempt) {
        if (url.isNullOrBlank()) null
        else ImageRequest.Builder(ctx)
            .data(url)
            .apply { if (nativeSize) size(Size.ORIGINAL) else size(targetPx) }
            .apply { if (crossfadeMs > 0) crossfade(crossfadeMs) }
            .build()
    }
}

@Composable
private fun PricePanel(card: Card) {
    val cmUrl = card.cmurl
    DetailSection("Prices") {
        Column {
            PriceRow("TCGplayer", card.price, card.delta7dPrice)
            PriceRow("TCGplayer foil", card.foilPrice, card.delta7dPriceFoil)
            PriceRow("Cardmarket", card.cmPrice, card.cmDelta7dPrice)
            PriceRow("Cardmarket foil", card.cmFoilPrice, card.cmDelta7dPriceFoil)
            if (!cmUrl.isNullOrBlank()) {
                val ctx = LocalContext.current
                TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(cmUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Open on Cardmarket", fontSize = AppType.body) }
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, price: String?, delta: String?) {
    // The feed sends zero as "0.000000" and six-decimal strings otherwise, so
    // the old `price == "0"` guard let "$0.000000" through next to formatted
    // deltas. Treat anything unparseable or non-positive as "no listing".
    val value = price?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return
    val d = delta?.toDoubleOrNull() ?: 0.0
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = AppType.body, color = AppColors.TextDim, modifier = Modifier.width(120.dp))
        Text("$" + "%.2f".format(value), fontSize = AppType.body, fontWeight = FontWeight.SemiBold, color = AppColors.OnSurface)
        if (d != 0.0) {
            Text(
                (if (d > 0) "+" else "") + "%.2f".format(d),
                fontSize = AppType.meta,
                color = if (d > 0) AppColors.Positive else AppColors.Danger,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun CollectionBlock(
    state: UiState,
    storeId: Long,
    readOnly: Boolean = false,
    printings: List<Card>,
    selectedId: String,
    ownedTotal: Owned,
    heldForSelected: Owned?,
    onSelect: (String) -> Unit,
    onSet: (String, Int) -> Unit,
    onSetWish: (Int) -> Unit,
    onMove: (Long) -> Unit,
) {
    val inBinder = storeId != 0L
    val storeName = binderTitle(state.binders, storeId)
    val store = if (inBinder) state.binderOwned else state.owned
    DetailSection(
        when {
            readOnly -> "All binders"
            inBinder -> "Binder: $storeName"
            else -> "Your collection"
        },
    ) {
        if (state.session == null) {
            Text("Sign in from the Account tab to sync your binder.", color = AppColors.TextDim, fontSize = AppType.body)
            return@DetailSection
        }
        // A printing is listed here if any of its counts is non-zero - owned, or
        // wished on the collection. A wished-only reprint would be invisible
        // otherwise, and that is exactly the row the user comes here to find.
        val held = printings.filter { p ->
            val o = p.id?.let { store[it] }
            o != null && (o.total > 0 || (!inBinder && o.wishTotal > 0))
        }
        if (held.isEmpty()) {
            Text(
                if (inBinder) "This binder has no copies of this card yet." else "You neither own nor wish for this card yet.",
                color = AppColors.TextDim,
                fontSize = AppType.body,
            )
        } else {
            held.forEach { p ->
                val o = store[p.id] ?: Owned()
                val counts = buildList {
                    if (o.total > 0) add("std ${o.normal} · foil ${o.foil}")
                    if (!inBinder && o.wishTotal > 0) add("wish ${o.wishTotal}")
                }.joinToString(" · ")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { p.id?.let(onSelect) }
                        .padding(vertical = 1.dp),
                ) {
                    Text(
                        p.id.orEmpty(),
                        fontSize = AppType.body,
                        color = if (p.id == selectedId) AppColors.Accent else AppColors.IconInactive,
                        modifier = Modifier.width(120.dp),
                    )
                    Text(counts, fontSize = AppType.body)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Editing printing ${printings.firstOrNull { it.id == selectedId }?.id ?: selectedId}",
            fontSize = AppType.meta,
            color = AppColors.TextDim,
        )
        if (readOnly) {
            Text(
                "All binders is a read-only summary of your collection and every binder. Open a single binder to edit counts or move cards.",
                color = AppColors.TextDim,
                fontSize = AppType.body,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            val foilOnly = printings.firstOrNull { it.id == selectedId }?.foilOnly == true
            if (foilOnly) {
                Text(
                    "Foil only - this printing has no standard version.",
                    color = AppColors.TextDim,
                    fontSize = AppType.meta,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Normal: ${heldForSelected?.normal ?: 0}", modifier = Modifier.width(110.dp), fontSize = AppType.body, color = AppColors.OnSurface)
                    Stepper(heldForSelected?.normal ?: 0) { onSet("standard", it) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Foil: ${heldForSelected?.foil ?: 0}", modifier = Modifier.width(110.dp), fontSize = AppType.body, color = AppColors.OnSurface)
                Stepper(heldForSelected?.foil ?: 0) { onSet("foil", it) }
            }
            // Wishes exist only on collection rows; a binder save cannot carry one,
            // so showing this stepper here would write to the collection silently.
            if (!inBinder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Wish: ${heldForSelected?.wishTotal ?: 0}",
                        modifier = Modifier.width(110.dp),
                        fontSize = AppType.body,
                        color = AppColors.OnSurface,
                    )
                    Stepper(heldForSelected?.wishTotal ?: 0) { onSetWish(it) }
                }
            }
        }
        Text(
            // wishFoil is display-only: the website's batch save offers no way
            // to edit it, so a wishFoil imported or set on the site is kept but
            // not adjustably counted here. `need` is the shortfall, and it is
            // what the wishlist view lists.
            if (inBinder) {
                "Binder total: std ${ownedTotal.normal} · foil ${ownedTotal.foil}"
            } else {
                "Card total: std ${ownedTotal.normal} · foil ${ownedTotal.foil} · wished ${ownedTotal.wishTotal} · need ${ownedTotal.need}"
            },
            fontSize = AppType.meta,
            color = AppColors.TextDim,
            modifier = Modifier.padding(top = 4.dp),
        )

        // Moving is all-or-nothing: the API moves the whole row for a printing.
        val copies = heldForSelected?.total ?: 0
        val destinations = moveDestinations(state.binders, storeId)
        if (!readOnly && copies > 0 && destinations.isNotEmpty()) {
            var moveOpen by remember { mutableStateOf(false) }
            TextButton(
                onClick = { moveOpen = true },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) {
                Text("Move to…", color = AppColors.Accent, fontSize = AppType.body)
            }
            if (moveOpen) {
                MoveCardsDialog(
                    heading = "Move ${printings.firstOrNull { it.id == selectedId }?.id ?: selectedId}",
                    detail = "Moves all $copies ${if (copies == 1) "copy" else "copies"} out of " +
                        "${if (inBinder) storeName else "Collection"}. There is no partial move.",
                    destinations = destinations,
                    onMove = { target ->
                        moveOpen = false
                        onMove(target)
                    },
                    onDismiss = { moveOpen = false },
                )
            }
        }
    }
}

/**
 * Destination picker for a move. Single and bulk share it because the API has
 * no quantity control either: each selected printing moves its entire row.
 */
@Composable
private fun MoveCardsDialog(
    heading: String,
    detail: String,
    destinations: List<Pair<Long, String>>,
    onMove: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Column {
                Text(
                    detail,
                    fontSize = AppType.body,
                    color = AppColors.TextDim,
                )
                Spacer(Modifier.height(10.dp))
                destinations.forEach { (id, name) ->
                    TextButton(
                        onClick = { onMove(id) },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                    ) {
                        Text(name, color = AppColors.Accent, fontSize = AppType.body)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(value - 1) }) { Text("−", fontSize = AppType.display, color = AppColors.OnSurface) }
        Text("$value", fontWeight = FontWeight.Bold, color = AppColors.OnSurface, modifier = Modifier.width(32.dp))
        IconButton(onClick = { onChange(value + 1) }) { Text("+", fontSize = AppType.display, color = AppColors.OnSurface) }
    }
}

@Composable
private fun DecksScreen(
    state: UiState,
    vm: AppViewModel,
    segment: DeckSegment,
    onSelectSegment: (DeckSegment) -> Unit,
    onOpenDeck: (gg.opgg.android.data.Deck) -> Unit,
    onOpenCard: (Card, List<Card>) -> Unit,
    metaListState: androidx.compose.foundation.lazy.LazyListState,
    builder: @Composable () -> Unit,
) {
    LaunchedEffect(Unit) { if (state.decks.isEmpty()) vm.loadDecks() }

    Column(Modifier.fillMaxSize()) {
        // No search bar here on purpose: the API ignores every deck filter we
        // tried, so it could only ever narrow the pages already fetched.
        //
        // Scrollable, and the labels shortened to "Public" / "Mine" so all four
        // segments fit without Meta being pushed off-screen where nobody finds
        // it. At the old widths "Public decks" + "My decks" + "Builder" already
        // filled the row.
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Selects a segment rather than toggling. Toggling two independent
            // flags meant tapping "Public" while on Meta flipped builderOpen to
            // true and showed the Builder. The transition is DeckSegment.tap, and
            // it is unit tested.
            //
            // Written as four explicit `item` blocks on purpose: generating them
            // from `DeckSegment.entries.forEach { item(key = ...) }` rendered
            // correctly but reported every chip except the last one as
            // unclickable, and synthetic taps are blocked here so the cause could
            // not be pinned down. The enum is what fixes the bug; the row layout
            // stays in the shape that was known to work.
            item {
                FilterChip(
                    selected = segment == DeckSegment.PUBLIC,
                    onClick = { onSelectSegment(DeckSegment.PUBLIC) },
                    label = { Text("Public", fontSize = AppType.body) },
                )
            }
            item {
                FilterChip(
                    selected = segment == DeckSegment.MINE,
                    onClick = { onSelectSegment(DeckSegment.MINE) },
                    label = { Text("Mine", fontSize = AppType.body) },
                )
            }
            item {
                FilterChip(
                    selected = segment == DeckSegment.BUILDER,
                    onClick = { onSelectSegment(DeckSegment.BUILDER) },
                    label = { Text("Builder", fontSize = AppType.body) },
                )
            }
            item {
                FilterChip(
                    selected = segment == DeckSegment.META,
                    onClick = { onSelectSegment(DeckSegment.META) },
                    label = { Text("Meta", fontSize = AppType.body) },
                )
            }
        }
        if (segment == DeckSegment.META) {
            MetaScreen(state, vm, onOpenCard, metaListState)
            return@Column
        }
        if (segment == DeckSegment.BUILDER) {
            builder()
            return@Column
        }
        // Deck search. The site filters server-side on `getdecks.smartsrch`, so
        // typing re-queries page 1 instead of narrowing the pages already held.
        // Older attempts used flat keys (q/search/name) that the API ignores,
        // which is why this box did not exist.
        var deckQueryText by rememberSaveable { mutableStateOf(state.deckQuery) }
        LaunchedEffect(deckQueryText) {
            if (deckQueryText.trim() != state.deckQuery) {
                delay(400)
                vm.setDeckQuery(deckQueryText)
            }
        }
        OutlinedTextField(
            value = deckQueryText,
            onValueChange = { deckQueryText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            singleLine = true,
            placeholder = {
                Text(
                    if (state.decksMine) "Search your decks" else "Search decks, leaders or cards",
                    fontSize = AppType.body,
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (deckQueryText.isNotEmpty()) {
                    IconButton(onClick = { deckQueryText = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear deck search")
                    }
                }
            },
        )
        state.decksCacheAt?.let { savedAt -> CachedDeckNotice(savedAt) }
        if (state.decksLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            state.decksError != null -> ErrorState(
                state.decksError,
                body = "Decks come from onepiece.gg. The refresh button in the app bar will try again.",
            )
            state.decks.isEmpty() && !state.decksLoading -> EmptyState(
                icon = Icons.Outlined.Style,
                title = when {
                    state.deckQuery.isNotBlank() -> "No decks match"
                    state.decksMine -> "No decks on onepiece.gg yet"
                    else -> "No decks loaded"
                },
                body = when {
                    state.deckQuery.isNotBlank() ->
                        "Nothing matched \"${state.deckQuery}\". Try a leader name, a deck name or a card ID."
                    state.decksMine ->
                        "Decks you save on onepiece.gg appear here. You can also build one on this device in the Builder tab - it stays local."
                    else ->
                        "Nothing came back from onepiece.gg. The refresh button in the app bar will try again."
                },
            )
            // The first page, before anything has arrived. The spinner at the foot
            // of the list below is for *more* pages, and there is nothing to
            // append to yet, so the whole list is a placeholder here.
            state.decks.isEmpty() && state.decksLoading -> SkeletonRowList()
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.decks, key = { it.slug ?: it.name ?: it.hashCode().toString() }) { deck ->
                    DeckRow(deck) { onOpenDeck(deck) }
                }
                item {
                    if (state.decksLoading) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (state.decksExhausted) {
                        Text(
                            when {
                                state.deckQuery.isNotBlank() ->
                                    "No more results for \"${state.deckQuery}\" (${state.decks.size})."
                                state.decksMine -> "That's all of your decks (${state.decks.size})."
                                else -> "That's every deck onepiece.gg has published (${state.decks.size})."
                            },
                            color = AppColors.TextDim,
                            fontSize = AppType.body,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    } else {
                        TextButton(
                            onClick = { vm.loadDecks() },
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        ) { Text("Load more") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckRow(deck: gg.opgg.android.data.Deck, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(
            deck.coverImage,
            null,
            Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
            targetPx = 200,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                deck.displayName,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    deck.authornick?.takeIf { it.isNotBlank() },
                    deck.format?.takeIf { it.isNotBlank() },
                    deck.archetype_name?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                color = AppColors.TextDim,
                fontSize = AppType.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${deck.uniqueCards} unique · ${deck.cardCount} cards" +
                    (deck.price?.takeIf { it > 0 }?.let { " · $${"%.2f".format(it)}" } ?: ""),
                color = AppColors.TextDim,
                fontSize = AppType.meta,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckDetailPage(
    deck: gg.opgg.android.data.Deck,
    state: UiState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenCard: (Card, List<Card>) -> Unit,
) {
    val slug = deck.slug
    LaunchedEffect(slug) { if (!slug.isNullOrBlank()) vm.loadDeckDetail(slug) }
    val detail = state.deckDetail
    // The list row already has the name, author, format and card list; the
    // getdeck call only adds the description, so render immediately.
    val name = detail?.displayName ?: deck.displayName
    val deckMap = detail?.deck?.takeIf { it.isNotEmpty() } ?: deck.deck
    val total = deckMap.values.sumOf { it.toIntOrNull() ?: 0 }
    val entries = remember(deckMap, state.cards) { state.resolveEntries(deckMap) }
    val ownedTotal = entries.sumOf { minOf(it.count, it.owned) }
    val cards = entries.mapNotNull { it.card }

    Surface(color = Palette.background, contentColor = AppColors.OnSurface) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Surface,
                    titleContentColor = Color.White,
                    navigationIconContentColor = AppColors.IconInactive,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        name,
                        fontSize = AppType.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                if (state.deckDetailLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (state.deckDetailError != null) {
                    Text(state.deckDetailError, color = AppColors.Danger, fontSize = AppType.body)
                }
                Text(
                    listOfNotNull(
                        deck.authornick?.takeIf { it.isNotBlank() },
                        deck.format?.takeIf { it.isNotBlank() },
                        deck.archetype_name?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    color = AppColors.TextDim,
                    fontSize = AppType.body,
                )
                if (!detail?.description.isNullOrBlank()) {
                    // The site stores descriptions as HTML - a tournament
                    // placement is an anchor - so it has to be parsed rather than
                    // printed, or the user sees the raw markup.
                    Text(
                        deckDescriptionHtml(detail!!.description),
                        fontSize = AppType.body,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    "${deckMap.size} unique · $total cards" +
                        (deck.price?.takeIf { it > 0 }?.let { " · $${"%.2f".format(it)}" } ?: ""),
                    color = AppColors.OnSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (state.session != null) {
                    Text(
                        "You own $ownedTotal of $total cards in this deck",
                        color = if (ownedTotal >= total && total > 0) AppColors.Positive else AppColors.Warning,
                        fontSize = AppType.body,
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Same grid and tile as the Cards tab. Count badge because a deck
                // needs copies; green dot when you own enough to complete it.
                val missing = entries.count { it.card == null }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries, key = { it.printingId }) { e ->
                        val card = e.card ?: Card(id = e.printingId, idNormal = e.normalId)
                        Box {
                            CardTile(card, state.ownedByNormal[card.key], onClick = { onOpenCard(card, cards) })
                            Text(
                                "x${e.count}",
                                fontSize = AppType.meta,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.AccentOn,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Palette.primary)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                            if (e.count > 0 && e.owned >= e.count) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AppColors.Positive),
                                )
                            }
                        }
                    }
                }
                if (missing > 0) {
                    Text(
                        "$missing card(s) were not found in the database.",
                        color = AppColors.Danger,
                        fontSize = AppType.meta,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionScreen(
    state: UiState,
    vm: AppViewModel,
    onSignIn: () -> Unit,
    gridState: LazyGridState,
    wishlist: Boolean,
    onOpenCard: (Card, List<Card>) -> Unit,
) {
    if (state.session == null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Sign in to sync your OnePiece.gg binder", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Your cards, prices and news come from the live OnePiece.gg database.",
                color = AppColors.TextDim,
                fontSize = AppType.body,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onSignIn) { Text("Sign in with OnePiece.gg") }
        }
        return
    }
    if (state.sessionRejected) {
        // The session was rejected, so the collection is empty because we cannot
        // read it - not because there is nothing in it. Saying "no binder yet"
        // here sends the user off to create a binder they already have.
        EmptyState(
            icon = Icons.Outlined.CloudOff,
            title = "Your session has expired",
            body = "OnePiece.gg is no longer accepting this device's sign-in, so your binder cannot be read. Sign in again to restore your collection.",
            action = {
                TextButton(onClick = onSignIn) {
                    Text(
                        "Sign in again",
                        color = AppColors.Accent,
                        fontSize = AppType.body,
                    )
                }
            },
        )
        return
    }
    if (state.binderError != null) {
        ErrorState(
            state.binderError!!,
            body = "Your collection is read from onepiece.gg. The app bar's refresh button will try again.",
        )
        return
    }
    // Quantity sits below the app bar, not beside the title: the title is a
    // picker and needs its width for binder names. The same row becomes the
    // bulk-move toolbar while selecting.
    //
    // In a binder (or the all-binders aggregate) the row also carries the value
    // of what is shown, priced from the card database already in memory.
    var addMode by remember(state.binderId) { mutableStateOf(false) }
    var selectMode by remember(state.binderId, wishlist) { mutableStateOf(false) }
    var selectedKeys by remember(state.binderId, wishlist) { mutableStateOf(setOf<String>()) }
    var bulkMoveOpen by remember { mutableStateOf(false) }
    val moveTargets = moveDestinations(state.binders, state.binderId)
    // A real binder has a mode switch and Add cards; All binders is a read-only
    // aggregate of every binder, so it must not offer editing.
    val inBinder = state.binderId > 0L
    val storeView = state.binderId != 0L
    val readOnly = state.binderId < 0L
    val storeValue = if (state.binderId != 0L) {
        remember(state.binderOwned, state.printingsByNormal) {
            val byId = state.printingsByNormal.values.flatten().associateBy { it.id }
            state.binderOwned.entries.sumOf { (id, counts) ->
                byId[id]?.let { ownedValue(counts, it.price, it.foilPrice) } ?: 0.0
            }
        }
    } else {
        0.0
    }
    val countLabel = if (wishlist) {
        "${state.wishCount} needed"
    } else {
        buildString {
            append("${state.activeStoreCount} copies")
            if (state.binderId != 0L && storeValue > 0.0) {
                append(" · $${"%.2f".format(storeValue)}")
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectMode) {
            Text(
                "${selectedKeys.size} selected",
                color = AppColors.TextDim,
                fontSize = AppType.body,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { bulkMoveOpen = true },
                enabled = selectedKeys.isNotEmpty(),
            ) {
                Text(
                    "Move…",
                    color = if (selectedKeys.isEmpty()) AppColors.IconDisabled else AppColors.Accent,
                    fontSize = AppType.body,
                )
            }
            TextButton(onClick = { selectMode = false; selectedKeys = emptySet() }) {
                Text("Cancel", color = AppColors.TextDim, fontSize = AppType.body)
            }
        } else {
            Text(
                countLabel,
                color = AppColors.TextDim,
                fontSize = AppType.body,
                modifier = Modifier.weight(1f),
            )
            // Explicit mode: long-press is the peek, so selection needs its own
            // entry point. Nothing to select in the wishlist, Add cards mode, or
            // the read-only all-binders aggregate.
            if (!wishlist && !addMode && !readOnly && moveTargets.isNotEmpty()) {
                TextButton(onClick = { selectMode = true; selectedKeys = emptySet() }) {
                    Text("Select", color = AppColors.Accent, fontSize = AppType.body)
                }
            }
        }
    }

    // One mode switch, inside a binder only: its contents, or the database used
    // to fill it. The store picker itself moved to the app-bar title.
    if (inBinder) {
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            SegmentedButton(
                selected = !addMode,
                onClick = { addMode = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text("In binder", fontSize = AppType.body)
            }
            SegmentedButton(
                selected = addMode,
                onClick = {
                    addMode = true
                    selectMode = false
                    selectedKeys = emptySet()
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text("Add cards", fontSize = AppType.body)
            }
        }
    }

    // Rows restored from disk are marked so nobody mistakes them for a live
    // read; the +/- below are disabled for the same reason.
    state.storeCacheAt?.let { savedAt -> CachedStoreNotice(savedAt) }

    val groups = when {
        wishlist -> state.wishGroups
        storeView -> state.binderGroups
        else -> state.ownedGroups
    }
    if (state.binderLoading) {
        // Reads are capped at 20 s now, but on a flaky link the skeleton can
        // still sit there. After 8 s, offer a retry so the user is not staring at
        // placeholders with no way out. The request id guard makes a retry safe.
        var slow by remember { mutableStateOf(false) }
        LaunchedEffect(state.binderId) {
            slow = false
            delay(8_000)
            slow = true
        }
        Box(Modifier.fillMaxSize()) {
            SkeletonCardGrid()
            if (slow) {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Still loading…", color = AppColors.TextDim, fontSize = AppType.body)
                    TextButton(onClick = { vm.loadBinder(state.binderId) }) {
                        Text("Retry", color = AppColors.Accent, fontSize = AppType.body)
                    }
                }
            }
        }
        return
    }

    if (!addMode && groups.isEmpty()) {
        // Distinct from the grid's own empty state: that one is a search that
        // matched nothing, this one is an empty store.
        EmptyState(
            icon = Icons.Outlined.Inventory2,
            title = when {
                wishlist -> "Your wishlist is empty"
                readOnly -> "No cards in your collection or binders"
                inBinder -> "This binder is empty"
                else -> "Your collection is empty"
            },
            body = when {
                wishlist -> "Open any card and use the bookmark, or the wishlist row under \"Your collection\", to mark the copies you want. Cards you own enough of drop off the list automatically."
                readOnly -> "Add cards to your collection or a binder and they will be summarised here."
                inBinder -> "Use \"Add cards\" above to find cards and set how many go in this binder. Copies live here, not in your collection."
                else -> "Open any card and set how many copies you own. It shows up here, and in the deck builder's \"Cards I need\"."
            },
            action = if (inBinder) {
                {
                    TextButton(onClick = { addMode = true }) {
                        Text("Add cards", color = AppColors.Accent, fontSize = AppType.body)
                    }
                }
            } else {
                null
            },
        )
        return
    }

    // Exactly the same grid as the Cards tab. In contents mode the list is one
    // entry per card held, using the printing actually held, filtered by the
    // shared search and sorted by the shared Sort. In add mode it is the card
    // database (already filtered for the Cards tab), so ± can put new copies in.
    val ownedCards = if (addMode) {
        state.visible
    } else {
        remember(groups, state.parsed, state.sort) {
            val base = groups.map { it.representative }
            val filtered = if (state.parsed.isEmpty) base
            else base.filter { CardSearch.matches(it, state.parsed) }
            sortCards(filtered, state.sort)
        }
    }

    CardsGrid(
        cards = ownedCards,
        state = state,
        vm = vm,
        wishMode = wishlist,
        // Binder views edit the binder; everything else edits the collection.
        // Binder views edit the binder; All binders is read-only; else collection.
        storeId = if (storeView) state.binderId else 0L,
        // All binders is a read-only aggregate; single binders edit normally.
        // Cached rows are a snapshot: writing onto them would be rejected anyway.
        editable = !readOnly && state.storeCacheAt == null,
        selectionMode = selectMode && !wishlist && !addMode,
        selectedKeys = selectedKeys,
        onToggleSelect = { c ->
            selectedKeys = if (c.key in selectedKeys) selectedKeys - c.key else selectedKeys + c.key
        },
        emptyState = {
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = "No cards match your search",
                body = when {
                    addMode -> "Clear the search to browse the card database."
                    wishlist -> "The wishlist holds ${groups.sumOf { it.wishTotal }} cards, but none of them match the search."
                    else -> "This store holds ${groups.sumOf { it.total }} cards, but none of them match the search."
                },
                action = {
                    TextButton(onClick = { vm.clearQuery() }) {
                        Text(
                            "Clear search",
                            color = AppColors.Accent,
                            fontSize = AppType.body,
                        )
                    }
                },
            )
        },
        gridState = gridState,
        // Hand over the exact list rendered, so swiping walks what is on screen.
        onClick = { onOpenCard(it, ownedCards) },
    )

    if (bulkMoveOpen) {
        MoveCardsDialog(
            heading = "Move ${selectedKeys.size} cards",
            detail = "Each selected card moves all its copies out of " +
                "${if (state.binderId == 0L) "Collection" else "this binder"}. There is no partial move.",
            destinations = moveTargets,
            onMove = { target ->
                val ids = selectedKeys.map { vm.printingToEdit(it, state.binderId) }
                bulkMoveOpen = false
                selectMode = false
                selectedKeys = emptySet()
                vm.moveCards(ids, state.binderId, target)
            },
            onDismiss = { bulkMoveOpen = false },
        )
    }
}

/** The site's fixed binder palette; `setbindercolor` rejects anything else. */
private val BinderColorPalette = listOf(
    "#3b82f6" to "Blue",
    "#ef4444" to "Red",
    "#22c55e" to "Green",
    "#14b8a6" to "Teal",
    "#8b5cf6" to "Purple",
    "#f59e0b" to "Orange",
    "#ec4899" to "Pink",
    "#eab308" to "Yellow",
    "#06b6d4" to "Cyan",
    "#64748b" to "Slate",
)

private fun binderColor(hex: String?): Color? =
    hex?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

/**
 * Indentation for one level of binder nesting.
 *
 * 12 dp was not readable as nesting on device, and rows without a colour dot
 * started further left than their coloured parent - the child looked *less*
 * indented than the parent. Each level is now a 10 dp slot with a faint rule,
 * so a child reads as sitting under its parent even when it has no colour.
 * (16 dp was then reported as too deep once the rule made the step obvious.)
 */
@Composable
private fun TreeIndent(depth: Int) {
    if (depth <= 0) return
    Box(Modifier.width((depth * 10).dp).height(24.dp)) {
        repeat(depth) { level ->
            Box(
                Modifier
                    .padding(start = (level * 10 + 4).dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(AppColors.DividerChart),
            )
        }
    }
}

/**
 * Compact replacement for `DropdownMenuItem`.
 *
 * Material's menu item enforces a 48 dp minimum height (`sizeIn(minHeight =
 * MenuListItemContainerHeight)`, which contentPadding cannot override), and the
 * binder picker is opened over the collection. These rows are 36 dp.
 */
@Composable
private fun MenuRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Menu text was labelLarge inside DropdownMenuItem; keep those metrics
        // now that the row is hand-rolled, otherwise the labels grow.
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            content()
        }
    }
}

/** Fixed-width colour chip, so rows with and without a colour align. */
@Composable
private fun BinderDot(hex: String?) {
    val c = binderColor(hex)
    if (c != null) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
    } else {
        Spacer(Modifier.size(10.dp))
    }
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun BinderNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun BinderActionsDialog(
    binder: ApiBinder,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onColor: (String) -> Unit,
    onShare: () -> Unit,
    onMoveUnder: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(binder.name ?: "Binder") },
        text = {
            Column {
                Text("Cards in a binder live there, separately from your collection.", fontSize = AppType.body, color = AppColors.TextDim)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRename) { Text("Rename", color = AppColors.Accent) }
                TextButton(onClick = onDelete) {
                    Text("Delete binder and its cards", color = AppColors.Danger)
                }
                TextButton(onClick = onMoveUnder) { Text("Move under…", color = AppColors.Accent) }
                TextButton(onClick = onShare) {
                    Text(
                        if (binder.public == 1) "Sharing (public)" else "Share…",
                        color = AppColors.Accent,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Colour", fontSize = AppType.meta, color = AppColors.TextDim)
                Spacer(Modifier.height(6.dp))
                // Two rows of five: ten 22dp swatches in one row overflowed the
                // dialog and the last colour was unreachable.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BinderColorPalette.chunked(5).forEach { chunk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            chunk.forEach { (hex, label) ->
                                val selected = binder.color?.equals(hex, ignoreCase = true) == true
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(binderColor(hex) ?: AppColors.SurfaceRaised)
                                        .border(
                                            if (selected) 2.dp else 1.dp,
                                            if (selected) AppColors.OnSurface else AppColors.Hairline,
                                            CircleShape,
                                        )
                                        .clickable { onColor(hex) }
                                        .semantics { contentDescription = "$label colour" },
                                )
                            }
                        }
                    }
                }
                if (!binder.color.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    TextButton(
                        onClick = { onColor("") },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                    ) {
                        Text("No colour", color = AppColors.TextDim, fontSize = AppType.body)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ManageBindersDialog(
    binders: List<ApiBinder>,
    onMove: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val tree = remember(binders) { orderWithDepth(binders) }
    // Arrows move a binder among its siblings only: a folder's children stay
    // under it, whatever the flat order looks like.
    val siblingPositions = remember(binders) {
        binders
            .groupBy { it.parent_id?.takeIf { p -> p != 0L } }
            .flatMap { (_, group) ->
                group.sortedWith(compareBy({ it.sort ?: Int.MAX_VALUE }, { it.id }))
                    .mapIndexed { index, b -> b.id to (index to group.size) }
            }
            .toMap()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage binders") },
        text = {
            Column {
                Text(
                    "Order here is the order in the picker. Arrows move a binder among its own level.",
                    fontSize = AppType.meta,
                    color = AppColors.TextDim,
                )
                Spacer(Modifier.height(8.dp))
                tree.forEach { (b, depth) ->
                    val position = siblingPositions[b.id]
                    val canUp = (position?.first ?: 0) > 0
                    val canDown = position != null && position.first < position.second - 1
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TreeIndent(depth)
                        BinderDot(b.color)
                        Text(
                            b.name ?: "Binder",
                            fontSize = AppType.body,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onMove(b.id, true) },
                            enabled = canUp,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move up",
                                tint = if (canUp) AppColors.OnSurface else AppColors.IconDisabled,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = { onMove(b.id, false) },
                            enabled = canDown,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move down",
                                tint = if (canDown) AppColors.OnSurface else AppColors.IconDisabled,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun ShareBinderDialog(
    binder: ApiBinder,
    nickname: String?,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember(binder.id, binder.public) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share ${binder.name ?: "binder"}") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Public link", fontSize = AppType.body)
                        Text(
                            if (binder.public == 1) {
                                "Anyone with the link can view this binder."
                            } else {
                                "Only you can see this binder."
                            },
                            fontSize = AppType.meta,
                            color = AppColors.TextDim,
                        )
                    }
                    Switch(checked = binder.public == 1, onCheckedChange = onToggle)
                }
                if (binder.public == 1) {
                    Spacer(Modifier.height(10.dp))
                    val url = binderShareUrl(nickname, binder)
                    if (url == null) {
                        Text(
                            "Add a nickname on onepiece.gg to get a share link.",
                            fontSize = AppType.meta,
                            color = AppColors.Warning,
                        )
                    } else {
                        Text(url, fontSize = AppType.meta, color = AppColors.Accent)
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Binder link", url))
                                copied = true
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                        ) {
                            Text(if (copied) "Copied" else "Copy link", color = AppColors.Accent, fontSize = AppType.body)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun MoveBinderDialog(
    binder: ApiBinder,
    options: List<Triple<Long, String, Int>>,
    onMove: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${binder.name ?: "binder"} under…") },
        text = {
            Column {
                Text(
                    "Folders are binders too. Pick a parent, or keep it at the top level.",
                    fontSize = AppType.body,
                    color = AppColors.TextDim,
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { (id, label, depth) ->
                    TextButton(
                        onClick = { onMove(id) },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TreeIndent(depth)
                            Text(label, color = AppColors.Accent, fontSize = AppType.body)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteBinderDialog(
    binder: ApiBinder,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${binder.name ?: "binder"}\"?") },
        text = {
            Text(
                "${binder.count ?: 0} card(s) inside will be deleted too. This cannot be undone.",
                fontSize = AppType.body,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = AppColors.Danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AccountScreen(state: UiState, vm: AppViewModel, onSignIn: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        val s = state.session
        if (s == null) {
            Text("Not signed in", fontWeight = FontWeight.Bold, fontSize = AppType.display)
            Spacer(Modifier.height(8.dp))
            Text(
                "Sign in with Google, Discord or email on the website's own login " +
                    "page. The app opens that page, you sign in there, and the site " +
                    "hands this app a session token. Your password is never typed " +
                    "into the app.",
                color = AppColors.IconInactive,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onSignIn) { Text("Sign in with OnePiece.gg") }
        } else {
            Text("Signed in", fontWeight = FontWeight.Bold, fontSize = AppType.display)
            Spacer(Modifier.height(8.dp))
            Text("Nick: ${s.nick ?: "?"}", color = AppColors.OnSurface)
            Text("User id: ${s.userId}", color = AppColors.TextDim)
            // If this matches User id, the Dotgguserauth header was accepted; if it
            // reads 0, the API silently treated us as anonymous and the collection
            // will always look empty.
            Text(
                "Server sees user id: ${state.serverUserId ?: "-"}",
                color = if (state.serverUserId != null && state.serverUserId > 0) AppColors.Positive else AppColors.Danger,
                fontSize = AppType.body,
            )
            Text("Binder id: ${state.binderId}", color = AppColors.TextDim, fontSize = AppType.body)
            Spacer(Modifier.height(8.dp))
            Text("Cards in binder: ${state.ownedCount}", color = AppColors.OnSurface)
            Text(
                if (state.wishCount > 0) "Still needed: ${state.wishCount}" else "Wishlist empty",
                color = AppColors.OnSurface,
                fontSize = AppType.body,
            )
            state.binderError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = AppColors.Danger, fontSize = AppType.body)
            }
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = { vm.logout() }) { Text("Log out") }
        }
        Spacer(Modifier.height(30.dp))
        Text("About", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Unofficial client. Data comes from api.dotgg.gg, the same backend onepiece.gg uses. " +
                "Not affiliated with or endorsed by OnePiece.gg.",
            color = AppColors.TextDim,
            fontSize = AppType.body,
        )
    }
}


