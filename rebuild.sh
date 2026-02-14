#!/bin/bash
set -e

echo "🧹 Cleaning Gradle cache..."
rm -rf .gradle build || true

echo "🔨 Building with Gradle..."
gradle --stop || true

echo "📦 Running tests..."
gradle clean test -x check --stacktrace 2>&1 | head -100

echo ""
echo "✅ Build complete!"
