#!/bin/bash

echo "Starting JLib Inspector with Docker Compose..."
echo "This will build and start both the backend server and frontend dashboard."
echo ""

# Check if Docker and Docker Compose are available
if ! command -v docker &> /dev/null; then
    echo "Error: Docker is not installed or not in PATH"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "Error: Docker Compose is not installed or not in PATH"
    exit 1
fi

# Move to the directory containing this script (and docker-compose.yml)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Build and start services
echo "Building and starting services..."

if command -v docker-compose &> /dev/null; then
    if ! docker-compose -f docker-compose.yml up --build; then
            echo "Error: Failed to start services"
            exit 1
    fi
else
    if ! docker compose -f docker-compose.yml up --build; then
            echo "Error: Failed to start services"
            exit 1
    fi
fi

echo ""
echo "Services started successfully!"
echo "Frontend: http://localhost:3000"
echo "Backend API: http://localhost:8080"
echo ""
echo "To stop the services, press Ctrl+C or run: (cd docker && docker compose down)"
