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
