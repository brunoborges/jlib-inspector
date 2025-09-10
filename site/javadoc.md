---
layout: default
title: Javadoc - JLib Inspector
---

# API Documentation (Javadoc)

Complete API reference documentation for all JLib Inspector components.

## 📚 Generated Documentation

The Javadoc documentation is automatically generated from the source code and provides detailed API information for all modules.

### 🔗 Access Javadoc

**Online Documentation**: [View Complete Javadoc](javadoc/index.html)

### 📦 Module Documentation

#### Agent Module
- **Package**: `io.github.brunoborges.jlib.agent`
- **Main Classes**:
  - `InspectorAgent` - Java instrumentation agent entry point
  - `JarInventory` - Central inventory of observed JARs
  - `ClassLoaderTrackerTransformer` - Tracks class loading events
  - `JLibServerClient` - HTTP client for server communication

#### Server Module  
- **Package**: `io.github.brunoborges.jlib.server`
- **Main Classes**:
  - `JLibServer` - HTTP server implementation
  - `AppsHandler` - Application API endpoints
  - `ReportHandler` - Aggregated reporting endpoints
  - `HealthHandler` - Health check endpoint

#### Common Module
- **Package**: `io.github.brunoborges.jlib.common`
- **Shared Classes**:
  - `JarMetadata` - JAR file metadata representation
  - `ApplicationIdUtil` - Application identification utilities

## 🔧 Generating Documentation

### Local Generation

Generate Javadoc locally during development:

```bash
# Generate aggregated Javadoc for all modules
./mvnw javadoc:aggregate

# Generate for specific module
./mvnw -pl agent javadoc:javadoc

# Include in full site generation
./mvnw site
```

**Output Location**: `target/site/apidocs/`

### Site Integration

The Javadoc is integrated into the Maven site:

```bash
# Generate complete site with Javadoc
./mvnw site

# Generate site for specific module
./mvnw -pl server site
```

### Configuration

The Javadoc generation is configured in the root `pom.xml`:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-javadoc-plugin</artifactId>
  <configuration>
    <aggregate>true</aggregate>
    <source>${maven.compiler.release}</source>
    <failOnError>false</failOnError>
    <doclint>none</doclint>
  </configuration>
</plugin>
```

## 📖 Key API Classes

### Agent APIs

#### InspectorAgent
```java
/**
 * Java instrumentation agent for tracking JAR loading and usage.
 * 
 * Usage: -javaagent:jlib-inspector-agent.jar[=server:host:port]
 */
public class InspectorAgent {
    public static void premain(String args, Instrumentation inst)
}
```

#### JarInventory
```java
/**
 * Central inventory of all JARs observed by the agent.
 * Tracks metadata including load status, checksums, and timestamps.
 */
public final class JarInventory {
    public void addDeclared(String id, long size, HashSupplier hashSupplier)
    public void markLoaded(String id)
    public List<JarMetadata> snapshot()
}
```

### Server APIs

#### JLibServer
```java
/**
 * Lightweight HTTP server for collecting JAR usage data.
 * Provides REST endpoints for agent communication and data querying.
 */
public class JLibServer {
    public void start(int port) throws IOException
    public void stop()
}
```

#### Application Management
```java
/**
 * Service for managing application lifecycle and data.
 */
public class ApplicationService {
    public void registerOrUpdateApplication(String appId, String commandLine, List<JarMetadata> jars)
    public List<JavaApplication> getAllApplications()
    public JavaApplication getApplication(String appId)
}
```

### Common APIs

#### JarMetadata
```java
/**
 * Immutable metadata about a JAR file including size, checksum, and load status.
 */
public class JarMetadata {
    public String getFileName()
    public String getFullPath()
    public long getSize()
    public String getSha256Hash()
    public boolean isLoaded()
    public Instant getFirstSeen()
}
```

## 🔍 API Usage Examples

### Agent Integration

```java
// Custom instrumentation (advanced usage)
import io.github.brunoborges.jlib.agent.JarInventory;

JarInventory inventory = new JarInventory();
inventory.addDeclared("my-lib.jar", 1024, () -> "sha256-hash");
inventory.markLoaded("my-lib.jar");
```

### Server Client Usage

```java
// Direct server communication
import io.github.brunoborges.jlib.agent.JLibServerClient;

JLibServerClient client = new JLibServerClient("localhost", 8080);
client.sendApplicationData("my-app", inventory);
```

### REST API Client

```java
// Java HTTP client example
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("http://localhost:8080/api/apps"))
    .GET()
    .build();

HttpResponse<String> response = client.send(request, 
    HttpResponse.BodyHandlers.ofString());
```

## 📋 Documentation Standards

### Javadoc Guidelines

The project follows standard Javadoc conventions:

- **Class Documentation**: Purpose, usage examples, thread safety
- **Method Documentation**: Parameters, return values, exceptions
- **Package Documentation**: High-level overview and relationships
- **Since Tags**: Version information where applicable

### Code Examples

Most public APIs include usage examples:

```java
/**
 * Tracks JAR file metadata and loading status.
 * 
 * <p>Example usage:
 * <pre>{@code
 * JarInventory inventory = new JarInventory();
 * inventory.addDeclared("app.jar", 1024, fileHashSupplier(jarFile));
 * inventory.markLoaded("app.jar");
 * }</pre>
 * 
 * @since 1.0
 */
```

### Thread Safety Documentation

Concurrent behavior is clearly documented:

```java
/**
 * Thread-safe inventory of JAR files.
 * 
 * <p>This class is safe for concurrent access from multiple threads.
 * All mutating operations are atomic and do not require external 
 * synchronization.
 */
```

## 🔧 Development Integration

### IDE Setup

Most IDEs can generate and display Javadoc:

**IntelliJ IDEA**:
- View → Tool Windows → Documentation
- Ctrl+Q (Windows/Linux) or Cmd+Q (Mac) for quick documentation

**Eclipse**:
- Window → Show View → Javadoc
- F2 for focused documentation view

**VS Code**:
- Install "Java Extension Pack"
- Hover over classes/methods for documentation

### Build Integration

Include Javadoc in automated builds:

```yaml
# GitHub Actions example
- name: Generate Documentation
  run: ./mvnw javadoc:aggregate

- name: Deploy Documentation
  run: |
    cp -r target/site/apidocs/* site/javadoc/
    git add site/javadoc/
```

## 📦 Offline Documentation

### Download Documentation

Generate standalone documentation:

```bash
# Create offline documentation bundle
./mvnw javadoc:aggregate
tar -czf jlib-inspector-javadoc.tar.gz -C target/site apidocs/
```

### Local Viewing

View documentation without a web server:

```bash
# Open local file
open target/site/apidocs/index.html

# Or start simple HTTP server
cd target/site/apidocs && python3 -m http.server 8000
```

## 🔗 External References

### Java Documentation

- [Java Platform API](https://docs.oracle.com/en/java/javase/21/docs/api/)
- [Java Instrumentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.instrument/java/lang/instrument/package-summary.html)
- [HTTP Server](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/package-summary.html)

### Related Projects

- [Maven Javadoc Plugin](https://maven.apache.org/plugins/maven-javadoc-plugin/)
- [JaCoCo Coverage](https://jacoco.org/jacoco/trunk/doc/)

## 🔗 Related Documentation

- [Getting Started](getting-started.html) - Build and setup instructions
- [Agent Documentation](agent.html) - Agent usage and configuration
- [Server Documentation](server.html) - Server API and deployment