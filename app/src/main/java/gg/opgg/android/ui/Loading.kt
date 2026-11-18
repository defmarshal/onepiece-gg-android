package gg.opgg.android.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Placeholder screens for when data is still arriving.
 *
 * A centred spinner says "wait" but also throws away the screen: the layout it
 * is about to fill is unknown, so the content jumps when the real thing appears.
 * A skeleton holds that layout, so nothing moves.
 *
 * The one rule that matters here is that a placeholder must match the shape of
 * the content it stands in for. These mirror the real tile geometry - the same
 * adaptive columns, the same 8dp gutters, the same 12dp frame, the same 140dp of
 * art and the same two-line name - and the rows mirror `DeckRow` at 52dp. If the
 * tile geometry changes and this does not, the swap becomes a jump again.
 *
 * The pulse is one slow alpha fade on the whole grid rather than a travelling
 * gradient. A travelling highlight has to be clipped and phase-aligned per tile
 * or it looks like the tiles are individually blinking, and it costs a gradient
 * and a graphics layer on every tile on screen. A single shared fade reads the
 * same and is one animation.
 */
@Composable
private fun rememberSkeletonPulse(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    return alpha
}

/** One placeholder in place of a card tile. */
@Composable
private fun SkeletonCardTile() {
    Column(
        Modifier
            .clip(TileShape)
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Hairline, TileShape)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(AppColors.SurfaceRaised)
        )
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
            Spacer(Modifier.height(4.dp))
            SkeletonBar(Modifier.fillMaxWidth())
            Spacer(Modifier.height(7.dp))
            SkeletonBar(Modifier.fillMaxWidth(0.62f))
            Spacer(Modifier.height(9.dp))
            // The set line, which is a caption rather than a name.
            SkeletonBar(Modifier.fillMaxWidth(0.42f))
        }
    }
}

@Composable
private fun SkeletonBar(modifier: Modifier) {
    Box(
        modifier
            .height(11.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(AppColors.SurfaceRaised)
    )
}

/**
 * Stands in for the card grid on the Cards, Collection and deck-contents screens.
 *
 * Scrolling is disabled: there is nothing real to scroll to, and letting a
 * placeholder scroll implies there is.
 */
@Composable
internal fun SkeletonCardGrid(
    modifier: Modifier = Modifier,
    tiles: Int = 18,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = modifier.fillMaxSize().alpha(rememberSkeletonPulse()),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(tiles) { SkeletonCardTile() }
    }
}

/**
 * Stands in for a list of rows: the public decks list and the metagame
 * archetypes.
 *
 * [thumb] is the leading square's size, which differs by row - 52dp on
 * [DeckRow], none on the metagame rows - so it is a parameter rather than a
 * constant.
 */
@Composable
internal fun SkeletonRowList(
    modifier: Modifier = Modifier,
    rows: Int = 8,
    thumb: Dp? = 52.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().alpha(rememberSkeletonPulse()),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(rows) { SkeletonRow(thumb) }
    }
}

@Composable
private fun SkeletonRow(thumb: Dp?) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumb != null) {
            Box(
                Modifier
                    .size(thumb)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.SurfaceRaised)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            SkeletonBar(Modifier.fillMaxWidth(0.55f))
            Spacer(Modifier.height(7.dp))
            SkeletonBar(Modifier.fillMaxWidth(0.85f))
        }
    }
}
