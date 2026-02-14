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
