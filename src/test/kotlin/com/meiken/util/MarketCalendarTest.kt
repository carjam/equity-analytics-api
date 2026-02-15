package com.meiken.util

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarketCalendarTest {

    @Test
    fun `isWeekend returns true for Saturday and Sunday`() {
        val sat = LocalDate(2024, 2, 10)
        val sun = LocalDate(2024, 2, 11)
        assertTrue(MarketCalendar.isWeekend(sat))
        assertTrue(MarketCalendar.isWeekend(sun))
    }

    @Test
    fun `isWeekend returns false for weekday`() {
        val mon = LocalDate(2024, 2, 12)
        assertFalse(MarketCalendar.isWeekend(mon))
    }

    @Test
    fun `isHoliday returns true for New Year`() {
        val newYear = LocalDate(2024, 1, 1)
        assertTrue(MarketCalendar.isHoliday(newYear))
    }

    @Test
    fun `isHoliday returns true for Christmas`() {
        val christmas = LocalDate(2024, 12, 25)
        assertTrue(MarketCalendar.isHoliday(christmas))
    }

    @Test
    fun `isMarketOpen returns false on weekend`() {
        assertFalse(MarketCalendar.isMarketOpen(LocalDate(2024, 2, 10)))
    }

    @Test
    fun `isMarketOpen returns false on holiday`() {
        assertFalse(MarketCalendar.isMarketOpen(LocalDate(2024, 1, 1)))
    }

    @Test
    fun `isMarketOpen returns true on regular weekday`() {
        assertTrue(MarketCalendar.isMarketOpen(LocalDate(2024, 2, 14)))
    }

    @Test
    fun `getTradingDays returns count within range`() {
        val from = LocalDate(2024, 2, 1)
        val to = LocalDate(2024, 2, 29)
        val trading = MarketCalendar.getTradingDays(from, to)
        assertTrue(trading > 0)
        assertTrue(trading <= 29)
    }

    @Test
    fun `getTradingDays single day weekday`() {
        val d = LocalDate(2024, 2, 14)
        assertEquals(1, MarketCalendar.getTradingDays(d, d))
    }

    @Test
    fun `getTradingDays single day weekend`() {
        val sat = LocalDate(2024, 2, 10)
        assertEquals(0, MarketCalendar.getTradingDays(sat, sat))
    }

    @Test
    fun `usHolidays returns set for year`() {
        val holidays = MarketCalendar.usHolidays(2024)
        assertTrue(holidays.isNotEmpty())
        assertTrue(LocalDate(2024, 1, 1) in holidays)
        assertTrue(LocalDate(2024, 12, 25) in holidays)
    }
}
