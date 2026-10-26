package com.hiragi0w0.geopolaroid.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 記録日の決定と完成判定。
 *
 * 端末ローカル時刻の 04:00 を境界とし、04:00 より前は前日の記録日に属する。
 * 完成フラグは持たず、`recordDate < 現在の記録日` を表示時に判定する。
 */
object RecordDay {
    val BOUNDARY: LocalTime = LocalTime.of(4, 0)

    fun recordDateOf(instant: Instant, zone: ZoneId): LocalDate {
        val local = instant.atZone(zone).toLocalDateTime()
        return if (local.toLocalTime() < BOUNDARY) {
            local.toLocalDate().minusDays(1)
        } else {
            local.toLocalDate()
        }
    }

    fun isCompleted(recordDate: LocalDate, currentRecordDate: LocalDate): Boolean =
        recordDate < currentRecordDate

    /** [instant] より後で最初に来るローカル 04:00（夏時間で存在しない場合は直後の時刻）。 */
    fun nextBoundaryAfter(instant: Instant, zone: ZoneId): Instant {
        val today = instant.atZone(zone).toLocalDate()
        val sameDay = today.atTime(BOUNDARY).atZone(zone).toInstant()
        return if (sameDay.isAfter(instant)) sameDay else today.plusDays(1).atTime(BOUNDARY).atZone(zone).toInstant()
    }
}

/** 現在時刻とタイムゾーンの取得元。タイムゾーンは呼び出しごとに端末設定を読む。 */
interface TimeSource {
    fun now(): Instant
    fun zone(): ZoneId

    fun currentRecordDate(): LocalDate = RecordDay.recordDateOf(now(), zone())
}

object SystemTimeSource : TimeSource {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
