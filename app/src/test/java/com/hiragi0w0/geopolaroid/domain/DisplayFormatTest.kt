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
}
