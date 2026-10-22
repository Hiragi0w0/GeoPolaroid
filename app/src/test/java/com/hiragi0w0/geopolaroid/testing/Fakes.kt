package com.hiragi0w0.geopolaroid.testing

import com.hiragi0w0.geopolaroid.domain.LocationProvider
import com.hiragi0w0.geopolaroid.domain.LocationResult
import com.hiragi0w0.geopolaroid.domain.MediaCheck
import com.hiragi0w0.geopolaroid.domain.MediaGateway
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.domain.PhotoDisplayOrder
import com.hiragi0w0.geopolaroid.domain.PhotoRepository
import com.hiragi0w0.geopolaroid.domain.PlaceNameResolver
import com.hiragi0w0.geopolaroid.domain.RecordDateSummary
import com.hiragi0w0.geopolaroid.domain.SaveState
import com.hiragi0w0.geopolaroid.domain.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FakeTimeSource(var instant: Instant, var zoneId: ZoneId = ZoneId.of("Asia/Tokyo")) : TimeSource {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId

    fun setLocal(text: String) {
        instant = java.time.LocalDateTime.parse(text).atZone(zoneId).toInstant()
    }
}

class FakePhotoRepository : PhotoRepository {
    private val rows = MutableStateFlow<Map<String, Photo>>(emptyMap())

    /** 起動時復旧が遅い端末を模擬する（仮想時間）。 */
    var findPendingDelayMs = 0L

    val all: List<Photo> get() = rows.value.values.sortedWith(PhotoDisplayOrder)
    fun saved(): List<Photo> = all.filter { it.saveState == SaveState.SAVED }
    fun pending(): List<Photo> = all.filter { it.saveState == SaveState.PENDING }

    fun put(photo: Photo) {
        rows.value = rows.value + (photo.photoId to photo)
    }

    override fun observeSavedPhotos(recordDate: LocalDate): Flow<List<Photo>> =
        rows.map { map ->
            map.values.filter { it.recordDate == recordDate && it.saveState == SaveState.SAVED }
                .sortedWith(PhotoDisplayOrder)
        }

    override fun observeActiveCount(recordDate: LocalDate): Flow<Int> =
        rows.map { map -> map.values.count { it.recordDate == recordDate } }

    override fun observeSavedDates(): Flow<List<RecordDateSummary>> =
        rows.map { map ->
            map.values.filter { it.saveState == SaveState.SAVED }
                .groupBy { it.recordDate }
                .map { (date, list) -> RecordDateSummary(date, list.size) }
                .sortedByDescending { it.recordDate }
        }

    override suspend fun countActive(recordDate: LocalDate): Int =
        rows.value.values.count { it.recordDate == recordDate }

    override suspend fun insertPendingIfUnderLimit(photo: Photo, limit: Int): Boolean {
        if (countActive(photo.recordDate) >= limit) return false
        require(photo.photoId !in rows.value) { "duplicate id" }
        put(photo)
        return true
    }

    override suspend fun findById(photoId: String): Photo? = rows.value[photoId]

    override suspend fun findPending(): List<Photo> {
        if (findPendingDelayMs > 0) delay(findPendingDelayMs)
        return pending()
    }

    override suspend fun findSavedWithoutPlaceName(limit: Int): List<Photo> =
        saved().filter { it.placeName == null }.sortedWith(PhotoDisplayOrder.reversed()).take(limit)

    override suspend fun markSaved(photoId: String): Boolean {
        val photo = rows.value[photoId]?.takeIf { it.saveState == SaveState.PENDING } ?: return false
        put(photo.copy(saveState = SaveState.SAVED))
        return true
    }

    override suspend fun updatePlaceName(photoId: String, placeName: String) {
        rows.value[photoId]?.let { put(it.copy(placeName = placeName)) }
    }

    override suspend fun deletePending(photoId: String): Boolean = deleteIf(photoId, SaveState.PENDING)

    override suspend fun excludeSaved(photoId: String): Boolean = deleteIf(photoId, SaveState.SAVED)

    private fun deleteIf(photoId: String, state: SaveState): Boolean {
        val photo = rows.value[photoId]?.takeIf { it.saveState == state } ?: return false
        rows.value = rows.value - photo.photoId
        return true
    }
}

/** MediaStore の代わり。項目ごとに検査結果を持つ。 */
class FakeMediaGateway : MediaGateway {
    val items = linkedMapOf<String, MediaCheck>()
    var failCreate = false

    /** 削除の途中でプロセスが落ちた状況を模擬する。 */
    var failDelete = false
    val deleted = mutableListOf<String>()
    private var counter = 0

    override suspend fun createCaptureUri(displayName: String): String? {
        if (failCreate) return null
        val uri = "content://media/external_primary/images/media/${++counter}"
        items[uri] = MediaCheck.EMPTY_OR_BROKEN // 作成直後は空
        return uri
    }

    /** OSカメラが書き込んだことにする。 */
    fun write(uri: String, check: MediaCheck = MediaCheck.VALID) {
        items[uri] = check
    }

    override suspend fun inspect(uri: String): MediaCheck = items[uri] ?: MediaCheck.MISSING

    override suspend fun delete(uri: String): Boolean {
        if (failDelete) throw IllegalStateException("process died")
        deleted += uri
        return items.remove(uri) != null
    }
}

class FakeLocationProvider(var result: LocationResult = LocationResult.Success(33.5902, 130.4017)) :
    LocationProvider {
    var calls = 0
    override suspend fun getCurrentLocation(): LocationResult {
        calls++
        return result
    }
}

class FakePlaceNameResolver(var answer: () -> String? = { "福岡市中央区" }) : PlaceNameResolver {
    override suspend fun resolve(latitude: Double, longitude: Double): String? = answer()
}

fun photo(
    id: String,
    date: String = "2026-10-22",
    at: Long = 0L,
    state: SaveState = SaveState.SAVED,
    lat: Double = 33.59,
    lng: Double = 130.40,
    place: String? = null,
    uri: String = "content://media/$id",
) = Photo(
    photoId = id,
    recordDate = LocalDate.parse(date),
    imageUri = uri,
    requestedAtUtc = at,
    zoneId = "Asia/Tokyo",
    latitude = lat,
    longitude = lng,
    placeName = place,
    saveState = state,
)
