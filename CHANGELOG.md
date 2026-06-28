# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Sortino ratio endpoint (`GET /tickers/{symbol}/sortino`): excess return over downside deviation only; penalizes harmful volatility without penalizing upside
- Calmar ratio endpoint (`GET /tickers/{symbol}/calmar`): annualized return divided by maximum drawdown; `+∞` when drawdown is zero
- Maximum drawdown endpoint (`GET /tickers/{symbol}/drawdown`): largest peak-to-trough decline with peak/trough dates, values, and optional recovery date
- Momentum / Rate of Change endpoint (`GET /tickers/{symbol}/momentum`): percentage price change over configurable lookback periods

### Changed
- Alpha calculation upgraded from simple excess-return difference to **Jensen's alpha via OLS** single-factor regression: `β = cov(target, benchmark) / var(benchmark)`; `α = mean(target) − β × mean(benchmark) − rf_daily × (1 − β)`, annualized geometrically. Response metadata now includes `beta` and `riskFreeRate`.
- Return annualization changed from arithmetic (`(1 + mean_daily)^252 − 1`) to **geometric mean** (`∏(1+r_t)^(252/n) − 1`), correcting the ~σ²/2/year upward bias from Jensen's inequality.
- Market data source changed from `TIME_SERIES_DAILY` to **`TIME_SERIES_DAILY_ADJUSTED`** so close prices are split- and dividend-adjusted, preventing spurious return spikes on corporate action dates.
- Variance and covariance now use **N-1 (Bessel's correction)** denominator — unbiased sample estimators.
- Outlier returns are now **winsorized at ±3σ** rather than removed, preserving series length and date alignment required for beta/alpha paired-return calculations. Winsorized count reported in `metadata.outlierCount`.
- Startup now logs a `WARN` when API key authentication is disabled, so the security posture is visible in logs without reading config.

### Added (infrastructure)
- `OutputValidator`: soft plausibility checks on all computed metrics (Sharpe, Beta, Alpha, Volatility, Sortino, Calmar, Max Drawdown). Implausible values emit warnings in `metadata.warnings`; they are never rejected.

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
