package com.hiragi0w0.geopolaroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiragi0w0.geopolaroid.domain.PhotoRepository
import com.hiragi0w0.geopolaroid.domain.RouteMapModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MapUiState(
    val isLoading: Boolean,
    val recordDate: LocalDate,
    val model: RouteMapModel,
)

/** 完成地図（終了した記録日）の表示状態。Room の写真群から都度描画する。 */
class MapViewModel(
    val recordDate: LocalDate,
    private val repository: PhotoRepository,
) : ViewModel() {
    val uiState: StateFlow<MapUiState> = repository.observeSavedPhotos(recordDate)
        .map { MapUiState(isLoading = false, recordDate = recordDate, model = RouteMapModel.from(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MapUiState(true, recordDate, RouteMapModel.from(emptyList())),
        )

    private val _excludeFailed = MutableStateFlow(false)
    val excludeFailed: StateFlow<Boolean> = _excludeFailed.asStateFlow()

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
