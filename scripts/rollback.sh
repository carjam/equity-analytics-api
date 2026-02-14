#!/bin/bash
set -e

echo "Rolling back deployment..."
kubectl rollout undo deployment/meiken
kubectl rollout status deployment/meiken
echo "Rollback complete"
