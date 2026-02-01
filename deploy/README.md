# Multi-Environment Deployment Guide

This directory contains the complete multi-environment deployment configuration for the Workflow Platform.

## Directory Structure

```
deploy/
├── environments/           # Environment-specific configurations
│   ├── dev/               # Development environment
│   │   ├── .env           # Environment variables
│   │   └── docker-compose.dev.yml
│   ├── sit/               # System Integration Testing
│   │   ├── .env
│   │   └── docker-compose.sit.yml
│   ├── uat/               # User Acceptance Testing
│   │   ├── .env
│   │   └── docker-compose.uat.yml
│   └── prod/              # Production environment
│       ├── .env
│       └── docker-compose.prod.yml
├── scripts/               # Deployment automation scripts
│   ├── build.ps1          # Multi-environment build script
│   ├── deploy.ps1         # Multi-environment deployment script
│   ├── build-frontend.ps1 # Frontend-specific build script
│   ├── test-deployment.ps1 # Deployment testing script
│   ├── dev-quick-start.ps1 # Quick development setup
│   └── extract-env-vars.ps1 # Environment variable extraction
└── init-scripts/          # Database initialization scripts
    ├── 00-schema/         # Database schema creation
    ├── 01-admin/          # Admin user setup
    ├── 02-test-data/      # Test data insertion
    └── 04-purchase-workflow/ # Purchase workflow setup
```

## Environments

### DEV (Development)
- **Purpose**: Local development and testing
- **Database**: PostgreSQL on port 5432
- **Services**: All backend and frontend services
- **Access**: http://localhost:8080 (API Gateway)

### SIT (System Integration Testing)
- **Purpose**: Integration testing between components
- **Database**: Separate PostgreSQL instance
- **Services**: All services with integration test data
- **Access**: Configured ports in SIT environment

### UAT (User Acceptance Testing)
- **Purpose**: User acceptance and business validation
- **Database**: Production-like data structure
- **Services**: All services with UAT-specific configuration
- **Access**: Configured ports in UAT environment

### PROD (Production)
- **Purpose**: Live production environment
- **Database**: Production PostgreSQL with backups
- **Services**: All services with production optimization
- **Resource Limits**: CPU and memory limits configured
- **Access**: Production URLs

## Quick Start

### 1. Development Environment

```powershell
# Quick start development environment
.\deploy\scripts\dev-quick-start.ps1

# Or manual deployment
.\deploy\scripts\deploy.ps1 -Environment dev -Action up -Build
```

### 2. Build All Services

```powershell
# Build all services for specific environment
.\deploy\scripts\build.ps1 -Environment dev

# Build only backend services
.\deploy\scripts\build.ps1 -Environment prod -Services backend

# Build specific service
.\deploy\scripts\build.ps1 -Environment sit -Services workflow-engine
```

### 3. Deploy to Environment

```powershell
# Deploy to development
.\deploy\scripts\deploy.ps1 -Environment dev -Action up

# Deploy to production with build
.\deploy\scripts\deploy.ps1 -Environment prod -Action up -Build

# Deploy only backend services
.\deploy\scripts\deploy.ps1 -Environment uat -Action up -Services backend
```

### 4. Test Deployment

```powershell
# Test deployment health
.\deploy\scripts\deploy.ps1 -Environment dev -Action test

# Or use dedicated test script
.\deploy\scripts\test-deployment.ps1 -Environment dev
```

## Script Reference

### build.ps1
Multi-environment build script with support for:
- Environment-specific builds (dev, sit, uat, prod)
- Service filtering (all, backend, frontend, specific services)
- Docker image tagging and registry push
- Maven build integration for backend services
- Frontend build with environment variables

**Usage:**
```powershell
.\build.ps1 -Environment <env> [-Services <services>] [-Tag <tag>] [-Registry <registry>] [-Push] [-NoFrontend] [-NoBackend]
```

### deploy.ps1
Multi-environment deployment script with actions:
- `up`: Start services
- `down`: Stop and remove services
- `restart`: Restart services
- `logs`: Show service logs
- `status`: Show service status
- `test`: Test deployment health

**Usage:**
```powershell
.\deploy.ps1 -Environment <env> -Action <action> [-Services <services>] [-Build]
```

### build-frontend.ps1
Frontend-specific build script with:
- Environment variable injection
- Build optimization
- Docker image creation
- Network connectivity handling

**Usage:**
```powershell
.\build-frontend.ps1 -Environment <env> [-SkipBuild] [-LocalOnly] [-Verbose]
```

### test-deployment.ps1
Comprehensive deployment testing with:
- Container status verification
- Port connectivity testing
- HTTP health check validation
- Service dependency verification

**Usage:**
```powershell
.\test-deployment.ps1 -Environment <env> [-SkipHealthChecks] [-Verbose]
```

## Environment Variables

Each environment has its own `.env` file with the following categories:

### Database Configuration
- `POSTGRES_DB`: Database name
- `POSTGRES_USER`: Database user
- `POSTGRES_PASSWORD`: Database password
- `POSTGRES_PORT`: Database port

### Cache Configuration
- `REDIS_PORT`: Redis port
- `CACHE_TTL`: Cache time-to-live

### Messaging Configuration
- `KAFKA_PORT`: Kafka port
- `ZOOKEEPER_PORT`: Zookeeper port

### Security Configuration
- `JWT_SECRET`: JWT signing secret
- `ENCRYPTION_KEY`: Data encryption key

### Service Ports
- `API_GATEWAY_PORT`: API Gateway port
- `WORKFLOW_ENGINE_PORT`: Workflow Engine port
- `USER_PORTAL_BACKEND_PORT`: User Portal backend port
- `DEVELOPER_WORKSTATION_BACKEND_PORT`: Developer Workstation backend port
- `ADMIN_CENTER_BACKEND_PORT`: Admin Center backend port
- `ADMIN_CENTER_FRONTEND_PORT`: Admin Center frontend port
- `USER_PORTAL_FRONTEND_PORT`: User Portal frontend port
- `DEVELOPER_WORKSTATION_FRONTEND_PORT`: Developer Workstation frontend port

### External Configuration
- `EXTERNAL_CONFIG_URL`: External configuration service URL
- `MONITORING_ENDPOINT`: Monitoring service endpoint
- `API_RATE_LIMIT`: API rate limiting configuration

## Service Architecture

### Infrastructure Services
1. **PostgreSQL**: Primary database
2. **Redis**: Caching and session storage
3. **Kafka**: Message queue and event streaming
4. **Zookeeper**: Kafka coordination

### Backend Services
1. **API Gateway**: Request routing and authentication
2. **Workflow Engine**: Core workflow processing
3. **User Portal**: User-facing API services
4. **Developer Workstation**: Development tools API
5. **Admin Center**: Administrative API services

### Frontend Services
1. **Admin Center Frontend**: Administrative web interface
2. **User Portal Frontend**: User-facing web interface
3. **Developer Workstation Frontend**: Development tools interface

## Health Checks

All services include comprehensive health checks:

### Infrastructure Health Checks
- PostgreSQL: `pg_isready` command
- Redis: `redis-cli ping` command
- Kafka: Broker API versions check

### Application Health Checks
- Spring Boot Actuator: `/actuator/health` endpoint
- Frontend: HTTP connectivity test

### Custom Health Checks
- Database connectivity
- Cache connectivity
- Message queue connectivity
- External service dependencies

## Troubleshooting

### Common Issues

1. **Port Conflicts**
   - Check if ports are already in use
   - Modify environment-specific `.env` files
   - Restart Docker services

2. **Database Connection Issues**
   - Verify PostgreSQL container is running
   - Check database credentials in `.env`
   - Ensure database initialization completed

3. **Frontend Build Failures**
   - Check Node.js version compatibility
   - Clear npm cache: `npm cache clean --force`
   - Use local build: `build-frontend.ps1 -LocalOnly`

4. **Service Startup Issues**
   - Check service logs: `deploy.ps1 -Action logs -Services <service>`
   - Verify environment variables are loaded
   - Check service dependencies are running

### Debugging Commands

```powershell
# Check service status
.\deploy\scripts\deploy.ps1 -Environment dev -Action status

# View service logs
.\deploy\scripts\deploy.ps1 -Environment dev -Action logs -Services workflow-engine

# Test specific service
.\deploy\scripts\test-deployment.ps1 -Environment dev -Verbose

# Restart problematic service
docker-compose -f deploy\environments\dev\docker-compose.dev.yml restart workflow-engine
```

## Production Deployment

### Prerequisites
1. Production server with Docker and Docker Compose
2. Environment variables configured in `deploy/environments/prod/.env`
3. SSL certificates for HTTPS (if required)
4. Database backups and monitoring setup

### Deployment Steps
1. **Build Production Images**
   ```powershell
   .\deploy\scripts\build.ps1 -Environment prod -Tag v1.0.0 -Registry your-registry.com -Push
   ```

2. **Deploy to Production**
   ```powershell
   .\deploy\scripts\deploy.ps1 -Environment prod -Action up
   ```

3. **Verify Deployment**
   ```powershell
   .\deploy\scripts\deploy.ps1 -Environment prod -Action test
   ```

4. **Monitor Services**
   ```powershell
   .\deploy\scripts\deploy.ps1 -Environment prod -Action status
   ```

### Production Considerations
- Resource limits are configured in `docker-compose.prod.yml`
- Restart policies set to `unless-stopped`
- Health checks with appropriate timeouts
- Volume persistence for data
- Network security and isolation
- Monitoring and logging integration

## Security

### Environment Security
- Sensitive data in `.env` files (not committed to version control)
- JWT secrets and encryption keys per environment
- Database credentials isolation
- Network segmentation between environments

### Container Security
- Non-root user execution where possible
- Minimal base images (Alpine Linux)
- Security scanning of images
- Regular updates of base images

### Network Security
- Internal Docker networks for service communication
- Exposed ports only where necessary
- API Gateway as single entry point
- Rate limiting and authentication

## Monitoring and Logging

### Application Monitoring
- Spring Boot Actuator endpoints
- Custom health checks
- Performance metrics collection

### Infrastructure Monitoring
- Container resource usage
- Database performance metrics
- Cache hit rates
- Message queue metrics

### Logging
- Centralized logging with Docker logs
- Application-specific log files
- Log rotation and retention policies
- Structured logging format

## Backup and Recovery

### Database Backups
- Automated PostgreSQL backups
- Point-in-time recovery capability
- Cross-environment backup testing

### Configuration Backups
- Environment configuration versioning
- Docker image versioning and tagging
- Infrastructure as Code practices

### Disaster Recovery
- Multi-environment deployment capability
- Rapid environment recreation
- Data migration procedures
- Service failover strategies