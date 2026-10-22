package com.hiragi0w0.geopolaroid.data

/**
 * photos テーブルに対する SQL。Room の @Query から参照し、JVM テストでも同じ文を実行して検証する。
 * saveState は Room の enum 既定変換（名前の TEXT）で保存される。
 */
object PhotoSql {
    const val TABLE = "photos"

    const val SELECT_SAVED_BY_DATE =
        "SELECT * FROM photos WHERE recordDate = :recordDate AND saveState = 'SAVED' " +
            "ORDER BY requestedAtUtc ASC, photoId ASC"

    /** 12枚上限の判定対象（SAVED + PENDING）。 */
    const val COUNT_ACTIVE =
        "SELECT COUNT(*) FROM photos WHERE recordDate = :recordDate " +
            "AND saveState IN ('PENDING', 'SAVED')"

    const val SELECT_SAVED_DATES =
        "SELECT recordDate, COUNT(*) AS photoCount FROM photos WHERE saveState = 'SAVED' " +
            "GROUP BY recordDate ORDER BY recordDate DESC"

    const val SELECT_BY_ID = "SELECT * FROM photos WHERE photoId = :photoId"

    const val SELECT_PENDING =
        "SELECT * FROM photos WHERE saveState = 'PENDING' ORDER BY requestedAtUtc ASC, photoId ASC"

    const val MARK_SAVED =
        "UPDATE photos SET saveState = 'SAVED' WHERE photoId = :photoId AND saveState = 'PENDING'"

    const val UPDATE_PLACE_NAME = "UPDATE photos SET placeName = :placeName WHERE photoId = :photoId"

    const val DELETE_PENDING = "DELETE FROM photos WHERE photoId = :photoId AND saveState = 'PENDING'"

    const val DELETE_SAVED = "DELETE FROM photos WHERE photoId = :photoId AND saveState = 'SAVED'"
}
