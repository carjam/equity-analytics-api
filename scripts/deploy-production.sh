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
