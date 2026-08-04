# Database Configuration Update Design

## Overview
This design outlines the systematic approach to update all database configurations in the project to use PostgreSQL with standardized credentials (database: `postgres`, username: `postgres`, password: `postgres`).

## Architecture Impact

### Current State
- Database name: Mixed (`postgres`, `workflow_platform`)
- Username: `platform` 
- Password: `platform123`
- Inconsistent configuration across environments

### Target State
- Database name: `postgres` (standardized)
- Username: `postgres` (standardized)
- Password: `postgres` (standardized)
- Consistent configuration across all environments

## Configuration Strategy

### 1. Environment Variable Hierarchy
```
Environment Variables (highest priority)
↓
Application Configuration Files
↓
Default Values (lowest priority)
```

### 2. Configuration Patterns

#### Standard Database URL Pattern
```yaml
url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/postgres?currentSchema=projectx}
username: ${SPRING_DATASOURCE_USERNAME:postgres}
password: ${SPRING_DATASOURCE_PASSWORD:postgres}
```

#### Docker Environment Pattern
```yaml
url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://postgres:5432/postgres}
username: ${SPRING_DATASOURCE_USERNAME:postgres}
password: ${SPRING_DATASOURCE_PASSWORD:postgres}
```

## Implementation Plan

### Phase 1: Environment Files
Update base environment configuration files to establish new defaults.

**Files:**
- `.env` - Docker Compose environment
- `.env.dev` - Local development environment  
- `.env.docker` - Docker container environment

**Changes:**
- `POSTGRES_PASSWORD=postgres`
- `SPRING_DATASOURCE_USERNAME=postgres`
- `SPRING_DATASOURCE_PASSWORD=postgres`

### Phase 2: Docker Configuration
Update Docker Compose and container configurations.

**Files:**
- `docker-compose.yml` - Main Docker Compose file
- `backend/workflow-engine-core/docker-compose.yml` - Service-specific compose

**Changes:**
- PostgreSQL service: `POSTGRES_USER: postgres`
- PostgreSQL service: `POSTGRES_PASSWORD: postgres`
- All service environments: Update datasource variables

### Phase 3: Application Configurations
Update Spring Boot application configuration files.

**Backend Services:**
- admin-center
- user-portal  
- developer-workstation
- workflow-engine-core

**Configuration Files per Service:**
- `application.yml` - Main configuration
- `application-docker.yml` - Docker-specific configuration
- `application-test.yml` - Test configuration (if exists)

### Phase 4: CI/CD Configuration
Update continuous integration and deployment configurations.

**Files:**
- `.github/workflows/ci.yml` - GitHub Actions workflow

## Database Connection Details

### Local Development
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres?currentSchema=projectx
    username: postgres
    password: postgres
```

### Docker Environment
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/postgres
    username: postgres  
    password: postgres
```

### Test Environment
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres_test
    username: postgres
    password: postgres
```

## Schema Considerations

### Current Schema Usage
- Services use `?currentSchema=projectx` parameter
- This allows multiple schemas in single database
- Maintains data isolation between environments

### Schema Strategy
- **Keep existing schema parameter** for local development
- **Remove schema parameter** for Docker (uses default public schema)
- **Maintain schema flexibility** through environment variables

## Migration Strategy

### 1. Backward Compatibility
- Use environment variables to allow gradual migration
- Maintain old defaults temporarily during transition
- Test each service independently

### 2. Rollback Plan
- Keep backup of original configuration files
- Document original values for quick restoration
- Test rollback procedure in development environment

### 3. Validation Steps
1. Update configuration files
2. Test local development startup
3. Test Docker Compose startup  
4. Verify database connectivity
5. Run integration tests
6. Validate CI/CD pipeline

## Security Considerations

### Development vs Production
- **Development**: Simple credentials for ease of use
- **Production**: Should use secure credentials via environment variables
- **CI/CD**: Isolated test database with test credentials

### Environment Variable Security
- Sensitive values should be injected via environment
- Configuration files contain defaults only
- Production deployments override defaults

## Testing Strategy

### Unit Tests
- No changes required (use in-memory databases)
- Maintain existing test configurations

### Integration Tests  
- Update test database configurations
- Verify connection with new credentials
- Test Flyway migrations with new setup

### End-to-End Tests
- Test full Docker Compose startup
- Verify all services connect successfully
- Test data persistence and retrieval

## Correctness Properties

### Property 1: Database Connectivity
**Description:** All services must successfully connect to PostgreSQL using the new credentials.

**Validation:** 
- Service startup logs show successful database connection
- Health checks pass for all services
- Database queries execute without authentication errors

### Property 2: Configuration Consistency
**Description:** All configuration files must use consistent database credentials.

**Validation:**
- All `application.yml` files use same default credentials
- All environment files specify same credential values
- Docker configurations use matching credential variables

### Property 3: Environment Override Capability
**Description:** Environment variables must successfully override configuration file defaults.

**Validation:**
- Setting `SPRING_DATASOURCE_USERNAME` environment variable changes connection username
- Setting `SPRING_DATASOURCE_PASSWORD` environment variable changes connection password
- Services connect using environment-specified credentials when provided

### Property 4: Schema Isolation
**Description:** Services must connect to correct database schema based on environment.

**Validation:**
- Local development uses `projectx` schema via URL parameter
- Docker environment uses default schema
- Test environment uses isolated test database

## Risk Assessment

### Low Risk
- Configuration file updates (easily reversible)
- Environment variable changes (non-breaking)

### Medium Risk  
- Docker Compose service dependencies
- Database connection timing during startup

### Mitigation Strategies
- Test changes in isolated environment first
- Update configurations incrementally
- Maintain rollback documentation
- Verify each service independently before full system test