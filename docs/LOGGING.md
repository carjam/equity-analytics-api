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
