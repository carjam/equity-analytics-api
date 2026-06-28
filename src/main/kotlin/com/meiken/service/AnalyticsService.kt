package com.meiken.service

import com.meiken.model.BetaResponse
import com.meiken.model.CalmarResponse
import com.meiken.model.CorrelationResponse
import com.meiken.model.DrawdownResponse
import com.meiken.model.MomentumResponse
import com.meiken.model.MovingAverageResponse
import com.meiken.model.SharpeResponse
import com.meiken.model.SortinoResponse
import com.meiken.model.VolatilityResponse
import kotlinx.datetime.LocalDate

/** Service for volatility, beta, Sharpe ratio, Sortino ratio, Calmar ratio, momentum, moving averages, drawdown, and rolling correlation analytics. All metrics use close-of-day prices only. */
interface AnalyticsService {
    /** Daily and annualized volatility (std dev of close-to-close daily returns) for the symbol. */
    suspend fun calculateVolatility(symbol: String, fromDate: LocalDate, toDate: LocalDate): VolatilityResponse
    /** Beta of target vs benchmark from close-of-day returns (covariance / benchmark variance). */
    suspend fun calculateBeta(target: String, benchmark: String, fromDate: LocalDate, toDate: LocalDate): BetaResponse
    /** Sharpe ratio from close-of-day returns: (annualized return - riskFreeRate) / annualized volatility. */
    suspend fun calculateSharpe(symbol: String, fromDate: LocalDate, toDate: LocalDate, riskFreeRate: Double): SharpeResponse
    /** Sortino ratio from close-of-day returns: (annualized return - riskFreeRate) / downside deviation. */
    suspend fun calculateSortino(symbol: String, fromDate: LocalDate, toDate: LocalDate, riskFreeRate: Double): SortinoResponse
    /** Calmar ratio: annualized return / max drawdown. Measures return per unit of drawdown risk. */
    suspend fun calculateCalmar(symbol: String, fromDate: LocalDate, toDate: LocalDate): CalmarResponse
    /** Momentum (Rate of Change): percentage change over multiple lookback periods. */
    suspend fun calculateMomentum(symbol: String, fromDate: LocalDate, toDate: LocalDate, lookbacks: List<Int>): MomentumResponse
    /** Moving averages: simple moving average over multiple window sizes. */
    suspend fun calculateMovingAverages(symbol: String, fromDate: LocalDate, toDate: LocalDate, windows: List<Int>): MovingAverageResponse
    /** Rolling correlation from close-of-day returns: for each window, correlation of the two return series; date = end of window. */
    suspend fun calculateCorrelation(ticker1: String, ticker2: String, fromDate: LocalDate, toDate: LocalDate, window: Int): CorrelationResponse
    /** Maximum drawdown: largest peak-to-trough decline as percentage from close prices. */
    suspend fun calculateDrawdown(symbol: String, fromDate: LocalDate, toDate: LocalDate): DrawdownResponse
}
