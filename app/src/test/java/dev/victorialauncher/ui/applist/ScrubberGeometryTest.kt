// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubberGeometryTest {

    @Test
    fun `favorites tall enough keep their own span`() {
        val band = ScrubberGeometry.bandForFavorites(
            topPx = 400f, bottomPx = 1000f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertEquals(400f, band.topPx, 0.01f)
        assertEquals(600f, band.heightPx, 0.01f)
    }

    @Test
    fun `a single favorite still gets a strip worth aiming at`() {
        // One row tall: the whole alphabet had a row's height to share between its letters.
        val band = ScrubberGeometry.bandForFavorites(
            topPx = 900f, bottomPx = 1000f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertEquals(400f, band.heightPx, 0.01f)
        // Grown around its own middle, so it stays where the favorites are.
        assertEquals(950f, band.topPx + band.heightPx / 2f, 0.01f)
    }

    @Test
    fun `growing never pushes the band off either end`() {
        val atTop = ScrubberGeometry.bandForFavorites(
            topPx = 0f, bottomPx = 50f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertEquals(0f, atTop.topPx, 0.01f)

        val atBottom = ScrubberGeometry.bandForFavorites(
            topPx = 1950f, bottomPx = 2000f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertEquals(2000f, atBottom.topPx + atBottom.heightPx, 0.01f)
    }

    @Test
    fun `a minimum taller than the screen is capped by the screen`() {
        val band = ScrubberGeometry.bandForFavorites(
            topPx = 100f, bottomPx = 200f, viewportPx = 600f, minHeightPx = 900f,
        )
        assertEquals(600f, band.heightPx, 0.01f)
        assertEquals(0f, band.topPx, 0.01f)
    }

    @Test
    fun `no favorites measured yet is not grown into nonsense`() {
        val band = ScrubberGeometry.bandForFavorites(
            topPx = 500f, bottomPx = 400f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertTrue(band.heightPx >= 0f)
    }

    @Test
    fun `favorites measured past the screen keep every letter on it`() {
        val band = ScrubberGeometry.bandForFavorites(
            topPx = 800f, bottomPx = 3000f, viewportPx = 2000f, minHeightPx = 400f,
        )
        assertTrue(band.topPx >= 0f)
        assertTrue(band.bottomPx <= 2000f)
    }

    @Test
    fun `a band-only strip stops above the corner button`() {
        val band = ScrubBand(topPx = 1200f, heightPx = 1000f)
        assertEquals(700f, bandOnlyHeightPx(band, limitPx = 1900f), 0.01f)
        assertEquals(1000f, bandOnlyHeightPx(band, limitPx = 2400f), 0.01f)
        assertEquals(0f, bandOnlyHeightPx(band, limitPx = 1000f), 0.01f)
    }

    @Test
    fun cellsAreAsTallAsTheirWeights() {
        val layout = ScrubLayout.of(listOf(1f, 3f), 2)
        // Band 0..400: the first cell is 0..100, the second 100..400.
        assertEquals(0, layout.indexForY(99f, 0f, 400f))
        assertEquals(1, layout.indexForY(101f, 0f, 400f))
        assertEquals(50f, layout.centerY(0, 0f, 400f), 0.001f)
        assertEquals(250f, layout.centerY(1, 0f, 400f), 0.001f)
    }

    @Test
    fun fractionWalksTheCellEvenly() {
        val layout = ScrubLayout.of(listOf(1f, 3f), 2)
        assertEquals(0f, layout.fractionIn(1, 100f, 0f, 400f), 0.001f)
        assertEquals(0.5f, layout.fractionIn(1, 250f, 0f, 400f), 0.001f)
        assertEquals(1f, layout.fractionIn(1, 400f, 0f, 400f), 0.001f)
    }

    @Test
    fun unusableWeightsFallBackToEqualCells() {
        val layout = ScrubLayout.of(listOf(0f, 0f), 2)
        assertEquals(0, layout.indexForY(199f, 0f, 400f))
        assertEquals(1, layout.indexForY(201f, 0f, 400f))
        assertEquals(1, ScrubLayout.of(null, 2).indexForY(300f, 0f, 400f))
    }

    @Test
    fun smallCellsAreHeldAtTheMinimum() {
        // 1 app against 99: proportionally 1% of the band, held at 10% instead.
        val layout = ScrubLayout.of(listOf(1f, 99f), 2, minFraction = 0.1f)
        assertEquals(20f, layout.centerY(0, 0f, 400f), 0.001f)
        assertEquals(40f, layout.bottomY(0, 0f, 400f), 0.001f)
        assertEquals(400f, layout.bottomY(1, 0f, 400f), 0.001f)
    }

    @Test
    fun rowPositionIsContinuousAcrossLetters() {
        // Two letters: rows 0..9 and 10..39, so cells of 100 and 300 px in a 400 px band.
        val sections = listOf(0..9, 10..39)
        val layout = ScrubLayout.of(sections.map { it.count().toFloat() }, 2)
        assertEquals(0f, layout.rowPositionForY(0f, 0f, 400f, sections), 0.001f)
        assertEquals(5f, layout.rowPositionForY(50f, 0f, 400f, sections), 0.001f)
        // Either side of the boundary lands on the same row.
        assertEquals(10f, layout.rowPositionForY(99.999f, 0f, 400f, sections), 0.01f)
        assertEquals(10f, layout.rowPositionForY(100.001f, 0f, 400f, sections), 0.01f)
        // Steady finger, steady list: 10 px is one row in both letters.
        assertEquals(25f, layout.rowPositionForY(250f, 0f, 400f, sections), 0.001f)
        assertEquals(40f, layout.rowPositionForY(400f, 0f, 400f, sections), 0.001f)
    }

    @Test
    fun rowPositionNeedsOneSectionPerCell() {
        val layout = ScrubLayout.uniform(3)
        assertEquals(-1f, layout.rowPositionForY(10f, 0f, 300f, listOf(0..1)), 0.001f)
    }
}

