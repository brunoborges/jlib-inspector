# Copilot instructions – Build, test, and run

This repo has three main parts:
- Java modules: `common`, `server` (backend), `agent` (Java agent)
- Node.js app: `frontend` (dashboard)

Tooling and versions:
- Java 21+, Maven 3.9.11+ (use `./mvnw`), Node 18+
- Default ports: server 8080, dashboard 3000 (HTTP), 3001 (WS)

## Build

- Full build (root aggregator):
	- `./mvnw -q clean verify`

- Module builds:
	- Server only: `./mvnw -P server package`
	- Agent only: `./mvnw -P agent package`

Artifacts (shaded JARs):
- Agent: `agent/target/jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar`
- Server: `server/target/jlib-inspector-server-1.0-SNAPSHOT-shaded.jar`

Notes:
- Maven Enforcer requires Maven 3.9.11; prefer the wrapper (`./mvnw`).
- Java 21 is required (`maven.compiler.release=21`).
- JaCoCo is enabled; reports: `target/site/jacoco/` per module.

## Build - frontend

From `frontend/`:

```bash
npm install   # or: npm ci
npm run build # builds the frontend
```

## Run – backend (server)

Start the HTTP server (port optional, default used here is 8080):

```bash
java -jar server/target/jlib-inspector-server-1.0-SNAPSHOT-shaded.jar 8080
```

Health and APIs:
- `GET /health`
- `GET /api/apps`
- `PUT /api/apps/{appId}`

## Run – agent (attach to any Java app)

Use the shaded agent jar and point to the server:

```bash
java -javaagent:agent/target/jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar=server:8080 -jar your-app.jar
```

Sample app (included):

```bash
java -javaagent:agent/target/jlib-inspector-agent-1.0-SNAPSHOT-shaded.jar=server:8080 -jar sample-spring-app/target/sample-spring-app-1.0-SNAPSHOT.jar
```

## Run – frontend (dashboard)

From `frontend/`:

```bash
npm install   # or: npm ci
npm start     # builds and starts Express on port 3000
```

Environment:
- `JLIB_SERVER_URL` (default: `http://localhost:8080`)
- `PORT` (default: `3000`), `WS_PORT` (default: `3001`)

## Tests

- Java unit tests: `./mvnw test` (root or module)
- Frontend: current `test` script is a no-op

## Docker (optional)

Run both backend and frontend with Docker Compose from repo root:

```bash
./docker/start-docker.sh
```

Then:
- Frontend: http://localhost:3000
- Backend:  http://localhost:8080/health

Stop:

```bash
cd docker && docker compose down
```

## Codespaces

- Devcontainer installs Java 21, Maven, Node 18 and pre-builds the project.
- Useful tasks (Run Task): Build (Maven), Start Server, Start Frontend, Run Sample App (with agent).

## Troubleshooting

- “Command not found” Maven or version mismatch → use `./mvnw`.
- Java < 21 → install JDK 21; verify `java -version`.
- Shaded jar missing → ensure `package` phase completed and see target paths above.
- Frontend cannot reach backend → check `JLIB_SERVER_URL` and server health endpoint.

