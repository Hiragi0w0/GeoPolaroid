package com.hiragi0w0.geopolaroid.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DisplayFormatTest {
    private val instant = Instant.parse("2026-10-22T01:05:00Z").toEpochMilli()

    @Test
    fun timeUsesZoneStoredAtCapture() {
        assertEquals("10:05", DisplayFormat.time(instant, "Asia/Tokyo"))
        assertEquals("01:05", DisplayFormat.time(instant, "UTC"))
    }

    @Test
    fun invalidZoneFallsBackWithoutCrash() {
        DisplayFormat.time(instant, "Not/AZone")
    }

    @Test
    fun dateIsJapaneseWithWeekday() {
        assertEquals("2026年10月22日(木)", DisplayFormat.date(LocalDate.parse("2026-10-22")))
    }

    @Test
    fun stampUsesCalendarDateAndTimeAtCapture() {
        assertEquals("2026.10.22  10:05", DisplayFormat.stamp(instant, "Asia/Tokyo"))
        // 04:00 前の写真は記録日（前日）ではなく撮影した暦日を表示する
        val earlyMorning = Instant.parse("2026-10-22T17:30:00Z").toEpochMilli()
        assertEquals("2026.10.23  02:30", DisplayFormat.stamp(earlyMorning, "Asia/Tokyo"))
    }
}
