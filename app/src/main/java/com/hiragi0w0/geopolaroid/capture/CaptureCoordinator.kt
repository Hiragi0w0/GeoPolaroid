package com.hiragi0w0.geopolaroid.capture

import com.hiragi0w0.geopolaroid.domain.LocationProvider
import com.hiragi0w0.geopolaroid.domain.LocationResult
import com.hiragi0w0.geopolaroid.domain.MAX_PHOTOS_PER_RECORD_DATE
import com.hiragi0w0.geopolaroid.domain.MediaCheck
import com.hiragi0w0.geopolaroid.domain.MediaGateway
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.domain.PhotoRepository
import com.hiragi0w0.geopolaroid.domain.RecordDay
import com.hiragi0w0.geopolaroid.domain.SaveState
import com.hiragi0w0.geopolaroid.domain.TimeSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 撮影・保存シーケンス（設計書 6章）を担う。Android API には依存しない。
 *
 * 1. [prepare]: 件数確認 → 現在地取得 → MediaStore URI 作成 → PENDING 行登録
 * 2. OSカメラ（呼び出し側）
 * 3. [complete]: 画像を検査して SAVED にするか、PENDING と空の項目を片付ける
 * 4. [recoverPending]: 途中終了で残った PENDING を起動時に再確認する
 *
 * 準備処理は同時に1件だけ実行する。カメラ起動中の photoId は画面側（SavedStateHandle）が保持し、
 * 準備中・カメラ起動中の再要求を止める（カメラ多重起動防止）。
 */
class CaptureCoordinator(
    private val repository: PhotoRepository,
    private val locationProvider: LocationProvider,
    private val mediaGateway: MediaGateway,
    private val timeSource: TimeSource,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val preparing = AtomicBoolean(false)

    sealed interface PrepareResult {
        data class Ready(val photoId: String, val imageUri: String) : PrepareResult
        data object Busy : PrepareResult
        data object LimitReached : PrepareResult
        data object LocationPermissionDenied : PrepareResult
        data object LocationUnavailable : PrepareResult
        data object StorageUnavailable : PrepareResult
    }

    enum class CompleteResult {
        SAVED,

        /** ユーザーによるキャンセル（カメラが false を返した）。 */
        CANCELLED,

        /** カメラは成功を返したが画像が空・欠落していた。 */
        FAILED,

        /** 画像を検査できなかったため PENDING のまま残した（次回起動時に再確認）。 */
        UNVERIFIED,

        /** すでに処理済み（復旧処理などで SAVED/削除済み）。 */
        ALREADY_HANDLED,
    }

    data class RecoveryReport(
        val recovered: List<String>,
        val discarded: List<String>,
        val kept: List<String>,
    )

    suspend fun prepare(): PrepareResult {
        if (!tryBeginPrepare()) return PrepareResult.Busy
        try {
            val initialDate = timeSource.currentRecordDate()
            if (repository.countActive(initialDate) >= MAX_PHOTOS_PER_RECORD_DATE) {
                return PrepareResult.LimitReached
            }

            val location = when (val result = locationProvider.getCurrentLocation()) {
                is LocationResult.Success -> result
                LocationResult.PermissionDenied -> return PrepareResult.LocationPermissionDenied
                LocationResult.Unavailable -> return PrepareResult.LocationUnavailable
            }

            // 位置取得後、カメラ起動直前の時刻を基準時刻とする。
            val requestedAt = timeSource.now()
            val zone = timeSource.zone()
            val photoId = idGenerator()
            // URI 作成から PENDING 登録までは中断させない（行のない空項目を残さないため）。
            return withContext(NonCancellable) { register(photoId, requestedAt, zone, location) }
        } finally {
            endPrepare()
        }
    }

    private suspend fun register(
        photoId: String,
        requestedAt: Instant,
        zone: ZoneId,
        location: LocationResult.Success,
    ): PrepareResult {
        val imageUri = mediaGateway.createCaptureUri(displayNameFor(requestedAt, zone, photoId))
            ?: return PrepareResult.StorageUnavailable
        val photo = Photo(
            photoId = photoId,
            recordDate = RecordDay.recordDateOf(requestedAt, zone),
            imageUri = imageUri,
            requestedAtUtc = requestedAt.toEpochMilli(),
            zoneId = zone.id,
            latitude = location.latitude,
            longitude = location.longitude,
            placeName = null,
            saveState = SaveState.PENDING,
        )
        val inserted = try {
            repository.insertPendingIfUnderLimit(photo, MAX_PHOTOS_PER_RECORD_DATE)
        } catch (e: Exception) {
            mediaGateway.delete(imageUri)
            throw e
        }
        if (!inserted) {
            // 位置取得中に記録日が変わる等で上限に達した。作った空項目は片付ける。
            mediaGateway.delete(imageUri)
            return PrepareResult.LimitReached
        }
        return PrepareResult.Ready(photoId, imageUri)
    }

    suspend fun complete(photoId: String, cameraReportedSuccess: Boolean): CompleteResult =
        // 片付けの途中で中断されると、参照されない空項目が残るため中断させない。
        withContext(NonCancellable) { completeInternal(photoId, cameraReportedSuccess) }

    private suspend fun completeInternal(photoId: String, cameraReportedSuccess: Boolean): CompleteResult {
        val photo = repository.findById(photoId)
        if (photo == null || photo.saveState != SaveState.PENDING) {
            return CompleteResult.ALREADY_HANDLED
        }
        val check = mediaGateway.inspect(photo.imageUri)
        if (cameraReportedSuccess) {
            return when (check) {
                MediaCheck.VALID -> {
                    if (repository.markSaved(photoId)) CompleteResult.SAVED
                    else CompleteResult.ALREADY_HANDLED
                }
                MediaCheck.EMPTY_OR_BROKEN, MediaCheck.MISSING -> {
                    discard(photo, check)
                    CompleteResult.FAILED
                }
                MediaCheck.UNVERIFIABLE -> CompleteResult.UNVERIFIED
            }
        }
        // キャンセル：枠を返す。空の項目だけ消し、中身のある・確認できない原本には触れない。
        if (check == MediaCheck.EMPTY_OR_BROKEN || check == MediaCheck.MISSING) {
            discard(photo, check)
        } else {
            repository.deletePending(photoId)
        }
        return CompleteResult.CANCELLED
    }

    /**
     * 起動時に残っている PENDING を再確認する。
     * 読める画像は SAVED に復旧、空・欠落は片付け、確認できないものは消さずに残す。
     * [activePhotoId] は結果待ちのカメラがあるため対象外にする。
     */
    suspend fun recoverPending(activePhotoId: String? = null): RecoveryReport {
        val recovered = mutableListOf<String>()
        val discarded = mutableListOf<String>()
        val kept = mutableListOf<String>()
        for (photo in repository.findPending()) {
            if (photo.photoId == activePhotoId) {
                kept += photo.photoId
                continue
            }
            when (val check = mediaGateway.inspect(photo.imageUri)) {
                MediaCheck.VALID -> {
                    if (repository.markSaved(photo.photoId)) recovered += photo.photoId
                }
                MediaCheck.EMPTY_OR_BROKEN, MediaCheck.MISSING -> {
                    val removed = withContext(NonCancellable) { discard(photo, check) }
                    if (removed) discarded += photo.photoId else kept += photo.photoId
                }
                MediaCheck.UNVERIFIABLE -> kept += photo.photoId
            }
        }
        return RecoveryReport(recovered, discarded, kept)
    }

    /**
     * 空・欠落した撮影の片付け。原本 → 行の順に消し、原本が消えたこと（元から無い場合を含む）を
     * 確かめてから行を消す。原本を消せなかったときは PENDING 行を残し、次回の起動時復旧で再試行する。
     * @return 行まで片付けられたら true。
     */
    private suspend fun discard(photo: Photo, check: MediaCheck): Boolean {
        val mediaGone = mediaGateway.delete(photo.imageUri) ||
            check == MediaCheck.MISSING ||
            mediaGateway.inspect(photo.imageUri) == MediaCheck.MISSING
        if (mediaGone) repository.deletePending(photo.photoId)
        return mediaGone
    }

    private fun tryBeginPrepare(): Boolean = preparing.compareAndSet(false, true)

    private fun endPrepare() = preparing.set(false)

    companion object {
        private val FILE_TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

        fun displayNameFor(requestedAt: Instant, zone: ZoneId, photoId: String): String =
            "GeoPolaroid_${FILE_TIME_FORMAT.format(requestedAt.atZone(zone))}_${photoId.take(8)}.jpg"
    }
}
