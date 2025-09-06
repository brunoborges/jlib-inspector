# JLib Inspector - Docker Setup

This document explains how to run the JLib Inspector using Docker Compose.

## Prerequisites

- Docker
- Docker Compose

## Quick Start

1. **Start the services:**
   ```bash
   ./start-docker.sh
   ```
   
   Or manually:
   ```bash
   docker-compose up --build
   ```

2. **Access the application:**
   - Frontend Dashboard: http://localhost:3000
   - Backend API: http://localhost:8080

3. **Stop the services:**
   ```bash
   docker-compose down
   ```

## Services

### JLib Server (Backend)
- **Port:** 8080
- **Health Check:** http://localhost:8080/health
- **API Endpoints:** http://localhost:8080/api/apps

### Frontend Dashboard
- **Port:** 3000
- **Built with:** Node.js, Express, React
- **Connects to:** JLib Server at http://jlib-server:8080

## Docker Configuration

### Backend (Dockerfile.backend)
- Base image: OpenJDK 21
- Builds Maven project
- Exposes port 8080
- Includes health check with curl

### Frontend (Dockerfile.frontend)
- Base image: Node.js 18 Alpine
- Builds React application with Webpack
- Serves via Express on port 3000
- Configured to connect to backend service

### Networking
- Both services run on a custom bridge network (`jlib-network`)
- Frontend connects to backend using service name `jlib-server`
- Ports are bound to host: 3000 (frontend) and 8080 (backend)

## Environment Variables

### Backend
- `JAVA_OPTS`: JVM options (default: `-Xms512m -Xmx1g`)

### Frontend
- `PORT`: Frontend server port (default: 3000)
- `JLIB_SERVER_URL`: Backend URL (default: `http://jlib-server:8080`)

## Logs

View logs for specific services:
```bash
# Backend logs
docker-compose logs jlib-server

# Frontend logs
docker-compose logs jlib-frontend

# All logs
docker-compose logs
```

## Troubleshooting

### Backend not starting
- Check if port 8080 is already in use
- Verify Java 21 compatibility
- Check build logs: `docker-compose logs jlib-server`

### Frontend not connecting to backend
- Ensure backend is healthy: `curl http://localhost:8080/health`
- Check network connectivity between containers
- Verify environment variable `JLIB_SERVER_URL`

### Build issues
- Clean and rebuild: `docker-compose down && docker-compose up --build --force-recreate`
- Check available disk space
- Verify all source files are present

## Development

For development with auto-reload:
```bash
# Build images once
docker-compose build

# Start with logs
docker-compose up

# In another terminal, make changes to source code
# Rebuild specific service:
docker-compose build jlib-frontend
docker-compose up -d jlib-frontend
```
