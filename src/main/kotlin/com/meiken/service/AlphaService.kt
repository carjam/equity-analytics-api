package com.meiken.service

import com.meiken.model.Alpha
import kotlinx.datetime.LocalDate

/** Service for computing Jensen's alpha (OLS single-factor regression intercept) over a date range. */
interface AlphaService {
    /** Returns Jensen's alpha of target vs benchmark from close-of-day returns via OLS regression on excess returns. */
    suspend fun calculateAlpha(
        target: String,
        benchmark: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        riskFreeRate: Double = 0.04
    ): Alpha
}
