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
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd  HH:mm")

    /** 撮影基準時刻を、撮影当時のタイムゾーンで HH:mm にする。 */
    fun time(photo: Photo): String = time(photo.requestedAtUtc, photo.zoneId)

    fun time(epochMillis: Long, zoneId: String): String =
        TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zoneOf(zoneId)))

    /**
     * ポラロイドの余白に入れる日付と時刻（「2026.10.22  10:05」）。記録日ではなく撮影した暦日なので、
     * 04:00 前の写真は記録日の翌日の日付になる。
     */
    fun stamp(photo: Photo): String = stamp(photo.requestedAtUtc, photo.zoneId)

    fun stamp(epochMillis: Long, zoneId: String): String =
        STAMP.format(Instant.ofEpochMilli(epochMillis).atZone(zoneOf(zoneId)))

    fun date(date: LocalDate): String = DATE.format(date)

    private fun zoneOf(zoneId: String): ZoneId = try {
        ZoneId.of(zoneId)
    } catch (e: DateTimeException) {
        ZoneId.systemDefault()
    }
}
