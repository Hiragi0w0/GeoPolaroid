package com.hiragi0w0.geopolaroid.domain

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 画面表示用の日時整形。 */
object DisplayFormat {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPANESE)

    /** 撮影基準時刻を、撮影当時のタイムゾーンで HH:mm にする。 */
    fun time(photo: Photo): String = time(photo.requestedAtUtc, photo.zoneId)

    fun time(epochMillis: Long, zoneId: String): String {
        val zone = try {
            ZoneId.of(zoneId)
        } catch (e: DateTimeException) {
            ZoneId.systemDefault()
        }
        return TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    }

    fun date(date: LocalDate): String = DATE.format(date)
}
