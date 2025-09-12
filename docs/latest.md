---
layout: default
title: Latest Release (1.0.0)
permalink: /latest.html
---

# JLib Inspector 1.0.0

The first stable release of JLib Inspector is now available.

## Artifacts

Download shaded JARs from the GitHub Releases page:

- Agent: `jlib-inspector-agent-1.0.0-shaded.jar`
- Server: `jlib-inspector-server-1.0.0-shaded.jar`

GitHub Releases: [v1.0.0](https://github.com/{{ site.repository }}/releases/tag/v1.0.0)

> Verify SHA256 checksums listed in the release before running in production.

## Frontend Container Image

A pre-built container image for the dashboard (UI + REST proxy + WebSocket) is published.

macOS / Windows (Docker Desktop) quick start (Linux differs; see below):

```bash
docker run \
  -e JLIB_SERVER_URL=http://host.docker.internal:8080 \
  -ti -p 3000:3000 -p 3001:3001 \
  brunoborges/jlib-frontend:1.0.0
```

Then open: <http://localhost:3000>

WebSocket default port: 3001 (exposed). Change via `PORT` / `WS_PORT` if needed.

## Running the Server (Java)

```bash
java -jar jlib-inspector-server-1.0.0-shaded.jar 8080
```

Health check:
```bash
curl -s http://localhost:8080/health
```

## Attaching the Agent to Your App

```bash
java -javaagent:/path/to/jlib-inspector-agent-1.0.0-shaded.jar=server:8080 -jar your-app.jar
```

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
docker run --rm --network host brunoborges/jlib-frontend:1.0.0
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
  brunoborges/jlib-frontend:1.0.0
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
| Connection refused (macOS) | Used `localhost` inside container | Use `host.docker.internal` |
| Slow shutdown w/ server reporting | Agent async send finishing | Accept or lower timeout (future config) |
| Multiple apps overwrite | Same computed App ID | Add distinguishing jars / params |

## Security Considerations

* Run server inside trusted network / behind firewall initially
* Add TLS (reverse proxy) for remote agents
* Inventory payload includes JAR metadata—avoid unencrypted transit on untrusted networks

## Roadmap Highlights After 1.0.0

* Incremental (periodic) reporting
* OpenTelemetry export
* SBOM correlation & drift diff
* Historical retention & comparison

## Feedback

Open issues or discussions: <https://github.com/{{ site.repository }}>

Happy inspecting!