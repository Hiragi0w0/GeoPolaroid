package com.hiragi0w0.geopolaroid.domain

import java.time.LocalDate

/** 1記録日あたりに保存できる写真の上限（SAVED + PENDING の合計）。 */
const val MAX_PHOTOS_PER_RECORD_DATE = 12

enum class SaveState { PENDING, SAVED }

/**
 * 記録対象の写真1件。
 *
 * [requestedAtUtc] はシャッター時刻ではなく、OSカメラを起動する直前の基準時刻（epoch ms）。
 * 座標も同じ時点で取得したもの。
 */
data class Photo(
    val photoId: String,
    val recordDate: LocalDate,
    val imageUri: String,
    val requestedAtUtc: Long,
    val zoneId: String,
    val latitude: Double,
    val longitude: Double,
    val placeName: String?,
    val saveState: SaveState,
)

/** 過去の記録日一覧の1行。[coverImageUri] は表示順で最初の写真（代表写真）。 */
data class RecordDateSummary(
    val recordDate: LocalDate,
    val photoCount: Int,
    val coverImageUri: String,
)

/** 表示順（撮影基準時刻の昇順、同時刻は photoId で安定化）。 */
val PhotoDisplayOrder: Comparator<Photo> =
    compareBy<Photo> { it.requestedAtUtc }.thenBy { it.photoId }
