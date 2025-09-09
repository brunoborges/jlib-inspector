# JLib Inspector - Docker Guide

> **⚠️ EXPERIMENTAL - NOT PRODUCTION READY**  
> This software is in development and should only be used for development, testing, and evaluation purposes.

This guide explains how to build and run the JLib Inspector services using Docker and Docker Compose.

## Prerequisites

- Docker Desktop (or Docker Engine) 20.10+
- Docker Compose v2 (docker compose) or legacy docker-compose
- Internet access for base images and npm/Maven downloads

## Project containers

- Backend (JLib Server)
  - Image built from `docker/Dockerfile.backend`
  - Runs the shaded server JAR
  - Exposes port `8080`
  - Health check endpoint: `http://localhost:8080/health`
- Frontend (Dashboard)
  - Image built from `docker/Dockerfile.frontend`
  - Serves the React app via Express
  - Exposes port `3000` (web) and uses `3001` for WebSocket internally
  - Connects to backend via `http://jlib-server:8080`

## Quick start

From the repository root:

```bash
./docker/start-docker.sh
```

This script:
- Ensures it runs from the `docker/` folder
- Starts both backend and frontend with `docker compose up --build`
- Works with either `docker compose` or `docker-compose`

Then open:
- Frontend: http://localhost:3000
- Backend:  http://localhost:8080

Stop the services with Ctrl+C, or in another terminal:

```bash
cd docker
# If using compose v2
docker compose down
# If using legacy compose
# docker-compose down
```

## How the backend image is built

`docker/Dockerfile.backend` uses multi-module Maven build steps tailored to the current project structure:

- Copies Maven wrapper and required POM files
- Builds `common` and then `server` module to produce `server/target/jlib-inspector-server-1.0-SNAPSHOT.jar`
- Runs the server with:

```bash
java -jar server/target/jlib-inspector-server-1.0-SNAPSHOT.jar 8080
```

Notes:
- The backend builds only `common` and `server` modules to keep the image small.
- The server JAR is shaded, so it contains all runtime deps.

## How the frontend image is built

`docker/Dockerfile.frontend`:
- Installs npm dependencies
- Builds the React app with `npm run build:only`
- Starts Express with `npm start`

## Using the Java agent with containers

The Java agent is not required inside the backend or frontend containers. Use it when running your Java apps (in or out of containers):

```bash
java -javaagent:agent/target/jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar=server:8080 -jar sample-spring-app/target/sample-spring-app-1.0-SNAPSHOT.jar
```

If your app runs in a different container/network, point the agent to the backend’s reachable hostname and port.

## Troubleshooting

- Build fails in backend Dockerfile
  - Ensure the repo has all modules and the Maven wrapper
  - Check that `common/` and `server/` exist and build locally with `./mvnw -pl common,server -am -DskipTests package`
- "No configuration file provided" when starting
  - Use `./docker/start-docker.sh` from repo root (it changes into the `docker/` dir and uses the correct compose file)
- Frontend can’t reach backend
  - Check backend health: `curl http://localhost:8080/health`
  - Verify `JLIB_SERVER_URL` in frontend environment (defaults to `http://jlib-server:8080` inside the compose network)

## Clean rebuild

```bash
cd docker
docker compose down -v
docker compose build --no-cache
docker compose up
```
