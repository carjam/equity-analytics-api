package com.meiken.validator

import com.meiken.model.DailyPrice
import org.slf4j.LoggerFactory
import kotlin.math.absoluteValue
import kotlin.math.sqrt

// --- Validation thresholds ---
/** Minimum allowed close price (below is treated as bad data and filtered out). */
/** Default min price when config not provided. */
private const val DEFAULT_MIN_PRICE = 0.01
/** Default max price when config not provided. */
private const val DEFAULT_MAX_PRICE = 1_000_000.0
/** Default max single-day change fraction when config not provided. */
private const val DEFAULT_MAX_SINGLE_DAY_CHANGE_PCT = 0.50
/** Default outlier sigma when config not provided. */
private const val DEFAULT_OUTLIER_SIGMA = 3.0

/** Result of price validation: cleaned list and issue types for metrics/logging. */
data class ValidationResult(
    val cleanedPrices: List<DailyPrice>,
    val issues: List<DataQualityIssue>
)

/** Type of data quality issue for metrics and logging. */
data class DataQualityIssue(
    val type: String,
    val detail: String? = null
)

/**
 * Validates and cleans market price data. Does not remove outliers from returns (real volatility);
 * [detectOutliers] only reports indices for metadata.
 * Thresholds can be supplied via [DataQualityConfig] or use built-in defaults.
 */
object DataValidator {

    private val log = LoggerFactory.getLogger(DataValidator::class.java)

    /**
     * Validates and cleans price data: unrealistic prices, duplicate dates (keep latest),
     * negative volumes (clamp to 0), massive single-day changes (log only). Sorts by date.
     * Returns cleaned list and issues for metrics.
     * Uses [config] for thresholds when provided; otherwise built-in defaults.
     */
    @JvmOverloads
    fun validatePriceData(
        prices: List<DailyPrice>,
        symbol: String = "",
        config: com.meiken.config.DataQualityConfig? = null
    ): ValidationResult {
        val minPrice = config?.minPrice ?: DEFAULT_MIN_PRICE
        val maxPrice = config?.maxPrice ?: DEFAULT_MAX_PRICE
        val maxSingleDayChangePct = config?.maxSingleDayChangePct ?: DEFAULT_MAX_SINGLE_DAY_CHANGE_PCT
        if (prices.isEmpty()) return ValidationResult(emptyList(), emptyList())
        val issues = mutableListOf<DataQualityIssue>()
        val prefix = if (symbol.isBlank()) "" else "[$symbol] "

        // Deduplicate by date: keep last occurrence per date (assumed latest/freshest).
        val byDate = prices.groupBy { it.date.toEpochDays() }
        val deduped = byDate.map { (_, list) ->
            if (list.size > 1) {
                issues.add(DataQualityIssue("duplicate_date", "kept latest of ${list.size}"))
                log.warn("{}Duplicate dates: kept latest of {}", prefix, list.size)
            }
            list.last()
        }
        val sorted = deduped.sortedBy { it.date.toEpochDays() }
        val cleaned = mutableListOf<DailyPrice>()
        var prevClose: Double? = null

        for (p in sorted) {
            // Filter out unrealistic close prices (likely bad data).
            if (p.close < minPrice || p.close > maxPrice) {
                issues.add(DataQualityIssue("unrealistic_price", "close=$p.close"))
                log.warn("{}Unrealistic price on {}: close={}", prefix, p.date, p.close)
                continue
            }
            // Flag (but keep) very large single-day moves; may indicate data error.
            prevClose?.let { prev ->
                val changePct = if (prev == 0.0) 0.0 else ((p.close - prev) / prev).absoluteValue
                if (changePct > maxSingleDayChangePct) {
                    issues.add(DataQualityIssue("large_change", "change=${changePct.times(100)}%"))
                    log.warn("{}Large single-day change on {}: {}%", prefix, p.date, changePct * 100)
                }
            }
            prevClose = p.close
            // Negative volume is invalid; store as null (effectively clamp to 0 for reporting).
            val volume = when {
                p.volume == null -> null
                p.volume < 0 -> {
                    issues.add(DataQualityIssue("negative_volume", "date=${p.date}"))
                    log.warn("{}Negative volume on {}: clamped to 0", prefix, p.date)
                    null
                }
                else -> p.volume
            }
            cleaned.add(if (volume != p.volume) p.copy(volume = volume) else p)
        }
        return ValidationResult(cleaned, issues)
    }

    /**
     * Detects outlier return indices using sigma rule (default 3-sigma). Does not remove outliers.
     * Returns indices into [returns] list.
     */
    @JvmOverloads
    fun detectOutliers(returns: List<Double>, sigma: Double = DEFAULT_OUTLIER_SIGMA): List<Int> {
        if (returns.size < 3) return emptyList()
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        val std = sqrt(variance)
        if (std == 0.0) return emptyList()
        return returns.mapIndexed { index, r -> index to r }
            .filter { (_, r) -> (r - mean).absoluteValue > sigma * std }
            .map { it.first }
    }
}
