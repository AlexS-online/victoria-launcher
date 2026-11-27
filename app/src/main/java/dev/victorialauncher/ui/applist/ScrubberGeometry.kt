// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

/**
 * One source of truth for where the A-Z strip sits, so the letter your finger is on is the
 * letter that lights up. The strip occupies a band — by default the vertical span of the
 * favorites list — rather than the whole screen, so scrubbing never asks for a stretch.
 */
object ScrubberGeometry {

    /** Fallback band (as a fraction of the screen) when the favorites bounds aren't known. */
    const val FALLBACK_TOP_FRACTION = 0.35f
    const val FALLBACK_HEIGHT_FRACTION = 0.5f

    fun indexForY(y: Float, topPx: Float, heightPx: Float, count: Int): Int {
        if (count <= 0 || heightPx <= 0f) return 0
        val idx = ((y - topPx) / (heightPx / count)).toInt()
        return idx.coerceIn(0, count - 1)
    }

    /** True while [y] is within the band, rather than clamped to one of its ends. */
    fun isWithin(y: Float, topPx: Float, heightPx: Float): Boolean =
        heightPx > 0f && y >= topPx && y <= topPx + heightPx

    /**
     * The band the strip should take, given where the favorites are.
     *
     * Normally exactly their span — the strip sits beside what it belongs to. But the favorites
     * are as tall as there are favorites, and one of them is one row: the whole alphabet then
     * has a row's height to divide between its letters, which is a strip with nothing in it
     * anyone could hit or read. Below a minimum it grows around its own middle instead, so it
     * stays where the favorites are without being their size.
     */
    fun bandForFavorites(
        topPx: Float,
        bottomPx: Float,
        viewportPx: Float,
        minHeightPx: Float,
    ): ScrubBand {
        val height = (bottomPx - topPx).coerceAtLeast(0f)
        if (viewportPx <= 0f) return ScrubBand(topPx, height)
        // A measurement taken mid-move can run past the screen, and the letters past its
        // edge could then never be reached. Kept on screen the way a hand-set band is.
        if (height >= minHeightPx) {
            val fitted = height.coerceAtMost(viewportPx)
            return ScrubBand(topPx.coerceIn(0f, viewportPx - fitted), fitted)
        }
        val wanted = minHeightPx.coerceAtMost(viewportPx)
        val center = (topPx + bottomPx) / 2f
        val top = (center - wanted / 2f).coerceIn(0f, viewportPx - wanted)
        return ScrubBand(top, wanted)
    }

    /** Screen-space center of the letter at [index], for a strip of equal cells. */
    fun letterCenterY(index: Int, topPx: Float, heightPx: Float, count: Int): Float {
        if (count <= 0) return topPx
        val spacing = heightPx / count
        return topPx + index * spacing + spacing / 2f
    }
}

/** The vertical band the strip is laid out in, in screen pixels. */
data class ScrubBand(val topPx: Float, val heightPx: Float) {
    val bottomPx: Float get() = topPx + heightPx

    companion object {
        fun fallbackFor(viewportHeightPx: Int) = ScrubBand(
            topPx = viewportHeightPx * ScrubberGeometry.FALLBACK_TOP_FRACTION,
            heightPx = viewportHeightPx * ScrubberGeometry.FALLBACK_HEIGHT_FRACTION,
        )
    }
}

/**
 * How the strip's height is shared between its letters (or color steps).
 *
 * Equal cells made the strip scroll in jerks of unlike length: a letter holding forty apps and
 * one holding two each got the same stretch of finger travel. Here each cell is as tall as the
 * apps it stands for, so a finger moving at a steady pace moves the list at a steady pace.
 * [bounds] are the cell edges as fractions of the band, from 0 to 1.
 */
class ScrubLayout private constructor(private val bounds: FloatArray) {
    val count: Int get() = bounds.size - 1

    /** The cell [y] falls in, clamped to the band's ends. */
    fun indexForY(y: Float, topPx: Float, heightPx: Float): Int {
        if (count <= 0 || heightPx <= 0f) return 0
        val f = ((y - topPx) / heightPx).coerceIn(0f, 1f)
        var lo = 0
        var hi = count - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (bounds[mid] <= f) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** How far down cell [index] [y] is, 0 at its top edge to 1 at its bottom. */
    fun fractionIn(index: Int, y: Float, topPx: Float, heightPx: Float): Float {
        if (count <= 0 || heightPx <= 0f) return 0f
        val i = index.coerceIn(0, count - 1)
        val span = (bounds[i + 1] - bounds[i]) * heightPx
        if (span <= 0f) return 0f
        return ((y - topPx - bounds[i] * heightPx) / span).coerceIn(0f, 1f)
    }

    fun topY(index: Int, topPx: Float, heightPx: Float): Float = topPx + bounds[index] * heightPx

    fun bottomY(index: Int, topPx: Float, heightPx: Float): Float = topPx + bounds[index + 1] * heightPx

    fun centerY(index: Int, topPx: Float, heightPx: Float): Float =
        (topY(index, topPx, heightPx) + bottomY(index, topPx, heightPx)) / 2f

    companion object {
        /** Equal cells, when nothing is known about what the letters hold. */
        fun uniform(count: Int) = ScrubLayout(FloatArray(count + 1) { if (count == 0) 0f else it / count.toFloat() })

        /** One cell per weight, tall in proportion to it. Falls back to equal cells if unusable. */
        fun of(weights: List<Float>?, count: Int): ScrubLayout {
            if (weights == null || weights.size != count || count == 0) return uniform(count)
            val total = weights.sum()
            if (!(total > 0f)) return uniform(count)
            val b = FloatArray(count + 1)
            var acc = 0f
            for (i in 0 until count) {
                b[i] = acc / total
                acc += weights[i].coerceAtLeast(0f)
            }
            b[count] = 1f
            return ScrubLayout(b)
        }
    }
}
