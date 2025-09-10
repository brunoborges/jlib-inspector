---
layout: default
title: Server Documentation - JLib Inspector
---

# Server Documentation

The JLib Server is a lightweight HTTP backend that collects and serves JAR usage information from instrumented Java applications. It provides a REST API for data collection and retrieval, enabling centralized monitoring of Java application dependencies across your infrastructure.

## 🎯 Overview

The JLib Server acts as the central hub for dependency monitoring:

- **Data Collection**: Receives JAR inventory data from instrumented applications
- **Data Aggregation**: Consolidates information across multiple applications  
- **REST API**: Provides endpoints for querying application and dependency data
- **Health Monitoring**: Tracks application status and last-seen timestamps
- **Report Generation**: Creates aggregated views of JAR usage across applications

## 🏗️ Architecture

The server is built on Java's built-in HTTP server (`com.sun.net.httpserver`) and consists of:

- **`JLibServer`** - Main server class and entry point
- **HTTP Handlers** - Process different API endpoints
- **Application Service** - Manages application lifecycle and data
- **JSON Processing** - Handles data serialization/deserialization

## 📦 Installation and Setup

### Build the Server

```bash
# Build the server module
./mvnw -P server package

# Or build everything
./mvnw clean package
```

This produces the shaded JAR:
```
server/target/jlib-inspector-server-1.0-SNAPSHOT-shaded.jar
```

### Start the Server

```bash
# Start on default port 8080
java -jar server/target/jlib-inspector-server-1.0-SNAPSHOT-shaded.jar

# Start on custom port
java -jar server/target/jlib-inspector-server-1.0-SNAPSHOT-shaded.jar 9000

# With custom JVM options
java -Xms512m -Xmx1g -jar server/target/jlib-inspector-server-1.0-SNAPSHOT-shaded.jar 8080
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

## 🌐 REST API

### Health Check

Check if the server is running:

```bash
GET /health
```

**Response:**
```json
{
  "status": "OK",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Application Management

#### List All Applications

```bash
GET /api/apps
```

**Response:**
```json
[
  {
    "applicationId": "app-123",
    "commandLine": "java -jar myapp.jar",
    "firstSeen": "2024-01-15T10:00:00Z",
    "lastSeen": "2024-01-15T10:30:00Z",
    "jarCount": 42,
    "loadedJarCount": 35
  }
]
```

#### Get Application Details

```bash
GET /api/apps/{appId}
```

**Response:**
```json
{
  "applicationId": "app-123",
  "commandLine": "java -jar myapp.jar",
  "firstSeen": "2024-01-15T10:00:00Z",
  "lastSeen": "2024-01-15T10:30:00Z",
  "jars": {
    "spring-boot-starter-web-2.7.0.jar": {
      "fileName": "spring-boot-starter-web-2.7.0.jar",
      "fullPath": "/app/lib/spring-boot-starter-web-2.7.0.jar",
      "size": 1024000,
      "sha256Hash": "a1b2c3d4...",
      "loaded": true,
      "firstSeen": "2024-01-15T10:00:00Z",
      "lastAccessed": "2024-01-15T10:30:00Z"
    }
  }
}
```

#### Register/Update Application

```bash
PUT /api/apps/{appId}
Content-Type: application/json

{
  "commandLine": "java -jar myapp.jar",
  "jars": [
    {
      "fileName": "spring-boot-starter-web-2.7.0.jar",
      "fullPath": "/app/lib/spring-boot-starter-web-2.7.0.jar",
      "size": 1024000,
      "checksum": "a1b2c3d4...",
      "loaded": true
    }
  ]
}
```

#### Get Application JARs

```bash
GET /api/apps/{appId}/jars
```

**Response:**
```json
[
  {
    "fileName": "spring-boot-starter-web-2.7.0.jar",
    "fullPath": "/app/lib/spring-boot-starter-web-2.7.0.jar", 
    "size": 1024000,
    "sha256Hash": "a1b2c3d4...",
    "loaded": true,
    "firstSeen": "2024-01-15T10:00:00Z",
    "lastAccessed": "2024-01-15T10:30:00Z"
  }
]
```

### Aggregated Reporting

#### Get Cross-Application JAR Report

```bash
GET /report
```

Provides an aggregated view of unique JARs across all applications:

**Response:**
```json
[
  {
    "checksum": "a1b2c3d4...",
    "fileName": "spring-boot-starter-web-2.7.0.jar",
    "size": 1024000,
    "loadedCount": 3,
    "paths": [
      "/app1/lib/spring-boot-starter-web-2.7.0.jar",
      "/app2/lib/spring-boot-starter-web-2.7.0.jar"
    ],
    "applications": [
      {
        "applicationId": "app-123",
        "commandLine": "java -jar app1.jar"
      },
      {
        "applicationId": "app-456", 
        "commandLine": "java -jar app2.jar"
      }
    ],
    "firstSeen": "2024-01-15T09:00:00Z",
    "lastAccessed": "2024-01-15T10:30:00Z"
  }
]
```

## ⚙️ Configuration

### Command Line Options

```bash
# Basic usage
java -jar jlib-inspector-server.jar [PORT]

# Examples
java -jar jlib-inspector-server.jar          # Default port 8080
java -jar jlib-inspector-server.jar 9000     # Custom port 9000
```

### JVM Options

```bash
# Memory settings
java -Xms512m -Xmx2g -jar jlib-inspector-server.jar 8080

# Garbage collection
java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -jar jlib-inspector-server.jar 8080

# Logging
java -Djava.util.logging.level=INFO -jar jlib-inspector-server.jar 8080
```

### Environment Variables

The server respects standard Java system properties and can be configured via environment variables:

```bash
# Server port (alternative to command line)
export JLIB_SERVER_PORT=8080

# Log level
export JAVA_UTIL_LOGGING_LEVEL=INFO

# Memory settings
export JAVA_OPTS="-Xms512m -Xmx2g"
```

## 🔍 Data Storage

### In-Memory Storage

The server uses in-memory data structures for high performance:

- **Applications**: Stored in `ConcurrentHashMap` for thread safety
- **JAR Metadata**: Nested within application objects
- **Timestamps**: Automatic tracking of first-seen and last-accessed times

### Data Persistence

**Current**: No persistence - data is lost on server restart
**Future Enhancement**: Configurable persistence options (file, database)

### Memory Management

- **Automatic Cleanup**: Inactive applications can be configured to expire
- **Compression**: JSON data is compressed for network efficiency  
- **Limits**: Built-in protection against excessive memory usage

## 📊 Monitoring and Operations

### Health Monitoring

```bash
# Simple health check
curl http://localhost:8080/health

# Application count
curl http://localhost:8080/api/apps | jq length

# Memory usage (via JVM metrics)
curl http://localhost:8080/api/apps | jq '.[].jarCount | add'
```

### Logging

The server provides structured logging:

```
INFO: JLib HTTP Server started on port 8080
INFO: Application registered: app-123
INFO: Updated application: app-123 (35 JARs, 30 loaded)
WARNING: Large application update: app-456 (500+ JARs)
```

### Performance Metrics

Monitor key performance indicators:

- **Request Rate**: HTTP requests per second
- **Response Time**: API endpoint latency
- **Memory Usage**: Heap and non-heap memory consumption
- **Application Count**: Number of registered applications
- **JAR Count**: Total unique JARs tracked

## 🚀 Production Deployment

### Systemd Service (Linux)

```ini
[Unit]
Description=JLib Inspector Server
After=network.target

[Service]
Type=simple
User=jlib
ExecStart=/usr/bin/java -jar /opt/jlib/jlib-inspector-server.jar 8080
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

### Docker Deployment

```dockerfile
FROM openjdk:21-jre-slim
COPY jlib-inspector-server.jar /app/
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1
CMD ["java", "-jar", "/app/jlib-inspector-server.jar", "8080"]
```

### Load Balancing

For high availability, run multiple server instances:

```bash
# Instance 1
java -jar jlib-inspector-server.jar 8080

# Instance 2  
java -jar jlib-inspector-server.jar 8081

# Use a load balancer (nginx, HAProxy) to distribute traffic
```

### Backup and Recovery

Since data is in-memory:

```bash
# Backup current state
curl http://localhost:8080/api/apps > backup-$(date +%Y%m%d).json

# Monitor for data continuity
watch -n 30 'curl -s http://localhost:8080/api/apps | jq length'
```

## 🔧 Troubleshooting

### Common Issues

#### Port Already in Use
```
java.net.BindException: Address already in use
```
**Solutions:**
- Check what's using the port: `netstat -an | grep 8080`
- Use a different port: `java -jar server.jar 8081`
- Kill conflicting process

#### Out of Memory
```
java.lang.OutOfMemoryError: Java heap space
```
**Solutions:**
- Increase heap size: `-Xmx4g`
- Monitor application count and JAR volume
- Consider data cleanup policies

#### High CPU Usage
**Symptoms**: Server becomes slow or unresponsive
**Solutions:**
- Enable GC logging: `-XX:+PrintGC`
- Use a more efficient GC: `-XX:+UseG1GC`
- Monitor request patterns for abuse

### Debug Mode

Enable detailed logging:

```bash
java -Djava.util.logging.level=FINE \
     -Djava.util.logging.ConsoleHandler.level=FINE \
     -jar jlib-inspector-server.jar 8080
```

### Connection Testing

Test server connectivity:

```bash
# Basic connectivity
telnet localhost 8080

# HTTP health check
curl -v http://localhost:8080/health

# API functionality
curl -H "Content-Type: application/json" \
     -X PUT \
     -d '{"commandLine":"test","jars":[]}' \
     http://localhost:8080/api/apps/test-app
```

## 🔗 Integration Examples

### CI/CD Pipeline Integration

```bash
# Start server in CI
java -jar jlib-inspector-server.jar 8080 &
SERVER_PID=$!

# Run tests with monitoring
java -javaagent:jlib-inspector-agent.jar=server:8080 -jar test-app.jar

# Collect results
curl http://localhost:8080/api/apps > dependency-report.json

# Cleanup
kill $SERVER_PID
```

### Monitoring Integration

```bash
# Prometheus metrics (custom implementation)
curl http://localhost:8080/api/apps | jq '
  {
    "applications_total": length,
    "jars_total": [.[].jarCount] | add,
    "loaded_jars_total": [.[].loadedJarCount] | add
  }'
```

### Security Scanning Integration

```bash
# Export dependency data for security tools
curl http://localhost:8080/report | jq '.[] | {checksum, fileName, applications: [.applications[].applicationId]}' > security-scan-input.json
```

## 📚 API Reference

For complete API documentation including request/response schemas, see the [Javadoc](javadoc/index.html).

## 🔗 Related Documentation

- [Agent Documentation](agent.html) - Configure agents to report to this server
- [Getting Started](getting-started.html) - Complete setup guide  
- [Screenshots](screenshots.html) - Visual overview of the web dashboard