# JLib Server API

Version: experimental (subject to change)
Base URL: `http://<host>:<port>` (default port 8080)
All responses are UTF-8 JSON unless otherwise noted. Errors generally return `text/plain` with an explanatory message.

## Conventions
- `appId`: Stable hash identifier for a JVM instance (derived from command line + JAR checksums + JDK info).
- Timestamps are ISO-8601 UTC strings (e.g. `2025-09-11T12:34:56.789Z`).
- Sizes are bytes (number).
- Absent / unknown values may be empty strings `""` or `"?"` (e.g. checksum when not yet computed).

## Endpoints

### 1. GET /health
Simple readiness / liveness check.

Response 200:
```json
{
  "status": "healthy",
  "applications": 3
}
```

### 2. GET /api/apps
List all currently known applications (lightweight summary – no per‑JAR details).

Response 200:
```json
{
  "applications": [
    {
      "appId": "c0ffee...",
      "name": "",              // Optional user‑assigned name (empty if unset)
      "commandLine": "java -jar sample.jar",
      "jdkVersion": "21.0.2",
      "jdkVendor": "Eclipse Adoptium",
      "firstSeen": "2025-09-11T12:30:10.123Z",
      "lastUpdated": "2025-09-11T12:33:42.901Z",
      "jarCount": 42
    }
  ]
}
```

Each application detail/listed jar now includes a `jarId` (see below) in detailed endpoints.

### 3. GET /api/apps/{appId}
Full application detail including all tracked JARs and manifest snippets.

Response 200:
```json
{
  "appId": "c0ffee...",
  "name": "My Service",          // May be empty string
  "description": "Optional text", // May be empty string
  "commandLine": "java -jar sample.jar --spring.profiles=dev",
  "jdkVersion": "21.0.2",
  "jdkVendor": "Eclipse Adoptium",
  "jdkPath": "/usr/lib/jvm/jdk-21",
  "firstSeen": "2025-09-11T12:30:10.123Z",
  "lastUpdated": "2025-09-11T12:33:42.901Z",
  "jarCount": 42,
  "tags": ["service", "demo"],
  "jars": [
    {
      "path": "file:/app/app.jar",
      "fileName": "app.jar",
      "size": 1234567,
      "checksum": "2f54..." ,      // "?" if unknown
  "jarId": "<derived-id>",
      "loaded": true,
      "lastAccessed": "2025-09-11T12:33:41.500Z",
  "_manifest": "(omitted – fetch via /api/jars/{jarId})"
    }
  ]
}
```

404 if `appId` not found.

### 4. GET /api/apps/{appId}/jars
Just the JAR list (subset of the full app detail) for targeted polling.

Manifest attributes are intentionally omitted here. Use `GET /api/jars/{jarId}` for manifest details.

Response 200:
```json
{
  "jars": [
    {
      "path": "file:/app/lib/dependency.jar",
      "fileName": "dependency.jar",
      "size": 20480,
      "checksum": "?",        // Not yet computed
  "jarId": "<derived-id>",
      "loaded": false,
      "lastAccessed": "2025-09-11T12:31:00.000Z",
    }
  ]
}
```

404 if `appId` not found.

### 5. PUT /api/apps/{appId}
Agent push: create or update an application plus (optionally) its JAR inventory.

Request body (JSON):
```json
{
  "commandLine": "java -jar app.jar",
  "jdkVersion": "21.0.2",
  "jdkVendor": "Eclipse Adoptium",
  "jdkPath": "/usr/lib/jvm/jdk-21",
  "jars": [
    {
      "path": "file:/app/app.jar",        // full (possibly nested) URL/path
      "fileName": "app.jar",              // convenience name
      "size": 1234567,
      "checksum": "2f54...",              // optional / may be "?"
      "loaded": true                       // optional; if omitted treated as false
    }
  ]
}
```
Notes:
- Required top-level fields: `commandLine`, `jdkVersion`, `jdkVendor`, `jdkPath`.
- Omitted or null required fields → 400.
- Each JAR entry may include an optional `manifest` in future (currently server derives and attaches asynchronously when available).

Responses:
- 200 "Application updated successfully" (plain text) on success.
- 400 for malformed / missing fields (plain text explanation).
- 500 on unexpected server errors.

### 6. PUT /api/apps/{appId}/metadata
Partial update for user-curated metadata only (does not touch jar list).

Request body:
```json
{
  "name": "Checkout Service",
  "description": "Handles cart + payment orchestration",
  "tags": ["checkout", "payments", "critical"]
}
```
Fields are optional; only provided ones are updated. An empty array for `tags` clears previous tags.

Response 200: returns the full application detail JSON (same as GET /api/apps/{appId}).

Errors:
- 404 if application absent.
- 400 if invalid JSON.

### 7. GET /api/dashboard
Composite snapshot for the UI (all apps including embedded JAR arrays + server status/time).

Response 200:
```json
{
  "applications": [ { /* same shape as GET /api/apps/{appId} (including jars array) */ } ],
  "lastUpdated": "2025-09-11T12:34:00.123Z",
  "serverStatus": "connected"
}
```

### 8. GET /report
Aggregated unique *loaded* JARs across all applications (grouped by checksum when available, else path).

Response 200:
```json
{
  "uniqueJars": [
    {
      "checksum": "2f54...",            // omitted if not known
      "size": 1234567,
      "fileName": "spring-core-6.1.0.jar", // representative name
      "firstSeen": "2025-09-11T12:30:10.000Z",
      "lastAccessed": "2025-09-11T12:33:41.000Z",
      "loadedCount": 5,                  // number of applications that loaded it
      "paths": ["file:/app/lib/spring-core-6.1.0.jar"],
      "fileNames": ["spring-core-6.1.0.jar"],
      "applications": [
        {
          "appId": "c0ffee...",
          "jdkVersion": "21.0.2",
          "jdkVendor": "Eclipse Adoptium",
          "jdkPath": "/usr/lib/jvm/jdk-21",
            "firstSeen": "2025-09-11T12:30:10.123Z",
            "lastUpdated": "2025-09-11T12:33:42.901Z",
            "jarPath": "file:/app/lib/spring-core-6.1.0.jar",
            "loaded": true,
            "lastAccessed": "2025-09-11T12:33:41.500Z"
        }
      ]
    }
  ]
}
```

### 9. GET /api/jars
Global deduplicated list of all known JARs aggregated across applications. Deduplication uses `jarId`.

Response 200:
```json
{
  "jars": [
    {
      "jarId": "a1b2c3...",
      "fileName": "spring-core-6.1.0.jar",
      "checksum": "2f54...",        // "?" if not yet known
      "size": 543210,
      "appCount": 3,                 // number of applications reporting this jar
      "loadedAppCount": 3            // apps where jar is marked loaded
    }
  ]
}
```

### 10. GET /api/jars/{jarId}
Detailed information for a specific JAR across all applications.

Response 200:
```json
{
  "jarId": "a1b2c3...",
  "fileName": "spring-core-6.1.0.jar",
  "checksum": "2f54...",
  "size": 543210,
  "firstSeen": "2025-09-11T12:30:10.000Z",
  "lastAccessed": "2025-09-11T12:33:41.000Z",
  "manifest": {                     // optional
    "Implementation-Title": "spring-core",
    "Implementation-Version": "6.1.0"
  },
  "applications": [
    {
      "appId": "c0ffee...",
      "loaded": true,
      "lastAccessed": "2025-09-11T12:33:41.500Z",
      "path": "file:/app/lib/spring-core-6.1.0.jar"
    }
  ]
}
```

404 if jar not found.

#### jarId Generation
`jarId = SHA256( checksum + ":" + fileName )` when a real checksum is available. If checksum is unknown (`?`), fallback:
`jarId = SHA256( fileName + ":" + size + ":" + fullPath )`. This may change after checksum computation; clients should refresh.

## Error Formats
Currently plain-text bodies with HTTP status codes (e.g., 404 Not found, 400 Invalid request data: <reason>). Future enhancement may formalize a JSON error envelope.

## Versioning & Stability
This API is experimental. Field names and shapes may evolve without notice until a 1.0 release. Consumers should code defensively (ignore unknown fields, allow missing optional fields).

## Security
No authentication / authorization is implemented (development use only). Do **not** expose the server to untrusted networks.

## CORS
Not currently enabled; intended for same-origin dashboard usage or reverse-proxy setups.

---
Generated automatically from current server source; regenerate after structural changes to handlers or JSON builders.
