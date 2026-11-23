---
layout: default
title: Latest Release
permalink: /latest.html
---

# JLib Inspector {{ site.latest_release }}

## What's New in 1.1.0

* **Periodic reporting.** Add `interval:<n>[s|m|h]` (for example `interval:30s`) and the agent sends its JAR inventory to the server on that schedule. Running apps now appear in the dashboard and stay current; without it, the agent still reports once, when the JVM exits.
* **Optional OpenTelemetry export.** Add `otel[:endpoint]` and the agent sends one `package.info` event per archive to your OTLP/HTTP collector, using the same attributes as the OpenTelemetry Java agent's experimental package emitter. It also includes JARs that were declared but never loaded, and JARs nested in fat JARs. With `interval`, only new archives and archives that became loaded are sent after the first export.
* **Fewer dependency clashes.** The agent's bundled `org.json` is now relocated, so it can't conflict with the `org.json` your application ships.

Full changelog: [v1.1.0 release notes](https://github.com/{{ site.repository }}/releases/tag/v1.1.0). Earlier versions: [all releases](https://github.com/{{ site.repository }}/releases).

## Artifacts

Download shaded JARs from the GitHub Releases page:

- Agent: `jlib-inspector-agent-{{ site.latest_release }}-shaded.jar`
- Server: `jlib-inspector-server-{{ site.latest_release }}-shaded.jar`

GitHub Releases: [v{{ site.latest_release }}](https://github.com/{{ site.repository }}/releases/tag/v{{ site.latest_release }})

> Verify SHA256 checksums listed in the release before running in production.

## Frontend Container Image

A pre-built container image for the dashboard (UI + REST proxy + WebSocket) is published.

macOS / Windows (Docker Desktop) quick start (Linux differs; see below):

```bash
docker run \
  -e JLIB_SERVER_URL=http://host.docker.internal:8080 \
  -ti -p 3000:3000 -p 3001:3001 \
  brunoborges/jlib-frontend:{{ site.latest_release }}
```

Then open: <http://localhost:3000>

WebSocket default port: 3001 (exposed). Change via `PORT` / `WS_PORT` if needed.

## Running the Server (Java)

```bash
java -jar jlib-inspector-server-{{ site.latest_release }}-shaded.jar 8080
```

Health check:
```bash
curl -s http://localhost:8080/health
```

## Attaching the Agent to Your App

```bash
java -javaagent:/path/to/jlib-inspector-agent-{{ site.latest_release }}-shaded.jar=server:8080,interval:30s -jar your-app.jar
```

`interval:30s` reports every 30 seconds while the app runs. Omit it to report once, at JVM exit.

Also export to an OpenTelemetry collector (OTLP/HTTP):
```bash
java -javaagent:/path/to/jlib-inspector-agent-{{ site.latest_release }}-shaded.jar=server:8080,otel:http://collector:4318,interval:5m -jar your-app.jar
```

Use `otel` with no endpoint to rely on `OTEL_EXPORTER_OTLP_ENDPOINT` (default `http://localhost:4318`). `OTEL_SERVICE_NAME`, `OTEL_RESOURCE_ATTRIBUTES` and `OTEL_EXPORTER_OTLP_HEADERS` are also honored.

Explicit host & port:
```bash
java -javaagent:/path/agent.jar=server:my-host.example.com:8080 -jar your-app.jar
```

Environment variable override (takes precedence over agent arg):
```bash
export JLIB_SERVER_URL="http://my-host.example.com:8080"
java -javaagent:/path/agent.jar -jar your-app.jar
```

## Docker Networking Notes (Linux vs macOS / Windows)

Host networking differs across platforms; this affects a containerized frontend reaching a server running on your host.

### Linux

True host networking is available:
```bash
docker run --rm --network host brunoborges/jlib-frontend:{{ site.latest_release }}
```
* Dashboard: <http://localhost:3000>
* Frontend reaches server at `http://localhost:8080`
* `-p` mappings unnecessary / ignored with `--network host`.

### macOS & Windows (Docker Desktop)

`--network host` is a partial emulation; container `localhost` != host.
Use the special DNS name:
```bash
JLIB_SERVER_URL=http://host.docker.internal:8080
```
Example:
```bash
docker run \
  -e JLIB_SERVER_URL=http://host.docker.internal:8080 \
  -p 3000:3000 -p 3001:3001 \
  brunoborges/jlib-frontend:{{ site.latest_release }}
```
Why:
* `host.docker.internal` resolves to the host OS
* Port publishing (`-p`) exposes the UI back to the host

### Verifying Connectivity

macOS / Windows container reaching host server:
```bash
docker run --rm alpine sh -c "apk add --no-cache curl >/dev/null && curl -v http://host.docker.internal:8080/health"
```
Linux host-network quick check:
```bash
docker run --rm --network host alpine curl -s http://localhost:8080/health
```

## Troubleshooting

| Symptom | Cause | Fix |
| ------- | ----- | --- |
| Frontend shows no apps | Agent not sending or server unreachable | Verify agent arg / env + server `/health` |
| App appears only after it exits | No `interval` set | Add `interval:30s` (or any `s`/`m`/`h` duration) |
| Connection refused (macOS) | Used `localhost` inside container | Use `host.docker.internal` |
| Slow shutdown w/ server reporting | Agent async send finishing | Accept or lower timeout (future config) |
| Multiple apps overwrite | Same computed App ID | Add distinguishing jars / params |

## Security Considerations

* Run server inside trusted network / behind firewall initially
* Add TLS (reverse proxy) for remote agents
* Inventory payload includes JAR metadata—avoid unencrypted transit on untrusted networks

## Roadmap Highlights After 1.1.0

* SBOM correlation & drift diff
* CycloneDX runtime delta export
* Historical retention & comparison

## Feedback

Open issues or discussions: <https://github.com/{{ site.repository }}>

Happy inspecting!