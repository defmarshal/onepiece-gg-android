package gg.opgg.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gg.opgg.android.data.AppViewModel
import gg.opgg.android.data.Card
import gg.opgg.android.data.MetaArchetype
import gg.opgg.android.data.UiState

/** Copies per deck, without a trailing ".0" on whole numbers. */
private fun format(n: Double): String =
    if (n == n.toLong().toDouble()) n.toLong().toString() else String.format("%.1f", n)

private val MetaDim = AppColors.TextDim
private val MetaGood = AppColors.Positive
private val MetaWarn = AppColors.Warning

/** Tier letters, S best. Only 9 of 51 archetypes carry one. */
private fun tierColour(tier: String?): Color? = when (tier?.uppercase()) {
    "S" -> AppColors.Warning
    "A" -> MetaGood
    "B" -> MetaDim
    "C" -> AppColors.Danger
    else -> null
}

/**
 * The metagame: which decks are actually being played, and how well.
 *
 * Two things about the data shape the layout, both measured on a live snapshot:
 * `tier` is present on only 9 of 51 archetypes, so it is a badge and never the
 * backbone - ordering comes from `rank` and `share`. And the field-wide
 * benchmarks are shown, because a tier written against a weak field is not a
 * promise: 20% share earned "S" while the field's own top-8 rate was 25%.
 */
@Composable
internal fun MetaScreen(
    state: UiState,
    vm: AppViewModel,
    onOpenCard: (Card, List<Card>) -> Unit,
    listState: LazyListState,
) {
    val snapshot = state.metaSnapshot
    val rows = remember(snapshot) { snapshot?.ranked().orEmpty() }
    val peakShare = remember(rows) { rows.maxOfOrNull { it.share ?: 0.0 }?.coerceAtLeast(1.0) ?: 1.0 }

    Column(Modifier.fillMaxSize()) {
        MetaHeader(state, vm, snapshot?.archetypes?.size ?: 0)

        if (state.metaLoading && snapshot == null) {
            // Rows without a leading thumbnail, matching the archetype list.
            SkeletonRowList(rows = 9, thumb = null)
            return@Column
        }
        if (snapshot == null) {
            // An error and "not loaded yet" are different situations and used to
            // be indistinguishable, since both fell through to the same grey line.
            if (state.metaError != null) {
                ErrorState(
                    state.metaError!!,
                    body = "The metagame snapshot is fetched from onepiece.gg. The app bar's refresh button will try again.",
                )
            } else {
                EmptyState(
                    icon = Icons.Outlined.Insights,
                    title = "No metagame data yet",
                    body = "The snapshot has not been fetched. Use the refresh button in the app bar.",
                )
            }
            return@Column
        }
        if (rows.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Insights,
                title = "No archetypes in this snapshot",
                body = "The snapshot loaded but listed none. It may have been taken between tournament windows.",
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(rows, key = { it.id ?: it.slug ?: it.name.orEmpty() }) { a ->
                ArchetypeRow(
                    a = a,
                    peakShare = peakShare,
                    fieldTop8 = snapshot.fieldTop8Rate,
                    state = state,
                    vm = vm,
                    onOpenCard = onOpenCard,
                )
            }
        }
    }
}

@Composable
private fun MetaHeader(state: UiState, vm: AppViewModel, count: Int) {
    val s = state.metaSnapshot
    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (count > 0) "$count archetypes" else "Metagame",
                    fontSize = AppType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.OnSurface,
                )
                val sample = s?.sample
                if (sample != null && sample.decks != null) {
                    Text(
                        "${sample.decks} decks · ${sample.events} events · " +
                            "${sample.matches} matches · ${s?.window ?: "30d"}",
                        fontSize = AppType.meta,
                        color = MetaDim,
                    )
                }
            }
            // No refresh button here: the app bar already has one for this
            // segment, and two identical icons three centimetres apart looked
            // like a mistake.
            if (state.metaLoading) {
                LinearProgressIndicator(Modifier.width(60.dp))
            }
        }
        if (state.metaError != null) {
            Text(
                "Could not refresh: ${state.metaError}. Showing the last snapshot.",
                fontSize = AppType.caption,
                color = MetaWarn,
            )
        }
    }
}

@Composable
private fun ArchetypeRow(
    a: MetaArchetype,
    peakShare: Double,
    fieldTop8: Double?,
    state: UiState,
    vm: AppViewModel,
    onOpenCard: (Card, List<Card>) -> Unit,
) {
    // Expansion lives in the ViewModel, not a `remember` here: opening a card
    // page from a staple replaces this whole screen, which discarded the
    // remembered flag and came back collapsed.
    val open = state.metaExpandedArchetype == a.key
    val share = a.share ?: 0.0
    val wr = a.winrate

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { vm.toggleMetaArchetype(a.key) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#${a.rank ?: "-"}",
                fontSize = AppType.body,
                fontWeight = FontWeight.Bold,
                color = MetaDim,
                modifier = Modifier.width(34.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.name.orEmpty(),
                        fontSize = AppType.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    tierColour(a.tier)?.let { c ->
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(c.copy(alpha = 0.22f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(a.tier.orEmpty(), fontSize = AppType.caption, fontWeight = FontWeight.Bold, color = c)
                        }
                    }
                    a.momentum?.let {
                        if (it != "flat") {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (it.startsWith("up")) "up" else "down",
                                fontSize = AppType.caption,
                                color = if (it.startsWith("up")) MetaGood else AppColors.Danger,
                            )
                        }
                    }
                }
                // Share, against the biggest deck in the field.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(AppColors.Track),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((share / peakShare).toFloat().coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(Palette.primary),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        String.format("%.1f%%", share),
                        fontSize = AppType.meta,
                        color = AppColors.OnSurface,
                    )
                }
                Text(
                    buildString {
                        append("${a.decks ?: 0} decks")
                        if (wr != null) {
                            append(" · ")
                            append(String.format("%.0f%% win", wr * 100))
                        }
                        val f8 = fieldTop8
                        val t8 = a.top8Rate
                        if (f8 != null && t8 != null) {
                            append(" · ")
                            append(
                                String.format(
                                    "top 8 %.0f%% (field %.0f%%)",
                                    t8 * 100,
                                    f8 * 100,
                                ),
                            )
                        }
                    },
                    fontSize = AppType.meta,
                    color = MetaDim,
                )
            }
        }

        AnimatedVisibility(visible = open) {
            ArchetypeDetail(a, state, onOpenCard)
        }
    }
}

@Composable
private fun ArchetypeDetail(
    a: MetaArchetype,
    state: UiState,
    onOpenCard: (Card, List<Card>) -> Unit,
) {
    Column(Modifier.padding(start = 34.dp, top = 8.dp)) {
        // The tier is written against the field, so show the reasoning, not just
        // the letter.
        a.tierReason?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = AppType.body, color = AppColors.OnSurface)
            Spacer(Modifier.height(6.dp))
        }
        a.researchNote?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = AppType.body, color = AppColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
        }

        if (a.form.isNotEmpty()) {
            Text("Recent finishes", fontSize = AppType.meta, fontWeight = FontWeight.Bold, color = MetaDim)
            a.form.take(6).forEach { f ->
                Text(
                    "· ${f.place?.let { "P$it" } ?: "-"} ${f.event ?: "event"}" +
                        (f.players?.let { " ($it players)" } ?: ""),
                    fontSize = AppType.meta,
                    color = AppColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        if (a.cards.isNotEmpty()) {
            Text("Staples", fontSize = AppType.meta, fontWeight = FontWeight.Bold, color = MetaDim)
            // Resolved through the same lookup the rest of the app uses, and
            // opened through the shared card page, so a staple here behaves
            // exactly like a tile on the Cards or Collection tab.
            val staples = a.cards.mapNotNull { entry ->
                state.printingsByNormal[entry.id]?.firstOrNull()?.let { entry to it }
            }
            if (staples.isEmpty()) {
                a.cards.take(10).forEach { c ->
                    Text("· ${c.name ?: c.id}", fontSize = AppType.meta, color = AppColors.TextSecondary)
                }
            } else {
                val swipeList = staples.map { it.second }
                staples.take(10).forEach { (entry, card) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCard(card, swipeList) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CardImage(
                            url = card.imageUrl,
                            description = card.name,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(5.dp)),
                            targetPx = 160,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                card.name ?: entry.id.orEmpty(),
                                fontSize = AppType.body,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildString {
                                    entry.copies?.let { append("x${format(it)} ") }
                                    entry.weight?.let {
                                        append("${(it * 100).toInt()}% of decks")
                                    }
                                },
                                fontSize = AppType.caption,
                                color = MetaDim,
                            )
                        }
                    }
                }
            }
        }

        if (a.evidence.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Sources", fontSize = AppType.meta, fontWeight = FontWeight.Bold, color = MetaDim)
            a.evidence.take(4).forEach { e ->
                Text(
                    "· ${e.title ?: "source"}${e.date?.let { " ($it)" } ?: ""}",
                    fontSize = AppType.meta,
                    color = MetaDim,
                )
            }
        }
    }
}
