package gg.opgg.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gg.opgg.android.data.AppViewModel
import gg.opgg.android.data.Card
import gg.opgg.android.data.DeckDiff
import gg.opgg.android.data.DeckDiffResult
import gg.opgg.android.data.MissingCard

private val Dim = AppColors.TextDim

/**
 * "Cards I still need" for a local deck: what it runs that the collection does
 * not cover, biggest gap first.
 *
 * The counting rules live in [DeckDiff] as pure functions, so this is only
 * presentation. Tapping a row opens the card, matching the grid's behaviour
 * elsewhere in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeckMissingScreen(
    deckId: String,
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenCard: (Card, List<Card>) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val deck = state.localDecks.firstOrNull { it.id == deckId }
    val lookup = remember(deckId, state.printingsByNormal) { vm.printingLookup() }
    val diff = remember(deck, state.ownedByNormal, state.printingsByNormal) {
        deck?.let {
            DeckDiff.diff(
                deck = it,
                ownedByNormal = { n -> state.ownedByNormal[n]?.total ?: 0 },
                cardById = lookup,
            )
        }
    }

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
                        "Cards I need",
                        fontSize = AppType.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )

            if (deck == null || diff == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Deck not found", color = AppColors.TextDim)
                }
                return@Column
            }

            // Never present a verdict drawn from a collection that has not
            // loaded. An empty owned map makes every deck look complete, which is
            // worse than saying nothing.
            when {
                state.session == null -> Notice("Sign in to compare against your collection.", Dim)
                state.binderLoading -> Notice("Loading your collection...", Dim)
                state.binderError != null ->
                    Notice("Could not load your collection: ${state.binderError}", Dim)
                state.ownedByNormal.isEmpty() ->
                    Notice("Your collection came back empty, so this cannot be checked.", Dim)
                else -> Unit
            }

            Summary(diff)

            if (state.ownedByNormal.isNotEmpty() && !state.binderLoading && diff.isComplete) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        "You own everything this deck needs.",
                        color = AppColors.Positive,
                    )
                }
                return@Column
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(diff.missing, key = { it.normalId }) { m ->
                    MissingRow(
                        m = m,
                        card = m.printingsInDeck.firstNotNullOfOrNull { lookup(it) },
                        onOpen = { card -> onOpenCard(card, listOf(card)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, tint: Color) {
    Text(
        text,
        fontSize = AppType.body,
        color = tint,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun Summary(diff: DeckDiffResult) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            "${diff.missing.size} card(s) short · ${diff.missingCopies} copies to buy",
            fontSize = AppType.title,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.OnSurface,
        )
        Text(
            "${diff.covered} of ${diff.covered + diff.missing.size} cards complete" +
                " · ${diff.requiredCards} copies in the deck",
            fontSize = AppType.meta,
            color = AppColors.TextDim,
        )
    }
}

@Composable
private fun MissingRow(
    m: MissingCard,
    card: Card?,
    onOpen: (Card) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = card != null) { card?.let(onOpen) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(
            url = card?.imageUrl,
            description = null,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(6.dp)),
            targetPx = 180,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.name ?: m.normalId,
                fontSize = AppType.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("have ${m.owned}", fontSize = AppType.meta, color = AppColors.TextDim)
                Text("need ${m.required}", fontSize = AppType.meta, color = AppColors.TextDim)
                if (m.isLeader) {
                    Text("Leader", fontSize = AppType.meta, color = AppColors.Positive)
                }
            }
        }
        Text(
            "-${m.missing}",
            fontSize = AppType.title,
            fontWeight = FontWeight.Bold,
            color = AppColors.Warning,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(AppColors.WarningSoft)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
