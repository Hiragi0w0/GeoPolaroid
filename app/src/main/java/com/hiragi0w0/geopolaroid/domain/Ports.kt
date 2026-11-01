package com.hiragi0w0.geopolaroid.domain

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 写真テーブルへのアクセス。Room 実装とテスト用フェイクで差し替える。 */
interface PhotoRepository {
    fun observeSavedPhotos(recordDate: LocalDate): Flow<List<Photo>>
    fun observeActiveCount(recordDate: LocalDate): Flow<Int>
    fun observeSavedDates(): Flow<List<RecordDateSummary>>

    suspend fun countActive(recordDate: LocalDate): Int

    /** SAVED + PENDING が [limit] 未満のときだけ PENDING 行を追加する（原子的）。 */
    suspend fun insertPendingIfUnderLimit(photo: Photo, limit: Int): Boolean

    suspend fun findById(photoId: String): Photo?
    suspend fun findPending(): List<Photo>

    /** 地名が未取得の SAVED 写真の id（新しい順、すべて）。 */
    suspend fun findSavedIdsWithoutPlaceName(): List<String>

    /** PENDING → SAVED。対象が PENDING でなければ false。 */
    suspend fun markSaved(photoId: String): Boolean

    /** PENDING → SAVED にし、撮影時刻と記録日をシャッター時点のものに置き換える。対象が PENDING でなければ false。 */
    suspend fun markSavedAt(photoId: String, capturedAtUtc: Long, recordDate: LocalDate): Boolean
    suspend fun updatePlaceName(photoId: String, placeName: String)

    /** PENDING 行の取り消し。SAVED 行には作用しない。 */
    suspend fun deletePending(photoId: String): Boolean

    /** 記録からの除外。SAVED 行の DB 削除のみで、画像には触れない。 */
    suspend fun excludeSaved(photoId: String): Boolean
}

sealed interface LocationResult {
    data class Success(val latitude: Double, val longitude: Double) : LocationResult
    data object PermissionDenied : LocationResult
    data object Unavailable : LocationResult
}

/** 現在地を1回だけ取得する。古い lastLocation へのフォールバックはしない。 */
fun interface LocationProvider {
    suspend fun getCurrentLocation(): LocationResult
}

enum class MediaCheck {
    /** 画像として読める。 */
    VALID,

    /** 項目はあるが空、または画像として読めない。 */
    EMPTY_OR_BROKEN,

    /** 項目自体が見つからない。 */
    MISSING,

    /** 権限・I/O などの理由で中身を確認できない。 */
    UNVERIFIABLE,
}

/** MediaStore への写真原本の作成・書き込み・検査・削除。URI は文字列で扱う。 */
interface MediaGateway {
    suspend fun createCaptureUri(displayName: String): String?

    /**
     * アプリ内カメラが一時ファイルに撮った写真を、[createCaptureUri] で作った項目へ書き込む。
     * 成否にかかわらず一時ファイルは消す。書き込めたら true。
     */
    suspend fun writeCapture(uri: String, sourcePath: String): Boolean
    suspend fun inspect(uri: String): MediaCheck
    suspend fun delete(uri: String): Boolean
}

/** アプリ外で削除され、原本が見つからない写真の photoId。 */
suspend fun MediaGateway.findMissing(photos: List<Photo>): Set<String> =
    photos.filter { inspect(it.imageUri) == MediaCheck.MISSING }.mapTo(mutableSetOf()) { it.photoId }

/** 座標から市区町村程度の地名を得る。失敗時は null。 */
fun interface PlaceNameResolver {
    suspend fun resolve(latitude: Double, longitude: Double): String?
}
