#!/usr/bin/env bash
set -euo pipefail

if [[ "${GITHUB_EVENT_NAME:-}" == "workflow_dispatch" ]]; then
  if [[ -z "${INPUT_VERSION:-}" ]]; then
    echo "INPUT_VERSION not provided for workflow_dispatch" >&2
    exit 1
  fi
  VERSION="$INPUT_VERSION"
else
  if [[ "${GITHUB_REF:-}" =~ refs/tags/ ]]; then
    VERSION="${GITHUB_REF#refs/tags/}"
  else
    echo "GITHUB_REF does not point to a tag; cannot derive version" >&2
    exit 1
  fi
fi

echo "Resolved version: $VERSION"
echo "VERSION=$VERSION" >> "$GITHUB_OUTPUT"
