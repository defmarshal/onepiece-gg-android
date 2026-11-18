package gg.opgg.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import gg.opgg.android.ui.ZoomMath
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zoom geometry. The pinching itself is Compose's job; what can go wrong is the
 * clamping - a card that slides off into the black, or a "1x" state that is not
 * actually fitted to the screen. The source art is 600x838 and the screen is
 * portrait, so the displayed image is width-limited, with bars above and below.
 */
class ZoomMathTest {

    private val phone = IntSize(1080, 2340)

    private fun assertClose(expected: Float, actual: Float, delta: Float = 0.01f) {
        assertTrue("expected $expected but was $actual", abs(expected - actual) <= delta)
    }

    // ---- scale ---------------------------------------------------------

    @Test
    fun scaleIsClampedToTheUsableRange() {
        assertClose(ZoomMath.MIN_SCALE, ZoomMath.clampScale(0.2f))
        assertClose(ZoomMath.MAX_SCALE, ZoomMath.clampScale(99f))
        assertClose(1.8f, ZoomMath.clampScale(1.8f))
    }

    @Test
    fun doubleTapTogglesBetweenFitAndZoom() {
        assertClose(ZoomMath.DOUBLE_TAP_SCALE, ZoomMath.toggleTarget(1f))
        assertClose(ZoomMath.MIN_SCALE, ZoomMath.toggleTarget(ZoomMath.DOUBLE_TAP_SCALE))
        // Anything meaningfully zoomed goes back to fit, not on to the next step.
        assertClose(ZoomMath.MIN_SCALE, ZoomMath.toggleTarget(1.2f))
        // A hair above 1x (a partially pinched finger lift) still counts as fit.
        assertClose(ZoomMath.DOUBLE_TAP_SCALE, ZoomMath.toggleTarget(1.02f))
    }

    // ---- fitted size ---------------------------------------------------

    @Test
    fun aPortraitPhoneShowsAWidthLimitedCard() {
        val (w, h) = ZoomMath.fittedSize(1080f, 2340f)
        assertClose(1080f, w)
        assertClose(1080f / ZoomMath.ART_ASPECT, h)
        // The art is shorter than the screen, so the black bars are real and the
        // vertical pan allowance must start at zero.
        assertTrue(h < 2340f)
    }

    @Test
    fun aWideWindowShowsAHeightLimitedCard() {
        val (w, h) = ZoomMath.fittedSize(2000f, 1000f)
        assertClose(1000f, h)
        assertClose(1000f * ZoomMath.ART_ASPECT, w)
    }

    @Test
    fun anUnmeasuredViewportFitsNothing() {
        assertEquals(0f to 0f, ZoomMath.fittedSize(0f, 900f))
        assertEquals(0f to 0f, ZoomMath.fittedSize(1080f, 0f))
    }

    // ---- pan -----------------------------------------------------------

    @Test
    fun noPanAtFit() {
        // Panning at 1x would only push the card into empty black; it is always
        // clamped back to the centre on both axes.
        assertEquals(Offset.Zero, ZoomMath.clampPan(Offset(300f, 300f), 1f, phone))
    }

    @Test
    fun panIsClampedToTheImageOverhang() {
        val fittedH = 1080f / ZoomMath.ART_ASPECT
        val maxX = (1080f * 2f - 1080f) / 2f
        val maxY = (fittedH * 2f - 2340f) / 2f
        val clamped = ZoomMath.clampPan(Offset(10_000f, -10_000f), 2f, phone)
        assertClose(maxX, clamped.x)
        assertClose(-maxY, clamped.y)
    }

    @Test
    fun aSmallPanIsLeftAlone() {
        val p = Offset(60f, -40f)
        assertEquals(p, ZoomMath.clampPan(p, 2f, phone))
    }

    @Test
    fun panIsZeroBeforeTheViewportIsMeasured() {
        assertEquals(Offset.Zero, ZoomMath.clampPan(Offset(50f, 50f), 2f, IntSize.Zero))
    }
}
