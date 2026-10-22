package com.hiragi0w0.geopolaroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiragi0w0.geopolaroid.domain.MediaGateway
import com.hiragi0w0.geopolaroid.domain.PhotoRepository
import com.hiragi0w0.geopolaroid.domain.RouteMapModel
import com.hiragi0w0.geopolaroid.domain.findMissing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MapUiState(
    val isLoading: Boolean,
    val recordDate: LocalDate,
    val model: RouteMapModel,
    val missingPhotoIds: Set<String>,
)

/** 完成地図（終了した記録日）の表示状態。Room の写真群から都度描画する。 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    val recordDate: LocalDate,
    private val repository: PhotoRepository,
    private val mediaGateway: MediaGateway,
) : ViewModel() {
    private val mediaCheckTick = MutableStateFlow(0)

    val uiState: StateFlow<MapUiState> = combine(
        repository.observeSavedPhotos(recordDate),
        mediaCheckTick,
    ) { photos, _ -> photos }
        .mapLatest { photos ->
            MapUiState(
                isLoading = false,
                recordDate = recordDate,
                model = RouteMapModel.from(photos),
                missingPhotoIds = mediaGateway.findMissing(photos),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MapUiState(true, recordDate, RouteMapModel.from(emptyList()), emptySet()),
        )

    private val _excludeFailed = MutableStateFlow(false)
    val excludeFailed: StateFlow<Boolean> = _excludeFailed.asStateFlow()

    /** 画面復帰時に、アプリ外で削除された原本を再検査する。 */
    fun onResume() {
        mediaCheckTick.update { it + 1 }
    }

    fun exclude(photoId: String) {
        viewModelScope.launch {
            try {
                repository.excludeSaved(photoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _excludeFailed.value = true
            }
        }
    }

    fun dismissExcludeError() {
        _excludeFailed.value = false
    }
}
