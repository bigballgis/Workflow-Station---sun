# Function Unit Access Config - Role Name Display Fix

**Date**: 2026-02-04  
**Status**: ✅ Complete

## Issue

In the Admin Center frontend, the "Access Config" dialog for function units showed empty Business Role column. The role names were not being displayed even though the data structure included a `roleName` field.

## Root Cause

The `FunctionUnitAccessInfo.fromEntity()` method in the backend was setting `targetName` to `null` with a comment "需要从其他服务获取" (needs to be fetched from other services). The `getRoleName()` method returns `targetName` when `targetType` is "ROLE", but since `targetName` was null, no role names appeared in the frontend.

## Solution

Modified the `FunctionUnitAccessService.getAccessConfigs()` method to populate role names by fetching them from the `RoleRepository` when building the response.

### Code Changes

**File**: `backend/admin-center/src/main/java/com/admin/service/FunctionUnitAccessService.java`

**Before**:
```java
@Transactional(readOnly = true)
public List<FunctionUnitAccessInfo> getAccessConfigs(String functionUnitId) {
    return accessRepository.findByFunctionUnitId(functionUnitId)
            .stream()
            .map(FunctionUnitAccessInfo::fromEntity)
            .collect(Collectors.toList());
}
```

**After**:
```java
@Transactional(readOnly = true)
public List<FunctionUnitAccessInfo> getAccessConfigs(String functionUnitId) {
    return accessRepository.findByFunctionUnitId(functionUnitId)
            .stream()
            .map(access -> {
                FunctionUnitAccessInfo info = FunctionUnitAccessInfo.fromEntity(access);
                
                // 填充目标名称（角色名）
                if ("ROLE".equals(access.getTargetType())) {
                    roleRepository.findById(access.getTargetId())
                            .ifPresent(role -> info.setTargetName(role.getName()));
                }
                
                return info;
            })
            .collect(Collectors.toList());
}
```

## Deployment

1. Rebuilt admin-center backend: `mvn clean package -DskipTests`
2. Redeployed admin-center container: `docker-compose -f deploy/environments/dev/docker-compose.dev.yml up -d --build admin-center`
3. Service started successfully on port 8080

## Testing

To verify the fix:
1. Login to Admin Center at http://localhost:3000
2. Navigate to Function Unit management
3. Click "Access Config" button for any function unit
4. The Business Role column should now display role names correctly

## Related Files

- `backend/admin-center/src/main/java/com/admin/service/FunctionUnitAccessService.java` - Service layer fix
- `backend/admin-center/src/main/java/com/admin/dto/response/FunctionUnitAccessInfo.java` - DTO with getRoleName() method
- `frontend/admin-center/src/views/function-unit/index.vue` - Frontend dialog implementation
- `frontend/admin-center/src/api/functionUnit.ts` - API interface definitions

## Notes

- The fix maintains backward compatibility by using the existing `targetName` field
- The `getRoleName()` method in `FunctionUnitAccessInfo` returns `targetName` when `targetType` is "ROLE"
- This approach allows the same DTO to support different target types (ROLE, USER, VIRTUAL_GROUP) in the future
