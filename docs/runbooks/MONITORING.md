# Monitoring Guide

## Key Metrics to Watch

### Request Metrics
- `api_requests_total` - Total requests (counter)
- `api_request_duration_seconds` - Request latency (histogram)
- `in_flight_requests` - Current active requests (gauge)

### Error Metrics
- Error rate: `rate(api_requests_total{status=~"5.."}[5m])`
- 4xx rate: `rate(api_requests_total{status=~"4.."}[5m])`

### Performance Metrics
- P50 latency: `histogram_quantile(0.50, rate(api_request_duration_seconds_bucket[5m]))`
- P95 latency: `histogram_quantile(0.95, rate(api_request_duration_seconds_bucket[5m]))`
- P99 latency: `histogram_quantile(0.99, rate(api_request_duration_seconds_bucket[5m]))`

### Resilience Metrics
- `circuit_breaker_state_gauge` - Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
- `circuit_breaker_calls_total` - Circuit breaker call outcomes
- `retry_attempts_total` - Retry attempts

### Cache Metrics
- `cache_hit_rate` - Cache effectiveness (0.0-1.0)
- `cache_size` - Number of cached entries
- `cache_hits_total` / `cache_misses_total` - Hit/miss counters

### System Metrics
- `jvm_memory_used_bytes{area="heap"}` - Heap usage
- `process_cpu_usage` - CPU usage
- `jvm_threads_live_threads` - Thread count

## Alert Thresholds

| Alert | Threshold | Duration | Severity |
|-------|-----------|----------|----------|
| HighErrorRate | > 5% | 5m | Critical |
| HighLatency | P95 > 1s | 5m | Warning |
| CircuitBreakerOpen | state=1 | 2m | Critical |
| HighMemoryUsage | > 90% | 5m | Warning |
| ServiceDown | up=0 | 1m | Critical |
| LowCacheHitRate | < 50% | 10m | Info |

## Dashboard Locations

- Grafana: https://grafana.example.com/d/meiken
- Prometheus: https://prometheus.example.com
- Logs: https://logs.example.com (Elasticsearch/Kibana or similar)

## Log Query Examples

### Find all errors for a request
correlationId="req-1234567890"

### Find slow requests
duration > 1000 AND level="INFO"

### Find Alpha Vantage failures
logger="AlphaVantageService" AND level="ERROR"

### Find circuit breaker events
message:*circuit*breaker*

### Find rate limit hits
message:"rate limit" OR status=429
