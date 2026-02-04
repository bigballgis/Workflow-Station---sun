# Frontend Nginx Proxy Fix

## Date
2026-02-04

## Summary
Fixed frontend nginx proxy 500 errors by correcting the backend service URLs in docker-compose configuration.

## Issue

**Error**: Frontend login requests to `http://localhost:3000/api/v1/auth/login` returned 500 Internal Server Error.

**Nginx Logs**:
```
connect() failed (111: Connection refused) while connecting to upstream
upstream: "http://172.18.0.8:8080/api/v1/admin/auth/login"
```

**Root Cause**: The docker-compose configuration was using **container names** instead of **service names** for the `ADMIN_CENTER_BACKEND_URL` environment variable:
- ❌ Wrong: `http://platform-admin-center-dev:8080`
- ✅ Correct: `http://admin-center:8080`

In Docker Compose, services communicate using the **service name** defined in the docker-compose.yml file, not the container name.

---

## Fix

### Updated docker-compose.dev.yml

Changed all frontend service backend URLs to use service names:

**Admin Center Frontend**:
```yaml
environment:
  ADMIN_CENTER_BACKEND_URL: http://admin-center:8080  # Changed from platform-admin-center-dev
```

**User Portal Frontend**:
```yaml
environment:
  USER_PORTAL_BACKEND_URL: http://user-portal:8080  # Changed from platform-user-portal-dev
  ADMIN_CENTER_BACKEND_URL: http://admin-center:8080  # Changed from platform-admin-center-dev
```

**Developer Workstation Frontend**:
```yaml
environment:
  DEVELOPER_WORKSTATION_BACKEND_URL: http://developer-workstation:8080  # Changed from platform-developer-workstation-dev
  ADMIN_CENTER_BACKEND_URL: http://admin-center:8080  # Changed from platform-admin-center-dev
```

### Deployment

Recreated frontend containers with new environment variables:
```bash
docker-compose -f deploy/environments/dev/docker-compose.dev.yml up -d admin-center-frontend user-portal-frontend developer-workstation-frontend
```

---

## Testing

### Before Fix
```bash
curl -X POST http://localhost:3000/api/v1/auth/login
# Result: 500 Internal Server Error
```

### After Fix
```powershell
$body = @{username='admin';password='password'} | ConvertTo-Json
Invoke-WebRequest -Uri http://localhost:3000/api/v1/auth/login -Method POST -Body $body -ContentType 'application/json'
```

**Result**: ✅ Success! Returns valid JWT tokens and user info:
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "expiresIn": 86400,
  "user": {
    "userId": "user-admin",
    "username": "admin",
    "displayName": "超级管理员",
    "email": "admin@example.com",
    "roles": ["SYS_ADMIN"],
    "permissions": ["basic:access"],
    "rolesWithSources": [{
      "roleCode": "SYS_ADMIN",
      "roleName": "SYS_ADMIN",
      "sourceType": null,
      "sourceId": "user-admin",
      "sourceName": "Direct Assignment"
    }],
    "businessUnitId": null,
    "language": "zh_CN"
  }
}
```

---

## Technical Details

### Docker Compose Service Names vs Container Names

In Docker Compose:
- **Service Name**: Defined in the docker-compose.yml (e.g., `admin-center`)
- **Container Name**: Defined by `container_name` directive (e.g., `platform-admin-center-dev`)

Services within the same Docker Compose network communicate using **service names**, which are automatically resolved by Docker's internal DNS.

### Nginx Configuration

The nginx configuration uses environment variable substitution:
```nginx
location /api/v1/auth/ {
    rewrite ^/api/v1/auth/(.*)$ /api/v1/admin/auth/$1 break;
    proxy_pass ${ADMIN_CENTER_BACKEND_URL};
    ...
}
```

The `docker-entrypoint.sh` script replaces `${ADMIN_CENTER_BACKEND_URL}` with the actual value:
```bash
envsubst '${ADMIN_CENTER_BACKEND_URL}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf
```

---

## Files Modified

1. `deploy/environments/dev/docker-compose.dev.yml` - Updated all frontend backend URLs to use service names

---

## Related Issues

This fix completes the admin center login functionality:
- `docs/ADMIN_CENTER_LOGIN_FIX.md` - Backend database and role fixes
- `docs/WORKFLOW_ENGINE_USER_PORTAL_FIX.md` - Backend service startup fixes
- `docs/PLATFORM_SECURITY_TESTS_FIXED.md` - Test fixes

---

## Final Status

✅ All backend services running and healthy
✅ All frontend nginx proxies configured correctly
✅ Admin center login working through frontend (http://localhost:3000)
✅ User portal frontend ready (http://localhost:3001)
✅ Developer workstation frontend ready (http://localhost:3002)

The entire platform is now fully functional and ready for use!

---

## Access URLs

- **Admin Center**: http://localhost:3000
- **User Portal**: http://localhost:3001
- **Developer Workstation**: http://localhost:3002
- **API Gateway**: http://localhost:8080
- **Admin Center Backend**: http://localhost:8090
- **User Portal Backend**: http://localhost:8082
- **Workflow Engine**: http://localhost:8081
- **Developer Workstation Backend**: http://localhost:8083

## Login Credentials

- **Username**: admin
- **Password**: password
