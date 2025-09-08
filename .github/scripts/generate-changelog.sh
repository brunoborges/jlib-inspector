#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:-}"
if [[ -z "$VERSION" ]]; then
  echo "VERSION env var required" >&2
  exit 1
fi

if git rev-parse --verify HEAD^ >/dev/null 2>&1; then
  LAST_TAG=$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || echo "")
else
  LAST_TAG=""
fi

if [[ -n "$LAST_TAG" ]]; then
  CHANGELOG_BODY=$(git log --pretty=format:"- %s (%an)" "$LAST_TAG"..HEAD)
else
  CHANGELOG_BODY=$(git log --pretty=format:"- %s (%an)" --max-count=20)
fi

{
  echo "## What's Changed"
  echo
  echo "$CHANGELOG_BODY"
  echo
  echo "## Installation"
  echo
  echo "1. Download an archive for your platform"
  echo "2. Extract it"
  echo "3. Run install.sh (Linux/macOS) or install.bat (Windows)"
  echo "4. Follow README instructions"
  echo
  echo "## Quick Start"
  echo
  echo '```bash'
  echo '# Run the demo'
  echo './demo-jlib-inspector.ps1'
  echo
  echo '# Use with your application'
  echo 'java -javaagent:jlib-inspector-agent-*.jar -jar your-app.jar'
  echo '```'
} > /tmp/changelog.txt

echo "CHANGELOG<<EOF" >> "$GITHUB_OUTPUT"
cat /tmp/changelog.txt >> "$GITHUB_OUTPUT"
echo "EOF" >> "$GITHUB_OUTPUT"

echo "Changelog generated for $VERSION"
