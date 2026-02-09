#!/bin/bash

# =====================================================
# Stop Development Environment (macOS/Linux)
# =====================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Get project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

# Environment files
ENV_DIR="deploy/environments/dev"
COMPOSE_FILE="$ENV_DIR/docker-compose.dev.yml"

info "Stopping development environment..."

# Stop all services
docker compose -f "$COMPOSE_FILE" down

if [ $? -eq 0 ]; then
    success "✅ Development environment stopped successfully"
else
    error "❌ Failed to stop development environment"
    exit 1
fi

# Show remaining containers
info "Remaining platform containers:"
docker ps -a | grep "platform-" || echo "No platform containers running"
