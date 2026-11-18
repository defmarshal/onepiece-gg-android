package gg.opgg.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gg.opgg.android.data.DeckAnalytics
import gg.opgg.android.data.DeckAnalyticsCalc

private val Dim = AppColors.TextDim
private val Rule = AppColors.DividerChart
private val Legal = AppColors.Positive

/**
 * Collapsible deck statistics, pinned above the card grid.
 *
 * Reports shape only. It deliberately offers no opinion about whether the
 * numbers are good, because the real decks do not agree on a target: across 619
 * complete published decks blocker count is multi-modal (peaks at 4 and 8, and
 * 9.5% run none at all), so warning on any particular figure would be noise.
 */
@Composable
internal fun DeckAnalyticsPanel(
    analytics: DeckAnalytics,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onToggle),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.QueryStats,
                contentDescription = null,
                tint = if (expanded) Palette.primary else Dim,
                modifier = Modifier.width(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Deck analytics",
                fontSize = AppType.body,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.OnSurface,
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse analytics" else "Expand analytics",
                tint = Dim,
            )
        }

        AnimatedVisibility(visible = expanded) {
            // Capped and internally scrollable. Without this the panel grew to
            // the full height of its sections and pushed the card grid off the
            // bottom of the screen, cutting off Power distribution entirely.
            Column(
                Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                HairLine()
                Stat("Average cost", analytics.averageCost?.let { String.format("%.2f", it) } ?: "–")
                HairLine()
                CostCurve(analytics)
                HairLine()
                Counters(analytics.counters)
                HairLine()
                Types(analytics.types)
                HairLine()
                TopCrews(analytics)
                if (analytics.colours.isNotEmpty()) {
                    HairLine()
                    Colours(analytics.colours)
                }
                HairLine()
                Stat("Blockers", "${analytics.blockers}")
                HairLine()
                PowerCurve(analytics.powers)
                if (analytics.uncounted > 0) {
                    HairLine()
                    Text(
                        "${analytics.uncounted} card(s) declare no cost and are left out of the curve.",
                        fontSize = AppType.caption,
                        color = Dim,
                    )
                }
            }
        }
    }
}

@Composable
private fun HairLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = AppType.body, color = Dim, modifier = Modifier.weight(1f))
        Text(value, fontSize = AppType.body, color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CostCurve(a: DeckAnalytics) {
    SectionLabel("Cost distribution")
    if (a.costs.all { it == 0 }) {
        Text("No cards with a cost yet.", fontSize = AppType.meta, color = Dim)
        return
    }
    LineChart(
        values = a.costs,
        labels = (1..DeckAnalytics.MAX_COST).map { it.toString() },
        tint = Palette.primary,
    )
}

/**
 * The five most-played crews. A card can belong to several, so these counts
 * deliberately do not sum to the deck size.
 */
@Composable
private fun TopCrews(a: DeckAnalytics) {
    SectionLabel("Top crews")
    val top = a.topCrews(5)
    if (top.isEmpty()) {
        Text("No cards yet.", fontSize = AppType.meta, color = Dim)
        return
    }
    val peak = top.maxOf { it.second }
    top.forEach { (crew, n) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                crew,
                fontSize = AppType.body,
                color = AppColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(112.dp),
            )
            Box(
                Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Rule),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(n.toFloat() / peak)
                        .height(8.dp)
                        .background(Palette.primary),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("$n", fontSize = AppType.body, color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Cost distribution as a line chart.
 *
 * A line rather than bars because the shape is the point: the curve spikes at
 * cost 1, plateaus across 2-6 and falls away above that, and a line makes the
 * cliff visible where separate bars would not connect it. Gaps (a cost with no
 * cards) are drawn as real zeroes, which is true - an absent cost is 0, not
 * unknown.
 */
@Composable
private fun LineChart(
    values: List<Int>,
    labels: List<String>,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val peak = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(96.dp),
        ) {
            val n = values.size
            if (n == 0) return@Canvas
            val w = size.width
            val h = size.height
            // One cell per value, with the point in the middle of its cell. The
            // labels below are laid out as n equal cells with centred text, so
            // label and point share a centre. Spacing the points w/(n-1) instead
            // put the first at x=0 and the last at x=w while the labels sat
            // inside the edges, so every number sat about half a label width to
            // the right of its point.
            val stepX = w / n

            // Horizontal guides, including the baseline and the peak.
            listOf(0f, 0.5f, 1f).forEach { f ->
                val y = h - h * f
                drawLine(
                    color = Rule,
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(w, y),
                    strokeWidth = 1f,
                )
            }

            fun point(i: Int) = androidx.compose.ui.geometry.Offset(
                x = stepX * (i + 0.5f),
                y = h - (h - 6f) * (values[i].toFloat() / peak),
            )

            val line = androidx.compose.ui.graphics.Path().apply {
                moveTo(point(0).x, point(0).y)
                for (i in 1 until n) lineTo(point(i).x, point(i).y)
            }
            val fill = androidx.compose.ui.graphics.Path().apply {
                addPath(line)
                lineTo(point(n - 1).x, h)
                lineTo(point(0).x, h)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.38f), tint.copy(alpha = 0.02f)),
                ),
            )
            drawPath(path = line, color = tint, style = Stroke(width = 2.dp.toPx()))
            for (i in 0 until n) {
                drawCircle(color = tint, radius = 2.5.dp.toPx(), center = point(i))
            }
        }
        // Equal cells, text centred, so each number sits under its own point.
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { l ->
                Text(
                    l,
                    fontSize = AppType.caption,
                    color = Dim,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text("peak $peak", fontSize = AppType.caption, color = Dim, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun PowerCurve(powers: List<Int>) {
    val peak = powers.maxOrNull()?.coerceAtLeast(1) ?: 1
    SectionLabel("Power distribution")
    powers.forEachIndexed { i, n ->
        if (n == 0) return@forEachIndexed
        Bar(
            label = DeckAnalyticsCalc.POWER_LABELS.getOrElse(i) { "${i}k" },
            count = n,
            peak = peak,
            tint = AppColors.AccentAlt,
        )
    }
}

/**
 * No counter / +1000 / +2000 or more on one line.
 *
 * Compact like Card types, and for the same reason: these are the three buckets
 * the counter filter also uses, so this row is the quick read and the filter is
 * the way to act on it.
 */
@Composable
private fun Counters(c: DeckAnalytics.Counters) {
    SectionLabel("Counters")
    if (c.total == 0) {
        Text("No cards yet.", fontSize = AppType.meta, color = Dim)
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "no counter ${c.none}  ·  +1000 ${c.plus1000}  ·  +2000+ ${c.plus2000}",
            fontSize = AppType.meta,
            color = AppColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Character / Event / Stage on one line.
 *
 * Compact because it is close to a constant - 81/17/2 across real decks - so it
 * is a reference line, not something to give three rows of vertical space.
 */
@Composable
private fun Types(types: Map<String, Int>) {
    SectionLabel("Card types")
    if (types.isEmpty()) {
        Text("No cards yet.", fontSize = AppType.meta, color = Dim)
        return
    }
    // Leader sits outside the 50, so it is not part of these counts.
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            types.entries.sortedByDescending { it.value }
                .joinToString("  ·  ") { "${it.key} ${it.value}" },
            fontSize = AppType.meta,
            color = AppColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Colours(colours: Map<String, Int>) {
    SectionLabel("Colours")
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        colours.entries.sortedByDescending { it.value }.forEach { (c, n) ->
            Text("$c $n", fontSize = AppType.body, color = AppColors.OnSurface)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = AppType.meta,
        fontWeight = FontWeight.SemiBold,
        color = Dim,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun Bar(label: String, count: Int, peak: Int, tint: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = AppType.meta, color = Dim, modifier = Modifier.width(26.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Rule)) {
            Box(
                Modifier
                    .fillMaxWidth(count.toFloat() / peak)
                    .height(8.dp)
                    .background(tint),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("$count", fontSize = AppType.meta, color = AppColors.OnSurface, modifier = Modifier.width(24.dp))
    }
}
