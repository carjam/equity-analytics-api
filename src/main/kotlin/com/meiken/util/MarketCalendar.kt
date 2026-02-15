package com.meiken.util

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.slf4j.LoggerFactory

/** Adds [days] to this date using epoch-day arithmetic (avoids kotlinx-datetime version-specific plus/minus APIs). */
private fun LocalDate.plusDays(days: Int): LocalDate = LocalDate.fromEpochDays(this.toEpochDays() + days)

/**
 * US equity market calendar: NYSE/NASDAQ trading days.
 * Weekends and fixed/rule-based US holidays; early closes are not modeled.
 *
 * **Note:** Holiday dates are computed from rules and depend on the year. For production use with
 * up-to-date or multi-year ranges, consider integrating a third-party calendar/holiday source.
 * For expansion beyond US domestic markets, a third-party integration is recommended to source
 * exchange-specific holidays and trading hours per region.
 */
object MarketCalendar {

    private val log = LoggerFactory.getLogger(MarketCalendar::class.java)

    /** Fixed or simple-rule US holidays (used only for building [usHolidays]; some duplicated below for clarity). */
    private fun fixedHolidays(year: Int): Set<LocalDate> = setOf(
        LocalDate(year, 1, 1),   // New Year's Day
        LocalDate(year, 7, 4),   // Independence Day
        LocalDate(year, 9, 1).nextWeekday(), // Labor Day (first Monday of September)
        LocalDate(year, 12, 25)  // Christmas
    )

    /** MLK Day: 3rd Monday of January. */
    private fun mlkDay(year: Int): LocalDate =
        LocalDate(year, 1, 1).nthWeekdayInMonth(3, DayOfWeek.MONDAY)

    /** Presidents Day: 3rd Monday of February. */
    private fun presidentsDay(year: Int): LocalDate =
        LocalDate(year, 2, 1).nthWeekdayInMonth(3, DayOfWeek.MONDAY)

    /** Good Friday: Friday before Easter. */
    private fun goodFriday(year: Int): LocalDate = easterSunday(year).plusDays(-2)

    /** Easter Sunday (Gauss algorithm); used to derive Good Friday. */
    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate(year, month, day)
    }

    /** Memorial Day: last Monday of May. */
    private fun memorialDay(year: Int): LocalDate {
        var d = LocalDate(year, 5, 31)
        while (d.dayOfWeek != DayOfWeek.MONDAY) {
            d = d.plusDays(-1)
        }
        return d
    }

    /** Thanksgiving: 4th Thursday of November. */
    private fun thanksgiving(year: Int): LocalDate =
        LocalDate(year, 11, 1).nthWeekdayInMonth(4, DayOfWeek.THURSDAY)

    /** All US equity holidays for the given year. Observance: Sat → Fri, Sun → Mon. No early closes. */
    fun usHolidays(year: Int): Set<LocalDate> = setOf(
        LocalDate(year, 1, 1),
        mlkDay(year),
        presidentsDay(year),
        goodFriday(year),
        memorialDay(year),
        LocalDate(year, 7, 4),
        LocalDate(year, 9, 1).nextWeekday(),
        thanksgiving(year),
        LocalDate(year, 12, 25)
    ).flatMap { date ->
        // When holiday falls on weekend, use observed day: Saturday → Friday, Sunday → Monday.
        when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> listOf(date.plusDays(-1))
            DayOfWeek.SUNDAY -> listOf(date.plusDays(1))
            else -> listOf(date)
        }
    }.toSet()

    /** True if [date] is Saturday or Sunday. */
    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY ||
            date.dayOfWeek == DayOfWeek.SUNDAY

    /** True if [date] is a US market holiday (observed). */
    fun isHoliday(date: LocalDate): Boolean = date in usHolidays(date.year)

    /** True if the US market is open on [date] (not weekend, not holiday). */
    fun isMarketOpen(date: LocalDate): Boolean =
        !isWeekend(date) && !isHoliday(date)

    /**
     * Number of trading days in [fromDate, toDate] (inclusive).
     * Logs at INFO when the range includes non-trading days (weekends/holidays).
     */
    fun getTradingDays(fromDate: LocalDate, toDate: LocalDate): Int {
        val total = (toDate.toEpochDays() - fromDate.toEpochDays()).toInt() + 1
        var trading = 0
        var current = fromDate
        val toEpoch = toDate.toEpochDays()
        while (current.toEpochDays() <= toEpoch) {
            if (isMarketOpen(current)) trading++
            current = current.plusDays(1)
        }
        if (trading < total) {
            log.info("Date range {} to {} includes non-trading days: {} calendar days, {} trading days", fromDate, toDate, total, trading)
        }
        return trading
    }
}

/** First weekday on or after this date (advance past Saturday/Sunday). */
private fun LocalDate.nextWeekday(): LocalDate {
    var d = this
    while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) {
        d = d.plusDays(1)
    }
    return d
}

/** Nth occurrence of [weekday] in the same month as this date (e.g. 3rd Monday). */
private fun LocalDate.nthWeekdayInMonth(n: Int, weekday: DayOfWeek): LocalDate {
    var d = LocalDate(year, month, 1)
    var count = 0
    while (d.month == month) {
        if (d.dayOfWeek == weekday) {
            count++
            if (count == n) return d
        }
        d = d.plusDays(1)
    }
    return d
}
