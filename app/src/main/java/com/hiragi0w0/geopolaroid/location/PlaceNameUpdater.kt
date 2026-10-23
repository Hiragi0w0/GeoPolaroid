package com.hiragi0w0.geopolaroid.location

import com.hiragi0w0.geopolaroid.domain.PhotoRepository
import com.hiragi0w0.geopolaroid.domain.PlaceNameResolver
import com.hiragi0w0.geopolaroid.domain.SaveState
import kotlinx.coroutines.CancellationException

/**
 * 保存済み写真の地名を非同期に補完する。
 * 失敗しても写真・座標はそのまま残し、placeName のみ未取得（null）とする。
 */
class PlaceNameUpdater(
    private val repository: PhotoRepository,
    private val resolver: PlaceNameResolver,
) {
    /** @return 地名を保存できたら true。 */
    suspend fun update(photoId: String): Boolean {
        val photo = repository.findById(photoId) ?: return false
        if (photo.saveState != SaveState.SAVED || photo.placeName != null) return false
        val name = try {
            resolver.resolve(photo.latitude, photo.longitude)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (name.isNullOrBlank()) return false
        repository.updatePlaceName(photoId, name)
        return true
    }
}
