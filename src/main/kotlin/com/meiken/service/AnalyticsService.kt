package com.meiken.service

import com.meiken.model.BetaResponse
import com.meiken.model.CorrelationResponse
import com.meiken.model.SharpeResponse
import com.meiken.model.VolatilityResponse
import kotlinx.datetime.LocalDate

/** Service for volatility, beta, Sharpe ratio, and rolling correlation analytics. */
interface AnalyticsService {
    /** Daily and annualized (252-day) volatility (std dev of returns) for the symbol. */
    suspend fun calculateVolatility(symbol: String, fromDate: LocalDate, toDate: LocalDate): VolatilityResponse
    /** Beta of target vs benchmark (covariance / benchmark variance). */
    suspend fun calculateBeta(target: String, benchmark: String, fromDate: LocalDate, toDate: LocalDate): BetaResponse
    /** Sharpe ratio: (annualized return - riskFreeRate) / annualized volatility. */
    suspend fun calculateSharpe(symbol: String, fromDate: LocalDate, toDate: LocalDate, riskFreeRate: Double): SharpeResponse
    /** Rolling correlation: for each window of size [window] days, correlation of the two return series; date = end of window. */
    suspend fun calculateCorrelation(ticker1: String, ticker2: String, fromDate: LocalDate, toDate: LocalDate, window: Int): CorrelationResponse
}
