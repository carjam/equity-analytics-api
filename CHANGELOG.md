# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Sortino ratio endpoint (`GET /tickers/{symbol}/sortino`): excess return over downside deviation only; penalizes harmful volatility without penalizing upside
- Calmar ratio endpoint (`GET /tickers/{symbol}/calmar`): annualized return divided by maximum drawdown; `+∞` when drawdown is zero (represented as `null` in the screener summary)
- Maximum drawdown endpoint (`GET /tickers/{symbol}/drawdown`): largest peak-to-trough decline with peak/trough dates, values, and optional recovery date
- Momentum / Rate of Change endpoint (`GET /tickers/{symbol}/momentum`): percentage price change over configurable lookback periods (comma-separated, e.g. `lookback=20,60,120`)
- Moving averages endpoint (`GET /tickers/{symbol}/moving-averages`): simple moving average for configurable window sizes (comma-separated, e.g. `window=20,50,200`)
- 52-week price levels endpoint (`GET /tickers/{symbol}/price-levels`): current price vs 52-week high/low with distance percentages for breakout and oversold/overbought screening
- Z-score endpoint (`GET /tickers/{symbol}/z-score`): standard deviations from mean price over a rolling window (configurable, default 60 days); mean-reversion signal
- Relative strength endpoint (`GET /relative-strength?target=&benchmark=`): cumulative return of target vs benchmark — positive = outperformance, negative = underperformance
- Treynor ratio endpoint (`GET /tickers/{symbol}/treynor?benchmark=`): `(annualized_return − risk_free_rate) / beta`; return per unit of systematic risk; complements Sharpe by isolating market-driven risk
- Information ratio endpoint (`GET /information-ratio?target=&benchmark=`): `annualized_active_return / tracking_error` where active return = target − benchmark per period; measures consistency of alpha generation
- Screener summary endpoint (`GET /tickers/{symbol}/summary`): all key single-symbol metrics (volatility, Sharpe, Sortino, Calmar, drawdown, momentum, moving averages, price levels, Z-score) in a single response for screener clients

### Changed
- Alpha calculation upgraded from simple excess-return difference to **Jensen's alpha via OLS** single-factor regression: `β = cov(target, benchmark) / var(benchmark)`; `α = mean(target) − β × mean(benchmark) − rf_daily × (1 − β)`, annualized geometrically. Response metadata now includes `beta` and `riskFreeRate`.
- Return annualization changed from arithmetic (`(1 + mean_daily)^252 − 1`) to **geometric mean** (`∏(1+r_t)^(252/n) − 1`), correcting the ~σ²/2/year upward bias from Jensen's inequality.
- Market data source changed from `TIME_SERIES_DAILY` to **`TIME_SERIES_DAILY_ADJUSTED`** so close prices are split- and dividend-adjusted, preventing spurious return spikes on corporate action dates.
- Variance and covariance now use **N-1 (Bessel's correction)** denominator — unbiased sample estimators.
- Outlier returns are now **winsorized at ±3σ** rather than removed, preserving series length and date alignment required for beta/alpha paired-return calculations. Winsorized count reported in `metadata.outlierCount`.
- Startup now logs a `WARN` when API key authentication is disabled, so the security posture is visible in logs without reading config.

### Added (infrastructure)
- `OutputValidator`: soft plausibility checks on all computed metrics (Sharpe, Beta, Alpha, Volatility, Sortino, Calmar, Max Drawdown, Treynor, Information Ratio). Implausible values emit warnings in `metadata.warnings`; they are never rejected.

## [1.0.0] - 2024-02-14

### Added
- Initial release
- REST API endpoints for returns, alpha, beta, volatility, sharpe ratio
- Alpha Vantage integration
- Comprehensive caching with lock-free concurrency
- Circuit breaker and retry patterns
- Rate limiting and API key authentication
- Prometheus metrics and structured logging
- Kubernetes deployment manifests
- Grafana dashboards and alerting
- Complete operational runbooks

### Security
- API key authentication
- Input validation
- Security headers
- TLS support
