package com.meiken.validator

/**
 * Plausibility checks on computed output values. Functions return a warning string to append to
 * the response metadata `warnings` list, or null when the value is within expected range. They
 * never throw — implausible results are surfaced to the caller, not rejected, because the
 * computation may be correct for an unusual security or period (e.g., a leveraged ETF has beta > 2;
 * a short measurement window produces noisy annualized alpha).
 *
 * Bounds are based on financial convention for exchange-listed equities and ETFs:
 *   Sharpe ratio: [-5, 5] — beyond this almost always signals degenerate data or an extremely
 *       short window with very low volatility.
 *   Beta: [-3, 3] — covers 3× inverse and 3× leveraged ETFs; beyond suggests a data error.
 *   Alpha (annualized OLS): [-1, 1] — ±100%/yr is extraordinary; short windows produce noisy
 *       estimates but values beyond this still warrant scrutiny.
 *   Annualized volatility: [0.05, 3.0] — below 5% implies near-constant prices (data quality
 *       concern); above 300% is implausible for publicly-traded equities.
 */
object OutputValidator {

    private const val SHARPE_MIN = -5.0
    private const val SHARPE_MAX = 5.0
    private const val BETA_MIN = -3.0
    private const val BETA_MAX = 3.0
    private const val ALPHA_MIN = -1.0
    private const val ALPHA_MAX = 1.0
    private const val VOL_MIN = 0.05
    private const val VOL_MAX = 3.0

    fun checkSharpe(value: Double): String? = when {
        !value.isFinite() -> "sharpe_not_finite"
        value < SHARPE_MIN || value > SHARPE_MAX -> "sharpe_implausible=%.2f".format(value)
        else -> null
    }

    fun checkBeta(value: Double): String? = when {
        !value.isFinite() -> "beta_not_finite"
        value < BETA_MIN || value > BETA_MAX -> "beta_implausible=%.2f".format(value)
        else -> null
    }

    fun checkAlpha(value: Double): String? = when {
        !value.isFinite() -> "alpha_not_finite"
        value < ALPHA_MIN || value > ALPHA_MAX -> "alpha_implausible=%.2f".format(value)
        else -> null
    }

    fun checkAnnualizedVolatility(value: Double): String? = when {
        !value.isFinite() -> "volatility_not_finite"
        value < VOL_MIN || value > VOL_MAX -> "volatility_implausible=%.2f".format(value)
        else -> null
    }
}
