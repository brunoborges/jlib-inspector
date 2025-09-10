---
layout: default
title: Agent Documentation - JLib Inspector
---

# Agent Documentation

The JLib Inspector Agent is a Java instrumentation agent that tracks JAR loading and usage at runtime. It provides comprehensive visibility into what dependencies are actually being used by your Java applications.

## 🎯 Overview

The agent instruments Java applications to:
- Track which JAR files are loaded during runtime
- Monitor actual class usage vs. declared dependencies
- Capture both declared JARs (from classpath) and dynamically loaded JARs
- Report findings to the JLib Server for centralized monitoring
- Provide local reporting capabilities

## 🏗️ Architecture

The agent consists of several key components:

- **`InspectorAgent`** - Main entry point (`premain` method)
- **`ClassLoaderTrackerTransformer`** - Tracks class sources through bytecode transformation
- **`ClasspathJarTracker`** - Scans declared classpath and nested JARs
- **`JarInventory`** - Central inventory of observed JARs with metadata
- **`JLibServerClient`** - Optional reporting to the backend server

## 📦 Installation

### Download

Get the latest agent JAR from the build artifacts:
```bash
# After building the project
ls agent/target/jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar
```

### Basic Usage

Add the agent to any Java application using the `-javaagent` JVM parameter:

```bash
java -javaagent:path/to/jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar [AGENT_OPTIONS] -jar your-application.jar
```

## ⚙️ Configuration Options

### Local Mode (No Server Reporting)

Run the agent without server reporting - data is only available locally:

```bash
java -javaagent:jlib-inspector-agent.jar -jar your-app.jar
```

In this mode, the agent will:
- Track JAR loading and usage locally
- Print summary reports to standard output
- Not send data to any external server

### Server Reporting Mode

Configure the agent to report to a JLib Server:

#### Local Server (Default Port)
```bash
java -javaagent:jlib-inspector-agent.jar=server:8080 -jar your-app.jar
```

#### Remote Server
```bash
java -javaagent:jlib-inspector-agent.jar=server:remote-host:9000 -jar your-app.jar
```

#### Server Configuration Format
- `server:PORT` - Connect to localhost on specified port
- `server:HOST:PORT` - Connect to specific host and port

## 📊 Data Collection

### JAR Metadata Tracked

For each JAR file, the agent collects:

| Field | Description | Example |
|-------|-------------|---------|
| **File Name** | Simple JAR filename | `spring-boot-2.7.0.jar` |
| **Full Path** | Complete path including nested JARs | `/app/lib/spring-boot-2.7.0.jar!/BOOT-INF/lib/tomcat-embed-core-9.0.62.jar` |
| **Size** | File size in bytes | `1024000` |
| **SHA-256 Hash** | Cryptographic hash for identification | `a1b2c3d4...` |
| **Loaded Status** | Whether classes were actually loaded | `true`/`false` |
| **First Seen** | Timestamp when first discovered | `2024-01-15T10:30:00Z` |
| **Last Accessed** | Timestamp of most recent access | `2024-01-15T10:35:22Z` |

### Classpath Scanning

The agent performs comprehensive classpath analysis:

1. **Declared Dependencies** - Scans the initial classpath
2. **Nested JARs** - Recursively scans JAR-within-JAR structures
3. **Dynamic Loading** - Tracks JARs loaded through custom class loaders
4. **Runtime Detection** - Monitors actual class loading events

## 🔍 Use Cases

### Security Vulnerability Assessment

Quickly identify exposure to security vulnerabilities:

```bash
# Monitor production application
java -javaagent:jlib-inspector-agent.jar=server:monitoring-server:8080 \
     -jar production-app.jar

# Check which applications use vulnerable libraries
curl http://monitoring-server:8080/api/apps | jq '.[] | select(.jars | has("log4j-core"))'
```

### Dependency Optimization

Identify unused dependencies for application optimization:

```bash
# Run your application through typical usage scenarios
java -javaagent:jlib-inspector-agent.jar -jar your-app.jar

# Review the output to see which JARs were declared but never loaded
# Consider removing unused dependencies to reduce application size
```

### Development Insights

Understand runtime dependency patterns during development:

```bash
# Track dependency usage during development
java -javaagent:jlib-inspector-agent.jar=server:localhost:8080 \
     -jar development-app.jar

# View real-time data in the dashboard at http://localhost:3000
```

## 🛠️ Advanced Configuration

### JVM Arguments

The agent respects standard JVM debugging and logging options:

```bash
# Enable detailed logging
java -Djava.util.logging.level=INFO \
     -javaagent:jlib-inspector-agent.jar=server:8080 \
     -jar your-app.jar

# Disable specific agent features (if needed for troubleshooting)
java -Djlib.agent.disable=true \
     -javaagent:jlib-inspector-agent.jar \
     -jar your-app.jar
```

### Application Identification

The agent automatically generates unique application IDs based on:
- Command line arguments
- Working directory
- System properties
- JAR file checksums

This ensures each unique application configuration is tracked separately.

## 📈 Performance Impact

The agent is designed for minimal performance impact:

- **Startup**: < 100ms additional startup time
- **Runtime**: < 1% CPU overhead
- **Memory**: < 10MB additional heap usage
- **I/O**: Minimal - only during JAR scanning and periodic reporting

### Performance Tuning

For high-throughput applications:

```bash
# Reduce reporting frequency (if using server mode)
java -Djlib.report.interval=300000 \
     -javaagent:jlib-inspector-agent.jar=server:8080 \
     -jar high-throughput-app.jar
```

## 🔧 Troubleshooting

### Common Issues

#### Agent Not Loading
```
Error: Could not find or load main class io.github.brunoborges.jlib.agent.InspectorAgent
```
**Solution**: Verify the agent JAR path is correct and the file exists.

#### Server Connection Failed
```
WARNING: Failed to send data to server: Connection refused
```
**Solution**: 
- Check that the JLib Server is running
- Verify network connectivity and firewall settings
- Confirm the server host and port are correct

#### Missing JAR Metadata
```
WARNING: Could not compute hash for JAR: /path/to/file.jar
```
**Solution**: This is usually non-critical. The agent will continue with limited metadata.

### Debug Mode

Enable verbose logging for troubleshooting:

```bash
java -Djava.util.logging.level=FINE \
     -Djava.util.logging.ConsoleHandler.level=FINE \
     -javaagent:jlib-inspector-agent.jar=server:8080 \
     -jar your-app.jar
```

### Health Checks

Verify agent functionality:

1. **Local Mode**: Check console output for JAR inventory reports
2. **Server Mode**: Verify data appears in server `/api/apps` endpoint
3. **Dashboard**: Confirm application appears in web dashboard

## 🔗 Integration Examples

### Spring Boot Applications

```bash
# Standard Spring Boot with embedded Tomcat
java -javaagent:jlib-inspector-agent.jar=server:8080 \
     -jar spring-boot-app.jar

# With custom profiles
java -javaagent:jlib-inspector-agent.jar=server:8080 \
     -Dspring.profiles.active=production \
     -jar spring-boot-app.jar
```

### Application Servers

```bash
# Tomcat
export CATALINA_OPTS="-javaagent:/path/to/jlib-inspector-agent.jar=server:8080"
./catalina.sh run

# JBoss/WildFly
export JAVA_OPTS="-javaagent:/path/to/jlib-inspector-agent.jar=server:8080"
./standalone.sh
```

### Container Deployments

```dockerfile
# Dockerfile example
FROM openjdk:21-jre
COPY jlib-inspector-agent.jar /opt/agent/
COPY your-app.jar /opt/app/
CMD ["java", "-javaagent:/opt/agent/jlib-inspector-agent.jar=server:jlib-server:8080", "-jar", "/opt/app/your-app.jar"]
```

## 📚 API Reference

For detailed API documentation, see the [Javadoc](javadoc/index.html).

## 🤝 Contributing

To contribute to agent development:

1. [Build the project](getting-started.html#build-the-project)
2. Modify agent source in `agent/src/main/java/`
3. Run tests: `./mvnw test`
4. Test with sample applications

## 🔗 Related Documentation

- [Server Documentation](server.html) - JLib Server setup and APIs
- [Getting Started](getting-started.html) - Complete setup guide
- [Screenshots](screenshots.html) - Visual overview of capabilities