---
layout: default
title: Getting Started - JLib Inspector
---

# Getting Started

This guide will help you build, run, and test JLib Inspector in your environment.

## 📋 Prerequisites

Before you begin, ensure you have the following installed:

- **Java 21+** (JDK) - Required for building and running
- **Maven 4+** - Use the included wrapper `./mvnw` 
- **Node.js 18+** and **npm** - For the web dashboard
- **PowerShell** (Windows) or equivalent shell

## 🚀 Quick Start

### 1. Build the Project

First, clone the repository and build all components:

```bash
# Clone the repository
git clone https://github.com/brunoborges/jlib-inspector.git
cd jlib-inspector

# Build all components (agent + server + sample app)
./mvnw clean package
```

This creates shaded JAR files:
- Agent: `agent/target/jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar`
- Server: `server/target/jlib-inspector-server-1.0-SNAPSHOT-shaded.jar`
- Sample App: `sample-spring-app/target/sample-spring-app-1.0-SNAPSHOT.jar`

### 2. Start the JLib Server

The JLib Server collects data from instrumented Java applications:

```bash
# Start the data collection server on port 8080 (shaded jar)
java -jar server/target/jlib-inspector-server-1.0-SNAPSHOT-shaded.jar 8080
```

**Expected Output:**
```
JLib HTTP Server started on port 8080
Available endpoints:
  PUT /api/apps/{appId} - Register/update application
  GET /api/apps - List all applications
  GET /api/apps/{appId} - Get application details
  GET /api/apps/{appId}/jars - List application JARs
  GET /health - Health check
```

### 3. Start the Web Dashboard

The unified Express.js server serves the React frontend:

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies (first time only)
npm install

# Build and start the unified server on port 3000
npm start
```

**Expected Output:**
```
> jlib-inspector-dashboard@2.0.0 start
> npm run build && node app.js

webpack 5.101.3 compiled successfully
Setting up data fetching schedule...
JLib Dashboard running on http://localhost:3000
WebSocket server running on port 3001
Connecting to JLib Server at http://localhost:8080
```

### 4. Run Java Applications with Monitoring

Instrument any Java application with the JLib Inspector agent:

**For your own applications:**
```bash
java "-javaagent:path/to/jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar=server:8080" -jar your-application.jar
```

**Test with the included sample application:**
```bash
# Run the sample Spring Boot application with monitoring
java "-javaagent:agent/target/jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar=server:8080" -jar sample-spring-app/target/sample-spring-app-1.0-SNAPSHOT.jar
```

### 5. View the Dashboard

Open your browser and navigate to:
- **Dashboard**: [http://localhost:3000](http://localhost:3000)
- **Server Health**: [http://localhost:8080/health](http://localhost:8080/health)

## 🐳 Docker Alternative

For containerized deployment:

```bash
# Start all services with Docker Compose
./docker/start-docker.sh
```

This provides:
- Backend container (JLib Server on port 8080)
- Frontend container (Dashboard on port 3000)

## 🛠️ Build Profiles

Use Maven profiles for specific builds:

```bash
# Build server only
./mvnw -P server package

# Build agent only  
./mvnw -P agent package

# Full build with tests
./mvnw clean verify
```

## 📊 Demo Scripts

For quick demonstrations:

```bash
# PowerShell (Windows)
./demo-jlib-inspector.ps1

# Bash (Linux/macOS)
./demo-jlib-inspector.sh
```

## 🔧 Configuration

### Agent Configuration

The agent accepts various parameters:

```bash
# Local mode only (no server reporting)
-javaagent:jlib-inspector-agent.jar

# Report to local server
-javaagent:jlib-inspector-agent.jar=server:8080

# Report to remote server
-javaagent:jlib-inspector-agent.jar=server:remote-host:9000
```

### Environment Variables

```bash
# JLib Server URL (default: http://localhost:8080)
export JLIB_SERVER_URL="http://localhost:8080"

# Dashboard port (default: 3000)
export PORT="3000"

# WebSocket port (default: 3001)
export WS_PORT="3001"
```

## 🧪 Testing

Run the test suite:

```bash
# Run all tests
./mvnw test

# Run tests with coverage
./mvnw verify

# View coverage reports
open target/site/jacoco/index.html
```

## 🔍 Troubleshooting

### Common Issues

**Build Failures:**
- Ensure Java 21+ is installed: `java -version`
- Use the Maven wrapper: `./mvnw` not `mvn`

**Server Connection Issues:**
- Check server health: `curl http://localhost:8080/health`
- Verify ports are not in use
- Check firewall settings

**Agent Not Reporting:**
- Verify server URL in agent configuration
- Check application logs for errors
- Ensure network connectivity

### Build Artifacts Missing

If shaded JARs are missing:
```bash
# Clean and rebuild
./mvnw clean package

# Verify artifacts exist
ls -la agent/target/*shaded.jar
ls -la server/target/*shaded.jar
```

## 📖 Next Steps

- Learn about [Agent usage](agent.html)
- Explore [Server configuration](server.html) 
- View [Screenshots](screenshots.html) of the dashboard
- Check the [Javadoc](javadoc/index.html) for API details

## 🤝 Need Help?

- [GitHub Issues](https://github.com/brunoborges/jlib-inspector/issues)
- [GitHub Discussions](https://github.com/brunoborges/jlib-inspector/discussions)
- [Source Code](https://github.com/brunoborges/jlib-inspector)