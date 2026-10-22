package com.hiragi0w0.geopolaroid.domain

import com.hiragi0w0.geopolaroid.testing.photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 仕様7章 地図描画ロジック（T08, T09, T10）。 */
class RouteMapModelTest {

    @Test
    fun emptyDayHasNoPinsNoLineNoBounds() {
        val model = RouteMapModel.from(emptyList())
        assertTrue(model.pins.isEmpty())
        assertTrue(model.polyline.isEmpty())
        assertNull(model.bounds)
    }

    // T08
    @Test
    fun singlePhotoHasPinOnlyWithoutLine() {
        val model = RouteMapModel.from(listOf(photo("a", lat = 33.0, lng = 130.0)))
        assertEquals(1, model.pins.size)
        assertTrue(model.polyline.isEmpty())
        assertTrue(model.bounds!!.isPointLike)
        assertEquals(GeoPoint(33.0, 130.0), model.bounds!!.center)
    }

    // T08
    @Test
    fun multiplePhotosAreConnectedInCaptureOrder() {
        val photos = listOf(
            photo("c", at = 3_000, lat = 3.0, lng = 30.0),
            photo("a", at = 1_000, lat = 1.0, lng = 10.0),
            photo("b", at = 2_000, lat = 2.0, lng = 20.0),
        )
        val model = RouteMapModel.from(photos)
        assertEquals(listOf("a", "b", "c"), model.pins.map { it.photoId })
        assertEquals(listOf(GeoPoint(1.0, 10.0), GeoPoint(2.0, 20.0), GeoPoint(3.0, 30.0)), model.polyline)
        assertEquals(GeoPoint(1.0, 10.0), model.bounds!!.southWest)
        assertEquals(GeoPoint(3.0, 30.0), model.bounds!!.northEast)
        assertFalse(model.bounds!!.isPointLike)
    }

    @Test
    fun sameTimestampIsOrderedStablyByPhotoId() {
        val model = RouteMapModel.from(listOf(photo("b", at = 5), photo("a", at = 5)))
        assertEquals(listOf("a", "b"), model.pins.map { it.photoId })
    }

    @Test
    fun pendingRowsAreNotDrawn() {
        val model = RouteMapModel.from(
            listOf(photo("a", at = 1), photo("p", at = 2, state = SaveState.PENDING)),
        )
        assertEquals(listOf("a"), model.pins.map { it.photoId })
        assertTrue(model.polyline.isEmpty())
    }

    // T09: 完全に重なる地点も分離せず、全ピンを保持する（到達性は一覧で補う）
    @Test
    fun overlappingPinsAreAllKept() {
        val model = RouteMapModel.from(
            listOf(photo("a", at = 1, lat = 1.0, lng = 1.0), photo("b", at = 2, lat = 1.0, lng = 1.0)),
        )
        assertEquals(2, model.pins.size)
        assertEquals(2, model.polyline.size)
        assertTrue(model.bounds!!.isPointLike)
    }

    // T10: 除外後に再描画するとピンと線から消える
    @Test
    fun excludedPhotoDisappearsOnRedraw() {
        val all = listOf(photo("a", at = 1, lat = 1.0), photo("b", at = 2, lat = 2.0), photo("c", at = 3, lat = 3.0))
        val after = RouteMapModel.from(all.filterNot { it.photoId == "b" })
        assertEquals(listOf("a", "c"), after.pins.map { it.photoId })
        assertEquals(listOf(1.0, 3.0), after.polyline.map { it.latitude })
    }
}
