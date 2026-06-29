package com.meiken.service

import com.meiken.model.BetaResponse
import com.meiken.model.CalmarResponse
import com.meiken.model.CorrelationResponse
import com.meiken.model.DrawdownResponse
import com.meiken.model.InformationRatioResponse
import com.meiken.model.RelativeStrengthResponse
import com.meiken.model.MomentumResponse
import com.meiken.model.MovingAverageResponse
import com.meiken.model.ScreenerSummaryResponse
import com.meiken.model.SharpeResponse
import com.meiken.model.SortinoResponse
import com.meiken.model.TreynorResponse
import com.meiken.model.VolatilityResponse
import com.meiken.model.ZScoreResponse
import kotlinx.datetime.LocalDate

/** Service for volatility, beta, Sharpe ratio, Sortino ratio, Calmar ratio, momentum, moving averages, price levels, Z-score, drawdown, rolling correlation, Treynor ratio, and information ratio analytics. All metrics use close-of-day prices only. */
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
    /** Price levels: 52-week high/low and distance from current price. */
    suspend fun calculatePriceLevels(symbol: String, fromDate: LocalDate, toDate: LocalDate): com.meiken.model.PriceLevelsResponse
    /** Z-Score: standard deviations from mean price over window (mean reversion indicator). */
    suspend fun calculateZScore(symbol: String, fromDate: LocalDate, toDate: LocalDate, window: Int): ZScoreResponse
    /** Relative strength: measures outperformance/underperformance vs benchmark over period. */
    suspend fun calculateRelativeStrength(target: String, benchmark: String, fromDate: LocalDate, toDate: LocalDate): RelativeStrengthResponse
    /** Rolling correlation from close-of-day returns: for each window, correlation of the two return series; date = end of window. */
    suspend fun calculateCorrelation(ticker1: String, ticker2: String, fromDate: LocalDate, toDate: LocalDate, window: Int): CorrelationResponse
    /** Maximum drawdown: largest peak-to-trough decline as percentage from close prices. */
    suspend fun calculateDrawdown(symbol: String, fromDate: LocalDate, toDate: LocalDate): DrawdownResponse
    /** Treynor ratio: (annualized return - riskFreeRate) / beta. Return per unit of systematic risk. */
    suspend fun calculateTreynor(symbol: String, benchmark: String, fromDate: LocalDate, toDate: LocalDate, riskFreeRate: Double): TreynorResponse
    /** Information ratio: annualized active return / annualized tracking error. Measures consistency of alpha. */
    suspend fun calculateInformationRatio(target: String, benchmark: String, fromDate: LocalDate, toDate: LocalDate): InformationRatioResponse
    /** Screener summary: all key single-symbol metrics in one call (volatility, Sharpe, Sortino, Calmar, drawdown, momentum, moving averages, price levels, Z-score). */
    suspend fun calculateSummary(symbol: String, fromDate: LocalDate, toDate: LocalDate, riskFreeRate: Double): ScreenerSummaryResponse
}
