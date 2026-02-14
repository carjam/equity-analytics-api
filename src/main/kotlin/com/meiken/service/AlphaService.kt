package com.meiken.service

import com.meiken.model.Alpha
import kotlinx.datetime.LocalDate

/** Service for computing alpha (excess return vs benchmark) over a date range. */
interface AlphaService {
    /** Returns annualized excess return of target over benchmark from close-of-day returns (alpha = target annualized - benchmark annualized). */
    suspend fun calculateAlpha(
        target: String,
        benchmark: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): Alpha
}
