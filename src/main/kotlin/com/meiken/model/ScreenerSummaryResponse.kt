package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Aggregated single-symbol metrics for stock screener use.
 * Returns all key analytics in one request, avoiding multiple round-trips.
 * Benchmark-relative metrics (beta, treynor, information ratio, relative strength) are
 * intentionally excluded; use the dedicated endpoints with a benchmark parameter.
 *
 * Momentum uses lookbacks [20, 60] and moving averages windows [20, 50]; only entries
 * computable from the available price history are included.
 */
@Serializable
data class ScreenerSummaryResponse(
    val symbol: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val volatility: VolatilityData,
    val sharpe: Double,
    val sortino: Double,
    /** Null when maxDrawdown is zero (Calmar would be infinite). */
    val calmar: Double?,
    val maxDrawdown: Double,
    /** Most recent Rate-of-Change entry per lookback period, for the lookbacks that have enough data. */
    val momentum: List<RateOfChangeData>,
    /** Most recent SMA value per window, for the windows that have enough data. */
    val movingAverages: List<MovingAverageData>,
    val priceLevels: PriceLevels,
    val zScore: Double,
    val riskFreeRate: Double,
    val metadata: ScreenerSummaryMetadata
)

@Serializable
data class ScreenerSummaryMetadata(
    val dataPoints: Int,
    val source: String = "market_data",
    val dataQuality: String? = null,
    val outlierCount: Int? = null,
    val missingDays: Int? = null,
    val warnings: List<String>? = null
)
