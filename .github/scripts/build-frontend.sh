#!/usr/bin/env bash
set -euo pipefail
pushd frontend >/dev/null
echo "Installing frontend dependencies (npm ci)..."
npm ci
echo "Building frontend (npm run build)..."
npm run build
popd >/dev/null
echo "Frontend build completed"
