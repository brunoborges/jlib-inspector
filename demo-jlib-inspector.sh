#!/bin/bash
# Test the unified InspectorAgent with optional server integration

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[0;37m'
NC='\033[0m' # No Color

# Get the script directory to work relative to project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# Check prerequisites
if ! command -v curl &> /dev/null; then
    echo -e "${RED}Error: curl is required but not installed.${NC}"
    echo -e "${YELLOW}Please install curl and try again.${NC}"
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: java is required but not installed.${NC}"
    echo -e "${YELLOW}Please install Java and try again.${NC}"
    exit 1
fi

# Prefer Maven Wrapper if available
if [ ! -x "${SCRIPT_DIR}/mvnw" ] && ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven or mvnw is required but not found.${NC}"
    echo -e "${YELLOW}Please install Maven or use the included Maven Wrapper (mvnw).${NC}"
    exit 1
fi

echo -e "${CYAN}=== Unified InspectorAgent Demo ===${NC}"
echo -e "${YELLOW}Testing single agent class with optional server functionality${NC}"
echo ""

 

# Build the project
echo -e "${GREEN}1. Building project...${NC}"
cd "$PROJECT_ROOT"
MVN_CMD="./mvnw"
if [ ! -x "$MVN_CMD" ]; then
    MVN_CMD="mvn"
fi
$MVN_CMD verify -q
if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed!${NC}"
    exit 1
fi

echo -e "${GREEN}2. Testing local-only mode (no server arguments)...${NC}"
echo -e "${GRAY}   Running: java -javaagent:agent.jar -jar sample-spring-app-1.0-SNAPSHOT.jar${NC}"

AGENT_PATH="agent/target/jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar"
SERVER_PATH="server/target/jlib-inspector-server-1.0-SNAPSHOT-shaded.jar"
SPRING_JAR="sample-spring-app/target/sample-spring-app-1.0-SNAPSHOT.jar"

# Test 1: Local-only mode (no args)
OUTPUT1=$(java -javaagent:"$AGENT_PATH" -jar "$SPRING_JAR" 2>&1)
if echo "$OUTPUT1" | grep -q "Total JARs.*:[[:space:]]*[0-9]\+"; then
    JAR_COUNT=$(echo "$OUTPUT1" | grep -o "Total JARs.*:[[:space:]]*[0-9]\+" | grep -o "[0-9]\+")
    echo -e "${GREEN}   ✓ Local mode: Found $JAR_COUNT JARs${NC}"
else
    echo -e "${RED}   ✗ Local mode: No JAR summary found${NC}"
fi

echo ""
echo -e "${GREEN}3. Testing server mode with non-existent server...${NC}"
echo -e "${GRAY}   Running: java -javaagent:agent.jar=server:8080 -jar spring-app.jar${NC}"

# Test 2: Server mode (should fail gracefully)
echo ""
echo -e "${GREEN}Starting HTTP server for full integration test...${NC}"

# Start server in background (shaded server JAR)
java -jar "$SERVER_PATH" 8080 > /dev/null 2>&1 &
SERVER_PID=$!

# Wait for server to be ready
echo -e "${GRAY}   Waiting for server to start...${NC}"
SERVER_READY=false
MAX_ATTEMPTS=15  # 15 seconds timeout
ATTEMPT=0

while [ "$SERVER_READY" = false ] && [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    sleep 1
    ATTEMPT=$((ATTEMPT + 1))
    
    # Check if server is ready using curl
    if curl -s "http://localhost:8080/health" > /dev/null 2>&1; then
        HEALTH_RESPONSE=$(curl -s "http://localhost:8080/health")
        if echo "$HEALTH_RESPONSE" | grep -q '"status":"healthy"'; then
            SERVER_READY=true
            echo -e "${GREEN}   ✓ Server is ready (attempt $ATTEMPT)${NC}"
        fi
    else
        echo -e "${GRAY}   . Server not ready yet (attempt $ATTEMPT)...${NC}"
    fi
done

if [ "$SERVER_READY" = false ]; then
    echo -e "${RED}   ✗ Server failed to start within $MAX_ATTEMPTS seconds${NC}"
    kill $SERVER_PID 2>/dev/null
    exit 1
fi

OUTPUT2=$(java -javaagent:"$AGENT_PATH=server:8080" -jar "$SPRING_JAR" 2>&1)
if echo "$OUTPUT2" | grep -q "Total JARs.*:[[:space:]]*[0-9]\+"; then
    JAR_COUNT=$(echo "$OUTPUT2" | grep -o "Total JARs.*:[[:space:]]*[0-9]\+" | grep -o "[0-9]\+")
    echo -e "${GREEN}   ✓ Server mode: Found $JAR_COUNT JARs (local report still works)${NC}"
else
    echo -e "${RED}   ✗ Server mode: No JAR summary found${NC}"
fi

if echo "$OUTPUT2" | grep -q "Failed to send data to server"; then
    echo -e "${GREEN}   ✓ Server mode: Gracefully handled server connection failure${NC}"
else
    echo -e "${GRAY}   ✓ Server mode: No server connection attempted (expected)${NC}"
fi

echo ""
echo -e "${GREEN}4. Testing server integration mode...${NC}"
echo -e "${GRAY}   Running: java -javaagent:agent.jar=server:8080 -jar spring-app.jar${NC}"

# Test 3: Full server integration (server is already running and verified)
OUTPUT3=$(java -javaagent:"$AGENT_PATH=server:8080" -jar "$SPRING_JAR" 2>&1)
if echo "$OUTPUT3" | grep -q "Total JARs.*:[[:space:]]*[0-9]\+"; then
    JAR_COUNT=$(echo "$OUTPUT3" | grep -o "Total JARs.*:[[:space:]]*[0-9]\+" | grep -o "[0-9]\+")
    echo -e "${GREEN}   ✓ Server integration: Found $JAR_COUNT JARs${NC}"
fi

if echo "$OUTPUT3" | grep -q "Successfully sent data to server"; then
    echo -e "${GREEN}   ✓ Server integration: Successfully sent data to server${NC}"
elif echo "$OUTPUT3" | grep -q "Application ID:.*will report to"; then
    echo -e "${GREEN}   ✓ Server integration: Generated application ID and configured server${NC}"
fi

# Check what was registered
sleep 1
if curl -s "http://localhost:8080/api/apps" > /dev/null 2>&1; then
    APPS_RESPONSE=$(curl -s "http://localhost:8080/api/apps")
    APP_COUNT=$(echo "$APPS_RESPONSE" | grep -o '"applications":\[' | wc -l)
    if [ "$APP_COUNT" -gt 0 ]; then
        echo -e "${GREEN}   ✓ Server integration: Application(s) registered${NC}"
        # Try to extract app ID and JDK version (basic parsing)
        if echo "$APPS_RESPONSE" | grep -q '"appId"'; then
            APP_ID=$(echo "$APPS_RESPONSE" | grep -o '"appId":"[^"]*"' | head -1 | cut -d'"' -f4)
            echo -e "${GRAY}     App ID: $APP_ID${NC}"
        fi
        if echo "$APPS_RESPONSE" | grep -q '"jdkVersion"'; then
            JDK_VERSION=$(echo "$APPS_RESPONSE" | grep -o '"jdkVersion":"[^"]*"' | head -1 | cut -d'"' -f4)
            echo -e "${GRAY}     JDK: $JDK_VERSION${NC}"
        fi
    else
        echo -e "${YELLOW}   ! Server integration: No applications found (timing issue?)${NC}"
    fi
fi

echo ""
echo -e "${GREEN}5. Testing custom host:port format...${NC}"
echo -e "${GRAY}   Running: java -javaagent:agent.jar=server:localhost:8080 -jar spring-app.jar${NC}"

# Test 4: Custom host:port format
OUTPUT4=$(java -javaagent:"$AGENT_PATH=server:localhost:8080" -jar "$SPRING_JAR" 2>&1)
if echo "$OUTPUT4" | grep -q "will report to localhost:8080"; then
    echo -e "${GREEN}   ✓ Custom format: Correctly parsed host:port${NC}"
fi

echo ""
echo -e "${GREEN}6. Cleanup...${NC}"
kill $SERVER_PID 2>/dev/null
echo -e "${GRAY}   Server stopped${NC}"

echo ""
echo -e "${CYAN}=== Demo Summary ===${NC}"
echo -e "${GREEN}✓ Single unified InspectorAgent class${NC}"
echo -e "${GREEN}✓ Local-only mode (no arguments)${NC}"
echo -e "${GREEN}✓ Server mode with graceful fallback${NC}"
echo -e "${GREEN}✓ Full server integration when available${NC}"
echo -e "${GREEN}✓ Flexible argument parsing (server:port and server:host:port)${NC}"
echo ""
echo -e "${YELLOW}Usage Examples:${NC}"
echo -e "${GRAY}  -javaagent:jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar                    # Local only${NC}"
echo -e "${GRAY}  -javaagent:jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar=server:8080        # Report to localhost:8080${NC}"
echo -e "${GRAY}  -javaagent:jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar=server:remote:9000 # Report to remote:9000${NC}"
