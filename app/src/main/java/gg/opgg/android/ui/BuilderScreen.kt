package gg.opgg.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gg.opgg.android.data.AppViewModel
import gg.opgg.android.data.Card
import gg.opgg.android.data.DeckAnalyticsCalc
import gg.opgg.android.data.DeckDiff
import gg.opgg.android.data.DeckRules
import gg.opgg.android.data.LocalDeck
import gg.opgg.android.data.Regulation
import gg.opgg.android.data.RegulationMode
import gg.opgg.android.data.SortField
import gg.opgg.android.data.TileAvailability
import gg.opgg.android.data.TileBlock
import gg.opgg.android.data.sortCards

/** Which dialog the long-press menu is asking for. */
private enum class Action { RENAME, DELETE }

private data class PendingDeckAction(val id: String, val name: String, val action: Action)

/**
 * The deck list. Decks live on this device only; "Copy deck code" is how one
 * reaches onepiece.gg, because the website's write endpoint is unsolved.
 *
 * [onEdit] receives the deck id to open rather than the screen reading it back
 * out of state. It used to call `vm.openLocalDeck(id)` and then navigate, but
 * the state update is asynchronous while the navigation callback read the
 * already-captured `state`, so it always saw null and pushed nothing. The row
 * rippled, nothing happened, and a second tap - by which time the composition
 * had caught up - worked. Hence "tap twice to open a deck".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen(
    vm: AppViewModel,
    onEdit: (String) -> Unit,
    onMissing: (String) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    // Held at screen level, not per row: a dialog parented to a LazyColumn item
    // is recreated mid-scroll and the dialog goes with it.
    var pending by remember { mutableStateOf<PendingDeckAction?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.loadLocalDecks()
        android.util.Log.i("OpggDecks", "builder list opened")
    }

    // No app bar here. The Builder is a segment of the Decks tab, so it used to
    // stack a second one directly under the Decks bar and the segment chips -
    // three rows of chrome before a single deck. The title and the Import / New
    // deck actions now live in the Decks tab's own bar, which already had them
    // free. Only the list is drawn here.
    Surface(color = Palette.background, contentColor = AppColors.OnSurface) {
        Column(Modifier.fillMaxSize()) {
            if (state.localDecks.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Style,
                    title = "No decks on this device yet",
                    body = "Decks you build here are stored locally and never touch onepiece.gg. Use New deck in the app bar above to start one.",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.localDecks, key = { it.id }) { deck ->
                        val lookup = remember(deck.id, state.printingsByNormal) { vm.printingLookup() }
                        val violations = remember(deck) { DeckRules.validate(deck, lookup) }
                        val main = remember(deck) { deck.mainDeckCount() }
                        val diff = remember(deck, state.ownedByNormal, state.printingsByNormal) {
                            DeckDiff.diff(
                                deck = deck,
                                ownedByNormal = { n -> state.ownedByNormal[n]?.total ?: 0 },
                                cardById = lookup,
                            )
                        }
                        // Per-item and fine to forget: it only has to survive a
                        // recomposition, and scrolling the row away should close
                        // the menu rather than leave a stray one anchored nowhere.
                        var menuOpen by remember(deck.id) { mutableStateOf(false) }
                        Box {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    // Tap still opens the editor; long-press is
                                    // additive rather than a replacement.
                                    .combinedClickable(
                                        onClick = { onEdit(deck.id) },
                                        onLongClick = { menuOpen = true },
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CardImage(
                                    url = deck.leaderId?.let { lookup(it)?.imageUrl },
                                    description = null,
                                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(6.dp)),
                                    targetPx = 180,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        deck.name,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "$main / ${DeckRules.MAIN_DECK_SIZE} cards · " +
                                            "${DeckRules.coloursUsed(deck, lookup).size} colour(s)",
                                        color = AppColors.TextDim,
                                        fontSize = AppType.body,
                                    )
                                    if (violations.isNotEmpty()) {
                                        Text(
                                            "${violations.size} issue(s)",
                                            color = AppColors.Warning,
                                            fontSize = AppType.meta,
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(vm.exportDeckCode(deck)))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy deck code")
                                }
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                // The count is the point: it is the answer to
                                // "can I play this deck tonight" without opening
                                // anything. But a verdict must never be claimed
                                // from a collection that has not loaded - an empty
                                // map would otherwise read as "you own it all".
                                val verdict = when {
                                    state.session == null -> "sign in to check"
                                    state.binderLoading -> "checking your collection..."
                                    state.binderError != null -> "collection unavailable"
                                    state.ownedByNormal.isEmpty() -> "collection empty"
                                    diff.isComplete -> "complete"
                                    else -> "${diff.missingCopies}"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Cards I need - $verdict",
                                            color = if (diff.isComplete &&
                                                state.ownedByNormal.isNotEmpty() &&
                                                !state.binderLoading
                                            ) {
                                                AppColors.Positive
                                            } else {
                                                AppColors.OnSurface
                                            },
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onMissing(deck.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        menuOpen = false
                                        renameDraft = deck.name
                                        pending = PendingDeckAction(deck.id, deck.name, Action.RENAME)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    onClick = {
                                        menuOpen = false
                                        // Opens the copy straight away: a duplicate
                                        // you have to go and find is no use.
                                        vm.duplicateLocalDeck(deck.id)?.let { onEdit(it.id) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy deck code") },
                                    onClick = {
                                        menuOpen = false
                                        clipboard.setText(AnnotatedString(vm.exportDeckCode(deck)))
                                    },
                                )
                                // The site EXPORTS `${count}x${printingId}` but
                                // IMPORTS the same text with the reprint suffix
                                // dropped, so a deck cannot get in by pasting what
                                // it copied out. This is that other shape.
                                DropdownMenuItem(
                                    text = { Text("Copy deck code for onepiece.gg") },
                                    onClick = {
                                        menuOpen = false
                                        val site = vm.exportDeckCodeForSite(deck)
                                        clipboard.setText(AnnotatedString(site.code))
                                        vm.postMessage(
                                            if (site.hasCollisions) {
                                                "Copied for onepiece.gg. Note: ${deck.name} holds " +
                                                    "both a printing and its reprint of " +
                                                    "${site.collapsedPrintings.joinToString()}, " +
                                                    "which the site cannot tell apart."
                                            } else {
                                                "Deck code copied for onepiece.gg."
                                            },
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = AppColors.Danger) },
                                    onClick = {
                                        menuOpen = false
                                        pending = PendingDeckAction(deck.id, deck.name, Action.DELETE)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (pending?.action == Action.RENAME) {
        val target = pending
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Rename deck") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    isError = renameDraft.isBlank(),
                    supportingText = if (renameDraft.isBlank()) {
                        { Text("A deck needs a name.") }
                    } else {
                        null
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameLocalDeck(target!!.id, renameDraft)
                        pending = null
                    },
                    enabled = renameDraft.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }

    if (pending?.action == Action.DELETE) {
        val target = pending
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Delete deck?") },
            text = {
                Text(
                    "\"${target?.name}\" will be removed from this device. " +
                        "There is no undo, and it is not on onepiece.gg, so " +
                        "copy the deck code first if you want to keep it.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteLocalDeck(target!!.id)
                    pending = null
                }) { Text("Delete", color = AppColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }

}

/**
 * The import sheet. Extracted so the Decks tab's app bar can open it: the
 * Builder no longer draws its own app bar, so the state lives with the tab that
 * hosts the button.
 */
@Composable
internal fun ImportDeckDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import deck code") },
        text = {
            Column {
                Text(
                    "Paste the site's deck code, e.g. 4xOP01-001. " +
                        "Cards that are not recognised will be listed.",
                    fontSize = AppType.body,
                    color = AppColors.TextDim,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImport(text) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The editor. Legality is shown as a live list rather than a single pass/fail,
 * and the picker refuses illegal adds up front rather than letting the user
 * build an illegal deck and finding out afterwards.
 */
/**
 * The editor.
 *
 * One scrolling grid, not a list inside a verticalScroll: nesting a lazy grid in
 * a scrollable column does not work, and that mistake is already in this
 * codebase. Tiles are the same ones the Cards and Collection tabs use, so the
 * app feels like one thing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckEditorScreen(
    deckId: String,
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenCard: (Card, List<Card>) -> Unit,
    gridState: LazyGridState,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val deck = state.localDecks.firstOrNull { it.id == deckId }
    // Keyed on the card database as well as the deck. Keyed only on deckId it
    // captured the lookup before the cards loaded and stayed empty forever, so
    // every Leader read as "no Leader" even with a valid leaderId.
    val lookup = remember(deckId, state.printingsByNormal) { vm.printingLookup() }
    // Held in the ViewModel, not here: opening a card page swaps this screen out
    // of composition, and plain `remember` state was discarded with it, so the
    // Owned only / In deck / Leader filters reset on the way back.
    val leadersOnly = state.editorLeadersOnly
    val ownedOnly = state.editorOwnedOnly
    val inDeckOnly = state.editorInDeckOnly
    val searchOpen = state.editorSearchOpen
    val sortOpen = state.editorSortOpen
    val analyticsExpanded = state.editorAnalyticsExpanded
    val issuesExpanded = state.editorIssuesExpanded
    fun setFlags(
        leaders: Boolean = leadersOnly,
        owned: Boolean = ownedOnly,
        inDeck: Boolean = inDeckOnly,
        search: Boolean = searchOpen,
        sort: Boolean = sortOpen,
        analytics: Boolean = analyticsExpanded,
        issues: Boolean = state.editorIssuesExpanded,
        regulation: RegulationMode = state.regulationMode,
    ) = vm.setEditorFlags(leaders, owned, inDeck, search, sort, analytics, issues, regulation)
    val clipboard = LocalClipboardManager.current

    // Re-read on entry, and log taps so a dead control is diagnosable.
    LaunchedEffect(deckId) {
        vm.loadLocalDecks()
        android.util.Log.i("OpggDecks", "editor open deck=$deckId")
    }

    if (deck == null) {
        // Deck creation is async, so "not in state yet" must not be reported as
        // "does not exist".
        if (!vm.isDecksReady()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Deck not found", color = AppColors.TextDim) }
        }
        return
    }

    // The editor's own search, independent of the Cards tab: it filters the
    // FULL pool (state.cards), not the Cards tab's filtered-and-sorted
    // `visible`, and reads the editor's query state. Neither side's tokens
    // leak into the other. The grid order follows the shared sort preference.
    var searchResults by remember { mutableStateOf(state.cards) }
    LaunchedEffect(state.editorQuery, state.cards, state.sort) {
        searchResults = sortCards(vm.searchCards(state.cards, state.editorQuery), state.sort)
    }

    val violations = remember(deck, state.printingsByNormal, state.regulationMode) {
        DeckRules.validate(deck, lookup, state.regulationMode)
    }
    // Keyed the same way as the rules check, so the statistics are recomputed
    // when the deck changes rather than on every recomposition.
    val analytics = remember(deck, state.printingsByNormal) { DeckAnalyticsCalc.analyse(deck, lookup) }
    // The Leader is resolved from the cards that are actually in the deck, not
    // from `leaderId`. A deck saved before withCount() cleared the id can still
    // name a Leader it does not contain, and trusting that id wedged the card
    // pool on the old Leader's colours with no way out.
    val deckLeaderId = deck.cards.entries
        .firstOrNull { (id, n) -> n > 0 && DeckRules.isLeader(lookup(id)) }
        ?.key
    val allowed = DeckRules.allowedColours(lookup(deckLeaderId.orEmpty()))
    val main = deck.mainDeckCount()
    val legal = violations.isEmpty()

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
                    OutlinedTextField(
                        value = deck.name,
                        onValueChange = { vm.updateLocalDeck(deck.copy(name = it)) },
                        textStyle = TextStyle(fontSize = AppType.title, fontWeight = FontWeight.Bold, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                actions = {
                    IconButton(onClick = { setFlags(search = !searchOpen) }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (searchOpen) Palette.primary else AppColors.IconInactive,
                        )
                    }
                    // Same SortSheet as the Cards tab; the preference is shared.
                    IconButton(onClick = { setFlags(sort = true) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = if (state.sort.field != SortField.NONE) Palette.primary else AppColors.IconInactive,
                        )
                    }
                    // Same FilterSheet as the Cards tab (Set/Rarity/Type/Color/
                    // Attribute/Cost/Block/Counter dropdowns writing search
                    // tokens). Lit when any token is active, like the Cards tab.
                    IconButton(onClick = onOpenFilters) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filters",
                            tint = if (state.editorParsed.terms.isNotEmpty()) Palette.primary else AppColors.IconInactive,
                        )
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(vm.exportDeckCode(deck))) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy deck code")
                    }
                },
            )

            // Analytics sits at the very top of the editor, above the grid and
            // above the filter chips, collapsed by default.
            DeckAnalyticsPanel(
                analytics = analytics,
                expanded = analyticsExpanded,
                onToggle = { setFlags(analytics = !analyticsExpanded) },
            )

            // Status line + leader mode switch, fixed above the scrolling grid.
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text(
                    "$main / ${DeckRules.MAIN_DECK_SIZE} cards" +
                        (deckLeaderId?.let { "  ·  ${lookup(it)?.name ?: it}" } ?: "  ·  no Leader yet"),
                    fontSize = AppType.body,
                    color = if (legal) AppColors.Positive else AppColors.Warning,
                )
                  if (allowed.isNotEmpty()) {
                      Text("Colours allowed: ${allowed.joinToString("/")}", fontSize = AppType.meta, color = AppColors.TextDim)
                  }
                  // Collapsed by default. A deck with a wrong-coloured Leader
                  // can produce one violation per card, which buried the grid
                  // under dozens of identical lines.
                  if (violations.isNotEmpty()) {
                      Row(
                          Modifier
                              .fillMaxWidth()
                              .clickable { setFlags(issues = !issuesExpanded) }
                              .padding(vertical = 3.dp),
                          verticalAlignment = Alignment.CenterVertically,
                      ) {
                          Text(
                              if (violations.size == 1) "1 issue" else "${violations.size} issues",
                              fontSize = AppType.body,
                              fontWeight = FontWeight.SemiBold,
                              color = AppColors.Warning,
                          )
                          Spacer(Modifier.width(6.dp))
                          Text(
                              if (issuesExpanded) "hide" else "show",
                              fontSize = AppType.meta,
                              color = AppColors.TextDim,
                          )
                      }
                      if (issuesExpanded) {
                        violations.forEach { v ->
                            Text("• ${v.message}", fontSize = AppType.meta, color = AppColors.Warning)
                        }
                      }
                  }
                // Builder-only quick toggles. Everything with a Cards-tab
                // equivalent lives in the shared FilterSheet / SortSheet; these
                // five have no counterpart there. Horizontally scrollable: five
                // chips do not fit a narrow phone in one row.
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Show leaders only, for switching the deck's Leader. A
                    // separate "Cards" chip said nothing; off is just the list.
                    item {
                        FilterChip(
                            selected = leadersOnly,
                            onClick = { setFlags(leaders = !leadersOnly) },
                            label = { Text("Leaders", fontSize = AppType.body) },
                        )
                    }
                    // Build from what you actually own.
                    item {
                        FilterChip(
                            selected = ownedOnly,
                            onClick = { setFlags(owned = !ownedOnly) },
                            label = { Text("Owned only", fontSize = AppType.body) },
                        )
                    }
                    // "What is in my deck?" Tiles only carry a count, so this
                    // is the view for auditing a deck.
                    item {
                        FilterChip(
                            selected = inDeckOnly,
                            onClick = { setFlags(inDeck = !inDeckOnly) },
                            label = {
                                Text(
                                    if (inDeckOnly) "In deck (${deck.totalWithLeader()})" else "In deck",
                                    fontSize = AppType.body,
                                )
                            },
                        )
                    }
                    // Standard / Extra. Standard is a rolling four-Block window
                    // plus the ban list; Extra is the whole back catalogue.
                    item {
                        FilterChip(
                            selected = state.regulationMode == RegulationMode.STANDARD,
                            onClick = { setFlags(regulation = RegulationMode.STANDARD) },
                            label = { Text("Standard", fontSize = AppType.body) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.regulationMode == RegulationMode.EXTRA,
                            onClick = { setFlags(regulation = RegulationMode.EXTRA) },
                            label = { Text("Extra", fontSize = AppType.body) },
                        )
                    }
                }
                // What is being enforced, and how old the hand-kept ban list is.
                // Shown because the list cannot be read from the API and will go
                // stale silently if nobody looks at it.
                Text(
                    Regulation.describe(Regulation.todayEpochDay(), state.regulationMode),
                    fontSize = AppType.caption,
                    color = AppColors.TextDim,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
                // Search hides behind the icon, same as the Cards tab. The
                // FilterList icon opens the shared FilterSheet; chips summarise
                // the active tokens underneath.
                if (searchOpen) {
                    SearchBar(
                        query = state.editorQuery,
                        onQueryChange = vm::setEditorQuery,
                        onOpenFilters = onOpenFilters,
                        unknown = state.editorParsed.unknownTokens,
                        activeTerms = state.editorParsed.terms.size,
                        suggestionsFor = vm::suggestionsFor,
                        applySuggestion = vm::applyEditorSuggestion,
                    )
                    ActiveFilterChips(
                        state,
                        vm,
                        parsed = state.editorParsed,
                        onClear = { vm.clearEditorQuery() },
                    )
                }
            }

            val shown = remember(
                state.editorQuery, leadersOnly, ownedOnly, inDeckOnly, searchResults, deck, allowed,
                state.regulationMode,
            ) {
                searchResults.asSequence()
                    .filter { leadersOnly == DeckRules.isLeader(it) }
                    // Banned cards are hidden rather than shown-and-blocked: the
                    // notice says they "cannot be included in any deck", so
                    // offering one in the picker only invites an illegal deck.
                    // Block-window cards stay visible - the window moves every
                    // April, and hiding a quarter of the pool would be worse
                    // than letting the status line explain it.
                    .filter { card ->
                        state.regulationMode != RegulationMode.STANDARD ||
                            !Regulation.isBanned(
                                card.idNormal ?: card.id.orEmpty(),
                                Regulation.todayEpochDay(),
                                state.regulationMode,
                            )
                    }
                    // Once a Leader is set, the main deck is limited to its
                    // colours, so cards outside that palette cannot be played.
                    //
                    // Leaders are exempt. The colour rule governs the *main deck*,
                    // not which Leader you may pick, and a Leader defines the
                    // palette rather than obeying it. Filtering Leaders too made a
                    // trap: switch to a mono-Red Leader and every Red/Blue Leader
                    // vanished from the list, so the wider Leader could never be
                    // chosen again.
                    .filter { card ->
                        DeckRules.isLeader(card) ||
                            allowed.isEmpty() ||
                            DeckRules.coloursOf(card).all { it in allowed }
                    }
                    .filter { card ->
                        !ownedOnly || (state.ownedByNormal[card.key]?.total ?: 0) > 0
                    }
                    .filter { card -> !inDeckOnly || (deck.cards[card.id] ?: 0) > 0 }
                    .take(120)
                    .toList()
            }

            if (shown.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "No cards match",
                    body = "Nothing available matches the search and the deck's colour rules. Clear the search to see everything you can legally add.",
                    action = {
                        TextButton(onClick = { vm.clearEditorQuery() }) {
                            Text("Clear search", color = AppColors.Accent, fontSize = AppType.body)
                        }
                    },
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    // Passed in from AppRoot. Created here it would be destroyed
                    // whenever a card page is pushed, resetting the scroll.
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.key }) { c ->
                        val id = c.id.orEmpty()
                        val isLeader = DeckRules.isLeader(c)
                        val isChosenLeader = leadersOnly && id == deckLeaderId
                        val inDeck = deck.cards[id] ?: 0
                        // Owned only => you cannot put in more than you hold.
                        val cap = if (ownedOnly) {
                            (state.ownedByNormal[c.key]?.total ?: 0)
                        } else {
                            DeckRules.MAX_COPIES_PER_PRINTING
                        }
                        val block = TileAvailability.blockFor(
                            deck = deck,
                            printingId = id,
                            cardById = lookup,
                            ownedCap = if (ownedOnly) cap else null,
                        )
                        DeckTile(
                            card = c,
                            count = inDeck,
                            chosen = isChosenLeader,
                            cap = cap,
                            block = block,
                            // With the In deck filter you are reviewing and editing
                            // what is already in the deck, so greying out and
                            // captioning the very cards you are working on works
                            // against it. The + button still respects the rules.
                            showHint = !inDeckOnly,
                            // Tapping the card opens its details, as on the
                            // Cards tab. Adding is only via + / -.
                            onOpen = { onOpenCard(c, shown) },
                            onAdd = {
                                if (isLeader) {
                                    vm.updateLocalDeck(
                                        deck.withLeader(id) { p -> DeckRules.isLeader(lookup(p)) },
                                    )
                                } else {
                                    vm.updateLocalDeck(deck.withCount(id, inDeck + 1))
                                }
                            },
                            onRemove = {
                                vm.updateLocalDeck(deck.withCount(id, inDeck - 1))
                            },
                        )
                    }
                }
            }
        }
    }

    // Shared with the Cards tab; the sort preference is one and the same.
    if (sortOpen) {
        SortSheet(state, vm) { setFlags(sort = false) }
    }
}

/** A card tile with the same + / - shape used on the Cards tab. */
@Composable
private fun DeckTile(
    card: Card,
    count: Int,
    block: TileBlock?,
    chosen: Boolean = false,
    cap: Int = DeckRules.MAX_COPIES_PER_PRINTING,
    /** False while the In deck filter is on: no captions, no greying. */
    showHint: Boolean = true,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    // A deck-level reason (no Leader, deck full) is already stated in the status
    // line above the grid, so it dims nothing and says nothing here. Only a
    // card's own problem is worth a caption.
    val cardLevel = block != null && !block.deckLevel
    val hint = showHint && cardLevel
    // This is a second card tile, separate from the one on the Cards tab: the
    // editor needs a chosen-Leader tick, a copy cap and a per-card warning, none
    // of which the Cards tab shows. It is denser on purpose - 110dp of art and a
    // caption-size name, because the stepper row below has to fit as well.
    //
    // What it must not do is look different. The frame matches the Cards tab's
    // exactly, so moving between the two grids does not feel like a different app.
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .alpha(if (hint) 0.4f else 1f)
            .clip(shape)
            .background(if (chosen) AppColors.SurfaceSelected else AppColors.Surface)
            .border(1.dp, AppColors.Hairline, shape)
            .padding(3.dp),
    ) {
        // Tapping the art opens the card's details, as on the Cards tab.
        Box(Modifier.clickable { onOpen() }) {
            CardImage(
                url = card.imageUrl,
                description = card.name,
                modifier = Modifier.fillMaxWidth().height(110.dp),
                targetPx = 260,
            )
            // A chosen Leader is marked, so there is never any doubt that only
            // one is set.
            if (chosen) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(AppColors.Positive),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", fontSize = AppType.meta, color = AppColors.PositiveOn, fontWeight = FontWeight.Bold)
                }
            }
            if (count > 0) {
                Text(
                    "x$count",
                    fontSize = AppType.meta,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.AccentOn,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Palette.primary)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            card.name.orEmpty(),
            fontSize = AppType.caption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
        )
        if (hint) {
            Text(
                block?.label.orEmpty(),
                fontSize = AppType.caption,
                color = AppColors.Warning,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 3.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileButton("−", enabled = count > 0, onClick = onRemove)
            if (chosen) {
                Text("Leader", fontSize = AppType.caption, color = AppColors.Positive, fontWeight = FontWeight.Bold)
            } else {
                // The cap sits centred between the two buttons rather than tucked
                // under the "+", so it reads as a property of the tile instead of
                // the button. It is also shorter than the 26dp buttons, so showing
                // it cannot change the row height - which is what keeps "+" and "-"
                // on the same baseline whether or not a cap is present.
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (cap < DeckRules.MAX_COPIES_PER_PRINTING) {
                        Text("max $cap", fontSize = AppType.caption, color = AppColors.TextDim)
                    }
                }
            }
            if (!chosen) {
                TileButton("+", enabled = block == null, onClick = onAdd)
            }
        }
    }
}
