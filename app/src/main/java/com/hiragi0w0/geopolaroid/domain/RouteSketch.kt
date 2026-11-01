package com.hiragi0w0.geopolaroid.domain

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/** 描画先の座標（px）。 */
data class SketchPoint(val x: Float, val y: Float)

/**
 * 地図タイルが使えないときに保存画像へ描く、撮影地点と撮影順の線の略図。
 * 地図と同じメルカトル図法で、全地点が [width]×[height] の枠に余白 [padding] を残して収まるように置く。
 * 縦横の縮尺は同じにし、余った向きは中央に寄せる。日付変更線をまたぐ範囲も地図と同じく短い側でつなぐ。
 */
object RouteSketch {
    fun layout(points: List<GeoPoint>, width: Float, height: Float, padding: Float): List<SketchPoint> {
        val bounds = GeoBounds.enclosing(points) ?: return emptyList()
        val west = bounds.southWest.longitude
        val xs = points.map { lonOffset(it.longitude, west) }
        val ys = points.map { mercatorY(it.latitude) }
        val spanX = xs.max() - xs.min()
        val spanY = ys.max() - ys.min()
        val innerW = (width - padding * 2).coerceAtLeast(0f)
        val innerH = (height - padding * 2).coerceAtLeast(0f)
        // 全地点がほぼ同じ場所なら、拡大しすぎず中央に置く。
        val scale = when {
            spanX <= EPSILON && spanY <= EPSILON -> 0.0
            spanX <= EPSILON -> innerH / spanY
            spanY <= EPSILON -> innerW / spanX
            else -> minOf(innerW / spanX, innerH / spanY)
        }
        val centerX = (xs.max() + xs.min()) / 2
        val centerY = (ys.max() + ys.min()) / 2
        return xs.indices.map { i ->
            SketchPoint(
                x = (width / 2 + (xs[i] - centerX) * scale).toFloat(),
                // 緯度が高いほど上（画面座標では小さい y）
                y = (height / 2 - (ys[i] - centerY) * scale).toFloat(),
            )
        }
    }

    /** 範囲の西端から東へ測った経度（0〜360）。 */
    private fun lonOffset(longitude: Double, west: Double): Double {
        val offset = longitude - west
        return if (offset < 0) offset + 360.0 else offset
    }

    private fun mercatorY(latitude: Double): Double {
        val clamped = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        return Math.toDegrees(ln(tan(PI / 4 + Math.toRadians(clamped) / 2)))
    }

    private const val EPSILON = 1e-9
    private const val MAX_LATITUDE = 85.0
}
