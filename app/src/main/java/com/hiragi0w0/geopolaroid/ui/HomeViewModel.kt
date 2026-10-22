package com.hiragi0w0.geopolaroid.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiragi0w0.geopolaroid.AppContainer
import com.hiragi0w0.geopolaroid.capture.CaptureCoordinator.CompleteResult
import com.hiragi0w0.geopolaroid.capture.CaptureCoordinator.PrepareResult
import com.hiragi0w0.geopolaroid.domain.MAX_PHOTOS_PER_RECORD_DATE
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.domain.RecordDateSummary
import com.hiragi0w0.geopolaroid.domain.RecordDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class CaptureStatus { IDLE, PREPARING, CAMERA_OPEN }

enum class HomeMessage(val retryable: Boolean = false) {
    LIMIT_REACHED,
    LOCATION_PERMISSION_DENIED(retryable = true),
    LOCATION_UNAVAILABLE(retryable = true),
    STORAGE_UNAVAILABLE,
    CAMERA_NOT_FOUND,
    CAPTURE_CANCELLED,
    CAPTURE_FAILED,
    CAPTURE_UNVERIFIED,
    UNEXPECTED_ERROR,
}

/** OSカメラに渡す保存先。UI が1回だけ消費する。 */
data class CameraRequest(val photoId: String, val imageUri: String)

data class HomeUiState(
    val isLoading: Boolean,
    val recordDate: LocalDate,
    val photos: List<Photo>,
    val activeCount: Int,
    val pastDates: List<RecordDateSummary>,
    val captureStatus: CaptureStatus,
    val resolvingPlaceIds: Set<String>,
    val message: HomeMessage?,
) {
    val savedCount: Int get() = photos.size
    val isLimitReached: Boolean get() = activeCount >= MAX_PHOTOS_PER_RECORD_DATE
    val canCapture: Boolean get() = !isLoading && !isLimitReached && captureStatus == CaptureStatus.IDLE

    companion object {
        fun initial(recordDate: LocalDate, captureStatus: CaptureStatus) = HomeUiState(
            isLoading = true,
            recordDate = recordDate,
            photos = emptyList(),
            activeCount = 0,
            pastDates = emptyList(),
            captureStatus = captureStatus,
            resolvingPlaceIds = emptySet(),
            message = null,
        )
    }
}

private data class DayData(
    val recordDate: LocalDate,
    val photos: List<Photo>,
    val activeCount: Int,
    val pastDates: List<RecordDateSummary>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val container: AppContainer,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val repository = container.repository
    private val coordinator = container.captureCoordinator
    private val timeSource = container.timeSource

    private val currentDate = MutableStateFlow(timeSource.currentRecordDate())

    /** 現在の記録日。04:00 を越えたかの判定に各画面が使う。 */
    val currentRecordDate: StateFlow<LocalDate> = currentDate.asStateFlow()

    private val captureStatus = MutableStateFlow(
        if (activePhotoId != null) CaptureStatus.CAMERA_OPEN else CaptureStatus.IDLE,
    )
    private val message = MutableStateFlow<HomeMessage?>(null)
    private val _cameraRequest = MutableStateFlow<CameraRequest?>(null)
    val cameraRequest: StateFlow<CameraRequest?> = _cameraRequest.asStateFlow()

    /** 撮影準備より先に完了させる、途中終了で残った PENDING の再確認。 */
    private val recoveryJob: Job = viewModelScope.launch {
        try {
            val report = coordinator.recoverPending(activePhotoId)
            report.recovered.forEach { container.resolvePlaceNameInBackground(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 復旧できなかった行は PENDING のまま残り、次回の起動で再確認される。
        }
    }

    private val dayData = currentDate.flatMapLatest { date ->
        combine(
            repository.observeSavedPhotos(date),
            repository.observeActiveCount(date),
            repository.observeSavedDates(),
        ) { photos, activeCount, dates ->
            DayData(
                recordDate = date,
                photos = photos,
                activeCount = activeCount,
                pastDates = dates.filter { RecordDay.isCompleted(it.recordDate, date) },
            )
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        dayData,
        captureStatus,
        message,
        container.resolvingPlaceIds,
    ) { day, status, msg, resolving ->
        HomeUiState(
            isLoading = false,
            recordDate = day.recordDate,
            photos = day.photos,
            activeCount = day.activeCount,
            pastDates = day.pastDates,
            captureStatus = status,
            resolvingPlaceIds = resolving,
            message = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState.initial(currentDate.value, captureStatus.value),
    )

    private var activePhotoId: String?
        get() = savedState[KEY_ACTIVE_PHOTO_ID]
        set(value) {
            savedState[KEY_ACTIVE_PHOTO_ID] = value
        }

    /** 画面復帰時・撮影開始時に記録日（04:00 境界）を再判定する。 */
    fun refreshRecordDate() {
        currentDate.value = timeSource.currentRecordDate()
    }

    fun dismissMessage() {
        message.value = null
    }

    fun onLocationPermissionDenied() {
        message.value = HomeMessage.LOCATION_PERMISSION_DENIED
    }

    /** 位置情報の権限が付与済みであることを UI 側で確認してから呼ぶ。 */
    fun startCapture() {
        if (captureStatus.value != CaptureStatus.IDLE || activePhotoId != null) return
        refreshRecordDate()
        message.value = null
        captureStatus.value = CaptureStatus.PREPARING
        viewModelScope.launch {
            val result = try {
                recoveryJob.join()
                coordinator.prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            when (result) {
                is PrepareResult.Ready -> {
                    activePhotoId = result.photoId
                    captureStatus.value = CaptureStatus.CAMERA_OPEN
                    _cameraRequest.value = CameraRequest(result.photoId, result.imageUri)
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

    fun onCameraLaunchFailed(photoId: String) {
        finishCapture(photoId, cameraReportedSuccess = false, failureMessage = HomeMessage.CAMERA_NOT_FOUND)
    }

    fun onCameraResult(success: Boolean) {
        val photoId = activePhotoId ?: return
        finishCapture(photoId, cameraReportedSuccess = success, failureMessage = null)
    }

    private fun finishCapture(photoId: String, cameraReportedSuccess: Boolean, failureMessage: HomeMessage?) {
        viewModelScope.launch {
            val result = try {
                coordinator.complete(photoId, cameraReportedSuccess)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (activePhotoId == photoId) activePhotoId = null
            captureStatus.value = CaptureStatus.IDLE
            refreshRecordDate()
            message.value = failureMessage ?: when (result) {
                CompleteResult.SAVED -> {
                    container.resolvePlaceNameInBackground(photoId)
                    null
                }
                CompleteResult.CANCELLED -> HomeMessage.CAPTURE_CANCELLED
                CompleteResult.FAILED -> HomeMessage.CAPTURE_FAILED
                CompleteResult.UNVERIFIED -> HomeMessage.CAPTURE_UNVERIFIED
                CompleteResult.ALREADY_HANDLED -> null
                null -> HomeMessage.UNEXPECTED_ERROR
            }
        }
    }

    /** 記録から除外する（DB行のみ削除し、端末の写真は残す）。 */
    fun exclude(photoId: String) {
        viewModelScope.launch {
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
        const val KEY_ACTIVE_PHOTO_ID = "activePhotoId"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
