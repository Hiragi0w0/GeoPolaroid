package com.hiragi0w0.geopolaroid.domain

data class GeoPoint(val latitude: Double, val longitude: Double)

data class GeoBounds(val southWest: GeoPoint, val northEast: GeoPoint) {
    val center: GeoPoint
        get() = GeoPoint(
            (southWest.latitude + northEast.latitude) / 2,
            (southWest.longitude + northEast.longitude) / 2,
        )

    /** 全ピンがほぼ同じ地点にあり、範囲に合わせると過剰にズームしてしまう状態。 */
    val isPointLike: Boolean
        get() = northEast.latitude - southWest.latitude < POINT_LIKE_DEGREES &&
            northEast.longitude - southWest.longitude < POINT_LIKE_DEGREES

    private companion object {
        const val POINT_LIKE_DEGREES = 0.0005
    }
}

/**
 * 完成地図の描画内容。ピンは全写真で同じ見た目、線は撮影順の直線（実際の移動経路ではない）。
 */
data class RouteMapModel(
    val pins: List<Photo>,
    val polyline: List<GeoPoint>,
    val bounds: GeoBounds?,
) {
    companion object {
        fun from(photos: List<Photo>): RouteMapModel {
            val ordered = photos.filter { it.saveState == SaveState.SAVED }.sortedWith(PhotoDisplayOrder)
            val points = ordered.map { GeoPoint(it.latitude, it.longitude) }
            val bounds = if (points.isEmpty()) {
                null
            } else {
                GeoBounds(
                    southWest = GeoPoint(points.minOf { it.latitude }, points.minOf { it.longitude }),
                    northEast = GeoPoint(points.maxOf { it.latitude }, points.maxOf { it.longitude }),
                )
            }
            return RouteMapModel(
                pins = ordered,
                polyline = if (points.size >= 2) points else emptyList(),
                bounds = bounds,
            )
        }
    }
}
