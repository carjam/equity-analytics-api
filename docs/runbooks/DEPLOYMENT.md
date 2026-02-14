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
