package com.hiragi0w0.geopolaroid.home

import com.hiragi0w0.geopolaroid.domain.MAX_PHOTOS_PER_RECORD_DATE
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.domain.RecordDateSummary
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
    CAPTURE_ABORTED,
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
    val missingPhotoIds: Set<String>,
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
            missingPhotoIds = emptySet(),
            message = null,
        )
    }
}

/** カメラ起動中の photoId の保存先。Android では SavedStateHandle でプロセス再生成を越えて保持する。 */
interface ActiveCaptureStore {
    var activePhotoId: String?
}

class InMemoryActiveCaptureStore(override var activePhotoId: String? = null) : ActiveCaptureStore
