package com.hiragi0w0.geopolaroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 仕様5章：記録日は端末ローカル 04:00 境界（T07）。 */
class RecordDayTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun recordDateAt(local: String, zone: ZoneId = tokyo): LocalDate =
        RecordDay.recordDateOf(LocalDateTime.parse(local).atZone(zone).toInstant(), zone)

    @Test
    fun before0400BelongsToPreviousDay() {
        assertEquals(LocalDate.parse("2026-10-21"), recordDateAt("2026-10-22T03:59:59.999"))
        assertEquals(LocalDate.parse("2026-10-21"), recordDateAt("2026-10-22T00:00"))
    }

    @Test
    fun at0400BelongsToSameDay() {
        assertEquals(LocalDate.parse("2026-10-22"), recordDateAt("2026-10-22T04:00"))
        assertEquals(LocalDate.parse("2026-10-22"), recordDateAt("2026-10-22T23:59:59"))
    }

    @Test
    fun monthAndYearBoundaries() {
        assertEquals(LocalDate.parse("2026-12-31"), recordDateAt("2027-01-01T03:30"))
        assertEquals(LocalDate.parse("2028-02-29"), recordDateAt("2028-03-01T01:00"))
    }

    @Test
    fun usesGivenZoneNotUtc() {
        // 2026-10-21T19:30Z は東京では 10/22 04:30、UTC では 10/21 19:30
        val instant = java.time.Instant.parse("2026-10-21T19:30:00Z")
        assertEquals(LocalDate.parse("2026-10-22"), RecordDay.recordDateOf(instant, tokyo))
        assertEquals(LocalDate.parse("2026-10-21"), RecordDay.recordDateOf(instant, ZoneId.of("UTC")))
    }

    @Test
    fun dstTransitionUsesLocalWallClock() {
        val newYork = ZoneId.of("America/New_York")
        // 2026-03-08 02:00 → 03:00 に進む日。03:30 はまだ前日扱い、04:00 で当日。
        assertEquals(LocalDate.parse("2026-03-07"), recordDateAt("2026-03-08T03:30", newYork))
        assertEquals(LocalDate.parse("2026-03-08"), recordDateAt("2026-03-08T04:00", newYork))
        // 2026-11-01 02:00 → 01:00 に戻る日。2回目の 01:30 も前日扱い。
        val secondOneThirty = LocalDateTime.parse("2026-11-01T01:30").atZone(newYork).withLaterOffsetAtOverlap()
        assertEquals(LocalDate.parse("2026-10-31"), RecordDay.recordDateOf(secondOneThirty.toInstant(), newYork))
    }

    @Test
    fun completedOnlyWhenBeforeCurrentRecordDate() {
        val today = LocalDate.parse("2026-10-22")
        assertTrue(RecordDay.isCompleted(LocalDate.parse("2026-10-21"), today))
        assertFalse(RecordDay.isCompleted(today, today))
        assertFalse(RecordDay.isCompleted(LocalDate.parse("2026-10-23"), today))
    }

    @Test
    fun nextBoundaryIsUpcomingLocal0400() {
        fun next(local: String, zone: ZoneId = tokyo) =
            RecordDay.nextBoundaryAfter(LocalDateTime.parse(local).atZone(zone).toInstant(), zone)
                .atZone(zone).toLocalDateTime()
        assertEquals(LocalDateTime.parse("2026-10-22T04:00"), next("2026-10-22T03:59:59"))
        assertEquals(LocalDateTime.parse("2026-10-23T04:00"), next("2026-10-22T04:00"))
        assertEquals(LocalDateTime.parse("2026-10-23T04:00"), next("2026-10-22T23:00"))
        // 夏時間の切替日でも次のローカル 04:00
        val newYork = ZoneId.of("America/New_York")
        assertEquals(LocalDateTime.parse("2026-03-08T04:00"), next("2026-03-08T01:30", newYork))
    }

    @Test
    fun completionSwitchesAcross0400() {
        // 10/21 の記録は、10/22 03:59 時点では未完成、04:00 で完成になる
        val record = LocalDate.parse("2026-10-21")
        assertFalse(RecordDay.isCompleted(record, recordDateAt("2026-10-22T03:59")))
        assertTrue(RecordDay.isCompleted(record, recordDateAt("2026-10-22T04:00")))
    }
}
