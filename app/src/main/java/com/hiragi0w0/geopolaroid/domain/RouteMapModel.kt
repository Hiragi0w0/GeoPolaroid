package com.hiragi0w0.geopolaroid.domain

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * 地図の表示範囲。経度は円環として扱い、日付変更線をまたぐ範囲は
 * southWest.longitude > northEast.longitude で表す（Google Maps の LatLngBounds と同じ表現）。
 */
data class GeoBounds(val southWest: GeoPoint, val northEast: GeoPoint) {
    /** 西端から東へ測った経度幅（0〜360）。 */
    val longitudeSpan: Double
        get() = (northEast.longitude - southWest.longitude).let { if (it < 0) it + FULL_CIRCLE else it }

    val center: GeoPoint
        get() = GeoPoint(
            (southWest.latitude + northEast.latitude) / 2,
            normalizeLongitude(southWest.longitude + longitudeSpan / 2),
        )

    /** 全ピンがほぼ同じ地点にあり、範囲に合わせると過剰にズームしてしまう状態。 */
    val isPointLike: Boolean
        get() = northEast.latitude - southWest.latitude < POINT_LIKE_DEGREES &&
            longitudeSpan < POINT_LIKE_DEGREES

    companion object {
        private const val POINT_LIKE_DEGREES = 0.0005
        private const val FULL_CIRCLE = 360.0

        /**
         * すべての点を含む最小の範囲。経度は円環上で点の間の最も広い隙間を除いた側をとるので、
         * 179.9° と -179.9° の2点は日付変更線をまたぐ約0.2°の範囲になる。
         */
        fun enclosing(points: List<GeoPoint>): GeoBounds? {
            if (points.isEmpty()) return null
            val longitudes = points.map { it.longitude }.distinct().sorted()
            // 既定は日付変更線の側の隙間（最東端 → 最西端+360°）。同じ幅ならまたがない範囲を優先する。
            var westIndex = 0
            var largestGap = longitudes.first() + FULL_CIRCLE - longitudes.last()
            for (i in 1 until longitudes.size) {
                val gap = longitudes[i] - longitudes[i - 1]
                if (gap > largestGap) {
                    largestGap = gap
                    westIndex = i
                }
            }
            val eastIndex = (westIndex - 1 + longitudes.size) % longitudes.size
            return GeoBounds(
                southWest = GeoPoint(points.minOf { it.latitude }, longitudes[westIndex]),
                northEast = GeoPoint(points.maxOf { it.latitude }, longitudes[eastIndex]),
            )
        }

        private fun normalizeLongitude(longitude: Double): Double =
            ((longitude + 180.0) % FULL_CIRCLE + FULL_CIRCLE) % FULL_CIRCLE - 180.0
    }
}

/** 地図上の1地点。同じ座標で撮った写真をまとめ、[numbers] にその撮影順の番号（1始まり）を持つ。 */
data class PinSpot(val point: GeoPoint, val photos: List<Photo>, val numbers: List<Int>) {
    /** ピンに書く番号（「3」「2,5」）。 */
    val label: String get() = numbers.joinToString(",")
}

/**
 * 完成地図の描画内容。ピンは全写真で同じ見た目、線は撮影順の直線（実際の移動経路ではない）。
 */
data class RouteMapModel(
    val pins: List<Photo>,
    val polyline: List<GeoPoint>,
    val bounds: GeoBounds?,
) {
    /** 同じ座標の写真を1つにまとめたピン（最初の写真の撮影順）。 */
    val spots: List<PinSpot> by lazy {
        pins.withIndex()
            .groupBy { GeoPoint(it.value.latitude, it.value.longitude) }
            .map { (point, numbered) ->
                PinSpot(point = point, photos = numbered.map { it.value }, numbers = numbered.map { it.index + 1 })
            }
    }

    /** 撮影順の番号（1始まり）。ピンと一覧で同じ番号を見せる。見つからなければ 0。 */
    fun numberOf(photoId: String): Int = pins.indexOfFirst { it.photoId == photoId } + 1

    /**
     * [photoId] の写真と完全に同じ座標にある写真（その写真自身を含む、撮影順）。
     * 重なったピンは分離しないので、カードの中で切り替えて全写真に到達できるようにする。
     */
    fun photosAtSameSpot(photoId: String): List<Photo> {
        val target = pins.firstOrNull { it.photoId == photoId } ?: return emptyList()
        return pins.filter { it.latitude == target.latitude && it.longitude == target.longitude }
    }

    companion object {
        fun from(photos: List<Photo>): RouteMapModel {
            val ordered = photos.filter { it.saveState == SaveState.SAVED }.sortedWith(PhotoDisplayOrder)
            val points = ordered.map { GeoPoint(it.latitude, it.longitude) }
            val bounds = GeoBounds.enclosing(points)
            return RouteMapModel(
                pins = ordered,
                polyline = if (points.size >= 2) points else emptyList(),
                bounds = bounds,
            )
        }
    }
}
