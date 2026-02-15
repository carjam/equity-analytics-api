# Diagnostics guide

Use this when builds fail, tests fail, or the service is misbehaving.

## Build and tests

- **Run tests:** `./gradlew test` (or `.\gradlew.bat test` on Windows)
- **Clean first:** `./gradlew clean test`
- **Stop daemon and retry:** `./gradlew --stop` then `./gradlew clean test --no-daemon`
- **Verbose:** `./gradlew test --info` or `--stacktrace`

Test and coverage reports are under `build/reports/`. See **BUILD.md** for more build troubleshooting.

## Service health

When the app is running:

- **Health:** `curl http://localhost:8080/health` — returns JSON with status, dependencies (Alpha Vantage, cache, circuit breaker), and system info.
- **Metrics:** `curl http://localhost:8080/metrics` — Prometheus-format metrics.

Use health to confirm the app is up and whether the Alpha Vantage circuit breaker is CLOSED/OPEN.

## Logs

- Logs are JSON (logstash-logback-encoder). Each request has a **correlation ID** (response header `X-Correlation-ID` and in log fields).
- Search by `correlationId` to trace a single request.
- See **LOGGING.md** for log levels and common queries.

## Common issues

| Symptom | Check |
|--------|--------|
| Tests fail | Run `./gradlew clean test --no-daemon`; read failure output and stack trace. |
| App won’t start | Port in use? Check `application.conf` and `PORT`. Java 11? |
| 503 / circuit breaker | `/health` → dependencies → alpha_vantage. Circuit OPEN when Alpha Vantage fails often; wait for HALF_OPEN or fix upstream. |
| 404 for symbol | Invalid or unknown ticker; Alpha Vantage returns “Error Message”. |
| High latency | Check cache hit rate and Alpha Vantage latency in metrics; see runbooks/TROUBLESHOOTING.md. |

## Runbooks

For deployment, monitoring, incidents, and rollback, see **runbooks/** (DEPLOYMENT.md, MONITORING.md, TROUBLESHOOTING.md, INCIDENT_RESPONSE.md).
