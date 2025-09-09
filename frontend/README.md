# JLib Inspector Dashboard

> **⚠️ EXPERIMENTAL - NOT PRODUCTION READY**  
> This software is in development and should only be used for development, testing, and evaluation purposes.

A simplified single Node.js application for monitoring Java applications and their JAR dependencies.

## Features

- **Real-time Monitoring**: Live updates via WebSocket connection
- **Application Overview**: View all monitored Java applications
- **JAR Dependencies**: Detailed view of JAR files and their loading status
- **Statistics Dashboard**: Summary statistics about applications and JARs
- **Responsive UI**: Clean, modern interface built with Tailwind CSS

## Getting Started

### Prerequisites

- Node.js 14.0.0 or higher
- JLib Server running on port 8080 (or custom port via environment variable)

### Installation

1. Install dependencies:
   ```bash
   npm install
   ```

2. Start the dashboard:
   ```bash
   npm start
   ```

   For development with auto-restart:
   ```bash
   npm run dev
   ```

3. Open your browser and navigate to:
   ```
   http://localhost:3000
   ```

### Configuration

The dashboard can be configured using environment variables:

- `PORT`: Dashboard server port (default: 3000)
- `JLIB_SERVER_URL`: JLib Server URL (default: http://localhost:8080)

Example:
```bash
PORT=8080 JLIB_SERVER_URL=http://localhost:9090 npm start
```

## Architecture

This is a simplified single-application architecture that combines:

- **Express.js Server**: Serves the web interface and provides API endpoints
- **WebSocket Server**: Provides real-time updates to connected clients
- **Static File Serving**: Serves the dashboard UI directly
- **JLib Server Integration**: Fetches data from the JLib Server every 10 seconds

### Endpoints

- `GET /`: Dashboard web interface
- `GET /api/dashboard`: Complete dashboard data (JSON)
- `GET /api/applications`: List of monitored applications (JSON)
- `GET /api/applications/:appId`: Specific application details (JSON)
- `GET /api/health`: Dashboard health status (JSON)
- `WebSocket ws://localhost:3001`: Real-time updates

## Technology Stack

- **Backend**: Node.js, Express.js, WebSocket
- **Frontend**: Vanilla JavaScript, Tailwind CSS, Lucide Icons
- **Data Fetching**: Axios, node-cron
- **Real-time Updates**: WebSocket

## Development

The application automatically:
- Fetches data from JLib Server every 10 seconds
- Broadcasts updates to all connected WebSocket clients
- Handles JLib Server disconnections gracefully
- Provides loading states and error handling

## Previous Architecture

This replaces the previous dual-application setup that had:
- Separate React client application
- Separate Node.js server application
- Complex build process and dependency management

The new simplified architecture provides the same functionality with:
- Single application to deploy and manage
- No build process required
- Reduced complexity and dependencies
- Better performance and reliability
