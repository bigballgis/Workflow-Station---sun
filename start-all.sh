#!/bin/bash

# Start All Services Script (macOS/Linux version)
# This script builds Docker images and starts all services using docker run

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[0;37m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color

# Parse command line arguments
BUILD=false
INFRA_ONLY=false
BACKEND_ONLY=false
FRONTEND_ONLY=false
NO_BUILD=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --build)
            BUILD=true
            shift
            ;;
        --infra-only)
            INFRA_ONLY=true
            shift
            ;;
        --backend-only)
            BACKEND_ONLY=true
            shift
            ;;
        --frontend-only)
            FRONTEND_ONLY=true
            shift
            ;;
        --no-build)
            NO_BUILD=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--build] [--infra-only] [--backend-only] [--frontend-only] [--no-build]"
            exit 1
            ;;
    esac
done

echo -e "${CYAN}========================================"
echo -e "  Workflow Platform - Start All Services"
echo -e "========================================${NC}"
echo ""

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running. Please start Docker first.${NC}"
    exit 1
fi

# Set environment variables
export POSTGRES_PASSWORD="platform123"
export REDIS_PASSWORD="redis123"
export JWT_SECRET_KEY="workflow-engine-jwt-secret-key-2026"
export ENCRYPTION_KEY="workflow-aes-256-encryption-key!"

# Network name
NETWORK_NAME="platform-network"

# Function to create network if it doesn't exist
ensure_network() {
    if ! docker network ls --filter "name=$NETWORK_NAME" --format "{{.Name}}" | grep -q "^$NETWORK_NAME$"; then
        echo -e "${YELLOW}Creating Docker network: $NETWORK_NAME...${NC}"
        docker network create $NETWORK_NAME
    else
        echo -e "${GRAY}Network $NETWORK_NAME already exists${NC}"
    fi
}

# Function to build Docker image
build_image() {
    local context=$1
    local dockerfile=$2
    local image_name=$3
    
    echo -e "${YELLOW}Building image: $image_name...${NC}"
    docker build -f "$dockerfile" -t "$image_name" "$context"
    echo -e "${GREEN}Image $image_name built successfully${NC}"
}

# Function to check if container exists
container_exists() {
    local container_name=$1
    docker ps -a --filter "name=$container_name" --format "{{.Names}}" | grep -q "^$container_name$"
}

# Function to remove container if exists
remove_container() {
    local container_name=$1
    if container_exists "$container_name"; then
        echo -e "${YELLOW}Removing existing container: $container_name...${NC}"
        docker rm -f "$container_name" >/dev/null
    fi
}

# Function to wait for service to be healthy
wait_for_service() {
    local container_name=$1
    local check_command=$2
    local max_retries=${3:-30}
    local retry_interval=${4:-2}
    
    echo -e "${GRAY}Waiting for $container_name to be ready...${NC}"
    local retry_count=0
    while [ $retry_count -lt $max_retries ]; do
        if docker exec "$container_name" $check_command >/dev/null 2>&1; then
            echo -e "${GREEN}$container_name is ready!${NC}"
            return 0
        fi
        retry_count=$((retry_count + 1))
        echo -e "${GRAY}Waiting for $container_name... ($retry_count/$max_retries)${NC}"
        sleep $retry_interval
    done
    echo -e "${RED}Error: $container_name failed to start${NC}"
    return 1
}

# Create network
ensure_network

if [ "$INFRA_ONLY" = true ]; then
    echo -e "${YELLOW}Starting infrastructure services only...${NC}"
    
    # Start PostgreSQL
    remove_container "platform-postgres"
    echo -e "${YELLOW}Starting PostgreSQL...${NC}"
    docker run -d \
        --name platform-postgres \
        --network $NETWORK_NAME \
        -e POSTGRES_DB=workflow_platform \
        -e POSTGRES_USER=platform \
        -e POSTGRES_PASSWORD=$POSTGRES_PASSWORD \
        -p 5432:5432 \
        -v postgres_data:/var/lib/postgresql/data \
        -v "$(pwd)/deploy/init-scripts/00-schema/01-schema.sql:/docker-entrypoint-initdb.d/00-01-schema.sql" \
        -v "$(pwd)/deploy/init-scripts/01-admin/01-admin-user.sql:/docker-entrypoint-initdb.d/01-01-admin-user.sql" \
        -v "$(pwd)/deploy/init-scripts/02-test-data/01-organization.sql:/docker-entrypoint-initdb.d/02-01-organization.sql" \
        -v "$(pwd)/deploy/init-scripts/02-test-data/02-organization-detail.sql:/docker-entrypoint-initdb.d/02-02-organization-detail.sql" \
        -v "$(pwd)/deploy/init-scripts/02-test-data/03-users.sql:/docker-entrypoint-initdb.d/02-03-users.sql" \
        -v "$(pwd)/deploy/init-scripts/02-test-data/04-role-assignments.sql:/docker-entrypoint-initdb.d/02-04-role-assignments.sql" \
        -v "$(pwd)/deploy/init-scripts/02-test-data/04-department-managers.sql:/docker-entrypoint-initdb.d/02-05-department-managers.sql" \
        -v "$(pwd)/deploy/init-scripts/02-test-data/05-virtual-groups.sql:/docker-entrypoint-initdb.d/02-06-virtual-groups.sql" \
        -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-01-function-unit.sql:/docker-entrypoint-initdb.d/04-01-function-unit.sql" \
        -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-02-tables.sql:/docker-entrypoint-initdb.d/04-02-tables.sql" \
        -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-03-fields-main-fixed.sql:/docker-entrypoint-initdb.d/04-03-fields-main-fixed.sql" \
        -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-04-fields-sub-fixed.sql:/docker-entrypoint-initdb.d/04-04-fields-sub-fixed.sql" \
        -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-05-fk-relations.sql:/docker-entrypoint-initdb.d/04-05-fk-relations.sql" \
        -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-06-forms-fixed.sql:/docker-entrypoint-initdb.d/04-06-forms-fixed.sql" \
        -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-07-actions-fixed.sql:/docker-entrypoint-initdb.d/04-07-actions-fixed.sql" \
        -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-08-process-fixed-v2-corrected.sql:/docker-entrypoint-initdb.d/04-08-process-fixed-v2-corrected.sql" \
        -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-09-form-bindings-fixed.sql:/docker-entrypoint-initdb.d/04-09-form-bindings-fixed.sql" \
        -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-10-form-configs.sql:/docker-entrypoint-initdb.d/04-10-form-configs.sql" \
        -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-11-action-configs.sql:/docker-entrypoint-initdb.d/04-11-action-configs.sql" \
        --restart unless-stopped \
        postgres:16.5-alpine
    
    # Start Redis
    remove_container "platform-redis"
    echo -e "${YELLOW}Starting Redis...${NC}"
    docker run -d \
        --name platform-redis \
        --network $NETWORK_NAME \
        -e REDIS_PASSWORD=$REDIS_PASSWORD \
        -p 6379:6379 \
        -v redis_data:/data \
        --restart unless-stopped \
        redis:7.2-alpine redis-server --appendonly yes --requirepass $REDIS_PASSWORD
    

    
    echo ""
    echo -e "${GREEN}Infrastructure services started!${NC}"
    echo -e "${WHITE}  - PostgreSQL: localhost:5432${NC}"
    echo -e "${WHITE}  - Redis: localhost:6379${NC}"
    exit 0
fi

# Step 1: Build platform modules (if needed)
if [ "$NO_BUILD" != true ]; then
    echo -e "${YELLOW}Step 1: Building platform modules...${NC}"
    echo -e "${GRAY}Running Maven build (this may take a few minutes)...${NC}"
    mvn clean install -DskipTests
    echo -e "${GREEN}Platform modules built successfully${NC}"
fi

# Step 2: Start infrastructure
echo ""
echo -e "${YELLOW}Step 2: Starting infrastructure services...${NC}"

# Start PostgreSQL
remove_container "platform-postgres"
echo -e "${YELLOW}Starting PostgreSQL...${NC}"
docker run -d \
    --name platform-postgres \
    --network $NETWORK_NAME \
    -e POSTGRES_DB=workflow_platform \
    -e POSTGRES_USER=platform \
    -e POSTGRES_PASSWORD=$POSTGRES_PASSWORD \
    -p 5432:5432 \
    -v postgres_data:/var/lib/postgresql/data \
    -v "$(pwd)/deploy/init-scripts/00-schema/01-schema.sql:/docker-entrypoint-initdb.d/00-01-schema.sql" \
    -v "$(pwd)/deploy/init-scripts/01-admin/01-admin-user.sql:/docker-entrypoint-initdb.d/01-01-admin-user.sql" \
    -v "$(pwd)/deploy/init-scripts/02-test-data/01-organization.sql:/docker-entrypoint-initdb.d/02-01-organization.sql" \
    -v "$(pwd)/deploy/init-scripts/02-test-data/02-organization-detail.sql:/docker-entrypoint-initdb.d/02-02-organization-detail.sql" \
    -v "$(pwd)/deploy/init-scripts/02-test-data/03-users.sql:/docker-entrypoint-initdb.d/02-03-users.sql" \
    -v "$(pwd)/deploy/init-scripts/02-test-data/04-role-assignments.sql:/docker-entrypoint-initdb.d/02-04-role-assignments.sql" \
    -v "$(pwd)/deploy/init-scripts/02-test-data/04-department-managers.sql:/docker-entrypoint-initdb.d/02-05-department-managers.sql" \
    -v "$(pwd)/deploy/init-scripts/02-test-data/05-virtual-groups.sql:/docker-entrypoint-initdb.d/02-06-virtual-groups.sql" \
    -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-01-function-unit.sql:/docker-entrypoint-initdb.d/04-01-function-unit.sql" \
    -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-02-tables.sql:/docker-entrypoint-initdb.d/04-02-tables.sql" \
    -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-03-fields-main-fixed.sql:/docker-entrypoint-initdb.d/04-03-fields-main-fixed.sql" \
    -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-04-fields-sub-fixed.sql:/docker-entrypoint-initdb.d/04-04-fields-sub-fixed.sql" \
    -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-05-fk-relations.sql:/docker-entrypoint-initdb.d/04-05-fk-relations.sql" \
    -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-06-forms-fixed.sql:/docker-entrypoint-initdb.d/04-06-forms-fixed.sql" \
    -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-07-actions-fixed.sql:/docker-entrypoint-initdb.d/04-07-actions-fixed.sql" \
    -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-08-process-fixed-v2-corrected.sql:/docker-entrypoint-initdb.d/04-08-process-fixed-v2-corrected.sql" \
    -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-09-form-bindings-fixed.sql:/docker-entrypoint-initdb.d/04-09-form-bindings-fixed.sql" \
    -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-10-form-configs.sql:/docker-entrypoint-initdb.d/04-10-form-configs.sql" \
    -v "$(pwd)/deploy/init-scripts/04-purchase-workflow/04-11-action-configs.sql:/docker-entrypoint-initdb.d/04-11-action-configs.sql" \
    --restart unless-stopped \
    postgres:16.5-alpine

# Start Redis
remove_container "platform-redis"
echo -e "${YELLOW}Starting Redis...${NC}"
docker run -d \
    --name platform-redis \
    --network $NETWORK_NAME \
    -p 6379:6379 \
    -v redis_data:/data \
    --restart unless-stopped \
    redis:7.2-alpine redis-server --appendonly yes --requirepass $REDIS_PASSWORD


# Wait for infrastructure
echo -e "${GRAY}Waiting for infrastructure to be ready...${NC}"
wait_for_service "platform-postgres" "pg_isready -U platform -d workflow_platform"
wait_for_service "platform-redis" "redis-cli -a $REDIS_PASSWORD ping"

# Step 3: Build backend services
if [ "$BACKEND_ONLY" = true ] || [ "$FRONTEND_ONLY" != true ]; then
    echo ""
    echo -e "${YELLOW}Step 3: Building backend services...${NC}"
    
    if [ "$NO_BUILD" != true ]; then
        echo -e "${GRAY}Building backend JAR files...${NC}"
        mvn clean package -DskipTests -pl backend/workflow-engine-core,backend/admin-center,backend/user-portal,backend/developer-workstation,backend/api-gateway -am
    fi
    
    # Build Docker images
    echo -e "${YELLOW}Building Docker images for backend services...${NC}"
    
    build_image "./backend/workflow-engine-core" "./backend/workflow-engine-core/Dockerfile" "workflow-engine:latest"
    build_image "./backend/admin-center" "./backend/admin-center/Dockerfile" "admin-center:latest"
    build_image "./backend/user-portal" "./backend/user-portal/Dockerfile" "user-portal:latest"
    build_image "./backend/developer-workstation" "./backend/developer-workstation/Dockerfile" "developer-workstation:latest"
    build_image "./backend/api-gateway" "./backend/api-gateway/Dockerfile" "api-gateway:latest"
    
    # Step 4: Start backend services
    echo ""
    echo -e "${YELLOW}Step 4: Starting backend services...${NC}"
    
    # Start Workflow Engine
    remove_container "platform-workflow-engine"
    echo -e "${YELLOW}Starting Workflow Engine...${NC}"
    docker run -d \
        --name platform-workflow-engine \
        --network $NETWORK_NAME \
        -e SERVER_PORT=8091 \
        -e SPRING_DATASOURCE_URL=jdbc:postgresql://platform-postgres:5432/workflow_platform?currentSchema=projectx \
        -e SPRING_DATASOURCE_USERNAME=platform \
        -e SPRING_DATASOURCE_PASSWORD=$POSTGRES_PASSWORD \
        -e SPRING_REDIS_HOST=platform-redis \
        -e SPRING_REDIS_PORT=6379 \
        -e SPRING_REDIS_PASSWORD=$REDIS_PASSWORD \
        -e ADMIN_CENTER_URL=http://platform-admin-center:8092/api/v1/admin \
        -e JWT_SECRET_KEY=$JWT_SECRET_KEY \
        -e ENCRYPTION_KEY=$ENCRYPTION_KEY \
        -p 8091:8091 \
        --restart unless-stopped \
        workflow-engine:latest
    
    # Start Admin Center
    remove_container "platform-admin-center"
    echo -e "${YELLOW}Starting Admin Center...${NC}"
    docker run -d \
        --name platform-admin-center \
        --network $NETWORK_NAME \
        -e SERVER_PORT=8092 \
        -e SPRING_DATASOURCE_URL=jdbc:postgresql://platform-postgres:5432/workflow_platform?currentSchema=projectx \
        -e SPRING_DATASOURCE_USERNAME=platform \
        -e SPRING_DATASOURCE_PASSWORD=$POSTGRES_PASSWORD \
        -e SPRING_REDIS_HOST=platform-redis \
        -e SPRING_REDIS_PORT=6379 \
        -e SPRING_REDIS_PASSWORD=$REDIS_PASSWORD \
        -e JWT_SECRET_KEY=$JWT_SECRET_KEY \
        -e ENCRYPTION_KEY=$ENCRYPTION_KEY \
        -p 8092:8092 \
        --restart unless-stopped \
        admin-center:latest
    
    # Start User Portal
    remove_container "platform-user-portal"
    echo -e "${YELLOW}Starting User Portal...${NC}"
    docker run -d \
        --name platform-user-portal \
        --network $NETWORK_NAME \
        -e SERVER_PORT=8093 \
        -e SPRING_DATASOURCE_URL=jdbc:postgresql://platform-postgres:5432/workflow_platform?currentSchema=projectx \
        -e SPRING_DATASOURCE_USERNAME=platform \
        -e SPRING_DATASOURCE_PASSWORD=$POSTGRES_PASSWORD \
        -e SPRING_REDIS_HOST=platform-redis \
        -e SPRING_REDIS_PORT=6379 \
        -e SPRING_REDIS_PASSWORD=$REDIS_PASSWORD \
        -e ADMIN_CENTER_URL=http://platform-admin-center:8092/api/v1/admin \
        -e WORKFLOW_ENGINE_URL=http://platform-workflow-engine:8091 \
        -e JWT_SECRET_KEY=$JWT_SECRET_KEY \
        -e ENCRYPTION_KEY=$ENCRYPTION_KEY \
        -p 8093:8093 \
        --restart unless-stopped \
        user-portal:latest
    
    # Start Developer Workstation
    remove_container "platform-developer-workstation"
    echo -e "${YELLOW}Starting Developer Workstation...${NC}"
    docker run -d \
        --name platform-developer-workstation \
        --network $NETWORK_NAME \
        -e SERVER_PORT=8094 \
        -e SPRING_DATASOURCE_URL=jdbc:postgresql://platform-postgres:5432/workflow_platform?currentSchema=projectx \
        -e SPRING_DATASOURCE_USERNAME=platform \
        -e SPRING_DATASOURCE_PASSWORD=$POSTGRES_PASSWORD \
        -e SPRING_REDIS_HOST=platform-redis \
        -e SPRING_REDIS_PORT=6379 \
        -e SPRING_REDIS_PASSWORD=$REDIS_PASSWORD \
        -e ADMIN_CENTER_URL=http://platform-admin-center:8092/api/v1/admin \
        -e JWT_SECRET_KEY=$JWT_SECRET_KEY \
        -e ENCRYPTION_KEY=$ENCRYPTION_KEY \
        -p 8094:8094 \
        --restart unless-stopped \
        developer-workstation:latest
    
    # Start API Gateway
    remove_container "platform-api-gateway"
    echo -e "${YELLOW}Starting API Gateway...${NC}"
    docker run -d \
        --name platform-api-gateway \
        --network $NETWORK_NAME \
        -e SERVER_PORT=8090 \
        -e SPRING_DATASOURCE_URL=jdbc:postgresql://platform-postgres:5432/workflow_platform?currentSchema=projectx \
        -e SPRING_DATASOURCE_USERNAME=platform \
        -e SPRING_DATASOURCE_PASSWORD=$POSTGRES_PASSWORD \
        -e SPRING_REDIS_HOST=platform-redis \
        -e SPRING_REDIS_PORT=6379 \
        -e SPRING_REDIS_PASSWORD=$REDIS_PASSWORD \
        -e WORKFLOW_ENGINE_URL=http://platform-workflow-engine:8091 \
        -e ADMIN_CENTER_URL=http://platform-admin-center:8092/api/v1/admin \
        -e USER_PORTAL_URL=http://platform-user-portal:8093/api/portal \
        -e DEVELOPER_WORKSTATION_URL=http://platform-developer-workstation:8094 \
        -e JWT_SECRET_KEY=$JWT_SECRET_KEY \
        -p 8090:8090 \
        --restart unless-stopped \
        api-gateway:latest
    
    echo -e "${GREEN}Backend services started!${NC}"
fi

# Step 5: Build and start frontend services
if [ "$FRONTEND_ONLY" = true ] || [ "$BACKEND_ONLY" != true ]; then
    echo ""
    echo -e "${YELLOW}Step 5: Building frontend services...${NC}"
    
    # Build Docker images
    build_image "./frontend/admin-center" "./frontend/admin-center/Dockerfile" "frontend-admin:latest"
    build_image "./frontend/user-portal" "./frontend/user-portal/Dockerfile" "frontend-portal:latest"
    build_image "./frontend/developer-workstation" "./frontend/developer-workstation/Dockerfile" "frontend-developer:latest"
    
    # Step 6: Start frontend services
    echo ""
    echo -e "${YELLOW}Step 6: Starting frontend services...${NC}"
    
    # Start Frontend Admin
    remove_container "platform-frontend-admin"
    echo -e "${YELLOW}Starting Frontend Admin...${NC}"
    docker run -d \
        --name platform-frontend-admin \
        --network $NETWORK_NAME \
        -p 3000:80 \
        --restart unless-stopped \
        frontend-admin:latest
    
    # Start Frontend Portal
    remove_container "platform-frontend-portal"
    echo -e "${YELLOW}Starting Frontend Portal...${NC}"
    docker run -d \
        --name platform-frontend-portal \
        --network $NETWORK_NAME \
        -p 3001:80 \
        --restart unless-stopped \
        frontend-portal:latest
    
    # Start Frontend Developer
    remove_container "platform-frontend-developer"
    echo -e "${YELLOW}Starting Frontend Developer...${NC}"
    docker run -d \
        --name platform-frontend-developer \
        --network $NETWORK_NAME \
        -p 3002:80 \
        --restart unless-stopped \
        frontend-developer:latest
    
    echo -e "${GREEN}Frontend services started!${NC}"
fi

echo ""
echo -e "${CYAN}========================================"
echo -e "  All Services Started Successfully!"
echo -e "========================================${NC}"
echo ""
echo -e "${YELLOW}Service URLs:${NC}"
echo -e "${WHITE}  Infrastructure:${NC}"
echo -e "${GRAY}    - PostgreSQL:     localhost:5432${NC}"
echo -e "${GRAY}    - Redis:          localhost:6379${NC}"
echo ""
echo -e "${WHITE}  Backend Services:${NC}"
echo -e "${GRAY}    - API Gateway:    http://localhost:8090${NC}"
echo -e "${GRAY}    - Workflow Engine: http://localhost:8091${NC}"
echo -e "${GRAY}    - Admin Center:   http://localhost:8092${NC}"
echo -e "${GRAY}    - User Portal:    http://localhost:8093${NC}"
echo -e "${GRAY}    - Developer WS:   http://localhost:8094${NC}"
echo ""
echo -e "${WHITE}  Frontend Applications:${NC}"
echo -e "${GRAY}    - Admin Center:   http://localhost:3000${NC}"
echo -e "${GRAY}    - User Portal:    http://localhost:3001${NC}"
echo -e "${GRAY}    - Developer WS:   http://localhost:3002${NC}"
echo ""
echo -e "${YELLOW}Commands:${NC}"
echo -e "${GRAY}  View logs:    docker logs -f [container-name]${NC}"
echo -e "${GRAY}  Stop all:     ./stop-all.sh${NC}"
echo -e "${GRAY}  List containers: docker ps${NC}"
echo ""