package com.bay.sudokusolver

/*
 * Small grids (pure Kotlin; mirrors run.process_zoom).
 *
 * At 640x480 a puzzle far from the camera is not even found: its thin lines blur away.
 * Finding in a 1280x960 frame works but costs ~34 ms a frame on the owner's phone, six
 * times the budget. So the app takes the big frame and looks at it one of two ways, both
 * 640x480 and both at the old cost:
 *   WIDE  the whole frame shrunk by 2: what the app always analysed
 *   ZOOM  the middle of the frame at full resolution: a 2x zoom
 * Measured in Python (13 photos shrunk into a 1280x960 frame, solved of 12-13), grid at
 * 12 / 9 / 7 / 5.5 / 4.5 / 3.5 / 2.8% of the frame: wide only 7 6 6 6 4 2 0, with the
 * zoom 9 10 10 10 9 9 6. Reading from the big frame after finding in the small one
 * ("hybrid") gave 7 6 6 6 4 3 0: finding is the limit, not reading.
 *
 * Corners are kept in WIDE pixels everywhere outside the frame analyzer, so the overlay,
 * the smoothing and the size advice don't know the zoom exists.
 *
 * Screen moire: at full resolution (ZOOM) a photographed screen's pixel grid gives empty
 * cells enough contrast to vote at random in the dark-mode vote; shrinking the frame
 * (WIDE) averages it away. So in ZOOM the vote is taken on a 5x5-Gaussian copy of the
 * flattened grid and the digits are read from the untouched one (run.ZOOM_SCREEN_FIX
 * has the numbers, and why softening the grid itself was dropped).
 */
enum class FrameView { WIDE, ZOOM }

/**
 * Which view the next frame is analysed in. Like GridPolarity it searches one view per
 * frame: a small grid found in WIDE moves to ZOOM (sharper digits), a grid that has
 * grown or reached the zoom's edge moves back, and while nothing is found the views
 * take turns, [MISSES_PER_VIEW] frames each so both polarities get a try in each.
 */
class ZoomPolicy {
    var view = FrameView.WIDE
        private set
    private var misses = 0

    /** A grid was found; [cornersWide] = x0,y0..x3,y3 in WIDE pixels of a [w] x [h] view. */
    fun onFound(cornersWide: FloatArray, w: Int, h: Int) {
        misses = 0
        val share = ScanState.gridShare(cornersWide, w, h)
        view = when (view) {
            FrameView.WIDE -> if (share < ZOOM_BELOW && Zoom.fits(cornersWide, w, h, ZOOM_MARGIN)) FrameView.ZOOM else FrameView.WIDE
            FrameView.ZOOM -> if (share >= ZOOM_OUT || !Zoom.fits(cornersWide, w, h, EDGE_MARGIN)) FrameView.WIDE else FrameView.ZOOM
        }
    }

    fun onMiss() {
        if (++misses % MISSES_PER_VIEW == 0) view = if (view == FrameView.WIDE) FrameView.ZOOM else FrameView.WIDE
    }

    companion object {
        /** A grid below this share of the frame is read in ZOOM (run.ZOOM_BELOW). */
        const val ZOOM_BELOW = 0.10
        /** ... if it lies inside the middle half of the frame with this share of the frame to spare. */
        const val ZOOM_MARGIN = 0.04f
        /** Back to WIDE from this share on (it then fills 64% of the zoom): no flapping around 10%. */
        const val ZOOM_OUT = 0.16
        /** ... or when a corner comes this close to the zoom's edge. */
        const val EDGE_MARGIN = 0.01f
        /** Nothing found: frames per view before the other gets its turn (one per polarity). */
        const val MISSES_PER_VIEW = 2
    }
}

object Zoom {
    /** Frames at least this long are analysed in two views; smaller ones as always. */
    const val BIG_FRAME = 1280

    /** Is the whole grid inside the middle half of the [w] x [h] WIDE view, [margin] (share of the frame) to spare? */
    fun fits(cornersWide: FloatArray, w: Int, h: Int, margin: Float): Boolean {
        for (k in 0 until 4) {
            val x = cornersWide[2 * k]
            val y = cornersWide[2 * k + 1]
            if (x < w / 4f + margin * w || x > 3 * w / 4f - margin * w) return false
            if (y < h / 4f + margin * h || y > 3 * h / 4f - margin * h) return false
        }
        return true
    }

    /** Corners found in [view] (pixels of that [w] x [h] view) -> WIDE pixels. */
    fun toWide(view: FrameView, corners: FloatArray, w: Int, h: Int): FloatArray =
        if (view == FrameView.WIDE) corners
        else FloatArray(8) { corners[it] / 2 + (if (it % 2 == 0) w else h) / 4f }

    /** WIDE pixels -> pixels of [view]. */
    fun toView(view: FrameView, cornersWide: FloatArray, w: Int, h: Int): FloatArray =
        if (view == FrameView.WIDE) cornersWide
        else FloatArray(8) { (cornersWide[it] - (if (it % 2 == 0) w else h) / 4f) * 2 }
}
