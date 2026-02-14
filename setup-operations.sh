#!/bin/bash

# Meiken Operations Setup Script
# This creates all operational files, scripts, and documentation

set -e

echo "🚀 Setting up Operations files for Meiken..."

# Create directories
mkdir -p .github/workflows
mkdir -p scripts
mkdir -p config
mkdir -p docs/runbooks
mkdir -p alerting
mkdir -p tests/smoke
mkdir -p db/migrations

echo "📁 Created directory structure"

# GitHub Actions - Enhanced CI
cat > .github/workflows/ci.yml << 'GHEOF'
name: CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Setup Gradle
      uses: gradle/gradle-build-action@v2
      
    - name: Build
      run: ./gradlew build
      
    - name: Run tests
      run: ./gradlew test
      
    - name: Test coverage
      run: ./gradlew jacocoTestReport
      
    - name: Upload coverage
      uses: codecov/codecov-action@v3
      with:
        files: ./build/reports/jacoco/test/jacocoTestReport.xml
GHEOF

# GitHub Actions - Deploy
cat > .github/workflows/deploy.yml << 'GHEOF'
name: Deploy

on:
  push:
    branches: [ main ]
    tags: [ 'v*' ]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
      
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Build with Gradle
      run: ./gradlew build
    
    - name: Log in to Container Registry
      uses: docker/login-action@v3
      with:
        registry: ${{ env.REGISTRY }}
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}
    
    - name: Extract metadata
      id: meta
      uses: docker/metadata-action@v5
      with:
        images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
        tags: |
          type=ref,event=branch
          type=ref,event=pr
          type=semver,pattern={{version}}
          type=semver,pattern={{major}}.{{minor}}
          type=sha
    
    - name: Build and push Docker image
      uses: docker/build-push-action@v5
      with:
        context: .
        push: true
        tags: ${{ steps.meta.outputs.tags }}
        labels: ${{ steps.meta.outputs.labels }}
    
    - name: Deploy to staging
      if: github.ref == 'refs/heads/main'
      run: |
        echo "Would deploy to staging here"
        # kubectl set image deployment/meiken meiken=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:sha-${GITHUB_SHA::7}
GHEOF

# GitHub Actions - Security
cat > .github/workflows/security.yml << 'GHEOF'
name: Security Scan

on:
  schedule:
    - cron: '0 0 * * 0'  # Weekly on Sunday
  workflow_dispatch:

jobs:
  dependency-check:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Run dependency check
      run: ./gradlew dependencyCheckAnalyze
    
  container-scan:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Build image
      run: docker build -t meiken:scan .
    
    - name: Run Trivy scanner
      uses: aquasecurity/trivy-action@master
      with:
        image-ref: meiken:scan
        format: 'sarif'
        output: 'trivy-results.sarif'
    
    - name: Upload results
      uses: github/codeql-action/upload-sarif@v2
      with:
        sarif_file: 'trivy-results.sarif'
GHEOF

# Deployment scripts
cat > scripts/deploy-staging.sh << 'SCRIPTEOF'
#!/bin/bash
set -e

echo "Deploying to staging..."
kubectl config use-context staging
kubectl apply -f k8s/
kubectl rollout status deployment/meiken
echo "Deployment complete"
SCRIPTEOF

cat > scripts/deploy-production.sh << 'SCRIPTEOF'
#!/bin/bash
set -e

read -p "Deploy to production? (yes/no): " confirm
if [ "$confirm" != "yes" ]; then 
  echo "Deployment cancelled"
  exit 1
fi

echo "Deploying to production..."
kubectl config use-context production
kubectl apply -f k8s/
kubectl rollout status deployment/meiken
echo "Production deployment complete"
SCRIPTEOF

cat > scripts/rollback.sh << 'SCRIPTEOF'
#!/bin/bash
set -e

echo "Rolling back deployment..."
kubectl rollout undo deployment/meiken
kubectl rollout status deployment/meiken
echo "Rollback complete"
SCRIPTEOF

cat > scripts/health-check.sh << 'SCRIPTEOF'
#!/bin/bash

ENDPOINT=${1:-http://localhost:8080}
response=$(curl -s "$ENDPOINT/health")
status=$(echo $response | jq -r '.status')

if [ "$status" != "healthy" ]; then
  echo "❌ Health check failed: $response"
  exit 1
fi

echo "✅ Health check passed"
SCRIPTEOF

chmod +x scripts/*.sh

# Environment configs
cat > config/staging.env << 'ENVEOF'
MEIKEN_ENVIRONMENT=staging
LOG_LEVEL=DEBUG
API_KEYS_ENABLED=false
ALLOWED_ORIGINS=https://staging.example.com
ENVEOF

cat > config/production.env << 'ENVEOF'
MEIKEN_ENVIRONMENT=production
LOG_LEVEL=INFO
API_KEYS_ENABLED=true
REQUIRE_HTTPS=true
ALLOWED_ORIGINS=https://api.example.com,https://www.example.com
ENVEOF

# Smoke tests
cat > tests/smoke/smoke-test.sh << 'SCRIPTEOF'
#!/bin/bash
set -e

BASE_URL=${1:-http://localhost:8080}

echo "Running smoke tests against $BASE_URL..."

# Test health
echo "Testing /health..."
curl -f "$BASE_URL/health" > /dev/null || { echo "❌ Health check failed"; exit 1; }

# Test metrics
echo "Testing /metrics..."
curl -f "$BASE_URL/metrics" > /dev/null || { echo "❌ Metrics check failed"; exit 1; }

# Test returns endpoint
echo "Testing returns endpoint..."
curl -f "$BASE_URL/api/v1/tickers/AAPL/returns?from_date=2024-01-01&to_date=2024-03-31" > /dev/null || { echo "❌ Returns endpoint failed"; exit 1; }

# Test alpha endpoint
echo "Testing alpha endpoint..."
curl -f "$BASE_URL/api/v1/alpha?target=AAPL&benchmark=SPY&from_date=2024-01-01&to_date=2024-03-31" > /dev/null || { echo "❌ Alpha endpoint failed"; exit 1; }

echo "✅ All smoke tests passed"
SCRIPTEOF

chmod +x tests/smoke/smoke-test.sh

# Prometheus alerting rules
cat > alerting/prometheus-rules.yml << 'ALERTEOF'
groups:
  - name: meiken_alerts
    interval: 30s
    rules:
      - alert: HighErrorRate
        expr: rate(api_requests_total{status=~"5.."}[5m]) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value }} (threshold: 0.05)"
      
      - alert: HighLatency
        expr: histogram_quantile(0.95, rate(api_request_duration_seconds_bucket[5m])) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High latency detected"
          description: "P95 latency is {{ $value }}s (threshold: 1s)"
      
      - alert: CircuitBreakerOpen
        expr: circuit_breaker_state_gauge == 1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Circuit breaker is OPEN"
          description: "Alpha Vantage circuit breaker has been open for 2+ minutes"
      
      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High memory usage"
          description: "Heap usage is {{ $value | humanizePercentage }}"
      
      - alert: ServiceDown
        expr: up{job="meiken"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service is down"
          description: "Meiken service has been down for 1+ minute"
      
      - alert: LowCacheHitRate
        expr: cache_hit_rate < 0.5
        for: 10m
        labels:
          severity: info
        annotations:
          summary: "Low cache hit rate"
          description: "Cache hit rate is {{ $value | humanizePercentage }}"
ALERTEOF

# Runbooks
cat > docs/runbooks/DEPLOYMENT.md << 'RUNBOOKEOF'
# Deployment Runbook

## Pre-Deployment Checklist

- [ ] All tests passing in CI
- [ ] Code review approved
- [ ] Version bumped in build.gradle.kts
- [ ] CHANGELOG.md updated
- [ ] Staging deployment successful
- [ ] Smoke tests passed on staging
- [ ] On-call engineer notified

## Deployment Steps

### Staging

1. Merge PR to main branch
2. CI automatically builds and deploys to staging
3. Run smoke tests: `./tests/smoke/smoke-test.sh https://staging.example.com`
4. Monitor metrics for 15 minutes
5. If issues: rollback with `./scripts/rollback.sh`

### Production

1. Create git tag: `git tag v1.2.3 && git push origin v1.2.3`
2. CI builds and pushes Docker image
3. Run: `./scripts/deploy-production.sh`
4. Monitor deployment: `kubectl rollout status deployment/meiken`
5. Run smoke tests: `./tests/smoke/smoke-test.sh https://api.example.com`
6. Monitor metrics and logs for 30 minutes

## Post-Deployment Validation

1. Check health endpoint: `curl https://api.example.com/health`
2. Verify metrics: `curl https://api.example.com/metrics | grep api_requests`
3. Check error rate in Grafana
4. Verify circuit breaker is CLOSED
5. Test each endpoint manually

## Rollback Procedure

If issues detected:

1. Run: `./scripts/rollback.sh`
2. Verify rollback: `kubectl rollout status deployment/meiken`
3. Run smoke tests
4. Investigate root cause
5. Fix and redeploy

## Rollback SLA

- Detection to rollback decision: < 5 minutes
- Rollback execution: < 2 minutes
- Service restoration: < 10 minutes total
RUNBOOKEOF

cat > docs/runbooks/INCIDENT_RESPONSE.md << 'RUNBOOKEOF'
# Incident Response Runbook

## Severity Levels

### P0 - Critical
- Complete service outage
- Data loss or corruption
- Security breach
- Response time: Immediate
- Notification: PagerDuty + Slack + Email

### P1 - High
- Partial outage (>50% error rate)
- Significant performance degradation
- Circuit breaker stuck open
- Response time: < 15 minutes
- Notification: PagerDuty + Slack

### P2 - Medium
- Elevated error rates (5-50%)
- Moderate performance issues
- Single endpoint degraded
- Response time: < 1 hour
- Notification: Slack

### P3 - Low
- Minor performance issues
- Low cache hit rate
- Non-critical warnings
- Response time: Next business day
- Notification: Slack

### P4 - Info
- Informational alerts
- Capacity planning
- Response time: As needed

## On-Call Rotation

- Primary: Check PagerDuty schedule
- Secondary: Escalate after 15 minutes
- Manager escalation: After 30 minutes

## Incident Response Process

1. **Acknowledge** - Respond within SLA
2. **Assess** - Check health, metrics, logs
3. **Communicate** - Update #incidents channel
4. **Mitigate** - Rollback or fix forward
5. **Resolve** - Confirm service restored
6. **Document** - Write postmortem

## Communication Templates

### Initial Update
"🚨 Incident detected: [brief description]. Severity: P[0-4]. Investigating. ETA: [time]"

### Progress Update
"Update: [what we found]. Current status: [status]. Next steps: [steps]. ETA: [time]"

### Resolution
"✅ Resolved: [brief summary]. Root cause: [cause]. Duration: [time]. Postmortem: [link]"
RUNBOOKEOF

cat > docs/runbooks/TROUBLESHOOTING.md << 'RUNBOOKEOF'
# Troubleshooting Guide

## High Latency

### Symptoms
- P95 latency > 1 second
- Alert: HighLatency

### Diagnosis
1. Check Grafana dashboard - which endpoint?
2. Check Alpha Vantage latency: logs for "alpha_vantage_request_duration"
3. Check circuit breaker state: `/health` endpoint
4. Check cache hit rate: `/metrics` → cache_hit_rate

### Solutions
- If Alpha Vantage slow: Circuit breaker will open automatically
- If low cache hit rate: Cache may have evicted - monitor memory
- If specific endpoint slow: Check logs for that endpoint
- Temporary: Scale up replicas: `kubectl scale deployment/meiken --replicas=5`

## High Error Rate

### Symptoms
- Error rate > 5%
- Alert: HighErrorRate

### Diagnosis
1. Check which endpoint: Grafana or `kubectl logs -l app=meiken | grep ERROR`
2. Check error types: 4xx vs 5xx
3. Check circuit breaker: `/health`
4. Check Alpha Vantage status

### Solutions
- 4xx errors: Check client requests, validation
- 5xx errors: Check dependencies, rollback if recent deploy
- Circuit breaker open: Wait for recovery or check Alpha Vantage
- Database errors: N/A (stateless)

## Circuit Breaker Open

### Symptoms
- Circuit breaker state = OPEN
- Alert: CircuitBreakerOpen
- 503 errors from API

### Diagnosis
1. Check `/health` → dependencies → alpha_vantage → circuit_breaker
2. Check metrics: circuit_breaker_state_gauge
3. Check Alpha Vantage status externally

### Solutions
- Wait 60 seconds for automatic recovery to HALF_OPEN
- If Alpha Vantage is down: Inform users, wait for recovery
- If persistent: Check API key validity, rate limits
- Emergency: Switch to mock service temporarily (requires redeploy)

## Memory Issues

### Symptoms
- Alert: HighMemoryUsage
- OOMKilled pods

### Diagnosis
1. Check heap usage: Grafana → JVM metrics
2. Check cache size: `/metrics` → cache_size
3. Check for memory leaks: heap dumps

### Solutions
- Clear cache: Restart pods (cache rebuilds)
- Increase memory limits: Update k8s/deployment.yaml
- Reduce cache size: Update application.conf
- Check for leaks: Analyze heap dump

## Service Down

### Symptoms
- Alert: ServiceDown
- Health check failing

### Diagnosis
1. Check pod status: `kubectl get pods -l app=meiken`
2. Check pod logs: `kubectl logs -l app=meiken --tail=100`
3. Check events: `kubectl get events --sort-by='.lastTimestamp'`
4. Check resource limits: `kubectl top pods`

### Solutions
- CrashLoopBackOff: Check logs, fix config, redeploy
- ImagePullBackOff: Check image tag, registry access
- OOMKilled: Increase memory limits
- Pending: Check node resources, HPA limits
RUNBOOKEOF

cat > docs/runbooks/MONITORING.md << 'RUNBOOKEOF'
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
RUNBOOKEOF

cat > docs/DISASTER_RECOVERY.md << 'DISASTEREOF'
# Disaster Recovery Plan

## Recovery Objectives

- **RTO** (Recovery Time Objective): < 15 minutes
- **RPO** (Recovery Point Objective): < 5 minutes (or N/A for stateless)

## Data Classification

### Critical Data
- None (stateless service)
- All state in external systems (Alpha Vantage)

### Configuration
- Stored in Git (version controlled)
- Kubernetes ConfigMaps/Secrets
- Backup: Git + encrypted secret store

### Ephemeral Data
- In-memory cache (can be rebuilt)
- No persistent storage required

## Backup Strategy

### Code & Configuration
- Repository: GitHub (multiple backups)
- Container Images: GitHub Container Registry + backup registry
- Kubernetes manifests: Git repository

### Logs
- Retention: 30 days
- Storage: Log aggregation service
- Backup: Not required (operational data only)

### Metrics
- Retention: 90 days
- Storage: Prometheus
- Backup: Not required (operational data only)

## Recovery Procedures

### Complete Outage

1. Deploy from scratch:
   - Clone repository
   - Build Docker image or use existing
   - Deploy to Kubernetes: `kubectl apply -f k8s/`
   - Verify health: `./scripts/health-check.sh`

Time: ~10 minutes

### Partial Outage

1. Rolling restart:
   - `kubectl rollout restart deployment/meiken`
   - Monitor: `kubectl rollout status deployment/meiken`

Time: ~3 minutes

### Data Corruption

N/A - No persistent data to corrupt

### Region Failure

1. Failover to backup region:
   - Update DNS to point to backup region
   - Verify services in backup region
   - Monitor traffic shift

Time: ~5 minutes (if backup region pre-deployed)

## Testing Schedule

- Disaster recovery drill: Quarterly
- Backup verification: Monthly
- Runbook review: Monthly

## Contact Information

- On-call: See PagerDuty schedule
- Infrastructure team: infra@example.com
- Security team: security@example.com
DISASTEREOF

cat > docs/LOGGING.md << 'LOGEOF'
# Logging Guide

## Log Levels

### ERROR
Use for: Failures that prevent request completion
Examples:
- API call failures
- Unexpected exceptions
- Circuit breaker trips

### WARN
Use for: Recoverable issues or degraded state
Examples:
- Slow API responses
- Cache misses
- Rate limit warnings

### INFO
Use for: Normal operations
Examples:
- Request start/completion
- Configuration loaded
- Circuit breaker state changes

### DEBUG
Use for: Detailed troubleshooting
Examples:
- Request parameters
- Cache operations
- Calculation details

## Structured Logging Format

All logs are JSON:

{
  "@timestamp": "2024-02-14T23:27:03.090Z",
  "level": "INFO",
  "logger": "com.meiken.Application",
  "message": "Request completed",
  "correlationId": "req-1234567890",
  "duration": 45,
  "endpoint": "/api/v1/alpha"
}

## Correlation IDs

Every request gets a unique correlation ID:
- Auto-generated: `req-{timestamp}-{random}`
- From header: `X-Correlation-ID`
- Stored in MDC
- Returned in response header
- Used in all logs for that request

## Common Log Queries

### Find all logs for a request
correlationId="req-1234567890"

### Find errors in last hour
level="ERROR" AND @timestamp > now-1h

### Find slow requests
duration > 1000

### Find specific endpoint issues
endpoint="/api/v1/alpha" AND level="ERROR"

## Log Retention

- Development: 7 days
- Staging: 14 days
- Production: 30 days
LOGEOF

cat > CONTRIBUTING.md << 'CONTRIBEOF'
# Contributing to Meiken

## Development Setup

1. Clone repository:
   git clone https://github.com/carjam/meiken.git
   cd meiken

2. Install dependencies:
   - Java 17+
   - Gradle (or use wrapper)
   - Docker (optional)

3. Set environment variables:
   export ALPHA_VANTAGE_API_KEY=your_key
   # Or leave unset to use mock service

4. Run tests:
   ./gradlew test

5. Run locally:
   ./gradlew run

6. Access at: http://localhost:8080

## Code Standards

- Kotlin style guide: Follow Kotlin conventions
- Line length: 120 characters
- Formatting: Use IntelliJ IDEA defaults
- Testing: Minimum 80% coverage

## Pull Request Process

1. Create feature branch: `git checkout -b feature/your-feature`
2. Make changes with clear commit messages
3. Add tests for new functionality
4. Run tests: `./gradlew test`
5. Push and create PR
6. Wait for CI to pass
7. Request review
8. Address feedback
9. Merge when approved

## Commit Message Format

feat: Add new feature
fix: Fix bug
docs: Update documentation
test: Add tests
refactor: Code refactoring
chore: Build/config changes

## Testing

- Unit tests: `./gradlew test`
- Integration tests: Included in test task
- Smoke tests: `./tests/smoke/smoke-test.sh`

## Questions?

Open an issue or discussion on GitHub
CONTRIBEOF

cat > CHANGELOG.md << 'CHANGEEOF'
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
CHANGEEOF

echo "✅ All operations files created!"
echo ""
echo "📋 Summary:"
echo "  - GitHub Actions workflows (CI, Deploy, Security)"
echo "  - Deployment scripts (staging, production, rollback)"
echo "  - Environment configs (staging, production)"
echo "  - Smoke tests"
echo "  - Prometheus alerting rules"
echo "  - Runbooks (Deployment, Incident Response, Troubleshooting, Monitoring)"
echo "  - Disaster Recovery plan"
echo "  - Logging guide"
echo "  - Contributing guide"
echo "  - Changelog"
echo ""
echo "🎯 Next steps:"
echo "  1. Review all generated files"
echo "  2. Customize for your environment"
echo "  3. Update secrets and URLs"
echo "  4. Commit to repository"
echo "  5. Test deployment pipeline"
