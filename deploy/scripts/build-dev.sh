#!/bin/bash

# =====================================================
# Build Script for Development Environment (macOS/Linux)
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

# Get project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

info "🚀 Starting build process..."
info "Project root: $PROJECT_ROOT"

# Generate timestamp
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
info "Build version: $TIMESTAMP"

# Check Docker
if ! docker version &> /dev/null; then
    error "Docker is not running. Please start Docker and try again."
    exit 1
fi
success "Docker is running"

# Backend services to build
BACKEND_SERVICES=(
    "platform-common"
    "platform-security"
    "platform-cache"
    "platform-messaging"
    "admin-center"
    "user-portal"
    "developer-workstation"
    "workflow-engine-core"
    "api-gateway"
)

# Services that need Docker images (exclude library projects)
DOCKER_SERVICES=(
    "admin-center"
    "user-portal"
    "developer-workstation"
    "workflow-engine-core"
    "api-gateway"
)

# Build Maven projects
info "📦 Building Maven projects..."
for service in "${BACKEND_SERVICES[@]}"; do
    info "Building $service..."
    
    if [ -d "backend/$service" ]; then
        cd "backend/$service"
        
        # Clean and build
        mvn clean package -Dmaven.test.skip=true
        
        if [ $? -eq 0 ]; then
            success "✅ $service built successfully"
        else
            error "❌ Failed to build $service"
            exit 1
        fi
        
        cd "$PROJECT_ROOT"
    else
        warning "⚠️  Directory not found: backend/$service"
    fi
done

# Build Docker images
info "🐳 Building Docker images..."
VERSION_FILE="deploy/environments/dev/.image-versions"
echo "# Build time: $(date '+%Y-%m-%d %H:%M:%S')" > "$VERSION_FILE"
echo "# Version: $TIMESTAMP" >> "$VERSION_FILE"
echo "" >> "$VERSION_FILE"

for service in "${DOCKER_SERVICES[@]}"; do
    info "Building Docker image for $service..."
    
    IMAGE_TAG="dev-$service:$TIMESTAMP"
    
    cd "deploy/environments/dev"
    docker build -t "$IMAGE_TAG" "../../../backend/$service"
    
    if [ $? -eq 0 ]; then
        success "✅ Docker image built: $IMAGE_TAG"
        echo "$IMAGE_TAG" >> "../../../$VERSION_FILE"
    else
        error "❌ Failed to build Docker image for $service"
        exit 1
    fi
    
    cd "$PROJECT_ROOT"
done

success "✅ All services built successfully!"
info "Version file saved to: $VERSION_FILE"

# Show built images
info "🐳 Built Docker images:"
docker images | grep "^dev-" | grep "$TIMESTAMP"
