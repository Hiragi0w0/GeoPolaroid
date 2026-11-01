package com.hiragi0w0.geopolaroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 保存画像の略図（地図タイルが使えないとき）の配置。 */
class RouteSketchTest {

    @Test
    fun noPointsGiveNoSketch() {
        assertTrue(RouteSketch.layout(emptyList(), 100f, 100f, 10f).isEmpty())
    }

    @Test
    fun singlePointIsCentered() {
        val points = RouteSketch.layout(listOf(GeoPoint(33.0, 130.0)), 200f, 100f, 10f)
        assertEquals(listOf(SketchPoint(100f, 50f)), points)
    }

    @Test
    fun pointsFitInsidePaddingWithSameScaleAndNorthUp() {
        val points = RouteSketch.layout(
            listOf(GeoPoint(33.0, 130.0), GeoPoint(33.0, 130.2), GeoPoint(33.1, 130.1)),
            width = 300f,
            height = 300f,
            padding = 20f,
        )
        // 東西に広い範囲なので横幅いっぱい、縦は中央に寄る
        assertEquals(20f, points[0].x, 0.01f)
        assertEquals(280f, points[1].x, 0.01f)
        assertEquals(150f, points[2].x, 0.01f)
        assertEquals(points[0].y, points[1].y, 0.01f)
        assertTrue(points[2].y < points[0].y) // 北が上
        assertTrue(points.all { it.y in 20f..280f })
        assertEquals(300f, points[0].y + points[2].y, 0.01f) // 上下の余りは均等
    }

    @Test
    fun antimeridianRangeUsesTheShortSide() {
        val points = RouteSketch.layout(
            listOf(GeoPoint(-17.0, 179.9), GeoPoint(-17.0, -179.9)),
            width = 220f,
            height = 100f,
            padding = 10f,
        )
        assertEquals(10f, points[0].x, 0.01f) // 西端
        assertEquals(210f, points[1].x, 0.01f) // 日付変更線の向こうの東端
    }
}
