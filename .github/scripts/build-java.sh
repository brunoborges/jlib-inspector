#!/usr/bin/env bash
set -euo pipefail

echo "Building Java components (clean verify)..."
./mvnw -q clean verify -B
echo "Java build completed"
