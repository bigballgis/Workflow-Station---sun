# Database Configuration Update Tasks

## Task List

### 1. Environment Files Update
- [x] 1.1 Update .env file with new PostgreSQL credentials
- [x] 1.2 Update .env.dev file with new PostgreSQL credentials  
- [x] 1.3 Update .env.docker file with new PostgreSQL credentials

### 2. Docker Configuration Update
- [x] 2.1 Update docker-compose.yml PostgreSQL service configuration
- [x] 2.2 Update docker-compose.yml service environment variables
- [x] 2.3 Update backend/workflow-engine-core/docker-compose.yml

### 3. Backend Service Configuration Update
- [x] 3.1 Update admin-center application configurations
  - [x] 3.1.1 Update backend/admin-center/src/main/resources/application.yml
  - [x] 3.1.2 Update backend/admin-center/src/main/resources/application-docker.yml
- [x] 3.2 Update user-portal application configurations
  - [x] 3.2.1 Update backend/user-portal/src/main/resources/application.yml
  - [x] 3.2.2 Update backend/user-portal/src/main/resources/application-docker.yml
- [x] 3.3 Update developer-workstation application configurations
  - [x] 3.3.1 Update backend/developer-workstation/src/main/resources/application.yml
- [x] 3.4 Update workflow-engine-core application configurations
  - [x] 3.4.1 Update backend/workflow-engine-core/src/main/resources/application.yml

### 4. CI/CD Configuration Update
- [x] 4.1 Update .github/workflows/ci.yml test database configuration

### 5. Validation and Testing
- [ ] 5.1 Test local development environment startup
- [ ] 5.2 Test Docker Compose environment startup
- [ ] 5.3 Verify all services connect to database successfully
- [ ] 5.4 Run integration tests to ensure functionality

## Task Details

### 1.1 Update .env file with new PostgreSQL credentials
**Description:** Update the main environment file to use postgres credentials.
**Files:** `.env`
**Changes:**
- Change `POSTGRES_PASSWORD=platform123` to `POSTGRES_PASSWORD=postgres`

### 1.2 Update .env.dev file with new PostgreSQL credentials
**Description:** Update development environment file for local testing.
**Files:** `.env.dev`
**Changes:**
- Change `SPRING_DATASOURCE_USERNAME=postgres` (already correct)
- Change `SPRING_DATASOURCE_PASSWORD=platform123` to `SPRING_DATASOURCE_PASSWORD=postgres`

### 1.3 Update .env.docker file with new PostgreSQL credentials
**Description:** Update Docker environment file for container deployments.
**Files:** `.env.docker`
**Changes:**
- Change `SPRING_DATASOURCE_USERNAME=platform` to `SPRING_DATASOURCE_USERNAME=postgres`
- Change `SPRING_DATASOURCE_PASSWORD=platform123` to `SPRING_DATASOURCE_PASSWORD=postgres`

### 2.1 Update docker-compose.yml PostgreSQL service configuration
**Description:** Update the PostgreSQL service definition in main Docker Compose file.
**Files:** `docker-compose.yml`
**Changes:**
- Change `POSTGRES_USER: platform` to `POSTGRES_USER: postgres`
- Change `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-platform123}` to `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-postgres}`

### 2.2 Update docker-compose.yml service environment variables
**Description:** Update all service environment variables in Docker Compose.
**Files:** `docker-compose.yml`
**Changes:**
- Change all `SPRING_DATASOURCE_USERNAME: platform` to `SPRING_DATASOURCE_USERNAME: postgres`
- Change all `SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-platform123}` to `SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-postgres}`

### 2.3 Update backend/workflow-engine-core/docker-compose.yml
**Description:** Update service-specific Docker Compose file.
**Files:** `backend/workflow-engine-core/docker-compose.yml`
**Changes:**
- Change `SPRING_DATASOURCE_USERNAME: platform` to `SPRING_DATASOURCE_USERNAME: postgres`
- Change `SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-platform123}` to `SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-postgres}`

### 3.1.1 Update backend/admin-center/src/main/resources/application.yml
**Description:** Update admin-center main application configuration.
**Files:** `backend/admin-center/src/main/resources/application.yml`
**Changes:**
- Change `username: ${SPRING_DATASOURCE_USERNAME:platform}` to `username: ${SPRING_DATASOURCE_USERNAME:postgres}`
- Change `password: ${SPRING_DATASOURCE_PASSWORD:platform123}` to `password: ${SPRING_DATASOURCE_PASSWORD:postgres}`

### 3.1.2 Update backend/admin-center/src/main/resources/application-docker.yml
**Description:** Update admin-center Docker-specific configuration.
**Files:** `backend/admin-center/src/main/resources/application-docker.yml`
**Changes:**
- Change `username: ${SPRING_DATASOURCE_USERNAME:platform}` to `username: ${SPRING_DATASOURCE_USERNAME:postgres}`
- Change `password: ${SPRING_DATASOURCE_PASSWORD:${POSTGRES_PASSWORD:-platform123}}` to `password: ${SPRING_DATASOURCE_PASSWORD:${POSTGRES_PASSWORD:-postgres}}`

### 3.2.1 Update backend/user-portal/src/main/resources/application.yml
**Description:** Update user-portal main application configuration.
**Files:** `backend/user-portal/src/main/resources/application.yml`
**Changes:**
- Change `username: ${SPRING_DATASOURCE_USERNAME:platform}` to `username: ${SPRING_DATASOURCE_USERNAME:postgres}`
- Change `password: ${SPRING_DATASOURCE_PASSWORD:platform123}` to `password: ${SPRING_DATASOURCE_PASSWORD:postgres}`

### 3.2.2 Update backend/user-portal/src/main/resources/application-docker.yml
**Description:** Update user-portal Docker-specific configuration.
**Files:** `backend/user-portal/src/main/resources/application-docker.yml`
**Changes:**
- Change `username: ${SPRING_DATASOURCE_USERNAME:platform}` to `username: ${SPRING_DATASOURCE_USERNAME:postgres}`
- Change `password: ${SPRING_DATASOURCE_PASSWORD:${POSTGRES_PASSWORD:-platform123}}` to `password: ${SPRING_DATASOURCE_PASSWORD:${POSTGRES_PASSWORD:-postgres}}`

### 3.3.1 Update backend/developer-workstation/src/main/resources/application.yml
**Description:** Update developer-workstation main application configuration.
**Files:** `backend/developer-workstation/src/main/resources/application.yml`
**Changes:**
- Change `username: ${SPRING_DATASOURCE_USERNAME:platform}` to `username: ${SPRING_DATASOURCE_USERNAME:postgres}`
- Change `password: ${SPRING_DATASOURCE_PASSWORD:platform123}` to `password: ${SPRING_DATASOURCE_PASSWORD:postgres}`

### 3.4.1 Update backend/workflow-engine-core/src/main/resources/application.yml
**Description:** Update workflow-engine-core main application configuration.
**Files:** `backend/workflow-engine-core/src/main/resources/application.yml`
**Changes:**
- Change `username: ${SPRING_DATASOURCE_USERNAME:platform}` to `username: ${SPRING_DATASOURCE_USERNAME:postgres}`
- Change `password: ${SPRING_DATASOURCE_PASSWORD:platform123}` to `password: ${SPRING_DATASOURCE_PASSWORD:postgres}`

### 4.1 Update .github/workflows/ci.yml test database configuration
**Description:** Update CI/CD pipeline database configuration for tests.
**Files:** `.github/workflows/ci.yml`
**Changes:**
- Change `SPRING_DATASOURCE_USERNAME: test` to `SPRING_DATASOURCE_USERNAME: postgres`
- Change `SPRING_DATASOURCE_PASSWORD: test` to `SPRING_DATASOURCE_PASSWORD: postgres`

### 5.1 Test local development environment startup
**Description:** Verify that local development environment works with new credentials.
**Steps:**
1. Start PostgreSQL with new credentials
2. Start each backend service individually
3. Verify successful database connections in logs
4. Test basic functionality

### 5.2 Test Docker Compose environment startup
**Description:** Verify that Docker Compose environment works with new credentials.
**Steps:**
1. Run `docker-compose down -v` to clean up
2. Run `docker-compose up -d postgres redis` to start infrastructure
3. Run `docker-compose up` to start all services
4. Verify all services start successfully and connect to database

### 5.3 Verify all services connect to database successfully
**Description:** Confirm database connectivity across all services.
**Validation:**
- Check service logs for successful database connection messages
- Verify health check endpoints return healthy status
- Test database queries through service APIs

### 5.4 Run integration tests to ensure functionality
**Description:** Execute test suite to ensure no functionality is broken.
**Steps:**
1. Run unit tests: `mvn test`
2. Run integration tests if available
3. Verify CI/CD pipeline passes with new configuration
4. Test basic CRUD operations through APIs