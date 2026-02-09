#!/bin/bash

# =====================================================
# Rebuild and Restart a Single Service (macOS/Linux)
# =====================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Check arguments
if [ $# -eq 0 ]; then
    error "Usage: $0 <service-name>"
    echo ""
    echo "Available services:"
    echo "  - admin-center"
    echo "  - user-portal"
    echo "  - developer-workstation"
    echo "  - workflow-engine-core"
    echo "  - api-gateway"
    echo ""
    echo "Example: $0 admin-center"
    exit 1
fi

SERVICE_NAME=$1

# Get project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

info "Rebuilding service: $SERVICE_NAME"

# Check if service exists
if [ ! -d "backend/$SERVICE_NAME" ]; then
    error "Service directory not found: backend/$SERVICE_NAME"
    exit 1
fi

# Generate timestamp
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")

# Step 1: Build Maven project
info "Building Maven project..."
cd "backend/$SERVICE_NAME"
mvn clean package -Dmaven.test.skip=true

if [ $? -ne 0 ]; then
    error "Maven build failed"
    exit 1
fi
success "Maven build completed"

cd "$PROJECT_ROOT"

# Step 2: Stop the running container
info "Stopping container..."
CONTAINER_NAME="platform-${SERVICE_NAME}-dev"
docker stop "$CONTAINER_NAME" 2>/dev/null || true
docker rm "$CONTAINER_NAME" 2>/dev/null || true

# Step 3: Build Docker image
info "Building Docker image..."
cd "deploy/environments/dev"
IMAGE_TAG="dev-$SERVICE_NAME:$TIMESTAMP"
docker build -t "$IMAGE_TAG" "../../../backend/$SERVICE_NAME"

if [ $? -ne 0 ]; then
    error "Docker build failed"
    exit 1
fi
success "Docker image built: $IMAGE_TAG"

cd "$PROJECT_ROOT"

# Step 4: Update version file
VERSION_FILE="deploy/environments/dev/.image-versions"
if [ -f "$VERSION_FILE" ]; then
    # Remove old version of this service
    sed -i.bak "/^dev-$SERVICE_NAME:/d" "$VERSION_FILE"
    rm -f "${VERSION_FILE}.bak"
fi

# Add new version
echo "$IMAGE_TAG" >> "$VERSION_FILE"
info "Updated version file"

# Step 5: Update temp env file
TEMP_ENV_FILE="deploy/environments/dev/.image-versions.env"
if [ -f "$TEMP_ENV_FILE" ]; then
    # Remove old version of this service
    sed -i.bak "/IMAGE_TAG.*$SERVICE_NAME/d" "$TEMP_ENV_FILE"
    rm -f "${TEMP_ENV_FILE}.bak"
fi

# Convert service name to env var format
ENV_VAR_NAME=$(echo "$SERVICE_NAME" | tr '[:lower:]' '[:upper:]' | tr '-' '_')
if [ "$ENV_VAR_NAME" = "WORKFLOW_ENGINE_CORE" ]; then
    ENV_VAR_NAME="WORKFLOW_ENGINE"
fi

echo "IMAGE_TAG_$ENV_VAR_NAME=$IMAGE_TAG" >> "$TEMP_ENV_FILE"
info "Updated environment file"

# Step 6: Start the service
info "Starting service..."
ENV_DIR="deploy/environments/dev"
ENV_FILE="$ENV_DIR/.env"
COMPOSE_FILE="$ENV_DIR/docker-compose.dev.yml"

# Map service name to docker-compose service name
COMPOSE_SERVICE_NAME=$(echo "$SERVICE_NAME" | sed 's/workflow-engine-core/workflow-engine/')

docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --env-file "$TEMP_ENV_FILE" up -d "$COMPOSE_SERVICE_NAME"

if [ $? -eq 0 ]; then
    success "Service started successfully"
    
    # Show logs
    info "Showing recent logs (Ctrl+C to exit)..."
    sleep 2
    docker logs -f --tail=50 "$CONTAINER_NAME"
else
    error "Failed to start service"
    exit 1
fi
