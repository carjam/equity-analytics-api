package com.meiken.util

import com.meiken.error.BadRequestException
import com.meiken.error.InvalidDateRangeException
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DateUtilsTest {

    @Test
    fun `validateDateRange throws when from after to`() {
        val from = LocalDate(2024, 2, 10)
        val to = LocalDate(2024, 2, 1)
        val ex = assertFailsWith<InvalidDateRangeException> {
            validateDateRange(from, to, maxDays = 365)
        }
        assert(ex.message!!.contains("before or equal"))
    }

    @Test
    fun `validateDateRange throws when from in future`() {
        val today = getToday()
        val from = LocalDate.fromEpochDays(today.toEpochDays() + 1)
        val to = LocalDate.fromEpochDays(today.toEpochDays() + 10)
        val ex = assertFailsWith<InvalidDateRangeException> {
            validateDateRange(from, to, maxDays = 365)
        }
        assert(ex.message!!.contains("future"))
    }

    @Test
    fun `validateDateRange throws when to in future`() {
        val today = getToday()
        val from = LocalDate.fromEpochDays(today.toEpochDays() - 10)
        val to = LocalDate.fromEpochDays(today.toEpochDays() + 1)
        val ex = assertFailsWith<InvalidDateRangeException> {
            validateDateRange(from, to, maxDays = 365)
        }
        assert(ex.message!!.contains("future"))
    }

    @Test
    fun `validateDateRange throws when range exceeds maxDays`() {
        val from = LocalDate(2024, 1, 1)
        val to = LocalDate(2024, 12, 31)
        val ex = assertFailsWith<InvalidDateRangeException> {
            validateDateRange(from, to, maxDays = 100)
        }
        assert(ex.message!!.contains("100") && ex.message!!.contains("366"))
    }

    @Test
    fun `validateDateRange accepts valid range`() {
        val from = LocalDate(2024, 1, 1)
        val to = LocalDate(2024, 1, 31)
        validateDateRange(from, to, maxDays = 365)
    }

    @Test
    fun `getCurrentYearStart returns Jan 1 of current year`() {
        val start = getCurrentYearStart()
        assertEquals(1, start.monthNumber)
        assertEquals(1, start.dayOfMonth)
    }

    @Test
    fun `getToday returns today in UTC`() {
        val today = getToday()
        assert(today.year >= 2024)
    }

    @Test
    fun `parseDate parses valid YYYY-MM-DD`() {
        val d = parseDate("2024-02-14")
        assertEquals(2024, d.year)
        assertEquals(2, d.monthNumber)
        assertEquals(14, d.dayOfMonth)
    }

    @Test
    fun `parseDate throws BadRequestException for invalid format`() {
        assertFailsWith<BadRequestException> {
            parseDate("14/02/2024")
        }
        assertFailsWith<BadRequestException> {
            parseDate("not-a-date")
        }
    }
}
