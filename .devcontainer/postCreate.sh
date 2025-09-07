#!/usr/bin/env bash
set -euo pipefail
echo "[postCreate] Setting up workspace..."

if [ -x "./mvnw" ]; then
  MVN=./mvnw
else
  MVN=mvn
fi

echo "[postCreate] Building Java modules (skip tests)"
$MVN -q -DskipTests package

echo "[postCreate] Installing frontend dependencies"
if [ -f frontend/package.json ]; then
  pushd frontend >/dev/null
  npm ci || npm install
  popd >/dev/null
fi

echo "[postCreate] Done."
