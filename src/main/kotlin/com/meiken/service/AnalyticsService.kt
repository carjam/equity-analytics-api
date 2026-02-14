package com.meiken.service

import com.meiken.model.BetaResponse
import com.meiken.model.CorrelationResponse
import com.meiken.model.SharpeResponse
import com.meiken.model.VolatilityResponse
import kotlinx.datetime.LocalDate

/** Service for volatility, beta, Sharpe ratio, and rolling correlation analytics. All metrics use close-of-day prices only. */
interface AnalyticsService {
    /** Daily and annualized volatility (std dev of close-to-close daily returns) for the symbol. */
    suspend fun calculateVolatility(symbol: String, fromDate: LocalDate, toDate: LocalDate): VolatilityResponse
    /** Beta of target vs benchmark from close-of-day returns (covariance / benchmark variance). */
    suspend fun calculateBeta(target: String, benchmark: String, fromDate: LocalDate, toDate: LocalDate): BetaResponse
    /** Sharpe ratio from close-of-day returns: (annualized return - riskFreeRate) / annualized volatility. */
    suspend fun calculateSharpe(symbol: String, fromDate: LocalDate, toDate: LocalDate, riskFreeRate: Double): SharpeResponse
    /** Rolling correlation from close-of-day returns: for each window, correlation of the two return series; date = end of window. */
    suspend fun calculateCorrelation(ticker1: String, ticker2: String, fromDate: LocalDate, toDate: LocalDate, window: Int): CorrelationResponse
}
