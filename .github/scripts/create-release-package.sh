#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:-}"
if [[ -z "$VERSION" ]]; then
  echo "VERSION env var not set" >&2
  exit 1
fi

echo "Creating release package for version $VERSION"
WORKDIR="release-package"
rm -rf "$WORKDIR" || true
mkdir -p "$WORKDIR/frontend"

# Copy built artifacts
cp agent/target/jlib-inspector-agent-*.jar "$WORKDIR/"
cp server/target/jlib-inspector-server-*.jar "$WORKDIR/"
cp sample-spring-app/target/sample-spring-app-*.jar "$WORKDIR/"

# Copy frontend build output and minimal runtime files
cp -r frontend/dist/* "$WORKDIR/frontend/"
cp frontend/app.js "$WORKDIR/frontend/"
cp frontend/package.json "$WORKDIR/frontend/"

# Documentation & scripts
cp demo-jlib-inspector.ps1 "$WORKDIR/"
cp README.md "$WORKDIR/"
cp LICENSE "$WORKDIR/"
if [[ -f logging.properties ]]; then
  cp logging.properties "$WORKDIR/"
fi

# Generate install.sh
cat > "$WORKDIR/install.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

VERSION_PLACEHOLDER="__VERSION__"
echo "=== JLib Inspector Installation ==="
echo "Version: ${VERSION_PLACEHOLDER}"
echo

if ! command -v java >/dev/null 2>&1; then
  echo "❌ Java is required but not installed." >&2
  echo "Install Java 21 or later and retry." >&2
  exit 1
fi

JAVA_VERSION_RAW=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
JAVA_MAJOR=$(echo "$JAVA_VERSION_RAW" | awk -F '.' '{print $1}')
if [[ "$JAVA_MAJOR" -lt 21 ]]; then
  echo "❌ Java 21 or later required. Found $JAVA_VERSION_RAW" >&2
  exit 1
fi
echo "✅ Java $JAVA_VERSION_RAW detected"

if command -v node >/dev/null 2>&1; then
  NODE_MAJOR=$(node --version | sed 's/^v//' | cut -d'.' -f1)
  if [[ "$NODE_MAJOR" -ge 18 ]]; then
    echo "✅ Node.js $(node --version) detected (frontend available)"
    HAS_NODE=true
  else
    echo "⚠️  Node.js 18+ recommended. Found $(node --version)"
    HAS_NODE=false
  fi
else
  echo "⚠️  Node.js not found. Frontend dashboard won't run."; HAS_NODE=false
fi

echo
echo "Usage:"; echo
echo "1. Run agent with your Java application:"; echo "   java -javaagent:jlib-inspector-agent-*.jar -jar your-app.jar"; echo
echo "2. Run with server integration:"; echo "   java -javaagent:jlib-inspector-agent-*.jar=server:8080 -jar your-app.jar"; echo
echo "3. Run demo (PowerShell required):"; echo "   ./demo-jlib-inspector.ps1"; echo
if [[ "$HAS_NODE" == true ]]; then
  echo "4. Start dashboard:"; echo "   cd frontend && npm install && npm start"; echo
fi
echo "See README.md for more details."
EOF

sed -i.bak "s/__VERSION__/$VERSION/g" "$WORKDIR/install.sh" && rm "$WORKDIR/install.sh.bak"
chmod +x "$WORKDIR/install.sh"

# Generate Windows install.bat
cat > "$WORKDIR/install.bat" <<EOF
@echo off
echo === JLib Inspector Installation ===
echo Version: $VERSION
echo.
java -version >nul 2>&1 || (echo Java 21+ required & pause & exit /b 1)
echo Java detected
node --version >nul 2>&1 && (echo Node.js detected (frontend available) & set HAS_NODE=true) || (echo Node.js not found. Frontend unavailable. & set HAS_NODE=false)
echo.
echo 1. Run agent: java -javaagent:jlib-inspector-agent-*.jar -jar your-app.jar
echo 2. With server: java -javaagent:jlib-inspector-agent-*.jar=server:8080 -jar your-app.jar
echo 3. Demo: .\demo-jlib-inspector.ps1
if "%HAS_NODE%"=="true" echo 4. Frontend: cd frontend && npm install && npm start
echo.
echo See README.md for more details.
pause
EOF

# Create archives
tar -czf jlib-inspector-${VERSION}-linux.tar.gz -C "$WORKDIR" .
tar -czf jlib-inspector-${VERSION}-windows.tar.gz -C "$WORKDIR" .
tar -czf jlib-inspector-${VERSION}.tar.gz -C "$WORKDIR" .

echo "Release archives created:"
ls -1 jlib-inspector-${VERSION}*.tar.gz
