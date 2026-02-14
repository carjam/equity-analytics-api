package com.meiken.util

import com.meiken.error.InvalidDateRangeException
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Validates date range: from < to, dates not in future, span <= maxDays.
 * @throws InvalidDateRangeException if validation fails
 */
fun validateDateRange(from: LocalDate, to: LocalDate, maxDays: Int = 365) {
    val today = Clock.System.todayIn(TimeZone.UTC)
    if (from > to) {
        throw InvalidDateRangeException("fromDate ($from) must be before or equal to toDate ($to)")
    }
    if (from > today) {
        throw InvalidDateRangeException("fromDate ($from) cannot be in the future")
    }
    if (to > today) {
        throw InvalidDateRangeException("toDate ($to) cannot be in the future")
    }
    val days = (to.toEpochDays() - from.toEpochDays()).toInt() + 1
    if (days > maxDays) {
        throw InvalidDateRangeException("Date range cannot exceed $maxDays days (requested $days days)")
    }
}

/**
 * Returns the first day of the current year (UTC).
 */
fun getCurrentYearStart(): LocalDate {
    val today = Clock.System.todayIn(TimeZone.UTC)
    return LocalDate(today.year, 1, 1)
}
