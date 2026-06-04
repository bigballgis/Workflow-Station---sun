package com.admin.audit;

import com.admin.bi.repository.BiDashboardAssignmentRepository;
import com.admin.bi.repository.BiDashboardRegistryRepository;
import com.admin.bi.repository.BiRbacMappingRepository;
import com.admin.component.SecurityAuditComponent;
import com.admin.enums.AuditAction;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RelationTableDefinitionRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;;

/**
 * AOP aspect that automatically records audit logs for all mutating operations
 * across all admin controllers, using the unified 4-category action model:
 *   CREATE – new record added
 *   UPDATE – existing record modified (config change, toggle, deploy, login/logout, etc.)
 *   DELETE – record removed
 *   QUERY  – read-only lookup (recorded selectively to avoid noise)
 */
@Slf4j
@Aspect
@Component
public class AdminAuditAspect {

    private final SecurityAuditComponent securityAuditComponent;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VirtualGroupRepository virtualGroupRepository;
    private final RelationTableDefinitionRepository relationTableDefinitionRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final BiDashboardRegistryRepository biDashboardRegistryRepository;
    private final BiDashboardAssignmentRepository biDashboardAssignmentRepository;
    private final BiRbacMappingRepository biRbacMappingRepository;
    private final TransactionTemplate auditTxTemplate;
    private final ObjectMapper mapper;

    public AdminAuditAspect(SecurityAuditComponent securityAuditComponent,
                             UserRepository userRepository,
                             RoleRepository roleRepository,
                             VirtualGroupRepository virtualGroupRepository,
                             RelationTableDefinitionRepository relationTableDefinitionRepository,
                             BusinessUnitRepository businessUnitRepository,
                             BiDashboardRegistryRepository biDashboardRegistryRepository,
                             BiDashboardAssignmentRepository biDashboardAssignmentRepository,
                             BiRbacMappingRepository biRbacMappingRepository,
                             PlatformTransactionManager transactionManager,
                             ObjectMapper objectMapper) {
        this.securityAuditComponent = securityAuditComponent;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.virtualGroupRepository = virtualGroupRepository;
        this.relationTableDefinitionRepository = relationTableDefinitionRepository;
        this.businessUnitRepository = businessUnitRepository;
        this.biDashboardRegistryRepository = biDashboardRegistryRepository;
        this.biDashboardAssignmentRepository = biDashboardAssignmentRepository;
        this.biRbacMappingRepository = biRbacMappingRepository;
        // REQUIRES_NEW + readOnly: isolate audit lookups from the controller's
        // Hibernate session so eager-fetching lazy collections (for JSON
        // serialisation) does not pollute the caller's persistence context.
        TransactionTemplate tpl = new TransactionTemplate(transactionManager);
        tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tpl.setReadOnly(true);
        this.auditTxTemplate = tpl;
        this.mapper = objectMapper.copy()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    // =========================================================================
    // Pointcuts
    // =========================================================================

    @Around("within(com.admin.controller.UserController)")
    public Object auditUser(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "USER");
    }

    @Around("within(com.admin.controller.RoleController) "
            + "&& !execution(* *.getRoleMembers(..)) "
            + "&& !execution(* *.getRoleMembersPaged(..)) "
            + "&& !execution(* *.getMemberCount(..)) "
            + "&& !execution(* *.getRoleHistory(..)) "
            + "&& !execution(* *.getRoleHistoryPaged(..))")
    public Object auditRole(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "ROLE");
    }

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

    @Around("within(com.admin.controller.AuthController) "
            + "&& (execution(* *.login(..)) || execution(* *.logout(..)))")
    public Object auditAuth(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "AUTH");
    }

    @Around("within(com.admin.controller.RelationTableStructureController) "
            + "&& !execution(* *.getTableList(..)) "
            + "&& !execution(* *.getTableById(..)) "
            + "&& !execution(* *.getVersionHistory(..)) "
            + "&& !execution(* *.getAccessConfig(..))")
    public Object auditRelationTableStructure(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "RELATION_TABLE");
    }

    @Around("within(com.admin.controller.BusinessUnitController) "
            + "&& !execution(* *.listBusinessUnits(..)) "
            + "&& !execution(* *.getOrganizationTree(..)) "
            + "&& !execution(* *.getBusinessUnit(..)) "
            + "&& !execution(* *.getChildBusinessUnits(..)) "
            + "&& !execution(* *.getBusinessUnitMembers(..)) "
            + "&& !execution(* *.searchBusinessUnits(..))")
    public Object auditBusinessUnit(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "BUSINESS_UNIT");
    }

    @Around("within(com.admin.controller.BusinessUnitRoleController) "
            + "&& !execution(* *.getBoundRoles(..))")
    public Object auditBusinessUnitRole(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "BUSINESS_UNIT_ROLE");
    }

    @Around("within(com.admin.controller.RelationTableDataController) "
            + "&& (execution(* *.addData(..)) "
            + "|| execution(* *.updateData(..)) "
            + "|| execution(* *.deleteData(..)) "
            + "|| execution(* *.changeStatus(..)))")
    public Object auditRelationTableData(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "RELATION_TABLE_DATA");
    }

    @Around("within(com.admin.bi.controller.BiDashboardRegistryController) "
            + "&& !execution(* *.listDashboards(..)) "
            + "&& !execution(* *.getDashboard(..))")
    public Object auditBiDashboardRegistry(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "BI_DASHBOARD");
    }

    @Around("within(com.admin.bi.controller.BiDashboardAssignmentController) "
            + "&& !execution(* *.listAssignments(..)) "
            + "&& !execution(* *.getUserDashboards(..))")
    public Object auditBiDashboardAssignment(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "BI_ASSIGNMENT");
    }

    @Around("within(com.admin.bi.controller.BiRbacMappingController) "
            + "&& !execution(* *.listSupersetRoles(..)) "
            + "&& !execution(* *.listMappings(..)) "
            + "&& !execution(* *.listUnmappedRoles(..))")
    public Object auditBiRbacMapping(ProceedingJoinPoint pjp) throws Throwable {
        return audit(pjp, "BI_RBAC");
    }

    // =========================================================================
    // Core audit logic
    // =========================================================================

    private Object audit(ProceedingJoinPoint pjp, String domain) throws Throwable {
        String methodName = pjp.getSignature().getName();
        Object[] args = pjp.getArgs();

        AuditMeta meta = resolveMeta(domain, methodName, args);
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
    // Meta resolution
    // =========================================================================

    private AuditMeta resolveMeta(String domain, String methodName, Object[] args) {
        String firstArg = args.length > 0 && args[0] instanceof String ? (String) args[0] : null;

        return switch (domain) {
            case "USER"              -> resolveUserMeta(methodName, firstArg, args);
            case "ROLE"              -> resolveRoleMeta(methodName, firstArg, args);
            case "VIRTUAL_GROUP"     -> resolveVgMeta(methodName, firstArg, args);
            case "AUTH"              -> resolveAuthMeta(methodName, args);
            case "RELATION_TABLE"      -> resolveRelationTableMeta(methodName, args);
            case "RELATION_TABLE_DATA" -> resolveRelationTableDataMeta(methodName, args);
            case "BUSINESS_UNIT"       -> resolveBusinessUnitMeta(methodName, args);
            case "BUSINESS_UNIT_ROLE"  -> resolveBusinessUnitRoleMeta(methodName, args);
            case "BI_DASHBOARD"        -> resolveBiDashboardMeta(methodName, args);
            case "BI_ASSIGNMENT"       -> resolveBiAssignmentMeta(methodName, args);
            case "BI_RBAC"             -> resolveBiRbacMeta(methodName, args);
            default -> new AuditMeta(AuditAction.QUERY, domain, null);
        };
    }

    private AuditMeta resolveUserMeta(String method, String userId, Object[] args) {
        return switch (method) {
            case "createUser"       -> new AuditMeta(AuditAction.CREATE, "USER", null);
            case "batchImport"      -> new AuditMeta(AuditAction.CREATE, "USER", null);
            case "updateUser"       -> new AuditMeta(AuditAction.UPDATE, "USER", userId);
            case "updateUserStatus" -> new AuditMeta(AuditAction.UPDATE, "USER", userId);
            case "resetPassword"    -> new AuditMeta(AuditAction.UPDATE, "USER", userId);
            case "deleteUser"       -> new AuditMeta(AuditAction.DELETE, "USER", userId);
            case "getUser"          -> new AuditMeta(AuditAction.QUERY,  "USER", userId);
            case "listUsers"        -> AuditMeta.skip();
            default                 -> AuditMeta.skip();
        };
    }

    private AuditMeta resolveRoleMeta(String method, String roleId, Object[] args) {
        return switch (method) {
            case "createRole"           -> new AuditMeta(AuditAction.CREATE, "ROLE", null);
            case "updateRole"           -> new AuditMeta(AuditAction.UPDATE, "ROLE", roleId);
            case "deleteRole"           -> new AuditMeta(AuditAction.DELETE, "ROLE", roleId);
            case "configurePermissions" -> new AuditMeta(AuditAction.UPDATE, "ROLE", roleId);
            case "addMember"            -> new AuditMeta(AuditAction.CREATE, "ROLE", roleId);
            case "removeMember"         -> new AuditMeta(AuditAction.DELETE, "ROLE", roleId);
            case "batchAddMembers"      -> new AuditMeta(AuditAction.CREATE, "ROLE", roleId);
            case "batchRemoveMembers"   -> new AuditMeta(AuditAction.DELETE, "ROLE", roleId);
            default                     -> AuditMeta.skip();
        };
    }

    private AuditMeta resolveVgMeta(String method, String groupId, Object[] args) {
        return switch (method) {
            case "createVirtualGroup"  -> new AuditMeta(AuditAction.CREATE, "VIRTUAL_GROUP", null);
            case "updateVirtualGroup"  -> new AuditMeta(AuditAction.UPDATE, "VIRTUAL_GROUP", groupId);
            case "deleteVirtualGroup"  -> new AuditMeta(AuditAction.DELETE, "VIRTUAL_GROUP", groupId);
            case "addMember"           -> new AuditMeta(AuditAction.CREATE, "VIRTUAL_GROUP", groupId);
            case "removeMember"        -> new AuditMeta(AuditAction.DELETE, "VIRTUAL_GROUP", groupId);
            case "activateGroup"       -> new AuditMeta(AuditAction.UPDATE, "VIRTUAL_GROUP", groupId);
            case "deactivateGroup"     -> new AuditMeta(AuditAction.UPDATE, "VIRTUAL_GROUP", groupId);
            case "claimTask"           -> new AuditMeta(AuditAction.UPDATE, "TASK", groupId);
            case "delegateTask"        -> new AuditMeta(AuditAction.UPDATE, "TASK", groupId);
            default                    -> new AuditMeta(AuditAction.QUERY,  "VIRTUAL_GROUP", groupId);
        };
    }

    private AuditMeta resolveAuthMeta(String method, Object[] args) {
        if ("login".equals(method)) {
            String username = extractField(args.length > 0 ? args[0] : null, "username");
            return new AuditMeta(AuditAction.UPDATE, "AUTH", username);
        }
        return new AuditMeta(AuditAction.UPDATE, "AUTH", null);
    }

    private AuditMeta resolveRelationTableMeta(String method, Object[] args) {
        String tableId = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : null;
        return switch (method) {
            case "createTable"            -> new AuditMeta(AuditAction.CREATE, "RELATION_TABLE", null);
            case "updateTable"            -> new AuditMeta(AuditAction.UPDATE, "RELATION_TABLE", tableId);
            case "deleteTable"            -> new AuditMeta(AuditAction.DELETE, "RELATION_TABLE", tableId);
            case "toggleEnabled"          -> new AuditMeta(AuditAction.UPDATE, "RELATION_TABLE", tableId);
            case "togglePortalVisibility" -> new AuditMeta(AuditAction.UPDATE, "RELATION_TABLE", tableId);
            case "deploy"                 -> new AuditMeta(AuditAction.UPDATE, "RELATION_TABLE", tableId);
            case "rollback"               -> new AuditMeta(AuditAction.UPDATE, "RELATION_TABLE", tableId);
            case "addAccess"              -> new AuditMeta(AuditAction.CREATE, "RELATION_TABLE", tableId);
            case "batchSetAccess"         -> new AuditMeta(AuditAction.UPDATE, "RELATION_TABLE", tableId);
            case "removeAccess"           -> new AuditMeta(AuditAction.DELETE, "RELATION_TABLE", tableId);
            default                       -> AuditMeta.skip();
        };
    }

    private AuditMeta resolveBusinessUnitMeta(String method, Object[] args) {
        String unitId = args.length > 0 && args[0] instanceof String s ? s : null;
        return switch (method) {
            case "createBusinessUnit" -> new AuditMeta(AuditAction.CREATE, "BUSINESS_UNIT", null);
            case "updateBusinessUnit" -> new AuditMeta(AuditAction.UPDATE, "BUSINESS_UNIT", unitId);
            case "deleteBusinessUnit" -> new AuditMeta(AuditAction.DELETE, "BUSINESS_UNIT", unitId);
            case "moveBusinessUnit"   -> new AuditMeta(AuditAction.UPDATE, "BUSINESS_UNIT", unitId);
            case "addMember"          -> new AuditMeta(AuditAction.CREATE, "BUSINESS_UNIT", unitId);
            case "removeMember"       -> new AuditMeta(AuditAction.DELETE, "BUSINESS_UNIT", unitId);
            default                   -> AuditMeta.skip();
        };
    }

    private AuditMeta resolveBusinessUnitRoleMeta(String method, Object[] args) {
        String unitId = args.length > 0 && args[0] instanceof String s ? s : null;
        return switch (method) {
            case "bindRole"   -> new AuditMeta(AuditAction.CREATE, "BUSINESS_UNIT", unitId);
            case "unbindRole" -> new AuditMeta(AuditAction.DELETE, "BUSINESS_UNIT", unitId);
            default           -> AuditMeta.skip();
        };
    }

    private AuditMeta resolveRelationTableDataMeta(String method, Object[] args) {
        String tableId    = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : null;
        String rowId      = args.length > 1 && args[1] instanceof String s ? s : null;
        String resourceId = rowId != null ? tableId + ":" + rowId : tableId;
        return switch (method) {
            case "addData"      -> new AuditMeta(AuditAction.CREATE, "RELATION_TABLE_ROW", tableId);
            case "updateData"   -> new AuditMeta(AuditAction.UPDATE, "RELATION_TABLE_ROW", resourceId);
            case "deleteData"   -> new AuditMeta(AuditAction.DELETE, "RELATION_TABLE_ROW", resourceId);
            case "changeStatus" -> new AuditMeta(AuditAction.UPDATE, "RELATION_TABLE_ROW", resourceId);
            default             -> AuditMeta.skip();
        };
    }

    private AuditMeta resolveBiDashboardMeta(String method, Object[] args) {
        String id = args.length > 0 && args[0] instanceof String s ? s : null;
        return switch (method) {
            case "syncDashboards"        -> new AuditMeta(AuditAction.UPDATE, "BI_DASHBOARD", null);
            case "updateDashboard"       -> new AuditMeta(AuditAction.UPDATE, "BI_DASHBOARD", id);
            case "updateDashboardStatus" -> new AuditMeta(AuditAction.UPDATE, "BI_DASHBOARD", id);
            case "deleteDashboard"       -> new AuditMeta(AuditAction.DELETE, "BI_DASHBOARD", id);
            default                      -> AuditMeta.skip();
        };
    }

    private AuditMeta resolveBiAssignmentMeta(String method, Object[] args) {
        String id = args.length > 0 && args[0] instanceof String s ? s : null;
        return switch (method) {
            case "createAssignment" -> new AuditMeta(AuditAction.CREATE, "BI_ASSIGNMENT", null);
            case "updateAssignment" -> new AuditMeta(AuditAction.UPDATE, "BI_ASSIGNMENT", id);
            case "deleteAssignment" -> new AuditMeta(AuditAction.DELETE, "BI_ASSIGNMENT", id);
            default                 -> AuditMeta.skip();
        };
    }

    private AuditMeta resolveBiRbacMeta(String method, Object[] args) {
        String sysRoleId = args.length > 0 && args[0] instanceof String s ? s : null;
        return switch (method) {
            case "syncSupersetRoles" -> new AuditMeta(AuditAction.UPDATE, "BI_RBAC", null);
            case "createMapping"     -> new AuditMeta(AuditAction.CREATE, "BI_RBAC", null);
            case "updateMapping"     -> new AuditMeta(AuditAction.UPDATE, "BI_RBAC", sysRoleId);
            case "deleteMapping"     -> new AuditMeta(AuditAction.DELETE, "BI_RBAC", sysRoleId);
            default                  -> AuditMeta.skip();
        };
    }

    // =========================================================================
    // Old value — fetch entity state BEFORE the operation
    // =========================================================================

    private String fetchOldValue(AuditMeta meta) {
        if (meta.resourceId == null) return null;
        // Skip: CREATE and QUERY operations have no meaningful "before" state
        if (meta.action == AuditAction.CREATE || meta.action == AuditAction.QUERY) {
            return null;
        }
        return fetchEntityJson(meta.resourceType, meta.resourceId);
    }

    private String fetchCurrentEntityState(AuditMeta meta) {
        if (meta.resourceId == null) return null;
        return fetchEntityJson(meta.resourceType, meta.resourceId);
    }

    /**
     * Load an entity from its repository and serialise to JSON inside an
     * isolated read-only transaction so that loading lazy associations (for
     * JSON serialisation) does NOT pollute the caller's Hibernate session.
     * Returns null if not found or not supported.
     */
    private String fetchEntityJson(String resourceType, String resourceId) {
        try {
            return auditTxTemplate.execute(status -> {
                Object entity = switch (resourceType) {
                    case "USER"           -> userRepository.findById(resourceId).orElse(null);
                    case "ROLE"           -> roleRepository.findById(resourceId).orElse(null);
                    case "VIRTUAL_GROUP"  -> virtualGroupRepository.findById(resourceId).orElse(null);
                    case "BUSINESS_UNIT"  -> businessUnitRepository.findById(resourceId).orElse(null);
                    case "RELATION_TABLE" -> parseLong(resourceId)
                            .flatMap(relationTableDefinitionRepository::findById)
                            .orElse(null);
                    case "BI_DASHBOARD"   -> biDashboardRegistryRepository.findById(resourceId).orElse(null);
                    case "BI_ASSIGNMENT"  -> biDashboardAssignmentRepository.findById(resourceId).orElse(null);
                    case "BI_RBAC"        -> biRbacMappingRepository.findById(resourceId).orElse(null);
                    // RELATION_TABLE_ROW uses a composite id (tableId:rowId) and row data is
                    // schema-less — skip DB lookup and rely on response body / request args.
                    default               -> null;
                };
                return entity != null ? toJson(entity) : null;
            });
        } catch (Throwable e) {
            log.debug("Could not fetch audit entity state (type={}, id={}): {}",
                    resourceType, resourceId, e.getMessage());
            return null;
        }
    }

    private static java.util.Optional<Long> parseLong(String s) {
        try { return java.util.Optional.of(Long.valueOf(s)); }
        catch (NumberFormatException e) { return java.util.Optional.empty(); }
    }

    // =========================================================================
    // New value — from operation args or result
    // =========================================================================

    private String resolveNewValue(AuditMeta meta, Object[] args, Object result, boolean success) {
        if (!success) return null;
        if (meta.action == AuditAction.QUERY) {
            return buildQueryDescription(args);
        }
        if (meta.action == AuditAction.DELETE) {
            return null;
        }
        // For CREATE, prefer the controller's response body — it contains the full
        // persisted entity (id, server-generated timestamps, audit fields, ...)
        // which is more useful than the inbound request payload.
        if (meta.action == AuditAction.CREATE) {
            Object body = unwrapBody(result);
            if (body != null) return toJsonMasked(body);
        }
        for (Object arg : args) {
            if (arg != null && !(arg instanceof String) && !(arg instanceof Number) && !(arg instanceof Boolean)) {
                return toJsonMasked(arg);
            }
        }
        return null;
    }

    /** Unwrap Spring {@link ResponseEntity#getBody()} if present; otherwise return the raw result. */
    private Object unwrapBody(Object result) {
        if (result == null) return null;
        if (result instanceof ResponseEntity<?> re) {
            return re.getBody();
        }
        return result;
    }

    // =========================================================================
    // Persist
    // =========================================================================

    /**
     * Fields that are auto-managed by the framework (Spring Data JPA Auditing,
     * Hibernate @Version, etc.) and therefore have no semantic meaning when
     * comparing "before" vs "after". They are excluded from the diff so the
     * audit detail view only shows the user-meaningful changes.
     */
    private static final Set<String> METADATA_FIELDS = Set.of(
            // timestamps
            "updatedAt", "createdAt", "timestamp",
            "lastModifiedAt", "lastModifiedDate", "modifiedAt",
            "lastUpdatedAt", "updateTime", "createTime",
            "createdDate",
            // user trace
            "updatedBy", "createdBy",
            "lastModifiedBy", "modifiedBy",
            "createBy", "updateBy",
            // optimistic lock
            "version");

    private boolean isUpdateAction(AuditAction action) {
        return action == AuditAction.UPDATE;
    }

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
        if (meta.action == AuditAction.QUERY && newValue == null) {
            return;
        }
        if (isUpdateAction(meta.action) && oldValue != null && newValue != null) {
            String[] diff = computeDiff(oldValue, newValue);
            oldValue = diff[0];
            newValue = diff[1];
        }

        AuditContextHolder.AuditContext ctx = AuditContextHolder.get();
        String userId = AuditActorResolver.resolveUserId(ctx);
        String userName = AuditActorResolver.resolveUserName(ctx, userId, userRepository);
        if (AuditActorResolver.isUnknown(userId)) {
            userId = "unknown";
        }
        if (AuditActorResolver.isUnknown(userName)) {
            userName = "unknown";
        }

        // For auth operations (login/logout), user is not yet authenticated in context
        if ("AUTH".equals(meta.resourceType) && "unknown".equals(userId)) {
            userId   = meta.resourceId != null ? meta.resourceId : "unknown";
            userName = "unknown".equals(userName) && meta.resourceId != null ? meta.resourceId : userName;
        }

        String ip = ctx != null ? ctx.getIpAddress() : null;
        String ua = ctx != null ? ctx.getUserAgent() : null;

        SecurityAuditComponent.AuditLogRequest req = new SecurityAuditComponent.AuditLogRequest();
        req.setAction(meta.action);
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
        } catch (Throwable e) {
            // NOTE: never fall back to obj.toString() here — Lombok @Data on
            // bidirectional JPA entities (e.g. RelationTableDefinition <-> 
            // RelationFieldDefinition) produces infinite-recursion toString
            // which blows the stack.
            log.debug("Failed to serialize audit payload ({}): {}",
                    obj.getClass().getSimpleName(), e.getMessage());
            return "{\"_class\":\"" + obj.getClass().getSimpleName() + "\",\"_error\":\"serialize_failed\"}";
        }
    }

    private static final String[] SENSITIVE_FIELDS = {
        "password", "passwordHash", "newPassword", "oldPassword",
        "initialPassword", "confirmPassword", "secret", "token",
        "accessToken", "refreshToken", "apiKey"
    };

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
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg == null) continue;
            String typeName = arg.getClass().getSimpleName();
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
                } catch (Exception e) { log.debug("Failed to serialize audit argument: {}", e.getMessage()); }
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
        boolean shouldRecord() { return action != null; }
        static AuditMeta skip() { return new AuditMeta(null, null, null); }
    }
}
