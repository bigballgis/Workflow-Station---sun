package com.admin.audit;

import com.admin.component.SecurityAuditComponent;
import com.admin.enums.AuditAction;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;;

/**
 * AOP aspect that automatically records audit logs for all mutating and key read
 * operations across User / Role / VirtualGroup / Auth controllers.
 *
 * Covers the four operation types required by auditLog.md:
 *   CREATE → old_value=null, new_value=serialized result
 *   UPDATE → old_value=state before, new_value=request body
 *   DELETE → old_value=state before, new_value=null
 *   READ   → change_details=query parameters
 */
@Slf4j
@Aspect
@Component
public class AdminAuditAspect {

    private final SecurityAuditComponent securityAuditComponent;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VirtualGroupRepository virtualGroupRepository;
    private final ObjectMapper mapper;

    public AdminAuditAspect(SecurityAuditComponent securityAuditComponent,
                             UserRepository userRepository,
                             RoleRepository roleRepository,
                             VirtualGroupRepository virtualGroupRepository,
                             ObjectMapper objectMapper) {
        this.securityAuditComponent = securityAuditComponent;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.virtualGroupRepository = virtualGroupRepository;
        // Use a copy so we don't alter the global mapper
        this.mapper = objectMapper.copy()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    // =========================================================================
    // Pointcuts
    // =========================================================================

    /** All UserController methods (write + read) */
    @Around("within(com.admin.controller.UserController)")
    public Object auditUser(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "USER");
    }

    /** All RoleController write methods */
    @Around("within(com.admin.controller.RoleController) "
            + "&& !execution(* *.getRoleMembers(..)) "
            + "&& !execution(* *.getRoleMembersPaged(..)) "
            + "&& !execution(* *.getMemberCount(..)) "
            + "&& !execution(* *.getRoleHistory(..)) "
            + "&& !execution(* *.getRoleHistoryPaged(..))")
    public Object auditRole(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "ROLE");
    }

    /** VirtualGroup create/update/delete + member changes */
    @Around("within(com.admin.controller.VirtualGroupController) "
            + "&& !execution(* *.listVirtualGroups(..)) "
            + "&& !execution(* *.getVirtualGroup(..)) "
            + "&& !execution(* *.getGroupMembers(..)) "
            + "&& !execution(* *.getGroupTasks(..)) "
            + "&& !execution(* *.getUserVisibleGroupTasks(..)) "
            + "&& !execution(* *.getTaskHistory(..))")
    public Object auditVirtualGroup(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "VIRTUAL_GROUP");
    }

    /** Login and logout */
    @Around("within(com.admin.controller.AuthController) "
            + "&& (execution(* *.login(..)) || execution(* *.logout(..)))")
    public Object auditAuth(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "AUTH");
    }

    // =========================================================================
    // Core audit logic
    // =========================================================================

    private Object audit(ProceedingJoinPoint pjp, String domain) throws Throwable {
        String methodName = pjp.getSignature().getName();
        Object[] args = pjp.getArgs();

        AuditMeta meta = resolveMeta(domain, methodName, args);
        // Skip recording for non-auditable operations (e.g. routine list calls)
        if (!meta.shouldRecord()) {
            return pjp.proceed();
        }
        String oldValue = fetchOldValue(meta);

        boolean success = true;
        String failureReason = null;
        Object result = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            success = false;
            failureReason = t.getMessage();
            throw t;
        } finally {
            try {
                String newValue;
                // For UPDATE operations, re-fetch the entity from DB after the operation
                // so that newValue has the same entity structure as oldValue → accurate diff.
                // updateUser returns ResponseEntity<Void> so we cannot rely on result.getBody().
                if (success && isUpdateAction(meta.action) && meta.resourceId != null) {
                    String afterState = fetchCurrentEntityState(meta);
                    newValue = afterState != null ? afterState : resolveNewValue(meta, args, result, success);
                } else {
                    newValue = resolveNewValue(meta, args, result, success);
                }
                persist(meta, oldValue, newValue, success, failureReason);
            } catch (Exception e) {
                log.warn("Failed to persist audit log for {}.{}: {}", domain, methodName, e.getMessage());
            }
        }
    }

    // =========================================================================
    // Meta resolution — maps (domain, methodName) → (action, resourceType, resourceId)
    // =========================================================================

    private AuditMeta resolveMeta(String domain, String methodName, Object[] args) {
        String firstArg = args.length > 0 && args[0] instanceof String ? (String) args[0] : null;

        return switch (domain) {
            case "USER" -> resolveUserMeta(methodName, firstArg, args);
            case "ROLE" -> resolveRoleMeta(methodName, firstArg, args);
            case "VIRTUAL_GROUP" -> resolveVgMeta(methodName, firstArg, args);
            case "AUTH" -> resolveAuthMeta(methodName, args);
            default -> new AuditMeta(AuditAction.DATA_QUERIED, domain, null);
        };
    }

    private AuditMeta resolveUserMeta(String method, String userId, Object[] args) {
        return switch (method) {
            case "createUser"       -> new AuditMeta(AuditAction.USER_CREATED, "USER", null);
            case "batchImport"      -> new AuditMeta(AuditAction.DATA_IMPORTED, "USER", null);
            case "updateUser"       -> new AuditMeta(AuditAction.USER_UPDATED, "USER", userId);
            case "updateUserStatus" -> new AuditMeta(AuditAction.USER_UPDATED, "USER", userId);
            case "resetPassword"    -> new AuditMeta(AuditAction.PASSWORD_RESET, "USER", userId);
            case "deleteUser"       -> new AuditMeta(AuditAction.USER_DELETED, "USER", userId);
            case "getUser"          -> new AuditMeta(AuditAction.DATA_QUERIED, "USER", userId);
            // listUsers is a routine paginated read — too noisy to record every call
            case "listUsers"        -> AuditMeta.skip();
            default                 -> AuditMeta.skip();
        };
    }

    private AuditMeta resolveRoleMeta(String method, String roleId, Object[] args) {
        return switch (method) {
            case "createRole"           -> new AuditMeta(AuditAction.ROLE_CREATED, "ROLE", null);
            case "updateRole"           -> new AuditMeta(AuditAction.ROLE_UPDATED, "ROLE", roleId);
            case "deleteRole"           -> new AuditMeta(AuditAction.ROLE_DELETED, "ROLE", roleId);
            case "configurePermissions" -> new AuditMeta(AuditAction.PERMISSION_GRANTED, "ROLE", roleId);
            case "addMember"            -> new AuditMeta(AuditAction.ROLE_ASSIGNED, "ROLE", roleId);
            case "removeMember"         -> new AuditMeta(AuditAction.ROLE_UNASSIGNED, "ROLE", roleId);
            case "batchAddMembers"      -> new AuditMeta(AuditAction.ROLE_ASSIGNED, "ROLE", roleId);
            case "batchRemoveMembers"   -> new AuditMeta(AuditAction.ROLE_UNASSIGNED, "ROLE", roleId);
            // Read-only list operations are too noisy to record
            default                     -> AuditMeta.skip();
        };
    }

    private AuditMeta resolveVgMeta(String method, String groupId, Object[] args) {
        return switch (method) {
            case "createVirtualGroup"  -> new AuditMeta(AuditAction.DATA_CREATED, "VIRTUAL_GROUP", null);
            case "updateVirtualGroup"  -> new AuditMeta(AuditAction.DATA_UPDATED, "VIRTUAL_GROUP", groupId);
            case "deleteVirtualGroup"  -> new AuditMeta(AuditAction.DATA_DELETED, "VIRTUAL_GROUP", groupId);
            case "addMember"           -> new AuditMeta(AuditAction.ROLE_ASSIGNED, "VIRTUAL_GROUP", groupId);
            case "removeMember"        -> new AuditMeta(AuditAction.ROLE_UNASSIGNED, "VIRTUAL_GROUP", groupId);
            case "activateGroup"       -> new AuditMeta(AuditAction.DATA_UPDATED, "VIRTUAL_GROUP", groupId);
            case "deactivateGroup"     -> new AuditMeta(AuditAction.DATA_UPDATED, "VIRTUAL_GROUP", groupId);
            case "claimTask"           -> new AuditMeta(AuditAction.DATA_UPDATED, "TASK", groupId);
            case "delegateTask"        -> new AuditMeta(AuditAction.DATA_UPDATED, "TASK", groupId);
            default                    -> new AuditMeta(AuditAction.DATA_QUERIED, "VIRTUAL_GROUP", groupId);
        };
    }

    private AuditMeta resolveAuthMeta(String method, Object[] args) {
        if ("login".equals(method)) {
            String username = extractField(args.length > 0 ? args[0] : null, "username");
            return new AuditMeta(AuditAction.USER_LOGIN, "AUTH", username);
        }
        return new AuditMeta(AuditAction.USER_LOGOUT, "AUTH", null);
    }


    // =========================================================================
    // Old value — fetch entity state BEFORE the operation
    // =========================================================================

    private String fetchOldValue(AuditMeta meta) {
        if (meta.resourceId == null) return null;
        if (meta.action == AuditAction.DATA_QUERIED
                || meta.action == AuditAction.DATA_CREATED
                || meta.action == AuditAction.ROLE_CREATED
                || meta.action == AuditAction.USER_CREATED
                || meta.action == AuditAction.USER_LOGIN
                || meta.action == AuditAction.USER_LOGOUT
                || meta.action == AuditAction.DATA_IMPORTED) {
            return null;
        }
        try {
            Object entity = switch (meta.resourceType) {
                case "USER" -> userRepository.findById(meta.resourceId).orElse(null);
                case "ROLE" -> roleRepository.findById(meta.resourceId).orElse(null);
                case "VIRTUAL_GROUP" -> virtualGroupRepository.findById(meta.resourceId).orElse(null);
                default -> null;
            };
            return entity != null ? toJson(entity) : null;
        } catch (Exception e) {
            log.debug("Could not fetch old value for audit (type={}, id={}): {}", meta.resourceType, meta.resourceId, e.getMessage());
            return null;
        }
    }

    /**
     * Fetch the current entity state from DB (call AFTER operation to get post-update state).
     * Same repository lookup as fetchOldValue but without the action-based skip logic.
     */
    private String fetchCurrentEntityState(AuditMeta meta) {
        if (meta.resourceId == null) return null;
        try {
            Object entity = switch (meta.resourceType) {
                case "USER"          -> userRepository.findById(meta.resourceId).orElse(null);
                case "ROLE"          -> roleRepository.findById(meta.resourceId).orElse(null);
                case "VIRTUAL_GROUP" -> virtualGroupRepository.findById(meta.resourceId).orElse(null);
                default              -> null;
            };
            return entity != null ? toJson(entity) : null;
        } catch (Exception e) {
            log.debug("Could not fetch entity state after update (type={}, id={}): {}",
                    meta.resourceType, meta.resourceId, e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // New value — from operation args or result
    // =========================================================================

    private String resolveNewValue(AuditMeta meta, Object[] args, Object result, boolean success) {
        if (!success) return null;
        if (meta.action == AuditAction.DATA_QUERIED) {
            return buildQueryDescription(args);
        }
        if (meta.action == AuditAction.USER_DELETED
                || meta.action == AuditAction.ROLE_DELETED
                || meta.action == AuditAction.DATA_DELETED) {
            return null;
        }
        // For creates, use the first non-String request-body argument (mask sensitive fields)
        for (Object arg : args) {
            if (arg != null && !(arg instanceof String)) {
                return toJsonMasked(arg);
            }
        }
        return args.length > 0 && args[0] != null ? String.valueOf(args[0]) : null;
    }

    // =========================================================================
    // Persist
    // =========================================================================

    // Auto-managed metadata fields that change on every save — exclude from diff
    private static final Set<String> METADATA_FIELDS = Set.of(
            "updatedAt", "createdAt", "timestamp", "version",
            "lastModifiedAt", "modifiedAt", "lastUpdatedAt", "updateTime", "createTime");

    private boolean isUpdateAction(AuditAction action) {
        return action == AuditAction.USER_UPDATED
                || action == AuditAction.ROLE_UPDATED
                || action == AuditAction.DATA_UPDATED
                || action == AuditAction.PASSWORD_RESET
                || action == AuditAction.PERMISSION_GRANTED;
    }

    /**
     * Compare two full-entity JSON strings and return a pair [oldDiff, newDiff]
     * containing only the fields whose values actually changed, excluding metadata fields.
     */
    private String[] computeDiff(String oldJson, String newJson) {
        try {
            TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
            Map<String, Object> oldMap = mapper.readValue(oldJson, typeRef);
            Map<String, Object> newMap = mapper.readValue(newJson, typeRef);

            Map<String, Object> oldDiff = new LinkedHashMap<>();
            Map<String, Object> newDiff = new LinkedHashMap<>();

            Set<String> allKeys = new LinkedHashSet<>(oldMap.keySet());
            allKeys.addAll(newMap.keySet());

            for (String key : allKeys) {
                if (METADATA_FIELDS.contains(key)) continue;
                String ov = mapper.writeValueAsString(oldMap.get(key));
                String nv = mapper.writeValueAsString(newMap.get(key));
                if (!ov.equals(nv)) {
                    oldDiff.put(key, oldMap.get(key));
                    newDiff.put(key, newMap.get(key));
                }
            }
            if (oldDiff.isEmpty()) {
                // No meaningful changes found — return originals so nothing is lost
                return new String[]{oldJson, newJson};
            }
            return new String[]{
                    mapper.writeValueAsString(oldDiff),
                    mapper.writeValueAsString(newDiff)
            };
        } catch (Exception e) {
            log.debug("Failed to compute audit diff: {}", e.getMessage());
            return new String[]{oldJson, newJson};
        }
    }

    private void persist(AuditMeta meta, String oldValue, String newValue,
                         boolean success, String failureReason) {
        // For DATA_QUERIED, skip if no meaningful query description was built
        if (meta.action == AuditAction.DATA_QUERIED && newValue == null) {
            return;
        }
        // For UPDATE operations, pre-compute diff so only changed fields are stored.
        // This avoids storing full entities and keeps audit logs compact and readable.
        if (isUpdateAction(meta.action) && oldValue != null && newValue != null) {
            String[] diff = computeDiff(oldValue, newValue);
            oldValue = diff[0];
            newValue = diff[1];
        }
        // Distinguish login vs login-failed
        AuditAction action = meta.action;
        if (action == AuditAction.USER_LOGIN && !success) {
            action = AuditAction.USER_LOGIN_FAILED;
        }
        AuditContextHolder.AuditContext ctx = AuditContextHolder.get();
        String userId   = ctx != null ? ctx.getUserId()   : "unknown";
        String userName = ctx != null ? ctx.getUserName() : "unknown";
        String ip       = ctx != null ? ctx.getIpAddress() : null;
        String ua       = ctx != null ? ctx.getUserAgent() : null;

        SecurityAuditComponent.AuditLogRequest req = new SecurityAuditComponent.AuditLogRequest();
        req.setAction(action);
        req.setResourceType(meta.resourceType);
        req.setResourceId(meta.resourceId);
        req.setUserId(userId);
        req.setUserName(userName);
        req.setIpAddress(ip);
        req.setUserAgent(ua);
        req.setOldValue(oldValue);
        req.setNewValue(newValue);
        req.setSuccess(success);
        req.setFailureReason(failureReason);

        securityAuditComponent.recordAudit(req);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private static final String[] SENSITIVE_FIELDS = {
        "password", "passwordHash", "newPassword", "oldPassword",
        "initialPassword", "confirmPassword", "secret", "token",
        "accessToken", "refreshToken", "apiKey"
    };

    /** Serialize but mask common sensitive fields */
    private String toJsonMasked(Object obj) {
        if (obj == null) return null;
        try {
            String json = mapper.writeValueAsString(obj);
            ObjectNode node = (ObjectNode) mapper.readTree(json);
            for (String field : SENSITIVE_FIELDS) {
                if (node.has(field)) node.put(field, "***");
            }
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            return toJson(obj);
        }
    }

    private String buildQueryDescription(Object[] args) {
        // Collect only meaningful (non-Pageable) string or object args
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg == null) continue;
            String typeName = arg.getClass().getSimpleName();
            // Skip framework types like Pageable, PageRequest, Sort
            if (typeName.contains("Pageable") || typeName.contains("PageRequest") || typeName.contains("Sort")) {
                continue;
            }
            if (arg instanceof String s) {
                if (!s.isBlank()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(s);
                }
            } else {
                try {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(mapper.writeValueAsString(arg));
                } catch (Exception ignored) {}
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String extractField(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            String json = mapper.writeValueAsString(obj);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(json, Map.class);
            Object val = map.get(fieldName);
            return val != null ? String.valueOf(val) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // Inner meta holder
    // =========================================================================

    private record AuditMeta(AuditAction action, String resourceType, String resourceId) {
        /** Returns true when this operation should NOT be recorded (e.g. routine list calls). */
        boolean shouldRecord() { return action != null; }

        /** Convenience factory for operations that should be silently skipped. */
        static AuditMeta skip() { return new AuditMeta(null, null, null); }
    }
}
