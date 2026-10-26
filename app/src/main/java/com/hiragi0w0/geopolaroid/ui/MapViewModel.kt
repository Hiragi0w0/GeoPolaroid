package com.hiragi0w0.geopolaroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiragi0w0.geopolaroid.domain.MediaGateway
import com.hiragi0w0.geopolaroid.domain.PhotoRepository
import com.hiragi0w0.geopolaroid.map.MapController
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** [MapController] を ViewModel の寿命に結び付けるだけの薄い層。 */
class MapViewModel(
    recordDate: LocalDate,
    repository: PhotoRepository,
    mediaGateway: MediaGateway,
    resolvingPlaceIds: StateFlow<Set<String>>,
) : ViewModel() {
    val controller = MapController(viewModelScope, recordDate, repository, mediaGateway, resolvingPlaceIds)
}
