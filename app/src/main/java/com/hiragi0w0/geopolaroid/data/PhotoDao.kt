package com.hiragi0w0.geopolaroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PhotoDao {
    @Query(PhotoSql.SELECT_SAVED_BY_DATE)
    abstract fun observeSaved(recordDate: String): Flow<List<PhotoEntity>>

    @Query(PhotoSql.COUNT_ACTIVE)
    abstract fun observeActiveCount(recordDate: String): Flow<Int>

    @Query(PhotoSql.COUNT_ACTIVE)
    abstract suspend fun countActive(recordDate: String): Int

    @Query(PhotoSql.SELECT_SAVED_DATES)
    abstract fun observeSavedDates(): Flow<List<SavedDateRow>>

    @Query(PhotoSql.SELECT_BY_ID)
    abstract suspend fun findById(photoId: String): PhotoEntity?

    @Query(PhotoSql.SELECT_PENDING)
    abstract suspend fun findPending(): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: PhotoEntity)

    @Transaction
    open suspend fun insertIfUnderLimit(entity: PhotoEntity, limit: Int): Boolean {
        if (countActive(entity.recordDate) >= limit) return false
        insert(entity)
        return true
    }

    @Query(PhotoSql.MARK_SAVED)
    abstract suspend fun markSaved(photoId: String): Int

    @Query(PhotoSql.UPDATE_PLACE_NAME)
    abstract suspend fun updatePlaceName(photoId: String, placeName: String): Int

    @Query(PhotoSql.DELETE_PENDING)
    abstract suspend fun deletePending(photoId: String): Int

    @Query(PhotoSql.DELETE_SAVED)
    abstract suspend fun deleteSaved(photoId: String): Int
}
