package com.hiragi0w0.geopolaroid

import android.content.Context
import com.hiragi0w0.geopolaroid.capture.CaptureCoordinator
import com.hiragi0w0.geopolaroid.data.AppDatabase
import com.hiragi0w0.geopolaroid.data.RoomPhotoRepository
import com.hiragi0w0.geopolaroid.domain.MediaGateway
import com.hiragi0w0.geopolaroid.domain.PhotoRepository
import com.hiragi0w0.geopolaroid.domain.SystemTimeSource
import com.hiragi0w0.geopolaroid.domain.TimeSource
import com.hiragi0w0.geopolaroid.location.FusedLocationProvider
import com.hiragi0w0.geopolaroid.location.GeocoderPlaceNameResolver
import com.hiragi0w0.geopolaroid.location.PlaceNameService
import com.hiragi0w0.geopolaroid.location.PlaceNameUpdater
import com.hiragi0w0.geopolaroid.media.MediaStoreGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** アプリ全体で共有する依存関係（手動DI）。 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** 画面のライフサイクルを越えて続ける処理（地名取得）用。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val timeSource: TimeSource = SystemTimeSource
    private val database = AppDatabase.create(appContext)
    val repository: PhotoRepository = RoomPhotoRepository(database.photoDao())
    val mediaGateway: MediaGateway = MediaStoreGateway(appContext.contentResolver)

    val captureCoordinator = CaptureCoordinator(
        repository = repository,
        locationProvider = FusedLocationProvider(appContext),
        mediaGateway = mediaGateway,
        timeSource = timeSource,
    )

    val placeNames = PlaceNameService(
        updater = PlaceNameUpdater(repository, GeocoderPlaceNameResolver(appContext)),
        scope = appScope,
    )
}
