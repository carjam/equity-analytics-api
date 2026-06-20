#!/bin/bash
set -e

echo "Rolling back deployment..."
kubectl rollout undo deployment/equity-analytics-api
kubectl rollout status deployment/equity-analytics-api
echo "Rollback complete"
