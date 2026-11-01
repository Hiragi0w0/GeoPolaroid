package com.hiragi0w0.geopolaroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 保存画像に入れる地図の切り抜き範囲。 */
class MapCropTest {

    private val visible = CropRect(0f, 100f, 1000f, 1700f)

    private fun CropRect.contains(p: SketchPoint) = p.x in left..right && p.y in top..bottom

    private fun CropRect.isInside(outer: CropRect) =
        left >= outer.left && top >= outer.top && right <= outer.right && bottom <= outer.bottom

    @Test
    fun keepsAllPinsWithMarginInsideAspectRange() {
        val pins = listOf(SketchPoint(400f, 600f), SketchPoint(600f, 800f))
        val crop = MapCrop.region(pins, visible, margin = 50f, minSide = 100f, minAspect = 0.75f, maxAspect = 1.25f)
        assertTrue(pins.all { crop.contains(it) })
        assertEquals(300f, crop.width, 0.01f)
        assertEquals(300f, crop.height, 0.01f)
        assertEquals(500f, (crop.left + crop.right) / 2, 0.01f) // ピンの中心に合わせる
    }

    @Test
    fun tallSpreadIsWidenedToMaxAspect() {
        val pins = listOf(SketchPoint(500f, 300f), SketchPoint(500f, 1100f))
        val crop = MapCrop.region(pins, visible, margin = 50f, minSide = 100f, minAspect = 0.75f, maxAspect = 1.25f)
        assertEquals(900f, crop.height, 0.01f)
        assertEquals(1.25f, crop.aspect, 0.001f)
        assertTrue(pins.all { crop.contains(it) })
    }

    @Test
    fun singlePinIsNotZoomedBelowMinSide() {
        val crop = MapCrop.region(
            listOf(SketchPoint(500f, 900f)), visible, margin = 50f, minSide = 400f, minAspect = 0.75f, maxAspect = 1.25f,
        )
        assertEquals(400f, crop.width, 0.01f)
        assertEquals(400f, crop.height, 0.01f)
    }

    @Test
    fun cropIsShiftedInsideVisibleNearEdges() {
        val pins = listOf(SketchPoint(20f, 120f))
        val crop = MapCrop.region(pins, visible, margin = 50f, minSide = 400f, minAspect = 0.75f, maxAspect = 1.25f)
        assertTrue(crop.isInside(visible))
        assertTrue(crop.contains(pins[0]))
    }

    @Test
    fun neverExceedsVisibleEvenWhenAspectCannotBeMet() {
        // 縦に広がりすぎて、横を広げても比率に届かない
        val pins = listOf(SketchPoint(500f, 110f), SketchPoint(500f, 1690f))
        val crop = MapCrop.region(pins, visible, margin = 50f, minSide = 100f, minAspect = 0.5f, maxAspect = 1.0f)
        assertTrue(crop.isInside(visible))
        assertEquals(1000f, crop.width, 0.01f)
        assertTrue(pins.all { crop.contains(it) })
    }

    @Test
    fun pinsOutsideVisibleAreIgnoredAndNoPinsUsesCenter() {
        val crop = MapCrop.region(
            listOf(SketchPoint(500f, 50f)), visible, margin = 50f, minSide = 100f, minAspect = 0.75f, maxAspect = 1.25f,
        )
        assertTrue(crop.isInside(visible))
        assertEquals(1000f, crop.width, 0.01f)
        assertEquals(1000f, crop.height, 0.01f)
        assertEquals(900f, (crop.top + crop.bottom) / 2, 0.01f)
    }
}
