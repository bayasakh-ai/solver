package com.bay.sudokusolver

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Small puzzles (Zoom.kt): which of the two views of a big frame is analysed, and the
 * corner arithmetic between them. The views are 480x640 here: the upright phone frame.
 */
class ZoomTest {

    private val w = 480
    private val h = 640

    /** A square grid of [side] px centred at ([cx], [cy]), in WIDE pixels. */
    private fun grid(side: Float, cx: Float = w / 2f, cy: Float = h / 2f): FloatArray {
        val r = side / 2
        return floatArrayOf(cx - r, cy - r, cx + r, cy - r, cx + r, cy + r, cx - r, cy + r)
    }

    @Test fun aSmallGridInTheMiddleIsReadZoomedAndABigOneWide() {
        val z = ZoomPolicy()
        assertEquals(FrameView.WIDE, z.view)
        z.onFound(grid(300f), w, h)                 // 29% of the frame
        assertEquals(FrameView.WIDE, z.view)
        z.onFound(grid(150f), w, h)                 // 7%: zoom in
        assertEquals(FrameView.ZOOM, z.view)
        z.onFound(grid(150f), w, h)
        assertEquals(FrameView.ZOOM, z.view)
    }

    @Test fun aSmallGridOffCentreStaysWide() {
        val z = ZoomPolicy()
        z.onFound(grid(120f, cx = 110f), w, h)      // small, but its left edge is outside the middle half
        assertEquals(FrameView.WIDE, z.view)
        // exactly at the margin: the middle half is 120..360, less 4% of the width
        assertTrue(Zoom.fits(grid(100f, cx = 190f), w, h, ZoomPolicy.ZOOM_MARGIN))
        assertFalse(Zoom.fits(grid(100f, cx = 188f), w, h, ZoomPolicy.ZOOM_MARGIN))
    }

    @Test fun zoomIsLeftOnlyWhenTheGridHasGrownOrReachesTheEdge() {
        val z = ZoomPolicy()
        z.onFound(grid(150f), w, h)
        assertEquals(FrameView.ZOOM, z.view)
        z.onFound(grid(190f), w, h)                 // 11.8%: above the way in, below the way out
        assertEquals(FrameView.ZOOM, z.view)
        z.onFound(grid(225f), w, h)                 // 16.5%: back to the whole frame
        assertEquals(FrameView.WIDE, z.view)
        z.onFound(grid(190f), w, h)                 // and it doesn't flap back at 11.8%
        assertEquals(FrameView.WIDE, z.view)
        // drifting to the zoom's edge also ends it
        z.onFound(grid(120f), w, h)
        assertEquals(FrameView.ZOOM, z.view)
        z.onFound(grid(120f, cx = 181f), w, h)      // left edge at 121: one pixel inside the zoom
        assertEquals(FrameView.WIDE, z.view)
    }

    @Test fun whileNothingIsFoundTheViewsTakeTurnsTwoFramesEach() {
        val z = ZoomPolicy()
        val seen = (1..8).map { z.onMiss(); z.view }
        assertEquals(listOf(FrameView.WIDE, FrameView.ZOOM, FrameView.ZOOM, FrameView.WIDE,
            FrameView.WIDE, FrameView.ZOOM, FrameView.ZOOM, FrameView.WIDE), seen)
        // with the polarity flipping on every miss, all four view x polarity pairs get a frame
        val p = GridPolarity()
        val z2 = ZoomPolicy()
        val pairs = HashSet<Pair<FrameView, Boolean>>()
        repeat(4) { pairs.add(z2.view to p.searchInverted); p.onMiss(); z2.onMiss() }
        assertEquals(4, pairs.size)
        // a find starts the count again
        z.onMiss()
        z.onFound(grid(300f), w, h)
        z.onMiss()
        assertEquals(FrameView.WIDE, z.view)
    }

    @Test fun cornersGoToWidePixelsAndBack() {
        val inZoom = floatArrayOf(0f, 0f, 480f, 0f, 480f, 640f, 0f, 640f) // the whole zoom view
        val wide = Zoom.toWide(FrameView.ZOOM, inZoom, w, h)
        assertArrayEquals(floatArrayOf(120f, 160f, 360f, 160f, 360f, 480f, 120f, 480f), wide, 1e-4f) // the middle half
        assertArrayEquals(inZoom, Zoom.toView(FrameView.ZOOM, wide, w, h), 1e-4f)
        val any = floatArrayOf(13f, 27f, 200f, 31f, 190f, 250f, 9f, 240f)
        assertArrayEquals(any, Zoom.toView(FrameView.ZOOM, Zoom.toWide(FrameView.ZOOM, any, w, h), w, h), 1e-3f)
        // the wide view is the wide view
        assertTrue(Zoom.toWide(FrameView.WIDE, any, w, h) === any)
        assertTrue(Zoom.toView(FrameView.WIDE, any, w, h) === any)
        // a grid's share of the frame is the same whichever view found it
        assertEquals(ScanState.gridShare(wide, w, h), 0.25, 1e-9)
    }

    @Test fun onlyBigFramesAreZoomable() {
        assertTrue(maxOf(1280, 960) >= Zoom.BIG_FRAME)
        assertFalse(maxOf(640, 480) >= Zoom.BIG_FRAME)
        assertEquals(0.10, ZoomPolicy.ZOOM_BELOW, 0.0)
        assertTrue(ZoomPolicy.ZOOM_OUT > ZoomPolicy.ZOOM_BELOW)
    }
}
