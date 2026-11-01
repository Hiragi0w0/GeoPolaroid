package com.hiragi0w0.geopolaroid.data

import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.domain.PhotoRepository
import com.hiragi0w0.geopolaroid.domain.RecordDateSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomPhotoRepository(private val dao: PhotoDao) : PhotoRepository {
    override fun observeSavedPhotos(recordDate: LocalDate): Flow<List<Photo>> =
        dao.observeSaved(recordDate.toString()).map { rows -> rows.map { it.toDomain() } }

    override fun observeActiveCount(recordDate: LocalDate): Flow<Int> =
        dao.observeActiveCount(recordDate.toString())

    override fun observeSavedDates(): Flow<List<RecordDateSummary>> =
        dao.observeSavedDates().map { rows ->
            rows.map { RecordDateSummary(LocalDate.parse(it.recordDate), it.photoCount, it.coverImageUri) }
        }

    override suspend fun countActive(recordDate: LocalDate): Int =
        dao.countActive(recordDate.toString())

    override suspend fun insertPendingIfUnderLimit(photo: Photo, limit: Int): Boolean =
        dao.insertIfUnderLimit(photo.toEntity(), limit)

    override suspend fun findById(photoId: String): Photo? = dao.findById(photoId)?.toDomain()

    override suspend fun findPending(): List<Photo> = dao.findPending().map { it.toDomain() }

    override suspend fun findSavedIdsWithoutPlaceName(): List<String> = dao.findSavedIdsWithoutPlaceName()

    override suspend fun markSaved(photoId: String): Boolean = dao.markSaved(photoId) > 0

    override suspend fun markSavedAt(photoId: String, capturedAtUtc: Long, recordDate: LocalDate): Boolean =
        dao.markSavedAt(photoId, capturedAtUtc, recordDate.toString()) > 0

    override suspend fun updatePlaceName(photoId: String, placeName: String) {
        dao.updatePlaceName(photoId, placeName)
    }

    override suspend fun deletePending(photoId: String): Boolean = dao.deletePending(photoId) > 0

    override suspend fun excludeSaved(photoId: String): Boolean = dao.deleteSaved(photoId) > 0
}
