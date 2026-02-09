#!/bin/bash

# =====================================================
# Start Development Environment (macOS/Linux)
# =====================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Functions
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Get project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

echo -e "${CYAN}========================================"
echo -e "  Workflow Platform - DEV Start"
echo -e "========================================${NC}"
echo ""

# Check Docker
info "Checking Docker status..."
if ! docker version &> /dev/null; then
    error "Docker is not running. Please start Docker and try again."
    exit 1
fi
success "Docker is running"

# Environment files
ENV_DIR="deploy/environments/dev"
ENV_FILE="$ENV_DIR/.env"
COMPOSE_FILE="$ENV_DIR/docker-compose.dev.yml"
VERSION_FILE="$ENV_DIR/.image-versions"

# Check if version file exists
if [ ! -f "$VERSION_FILE" ]; then
    warning "Image version file not found: $VERSION_FILE"
    warning "Please run ./deploy/scripts/build-dev.sh first to build images"
    exit 1
fi

# Load image versions and create env file
info "Loading image versions..."
TEMP_ENV_FILE="$ENV_DIR/.image-versions.env"
> "$TEMP_ENV_FILE"

while IFS= read -r line; do
    # Skip comments and empty lines
    if [[ "$line" =~ ^#.*$ ]] || [[ -z "$line" ]]; then
        continue
    fi
    
    # Parse image name: dev-service-name:timestamp
    if [[ "$line" =~ ^dev-(.+):([0-9]+-[0-9]+)$ ]]; then
        service_name="${BASH_REMATCH[1]}"
        
        # Convert service name to env var format
        env_var_name=$(echo "$service_name" | tr '[:lower:]' '[:upper:]' | tr '-' '_')
        
        # Special mapping for workflow-engine-core
        if [ "$env_var_name" = "WORKFLOW_ENGINE_CORE" ]; then
            env_var_name="WORKFLOW_ENGINE"
        fi
        
        echo "IMAGE_TAG_$env_var_name=$line" >> "$TEMP_ENV_FILE"
        info "  Set IMAGE_TAG_$env_var_name=$line"
    fi
done < "$VERSION_FILE"

# Start infrastructure services first
info "Starting infrastructure services (postgres, redis, zookeeper, kafka)..."
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d postgres redis zookeeper kafka

# Wait for infrastructure to be healthy
info "Waiting for infrastructure services to be ready..."
MAX_WAIT=120
ELAPSED=0
INTERVAL=5

while [ $ELAPSED -lt $MAX_WAIT ]; do
    sleep $INTERVAL
    ELAPSED=$((ELAPSED + INTERVAL))
    
    POSTGRES_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' platform-postgres-dev 2>/dev/null || echo "unknown")
    REDIS_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' platform-redis-dev 2>/dev/null || echo "unknown")
    
    info "Infrastructure status - Postgres: $POSTGRES_HEALTH, Redis: $REDIS_HEALTH"
    
    if [ "$POSTGRES_HEALTH" = "healthy" ] && [ "$REDIS_HEALTH" = "healthy" ]; then
        success "Infrastructure services are ready"
        break
    fi
    
    if [ $ELAPSED -ge $MAX_WAIT ]; then
        error "Infrastructure services failed to start within $MAX_WAIT seconds"
        exit 1
    fi
done

# Start backend services
info "Starting backend services..."
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --env-file "$TEMP_ENV_FILE" up -d \
    workflow-engine admin-center user-portal developer-workstation api-gateway

# Wait for backend services
info "Waiting for backend services to start..."
sleep 30

# Start frontend services (if images exist)
info "Starting frontend services..."
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d \
    admin-center-frontend user-portal-frontend developer-workstation-frontend 2>/dev/null || \
    warning "Frontend services not available (images may not be built)"

# Show service status
info "Checking service status..."
docker compose -f "$COMPOSE_FILE" ps

echo ""
echo -e "${GREEN}========================================"
echo -e "  Development Environment Started!"
echo -e "========================================${NC}"
echo ""

# Load and display URLs
source "$ENV_FILE"

echo -e "${YELLOW}Backend Services:${NC}"
echo "  API Gateway:           http://localhost:${API_GATEWAY_PORT}"
echo "  Workflow Engine:       http://localhost:${WORKFLOW_ENGINE_PORT}"
echo "  Admin Center:          http://localhost:${ADMIN_CENTER_PORT}"
echo "  User Portal:           http://localhost:${USER_PORTAL_PORT}"
echo "  Developer Workstation: http://localhost:${DEVELOPER_WORKSTATION_PORT}"
echo ""

echo -e "${YELLOW}Frontend Applications:${NC}"
echo "  Admin Center:          http://localhost:${ADMIN_CENTER_FRONTEND_PORT:-3001}"
echo "  User Portal:           http://localhost:${USER_PORTAL_FRONTEND_PORT:-3002}"
echo "  Developer Workstation: http://localhost:${DEVELOPER_WORKSTATION_FRONTEND_PORT:-3003}"
echo ""

echo -e "${YELLOW}Infrastructure Services:${NC}"
echo "  PostgreSQL:            localhost:${POSTGRES_PORT} (${POSTGRES_USER}/${POSTGRES_PASSWORD})"
echo "  Redis:                 localhost:${REDIS_PORT} (password: ${REDIS_PASSWORD})"
echo "  Kafka:                 localhost:9092"
echo ""

echo -e "${CYAN}Useful Commands:${NC}"
echo "  View logs:             docker compose -f $COMPOSE_FILE logs -f [service-name]"
echo "  Stop services:         ./deploy/scripts/stop-dev.sh"
echo "  Service status:        docker compose -f $COMPOSE_FILE ps"
echo ""

success "Development environment is ready for use!"
