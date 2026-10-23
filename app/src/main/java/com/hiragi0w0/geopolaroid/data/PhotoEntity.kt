package com.hiragi0w0.geopolaroid.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.domain.SaveState
import java.time.LocalDate

@Entity(
    tableName = PhotoSql.TABLE,
    indices = [Index(value = ["recordDate", "saveState", "requestedAtUtc", "photoId"])],
)
data class PhotoEntity(
    @PrimaryKey val photoId: String,
    /** YYYY-MM-DD。撮影基準時刻から 04:00 境界で決定。 */
    val recordDate: String,
    val imageUri: String,
    val requestedAtUtc: Long,
    val zoneId: String,
    val latitude: Double,
    val longitude: Double,
    val placeName: String?,
    val saveState: SaveState,
)

data class SavedDateRow(
    val recordDate: String,
    val photoCount: Int,
)

fun PhotoEntity.toDomain(): Photo = Photo(
    photoId = photoId,
    recordDate = LocalDate.parse(recordDate),
    imageUri = imageUri,
    requestedAtUtc = requestedAtUtc,
    zoneId = zoneId,
    latitude = latitude,
    longitude = longitude,
    placeName = placeName,
    saveState = saveState,
)

fun Photo.toEntity(): PhotoEntity = PhotoEntity(
    photoId = photoId,
    recordDate = recordDate.toString(),
    imageUri = imageUri,
    requestedAtUtc = requestedAtUtc,
    zoneId = zoneId,
    latitude = latitude,
    longitude = longitude,
    placeName = placeName,
    saveState = saveState,
)
