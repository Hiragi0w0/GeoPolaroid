package com.hiragi0w0.geopolaroid

import android.content.Context
import com.hiragi0w0.geopolaroid.capture.CaptureCoordinator
import com.hiragi0w0.geopolaroid.data.AppDatabase
import com.hiragi0w0.geopolaroid.data.RoomPhotoRepository
import com.hiragi0w0.geopolaroid.domain.PhotoRepository
import com.hiragi0w0.geopolaroid.domain.SystemTimeSource
import com.hiragi0w0.geopolaroid.domain.TimeSource
import com.hiragi0w0.geopolaroid.location.FusedLocationProvider
import com.hiragi0w0.geopolaroid.location.GeocoderPlaceNameResolver
import com.hiragi0w0.geopolaroid.location.PlaceNameUpdater
import com.hiragi0w0.geopolaroid.media.MediaStoreGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** アプリ全体で共有する依存関係（手動DI）。 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** 画面のライフサイクルを越えて続ける処理（地名取得）用。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val timeSource: TimeSource = SystemTimeSource
    private val database = AppDatabase.create(appContext)
    val repository: PhotoRepository = RoomPhotoRepository(database.photoDao())

    val captureCoordinator = CaptureCoordinator(
        repository = repository,
        locationProvider = FusedLocationProvider(appContext),
        mediaGateway = MediaStoreGateway(appContext.contentResolver),
        timeSource = timeSource,
    )

    private val placeNameUpdater = PlaceNameUpdater(repository, GeocoderPlaceNameResolver(appContext))

    private val _resolvingPlaceIds = MutableStateFlow<Set<String>>(emptySet())

    /** 地名を取得中の photoId。表示を「取得中」と「取得失敗」で分けるために使う。 */
    val resolvingPlaceIds: StateFlow<Set<String>> = _resolvingPlaceIds.asStateFlow()

    fun resolvePlaceNameInBackground(photoId: String) {
        _resolvingPlaceIds.update { it + photoId }
        appScope.launch {
            try {
                placeNameUpdater.update(photoId)
            } finally {
                _resolvingPlaceIds.update { it - photoId }
            }
        }
    }
}
