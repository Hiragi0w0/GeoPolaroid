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
    fun boundsAcrossAntimeridianUseTheShortSide() {
        val model = RouteMapModel.from(
            listOf(photo("a", at = 1, lat = -17.0, lng = 179.9), photo("b", at = 2, lat = -18.0, lng = -179.9)),
        )
        val bounds = model.bounds!!
        assertEquals(179.9, bounds.southWest.longitude, 1e-9) // 西端
        assertEquals(-179.9, bounds.northEast.longitude, 1e-9) // 東端（日付変更線の向こう）
        assertEquals(0.2, bounds.longitudeSpan, 1e-9)
        assertEquals(180.0, kotlin.math.abs(bounds.center.longitude), 1e-9)
        assertEquals(-17.5, bounds.center.latitude, 1e-9)
        assertFalse(bounds.isPointLike)
    }

    @Test
    fun boundsAcrossAntimeridianWithSeveralPoints() {
        val bounds = RouteMapModel.from(
            listOf(
                photo("a", at = 1, lng = 178.0),
                photo("b", at = 2, lng = -179.0),
                photo("c", at = 3, lng = 179.5),
                photo("d", at = 4, lng = -177.0),
            ),
        ).bounds!!
        assertEquals(178.0, bounds.southWest.longitude, 1e-9)
        assertEquals(-177.0, bounds.northEast.longitude, 1e-9)
        assertEquals(5.0, bounds.longitudeSpan, 1e-9)
        assertEquals(-179.5, bounds.center.longitude, 1e-9)
    }

    @Test
    fun ordinaryBoundsAreUnchanged() {
        val bounds = RouteMapModel.from(
            listOf(photo("a", at = 1, lng = 130.0), photo("b", at = 2, lng = 140.0), photo("c", at = 3, lng = -10.0)),
        ).bounds!!
        // 隙間は 140→-10(+360)=210° が最大なので、-10〜140 の通常範囲
        assertEquals(-10.0, bounds.southWest.longitude, 1e-9)
        assertEquals(140.0, bounds.northEast.longitude, 1e-9)
        assertEquals(150.0, bounds.longitudeSpan, 1e-9)
        assertEquals(65.0, bounds.center.longitude, 1e-9)
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

    // T09：重なったピンはカード内で切り替える
    @Test
    fun photosAtSameSpotAreGroupedInCaptureOrder() {
        val model = RouteMapModel.from(
            listOf(
                photo("b", at = 2_000, lat = 33.0, lng = 130.0),
                photo("x", at = 1_500, lat = 33.0, lng = 130.0001),
                photo("a", at = 1_000, lat = 33.0, lng = 130.0),
                photo("c", at = 3_000, lat = 33.0, lng = 130.0),
            ),
        )
        assertEquals(listOf("a", "b", "c"), model.photosAtSameSpot("b").map { it.photoId })
        assertEquals(listOf("x"), model.photosAtSameSpot("x").map { it.photoId })
        assertTrue(model.photosAtSameSpot("missing").isEmpty())
    }

    // ピンと一覧には撮影順の番号を出し、同じ地点のピンは1つにまとめて番号を並べる
    @Test
    fun spotsMergeSameCoordinatesAndKeepCaptureNumbers() {
        val model = RouteMapModel.from(
            listOf(
                photo("b", at = 2_000, lat = 33.0, lng = 130.0),
                photo("x", at = 1_500, lat = 33.0, lng = 130.0001),
                photo("a", at = 1_000, lat = 33.0, lng = 130.0),
                photo("c", at = 3_000, lat = 33.0, lng = 130.0),
            ),
        )
        assertEquals(2, model.spots.size)
        assertEquals(listOf("a", "b", "c"), model.spots[0].photos.map { it.photoId })
        assertEquals("1,3,4", model.spots[0].label)
        assertEquals(GeoPoint(33.0, 130.0001), model.spots[1].point)
        assertEquals("2", model.spots[1].label)
        assertEquals(2, model.numberOf("x"))
        assertEquals(0, model.numberOf("missing"))
    }
}
