#!/bin/bash
set -e

echo "Deploying to staging..."
kubectl config use-context staging
kubectl apply -f k8s/
kubectl rollout status deployment/equity-analytics-api
echo "Deployment complete"
