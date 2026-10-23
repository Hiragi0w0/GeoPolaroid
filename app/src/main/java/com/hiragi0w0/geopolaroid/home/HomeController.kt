package com.hiragi0w0.geopolaroid.home

import com.hiragi0w0.geopolaroid.capture.CaptureCoordinator
import com.hiragi0w0.geopolaroid.capture.CaptureCoordinator.CompleteResult
import com.hiragi0w0.geopolaroid.capture.CaptureCoordinator.PrepareResult
import com.hiragi0w0.geopolaroid.domain.MediaGateway
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.domain.PhotoRepository
import com.hiragi0w0.geopolaroid.domain.RecordDateSummary
import com.hiragi0w0.geopolaroid.domain.RecordDay
import com.hiragi0w0.geopolaroid.domain.SaveState
import com.hiragi0w0.geopolaroid.domain.TimeSource
import com.hiragi0w0.geopolaroid.domain.findMissing
import com.hiragi0w0.geopolaroid.location.PlaceNameService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDate

/**
 * SC-01 ホームの状態と撮影フロー。Android に依存しないので JVM でテストできる。
 * [scope] は画面（ViewModel）の寿命に合わせる。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeController(
    private val scope: CoroutineScope,
    private val repository: PhotoRepository,
    private val coordinator: CaptureCoordinator,
    private val mediaGateway: MediaGateway,
    private val placeNames: PlaceNameService,
    private val timeSource: TimeSource,
    private val activeStore: ActiveCaptureStore,
) {
    private val currentDate = MutableStateFlow(timeSource.currentRecordDate())

    /** 現在の記録日。04:00 を越えたかの判定に各画面が使う。 */
    val currentRecordDate: StateFlow<LocalDate> = currentDate.asStateFlow()

    private val captureStatus = MutableStateFlow(
        if (activeStore.activePhotoId != null) CaptureStatus.CAMERA_OPEN else CaptureStatus.IDLE,
    )
    private val message = MutableStateFlow<HomeMessage?>(null)
    private val _cameraRequest = MutableStateFlow<CameraRequest?>(null)
    val cameraRequest: StateFlow<CameraRequest?> = _cameraRequest.asStateFlow()

    /**
     * 画面が前面にあるか。背面にいる間に準備が終わったカメラ要求は、戻ったときに
     * 古い時刻・位置のままカメラが自動で開かないよう中止する。
     */
    private var isForeground = true

    /** 原本の有無を再検査するきっかけ（画面復帰時）。 */
    private val mediaCheckTick = MutableStateFlow(0)

    /**
     * 撮影準備・完了処理と、残った PENDING の片付けを1つずつ実行するためのロック。
     * 準備で作ったばかりの（まだ空の）行を、片付けが誤って消さないようにする。
     */
    private val pendingLock = Mutex()

    /**
     * 撮影準備より先に完了させる、途中終了で残った PENDING の再確認。
     * 続けて、取得前にプロセスが終わった・オフラインだった等で地名が未取得の写真を再取得する。
     */
    private val recoveryJob: Job = scope.launch {
        cleanUpLeftoverPending()
        try {
            placeNames.resolveAllInBackground(repository.findSavedIdsWithoutPlaceName())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 次回の起動で再試行する。
        }
    }

    init {
        // 画面を開いたまま 04:00 を跨いでも記録日を切り替える。
        scope.launch {
            while (true) {
                val now = timeSource.now()
                val untilBoundary = Duration.between(now, RecordDay.nextBoundaryAfter(now, timeSource.zone()))
                // 端末時刻の変更に備え、長くても一定間隔で再判定する。
                delay(untilBoundary.toMillis().coerceIn(MIN_TICK_MS, MAX_TICK_MS))
                refreshRecordDate()
            }
        }
    }

    private data class DayData(
        val recordDate: LocalDate,
        val photos: List<Photo>,
        val activeCount: Int,
        val pastDates: List<RecordDateSummary>,
        val missingIds: Set<String>,
    )

    private val dayData = currentDate.flatMapLatest { date ->
        combine(
            repository.observeSavedPhotos(date),
            repository.observeActiveCount(date),
            repository.observeSavedDates(),
            mediaCheckTick,
        ) { photos, activeCount, dates, _ -> Triple(photos, activeCount, dates) }
            .mapLatest { (photos, activeCount, dates) ->
                DayData(
                    recordDate = date,
                    photos = photos,
                    activeCount = activeCount,
                    pastDates = dates.filter { RecordDay.isCompleted(it.recordDate, date) },
                    missingIds = mediaGateway.findMissing(photos),
                )
            }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        dayData,
        captureStatus,
        message,
        placeNames.resolvingIds,
    ) { day, status, msg, resolving ->
        HomeUiState(
            isLoading = false,
            recordDate = day.recordDate,
            photos = day.photos,
            activeCount = day.activeCount,
            pastDates = day.pastDates,
            captureStatus = status,
            resolvingPlaceIds = resolving,
            missingPhotoIds = day.missingIds,
            message = msg,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState.initial(currentDate.value, captureStatus.value),
    )

    /** 記録日（04:00 境界）を再判定する。 */
    fun refreshRecordDate() {
        currentDate.value = timeSource.currentRecordDate()
    }

    /** 画面復帰時：記録日の再判定、アプリ外で削除された原本の再検査、残った PENDING の片付けの再試行。 */
    fun onResume() {
        isForeground = true
        refreshRecordDate()
        mediaCheckTick.update { it + 1 }
        if (captureStatus.value == CaptureStatus.IDLE) scope.launch { cleanUpLeftoverPending() }
    }

    /**
     * 画面が見えなくなったとき。まだカメラを起動していない撮影要求は中止する。
     * 画面回転などの構成変更による停止はアプリを離れたわけではないので対象外（新しい画面が要求を引き継ぐ）。
     */
    fun onStop(isChangingConfigurations: Boolean) {
        if (isChangingConfigurations) return
        isForeground = false
        val pending = _cameraRequest.value ?: return
        if (consumeCameraRequest(pending)) abortCapture(pending.photoId)
    }

    fun dismissMessage() {
        message.value = null
    }

    fun onLocationPermissionDenied() {
        message.value = HomeMessage.LOCATION_PERMISSION_DENIED
    }

    /** 位置情報の権限が付与済みであることを UI 側で確認してから呼ぶ。 */
    fun startCapture() {
        if (captureStatus.value != CaptureStatus.IDLE || activeStore.activePhotoId != null) return
        refreshRecordDate()
        message.value = null
        captureStatus.value = CaptureStatus.PREPARING
        scope.launch {
            val result = try {
                recoveryJob.join()
                pendingLock.withLock {
                    coordinator.prepare().also {
                        if (it is PrepareResult.Ready) activeStore.activePhotoId = it.photoId
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            when (result) {
                is PrepareResult.Ready -> {
                    captureStatus.value = CaptureStatus.CAMERA_OPEN
                    if (isForeground) {
                        _cameraRequest.value = CameraRequest(result.photoId, result.imageUri)
                    } else {
                        abortCapture(result.photoId)
                    }
                    return@launch
                }
                PrepareResult.Busy -> Unit
                PrepareResult.LimitReached -> message.value = HomeMessage.LIMIT_REACHED
                PrepareResult.LocationPermissionDenied -> message.value = HomeMessage.LOCATION_PERMISSION_DENIED
                PrepareResult.LocationUnavailable -> message.value = HomeMessage.LOCATION_UNAVAILABLE
                PrepareResult.StorageUnavailable -> message.value = HomeMessage.STORAGE_UNAVAILABLE
                null -> message.value = HomeMessage.UNEXPECTED_ERROR
            }
            captureStatus.value = CaptureStatus.IDLE
        }
    }

    /** UI がカメラを起動する直前に呼び、同じ要求で2回起動しないようにする。 */
    fun consumeCameraRequest(request: CameraRequest): Boolean {
        if (_cameraRequest.value != request) return false
        _cameraRequest.value = null
        return true
    }

    private fun abortCapture(photoId: String) {
        finishCapture(photoId, cameraReportedSuccess = false, failureMessage = HomeMessage.CAPTURE_ABORTED)
    }

    fun onCameraLaunchFailed(photoId: String) {
        finishCapture(photoId, cameraReportedSuccess = false, failureMessage = HomeMessage.CAMERA_NOT_FOUND)
    }

    fun onCameraResult(success: Boolean) {
        val photoId = activeStore.activePhotoId ?: return
        finishCapture(photoId, cameraReportedSuccess = success, failureMessage = null)
    }

    private fun finishCapture(photoId: String, cameraReportedSuccess: Boolean, failureMessage: HomeMessage?) {
        scope.launch {
            val result = try {
                pendingLock.withLock {
                    try {
                        coordinator.complete(photoId, cameraReportedSuccess)
                    } finally {
                        if (activeStore.activePhotoId == photoId) activeStore.activePhotoId = null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            captureStatus.value = CaptureStatus.IDLE
            if (result != CompleteResult.SAVED && result != CompleteResult.ALREADY_HANDLED) {
                retryLeftoverCleanup(photoId)
            }
            refreshRecordDate()
            if (result == CompleteResult.SAVED) placeNames.resolveInBackground(photoId)
            message.value = failureMessage ?: when (result) {
                CompleteResult.SAVED, CompleteResult.ALREADY_HANDLED -> null
                CompleteResult.CANCELLED -> HomeMessage.CAPTURE_CANCELLED
                CompleteResult.FAILED -> HomeMessage.CAPTURE_FAILED
                CompleteResult.UNVERIFIED -> HomeMessage.CAPTURE_UNVERIFIED
                null -> HomeMessage.UNEXPECTED_ERROR
            }
        }
    }

    /**
     * 空項目を削除できずに PENDING が残った（枠を使ったままの）場合、プロセスの再起動を待たずに
     * 片付けを再試行する。画面復帰時にも [onResume] から再試行される。
     */
    private fun retryLeftoverCleanup(photoId: String) {
        scope.launch {
            try {
                repeat(LEFTOVER_RETRY_COUNT) {
                    if (repository.findById(photoId)?.saveState != SaveState.PENDING) return@launch
                    delay(LEFTOVER_RETRY_INTERVAL_MS)
                    cleanUpLeftoverPending()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 画面復帰時・次回起動時に再試行する。
            }
        }
    }

    /** 残った PENDING を再確認する（結果待ちのカメラの行は対象外）。 */
    private suspend fun cleanUpLeftoverPending() {
        val report = try {
            pendingLock.withLock { coordinator.recoverPending(activeStore.activePhotoId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 片付けられなかった行は PENDING のまま残り、次の機会に再確認される。
            return
        }
        if (report.recovered.isNotEmpty()) placeNames.resolveAllInBackground(report.recovered)
    }

    /** 記録から除外する（DB行のみ削除し、端末の写真は残す）。 */
    fun exclude(photoId: String) {
        scope.launch {
            try {
                repository.excludeSaved(photoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = HomeMessage.UNEXPECTED_ERROR
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MIN_TICK_MS = 1_000L
        const val MAX_TICK_MS = 15 * 60 * 1_000L
        const val LEFTOVER_RETRY_COUNT = 5
        const val LEFTOVER_RETRY_INTERVAL_MS = 10_000L
    }
}
