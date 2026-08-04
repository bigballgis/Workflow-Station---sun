# Database Configuration Update Requirements

## Overview
Update all database configurations in the project to use PostgreSQL with standardized credentials.

## User Stories

### 1. Standardize Database Credentials
**As a** developer  
**I want** all database connections to use consistent PostgreSQL credentials  
**So that** the project has a unified database configuration across all environments

### 2. Update Environment Files
**As a** developer  
**I want** all environment files to use the new database credentials  
**So that** local development and Docker environments work consistently

### 3. Update Application Configuration Files
**As a** developer  
**I want** all Spring Boot application.yml files to use the new database credentials  
**So that** all microservices connect to the database with consistent settings

### 4. Update Docker Configuration
**As a** developer  
**I want** Docker Compose and container configurations to use the new credentials  
**So that** containerized deployments work with the standardized database setup

## Acceptance Criteria

### 1.1 Database Name Standardization
- All database connections must use database name: `postgres`
- Remove any references to `workflow_platform` or other database names
- Maintain schema references where needed (e.g., `?currentSchema=projectx`)

### 1.2 Username Standardization  
- All database connections must use username: `postgres`
- Replace all instances of `platform` username
- Update both default values and environment variable references

### 1.3 Password Standardization
- All database connections must use password: `postgres`
- Replace all instances of `platform123` password
- Update both default values and environment variable references

### 1.4 Environment Files Update
- Update `.env` file with new credentials
- Update `.env.dev` file with new credentials  
- Update `.env.docker` file with new credentials

### 1.5 Application Configuration Update
- Update all `application.yml` files in backend services
- Update all `application-docker.yml` files
- Update all `application-test.yml` files where applicable

### 1.6 Docker Configuration Update
- Update `docker-compose.yml` PostgreSQL service configuration
- Update all service environment variables in docker-compose.yml
- Update individual service Docker configurations

### 1.7 CI/CD Configuration Update
- Update GitHub Actions workflow database configuration
- Update any test database configurations

## Technical Requirements

### 2.1 Backward Compatibility
- Ensure existing data migration scripts continue to work
- Maintain schema structure and relationships
- Preserve Flyway migration compatibility

### 2.2 Environment Variable Support
- Maintain environment variable override capability
- Use consistent variable naming across all configurations
- Provide sensible defaults in configuration files

### 2.3 Service Configuration Consistency
- All backend services must use identical database connection patterns
- Maintain connection pooling and performance settings
- Preserve database driver and connection properties

## Files to Update

### Environment Files
- `.env`
- `.env.dev` 
- `.env.docker`

### Backend Service Configurations
- `backend/admin-center/src/main/resources/application.yml`
- `backend/admin-center/src/main/resources/application-docker.yml`
- `backend/user-portal/src/main/resources/application.yml`
- `backend/user-portal/src/main/resources/application-docker.yml`
- `backend/developer-workstation/src/main/resources/application.yml`
- `backend/workflow-engine-core/src/main/resources/application.yml`

### Docker Configurations
- `docker-compose.yml`
- `backend/workflow-engine-core/docker-compose.yml`

### CI/CD Configurations
- `.github/workflows/ci.yml`

## Success Criteria
- All services can connect to PostgreSQL using the new credentials
- Docker Compose starts successfully with new configuration
- Local development environment works with new credentials
- CI/CD pipeline passes with updated test database configuration
- No breaking changes to existing functionality