package gg.opgg.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Geometry for the card-image loupe.
 *
 * Pure functions, kept apart from the composable so the clamping can be unit
 * tested. The card art is a fixed-aspect portrait image, and `ContentScale.Fit`
 * means the *displayed* image is not the viewport: on a tall phone the art is
 * width-limited, with black bars above and below. Pan bounds therefore come
 * from the fitted size, not from the viewport, or zooming would let the art
 * slide off into empty space.
 */
internal object ZoomMath {

    const val MIN_SCALE = 1f
    const val MAX_SCALE = 3f

    /**
     * 600 x 838 is the actual webp the CDN serves for every printing. The
     * printed card is 59 x 86, but the scan includes the card border, and the
     * fit is computed against what is really in the file.
     */
    const val ART_ASPECT = 600f / 838f

    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /** Double-tap toggles between fit and [DOUBLE_TAP_SCALE]. */
    fun toggleTarget(current: Float): Float =
        if (current > MIN_SCALE + 0.05f) MIN_SCALE else DOUBLE_TAP_SCALE

    /**
     * Size the art occupies at 1x inside [viewportW] x [viewportH], preserving
     * [aspect]. Returns (0, 0) before the viewport has been measured.
     */
    fun fittedSize(
        viewportW: Float,
        viewportH: Float,
        aspect: Float = ART_ASPECT,
    ): Pair<Float, Float> {
        if (viewportW <= 0f || viewportH <= 0f) return 0f to 0f
        val widthLimited = viewportW / aspect <= viewportH
        return if (widthLimited) {
            viewportW to viewportW / aspect
        } else {
            viewportH * aspect to viewportH
        }
    }

    /**
     * Keeps the image edges from leaving the viewport: no pan at 1x, and at
     * higher scales only as far as the overhang allows on each axis.
     */
    fun clampPan(pan: Offset, scale: Float, viewport: IntSize): Offset {
        if (viewport.width <= 0 || viewport.height <= 0) return Offset.Zero
        val (w, h) = fittedSize(viewport.width.toFloat(), viewport.height.toFloat())
        val maxX = max(0f, (w * scale - viewport.width) / 2f)
        val maxY = max(0f, (h * scale - viewport.height) / 2f)
        return Offset(
            pan.x.coerceIn(-maxX, maxX),
            pan.y.coerceIn(-maxY, maxY),
        )
    }

    const val DOUBLE_TAP_SCALE = 2.5f
}

/**
 * Full-screen, zoomable card art.
 *
 * The website has no zoom at all, and the CDN tops out at 600x838 per
 * printing, so this is for reading effect text, not for print inspection: at
 * 1x the art is already stretched across a 1080px-wide screen, and the scale is
 * capped at 3x rather than allowed to dissolve completely.
 *
 * Gestures follow the usual photo-viewer grammar: pinch to zoom, drag when
 * zoomed (clamped to the image), double-tap to toggle fit/2.5x, and back or the
 * close button to leave. There is deliberately no horizontal pager in here -
 * panning and paging would fight over the same drag.
 */
@Composable
internal fun CardImageZoomOverlay(
    url: String?,
    description: String?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(onBack = onDismiss)

        var viewport by remember { mutableStateOf(IntSize.Zero) }
        var scale by remember { mutableFloatStateOf(ZoomMath.MIN_SCALE) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        val scope = rememberCoroutineScope()

        // Double-tap animates; gestures stay instant. A snap would read as a
        // glitch on a large image, while interpolating a pinch adds lag.
        fun settleTo(target: Float) {
            scope.launch {
                animate(scale, target, animationSpec = tween(durationMillis = 220)) { value, _ ->
                    scale = value
                    pan = ZoomMath.clampPan(pan, value, viewport)
                }
                if (target <= ZoomMath.MIN_SCALE + 0.01f) pan = Offset.Zero
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { viewport = it }
                .pointerInput(Unit) {
                    detectTransformGestures(panZoomLock = true) { _, panChange, zoomChange, _ ->
                        val next = ZoomMath.clampScale(scale * zoomChange)
                        scale = next
                        pan = ZoomMath.clampPan(pan + panChange, next, viewport)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { settleTo(ZoomMath.toggleTarget(scale)) },
                    )
                },
        ) {
            CardImage(
                url = url,
                description = description,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    },
                // Every pixel the file has; no square target box, which would
                // downscale a portrait card.
                nativeSize = true,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // The dialog draws under the status bar, and a control placed
                    // there is not tappable - the system owns that strip. Inset
                    // it into the safe area, or the X silently swallows taps.
                    .statusBarsPadding()
                    .padding(20.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
            }
            Text(
                text = if (scale > ZoomMath.MIN_SCALE + 0.05f) {
                    "Double-tap to fit"
                } else {
                    "Double-tap to zoom"
                },
                color = Color.White.copy(alpha = 0.65f),
                fontSize = AppType.meta,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
            )
        }
    }
}
