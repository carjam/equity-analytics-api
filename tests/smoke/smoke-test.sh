#!/bin/bash
set -e

BASE_URL=${1:-http://localhost:8080}

echo "Running smoke tests against $BASE_URL..."

# Test health
echo "Testing /health..."
curl -f "$BASE_URL/health" > /dev/null || { echo "❌ Health check failed"; exit 1; }

# Test metrics
echo "Testing /metrics..."
curl -f "$BASE_URL/metrics" > /dev/null || { echo "❌ Metrics check failed"; exit 1; }

# Test returns endpoint
echo "Testing returns endpoint..."
curl -f "$BASE_URL/api/v1/tickers/AAPL/returns?from_date=2024-01-01&to_date=2024-03-31" > /dev/null || { echo "❌ Returns endpoint failed"; exit 1; }

# Test alpha endpoint
echo "Testing alpha endpoint..."
curl -f "$BASE_URL/api/v1/alpha?target=AAPL&benchmark=SPY&from_date=2024-01-01&to_date=2024-03-31" > /dev/null || { echo "❌ Alpha endpoint failed"; exit 1; }

echo "✅ All smoke tests passed"
