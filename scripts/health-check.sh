#!/bin/bash

ENDPOINT=${1:-http://localhost:8080}
response=$(curl -s "$ENDPOINT/health")
status=$(echo $response | jq -r '.status')

if [ "$status" != "healthy" ]; then
  echo "❌ Health check failed: $response"
  exit 1
fi

echo "✅ Health check passed"
