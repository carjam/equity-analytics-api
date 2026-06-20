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
- Temporary: Scale up replicas: `kubectl scale deployment/equity-analytics-api --replicas=5`

## High Error Rate

### Symptoms
- Error rate > 5%
- Alert: HighErrorRate

### Diagnosis
1. Check which endpoint: Grafana or `kubectl logs -l app=equity-analytics-api | grep ERROR`
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
1. Check pod status: `kubectl get pods -l app=equity-analytics-api`
2. Check pod logs: `kubectl logs -l app=equity-analytics-api --tail=100`
3. Check events: `kubectl get events --sort-by='.lastTimestamp'`
4. Check resource limits: `kubectl top pods`

### Solutions
- CrashLoopBackOff: Check logs, fix config, redeploy
- ImagePullBackOff: Check image tag, registry access
- OOMKilled: Increase memory limits
- Pending: Check node resources, HPA limits
