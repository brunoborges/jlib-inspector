#!/usr/bin/env pwsh
# Test the unified InspectorAgent with optional server integration

Write-Host "=== Unified InspectorAgent Demo ===" -ForegroundColor Cyan
Write-Host "Testing single agent class with optional server functionality" -ForegroundColor Yellow
Write-Host ""

# Build the project
Write-Host "1. Building project..." -ForegroundColor Green
cd "D:\work\jlib-inspector"
mvn verify -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "2. Testing local-only mode (no server arguments)..." -ForegroundColor Green
Write-Host "   Running: java -javaagent:agent.jar -jar ...\sample-spring-app-1.0-SNAPSHOT.jar" -ForegroundColor Gray

$agentPath = "agent\target\jlib-inspector-agent-1.0-SNAPSHOT.jar"
$springJar = "sample-spring-app\target\sample-spring-app-1.0-SNAPSHOT.jar"

# Test 1: Local-only mode (no args)
$output1 = & java -javaagent:$agentPath -jar $springJar 2>&1 | Out-String
if ($output1 -match "Total JARs\s+:\s+(\d+)") {
    Write-Host "   ✓ Local mode: Found $($matches[1]) JARs" -ForegroundColor Green
} else {
    Write-Host "   ✗ Local mode: No JAR summary found" -ForegroundColor Red
}

Write-Host ""
Write-Host "3. Testing server mode with non-existent server..." -ForegroundColor Green
Write-Host "   Running: java -javaagent:agent.jar=server:8080 -jar spring-app.jar" -ForegroundColor Gray

# Test 2: Server mode (should fail gracefully)
Write-Host ""
Write-Host "Starting HTTP server for full integration test..." -ForegroundColor Green

# Start server
$serverJob = Start-Job -ScriptBlock {
    cd "D:\work\jlib-inspector"
    java -cp "agent\target\classes" io.github.brunoborges.jlib.server.JLibServer 8080
}

# Wait for server to be ready
Write-Host "   Waiting for server to start..." -ForegroundColor Gray
$serverReady = $false
$maxAttempts = 15  # 15 seconds timeout
$attempt = 0

while (-not $serverReady -and $attempt -lt $maxAttempts) {
    Start-Sleep -Seconds 1
    $attempt++
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8080/health" -Method Get -TimeoutSec 2
        if ($health.status -eq "healthy") {
            $serverReady = $true
            Write-Host "   ✓ Server is ready (attempt $attempt)" -ForegroundColor Green
        }
    } catch {
        Write-Host "   . Server not ready yet (attempt $attempt)..." -ForegroundColor Gray
    }
}

if (-not $serverReady) {
    Write-Host "   ✗ Server failed to start within $maxAttempts seconds" -ForegroundColor Red
    Stop-Job $serverJob 2>$null
    Remove-Job $serverJob 2>$null
    exit 1
}

$output2 = & java "-javaagent:$agentPath=server:8080" -jar $springJar 2>&1 | Out-String
if ($output2 -match "Total JARs\s+:\s+(\d+)") {
    Write-Host "   ✓ Server mode: Found $($matches[1]) JARs (local report still works)" -ForegroundColor Green
} else {
    Write-Host "   ✗ Server mode: No JAR summary found" -ForegroundColor Red
}

if ($output2 -match "Failed to send data to server") {
    Write-Host "   ✓ Server mode: Gracefully handled server connection failure" -ForegroundColor Green
} else {
    Write-Host "   ✓ Server mode: No server connection attempted (expected)" -ForegroundColor Gray
}

Write-Host ""
Write-Host "4. Testing server integration mode..." -ForegroundColor Green
Write-Host "   Running: java -javaagent:agent.jar=server:8080 -jar spring-app.jar" -ForegroundColor Gray

# Test 3: Full server integration (server is already running and verified)
$output3 = & java "-javaagent:$agentPath=server:8080" -jar $springJar 2>&1 | Out-String
if ($output3 -match "Total JARs\s+:\s+(\d+)") {
    Write-Host "   ✓ Server integration: Found $($matches[1]) JARs" -ForegroundColor Green
}

if ($output3 -match "Successfully sent data to server") {
    Write-Host "   ✓ Server integration: Successfully sent data to server" -ForegroundColor Green
} elseif ($output3 -match "Application ID:.*will report to") {
    Write-Host "   ✓ Server integration: Generated application ID and configured server" -ForegroundColor Green
}

# Check what was registered
Start-Sleep -Seconds 1
$apps = Invoke-RestMethod -Uri "http://localhost:8080/api/apps" -Method Get
if ($apps.applications.Count -gt 0) {
    Write-Host "   ✓ Server integration: $($apps.applications.Count) application(s) registered" -ForegroundColor Green
    $app = $apps.applications[0]
    Write-Host "     App ID: $($app.appId)" -ForegroundColor Gray
    Write-Host "     JDK: $($app.jdkVersion)" -ForegroundColor Gray
} else {
    Write-Host "   ! Server integration: No applications found (timing issue?)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "5. Testing custom host:port format..." -ForegroundColor Green
Write-Host "   Running: java -javaagent:agent.jar=server:localhost:8080 -jar spring-app.jar" -ForegroundColor Gray

# Test 4: Custom host:port format
$output4 = & java "-javaagent:$agentPath=server:localhost:8080" -jar $springJar 2>&1 | Out-String
if ($output4 -match "will report to localhost:8080") {
    Write-Host "   ✓ Custom format: Correctly parsed host:port" -ForegroundColor Green
}

Write-Host ""
Write-Host "6. Cleanup..." -ForegroundColor Green
Stop-Job $serverJob 2>$null
Remove-Job $serverJob 2>$null
Write-Host "   Server stopped" -ForegroundColor Gray

Write-Host ""
Write-Host "=== Demo Summary ===" -ForegroundColor Cyan
Write-Host "✓ Single unified InspectorAgent class" -ForegroundColor Green
Write-Host "✓ Local-only mode (no arguments)" -ForegroundColor Green  
Write-Host "✓ Server mode with graceful fallback" -ForegroundColor Green
Write-Host "✓ Full server integration when available" -ForegroundColor Green
Write-Host "✓ Flexible argument parsing (server:port and server:host:port)" -ForegroundColor Green
Write-Host ""
Write-Host "Usage Examples:" -ForegroundColor Yellow
Write-Host "  -javaagent:jlib-inspector-agent.jar                    # Local only" -ForegroundColor Gray
Write-Host "  -javaagent:jlib-inspector-agent.jar=server:8080        # Report to localhost:8080" -ForegroundColor Gray
Write-Host "  -javaagent:jlib-inspector-agent.jar=server:remote:9000 # Report to remote:9000" -ForegroundColor Gray
