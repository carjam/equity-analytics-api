package com.meiken.calculator

import kotlin.math.sqrt

/** Basic statistics: mean, variance, std dev, covariance, Pearson correlation. */
object StatisticalCalculations {

    fun mean(values: List<Double>): Double {
        require(values.isNotEmpty()) { "Cannot calculate mean of empty list" }
        return values.average()
    }

    /** Sample variance: sum of squared deviations from mean, divided by (n − 1). Requires at least 2 values. */
    fun variance(values: List<Double>): Double {
        require(values.size >= 2) { "Cannot calculate variance of fewer than 2 values" }
        val avg = mean(values)
        return values.sumOf { (it - avg) * (it - avg) } / (values.size - 1)
    }

    fun standardDeviation(values: List<Double>): Double = sqrt(variance(values))

    /** Sample covariance: Σ[(X − mean(X))(Y − mean(Y))] / (n − 1); series must have same length and at least 2 elements. */
    fun covariance(series1: List<Double>, series2: List<Double>): Double {
        require(series1.size == series2.size) { "Series must have same length" }
        require(series1.size >= 2) { "Cannot calculate covariance of fewer than 2 values" }
        val mean1 = mean(series1)
        val mean2 = mean(series2)
        return series1.zip(series2).sumOf { (x, y) -> (x - mean1) * (y - mean2) } / (series1.size - 1)
    }

    /** Pearson correlation: covariance / (std1 * std2). Fails if either std dev is zero. */
    fun correlation(series1: List<Double>, series2: List<Double>): Double {
        val cov = covariance(series1, series2)
        val std1 = standardDeviation(series1)
        val std2 = standardDeviation(series2)
        require(std1 != 0.0 && std2 != 0.0) { "Standard deviation cannot be zero" }
        return cov / (std1 * std2)
    }
}
