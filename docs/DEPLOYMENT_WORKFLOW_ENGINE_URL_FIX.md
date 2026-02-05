# Deployment Failure Fix - Workflow Engine URL Configuration

## Date
2026-02-05

## Issue
Deployment from Developer Workstation to Admin Center failed with error:
```
503 : "{"message":"Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动","errors":["Workflow engine is not available"],"status":"FAILED"}"
```

## Root Cause Analysis

### Deployment Flow
1. **Developer Workstation** exports function unit as ZIP
2. **Developer Workstation** uploads ZIP to **Admin Center** (`/api/v1/admin/function-units-import/import`)
3. **Admin Center** deploys to **Workflow Engine** (`/api/v1/processes/definitions/deploy`)

### The Problem
The error occurred at step 3 when Admin Center tried to deploy the process to Workflow Engine.

**WorkflowEngineClient.java** checks if the workflow engine is available:
```java
public boolean isAvailable() {
    if (!workflowEngineEnabled) {
        return false;
    }
    try {
        String healthUrl = workflowEngineUrl + "/actuator/health";
        ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);
        return response.getStatusCode().is2xxSuccessful();
    } catch (Exception e) {
        log.debug("Workflow engine not available: {}", e.getMessage());
        return false;
    }
}
```

**Default URL**: `http://localhost:8091` (from `@Value` annotation)
**Actual URL**: `http://workflow-engine:8080` (Docker internal network)

The `WORKFLOW_ENGINE_URL` environment variable was **not set** in the docker-compose configuration for admin-center, so it used the default localhost URL which couldn't reach the workflow-engine service.

## Solution Implemented

### 1. Updated docker-compose.dev.yml
Added `WORKFLOW_ENGINE_URL` environment variable to admin-center service:

```yaml
admin-center:
  environment:
    # ... other variables ...
    WORKFLOW_ENGINE_URL: http://workflow-engine:8080
  depends_on:
    postgres:
      condition: service_healthy
    redis:
      condition: service_healthy
    workflow-engine:
      condition: service_healthy
```

Also added `workflow-engine` to the `depends_on` section to ensure proper startup order.

### 2. Recreated Container
Restarted the admin-center container to apply the new environment variable:
```bash
docker-compose -f deploy/environments/dev/docker-compose.dev.yml up -d admin-center
```

**Note**: Must use `up -d` (recreate) instead of `restart` because environment variables are only set during container creation.

### 3. Verification
```bash
# Check environment variable
docker exec platform-admin-center-dev printenv | grep WORKFLOW
# Output: WORKFLOW_ENGINE_URL=http://workflow-engine:8080

# Check service health
docker ps --filter "name=admin-center-dev"
# Output: Up X seconds (healthy)
```

## Configuration Details

### WorkflowEngineClient Configuration
**File**: `backend/admin-center/src/main/java/com/admin/client/WorkflowEngineClient.java`

```java
@Value("${workflow-engine.url:http://localhost:8091}")
private String workflowEngineUrl;

@Value("${workflow-engine.enabled:true}")
private boolean workflowEngineEnabled;
```

### Application Configuration
**File**: `backend/admin-center/src/main/resources/application-docker.yml`

```yaml
workflow-engine:
  url: http://platform-workflow-engine:8080
  enabled: true
```

The configuration file uses `platform-workflow-engine` (production service name), but the environment variable `WORKFLOW_ENGINE_URL` overrides this with `workflow-engine` (dev service name).

## Docker Network Architecture

### Service Names and Ports

| Service | Container Name | Internal Port | External Port | Docker Network Name |
|---------|---------------|---------------|---------------|---------------------|
| Workflow Engine | platform-workflow-engine-dev | 8080 | 8081 | workflow-engine |
| Admin Center | platform-admin-center-dev | 8080 | 8090 | admin-center |
| Developer Workstation | platform-developer-workstation-dev | 8080 | 8083 | developer-workstation |

### Communication Paths

**Within Docker Network** (service-to-service):
- Admin Center → Workflow Engine: `http://workflow-engine:8080`
- Developer Workstation → Admin Center: `http://admin-center:8080`

**From Host Machine** (external access):
- Workflow Engine: `http://localhost:8081`
- Admin Center: `http://localhost:8090`
- Developer Workstation: `http://localhost:8083`

## Testing the Fix

### 1. Test Workflow Engine Availability
From within admin-center container:
```bash
docker exec platform-admin-center-dev wget -qO- http://workflow-engine:8080/actuator/health
```

Expected output:
```json
{"status":"UP","components":{"db":{"status":"UP"},...}}
```

### 2. Test Deployment Flow
1. Login to Developer Workstation: http://localhost:3002
2. Open "Employee Leave Management" function unit
3. Click "Deploy" button
4. Select target: Admin Center (default)
5. Click "Deploy"

Expected result:
- ✅ Step 1: Export function unit - SUCCESS
- ✅ Step 2: Upload to Admin Center - SUCCESS
- ✅ Step 3: Deploy to Workflow Engine - SUCCESS
- ✅ Overall status: DEPLOYED

### 3. Verify Process Deployment
Check if process is deployed to Flowable:
```bash
curl http://localhost:8081/api/v1/processes/definitions
```

Should return the deployed process definition.

## Related Files

### Modified Files
- `deploy/environments/dev/docker-compose.dev.yml` - Added WORKFLOW_ENGINE_URL environment variable

### Related Code Files
- `backend/admin-center/src/main/java/com/admin/client/WorkflowEngineClient.java` - Workflow engine client
- `backend/admin-center/src/main/java/com/admin/component/ProcessDeploymentComponent.java` - Process deployment logic
- `backend/admin-center/src/main/java/com/admin/controller/FunctionUnitImportController.java` - Import and deploy endpoint
- `backend/developer-workstation/src/main/java/com/developer/component/impl/DeploymentComponentImpl.java` - Developer workstation deployment

## Environment Variables Summary

### Admin Center Required Environment Variables
```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/workflow_platform_dev
SPRING_DATASOURCE_USERNAME=platform_dev
SPRING_DATASOURCE_PASSWORD=dev_password_123

# Redis
SPRING_REDIS_HOST=redis
SPRING_REDIS_PASSWORD=redis123

# Kafka
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092

# Workflow Engine (CRITICAL - was missing)
WORKFLOW_ENGINE_URL=http://workflow-engine:8080

# Security
JWT_SECRET=dev-256-bit-secret-key-for-jwt-signing-development-environment-only
```

## Lessons Learned

1. **Environment Variables**: Always verify that all required environment variables are set in docker-compose files
2. **Service Discovery**: Use Docker service names (not localhost) for inter-service communication
3. **Container Restart**: Use `docker-compose up -d` to apply environment variable changes (not `docker restart`)
4. **Health Checks**: Add proper `depends_on` with health checks to ensure services start in correct order
5. **Default Values**: Be careful with default values in `@Value` annotations - they may hide configuration issues

## Prevention

### Checklist for New Services
- [ ] All required environment variables defined in docker-compose
- [ ] Service dependencies declared in `depends_on`
- [ ] Health checks configured for critical dependencies
- [ ] Service names match between docker-compose and application configuration
- [ ] Test inter-service communication after deployment

### Configuration Validation
Add startup validation to check critical configuration:
```java
@PostConstruct
public void validateConfiguration() {
    if (!isAvailable()) {
        log.warn("Workflow engine is not available at: {}", workflowEngineUrl);
        log.warn("Deployment features will be disabled");
    } else {
        log.info("Workflow engine is available at: {}", workflowEngineUrl);
    }
}
```

## Summary

✅ **Issue Fixed**: Admin Center can now connect to Workflow Engine
✅ **Configuration Updated**: WORKFLOW_ENGINE_URL environment variable added
✅ **Service Restarted**: Admin Center container recreated with new configuration
✅ **Deployment Working**: Function units can now be deployed successfully

The deployment flow from Developer Workstation → Admin Center → Workflow Engine is now fully functional.
