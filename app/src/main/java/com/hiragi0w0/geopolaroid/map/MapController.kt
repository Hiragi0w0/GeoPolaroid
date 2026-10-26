package com.hiragi0w0.geopolaroid.map

import com.hiragi0w0.geopolaroid.domain.MediaGateway
import com.hiragi0w0.geopolaroid.domain.PhotoRepository
import com.hiragi0w0.geopolaroid.domain.RouteMapModel
import com.hiragi0w0.geopolaroid.domain.findMissing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
    val resolvingPlaceIds: Set<String>,
)

/**
 * SC-03 完成地図（終了した記録日）の表示状態。Room の写真群から都度描画する。
 * Android に依存しないので JVM でテストできる。[scope] は画面（ViewModel）の寿命に合わせる。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapController(
    private val scope: CoroutineScope,
    val recordDate: LocalDate,
    private val repository: PhotoRepository,
    private val mediaGateway: MediaGateway,
    resolvingPlaceIds: StateFlow<Set<String>>,
) {
    /** 原本の有無を再検査するきっかけ（画面復帰時）。 */
    private val mediaCheckTick = MutableStateFlow(0)

    private val photoState = combine(
        repository.observeSavedPhotos(recordDate),
        mediaCheckTick,
    ) { photos, _ -> photos }
        .mapLatest { photos -> RouteMapModel.from(photos) to mediaGateway.findMissing(photos) }

    val uiState: StateFlow<MapUiState> = combine(photoState, resolvingPlaceIds) { (model, missing), resolving ->
        MapUiState(
            isLoading = false,
            recordDate = recordDate,
            model = model,
            missingPhotoIds = missing,
            resolvingPlaceIds = resolving,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = MapUiState(true, recordDate, RouteMapModel.from(emptyList()), emptySet(), emptySet()),
    )

    private val _excludeFailed = MutableStateFlow(false)
    val excludeFailed: StateFlow<Boolean> = _excludeFailed.asStateFlow()

    /** 画面復帰時に、アプリ外で削除された原本を再検査する。 */
    fun onResume() {
        mediaCheckTick.update { it + 1 }
    }

    /** 記録から除外する（DB行のみ削除し、端末の写真は残す）。次の描画でピンと線から消える。 */
    fun exclude(photoId: String) {
        scope.launch {
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
