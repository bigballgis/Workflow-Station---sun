package com.workflow.service;

import com.workflow.client.AdminCenterClient;
import com.workflow.enums.AssigneeType;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 任务处理人解析服务
 * 根据 AssigneeType 解析实际的处理人或候选人列表
 * 
 * 支持9种标准分配类型：
 * - 直接分配（3种）：FUNCTION_MANAGER, ENTITY_MANAGER, INITIATOR
 * - 认领类型（6种）：CURRENT_BU_ROLE, CURRENT_PARENT_BU_ROLE, INITIATOR_BU_ROLE, 
 *                   INITIATOR_PARENT_BU_ROLE, FIXED_BU_ROLE, BU_UNBOUNDED_ROLE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAssigneeResolver {
    
    private final AdminCenterClient adminCenterClient;
    
    /**
     * 解析结果
     */
    @Data
    @Builder
    public static class ResolveResult {
        /** 直接分配的处理人ID（如果是直接分配类型） */
        private String assignee;
        /** 候选人ID列表（如果是认领类型） */
        private List<String> candidateUsers;
        /** 是否需要认领 */
        private boolean requiresClaim;
        /** 分配类型 */
        private AssigneeType assigneeType;
        /** 错误信息（如果解析失败） */
        private String errorMessage;
    }
    
    /**
     * 解析任务处理人（新版本，支持9种分配类型）
     * 
     * @param assigneeTypeCode 分配类型代码
     * @param roleId 角色ID（6种角色类型需要）
     * @param businessUnitId 业务单元ID（FIXED_BU_ROLE需要）
     * @param initiatorId 流程发起人ID
     * @param currentUserId 当前处理人ID（用于基于当前人的分配）
     * @return 解析结果
     */
    public ResolveResult resolve(String assigneeTypeCode, String roleId, 
                                  String businessUnitId, String initiatorId, 
                                  String currentUserId) {
        AssigneeType assigneeType = AssigneeType.fromCode(assigneeTypeCode);
        
        if (assigneeType == null) {
            log.warn("Unknown assignee type: {}", assigneeTypeCode);
            return ResolveResult.builder()
                    .errorMessage("Unknown assignee type: " + assigneeTypeCode)
                    .build();
        }
        
        return resolve(assigneeType, roleId, businessUnitId, initiatorId, currentUserId);
    }
    
    /**
     * 解析任务处理人
     */
    public ResolveResult resolve(AssigneeType assigneeType, String roleId, 
                                  String businessUnitId, String initiatorId, 
                                  String currentUserId) {
        log.info("Resolving assignee: type={}, roleId={}, businessUnitId={}, initiator={}, currentUser={}", 
                assigneeType, roleId, businessUnitId, initiatorId, currentUserId);
        
        // 验证必需参数
        String validationError = validateParameters(assigneeType, roleId, businessUnitId, initiatorId, currentUserId);
        if (validationError != null) {
            return ResolveResult.builder()
                    .assigneeType(assigneeType)
                    .requiresClaim(assigneeType.requiresClaim())
                    .errorMessage(validationError)
                    .build();
        }
        
        try {
            return switch (assigneeType) {
                // 直接分配类型
                case FUNCTION_MANAGER -> resolveFunctionManager(initiatorId);
                case ENTITY_MANAGER -> resolveEntityManager(initiatorId);
                case INITIATOR -> resolveInitiator(initiatorId);
                // 基于当前人业务单元的角色分配
                case CURRENT_BU_ROLE -> resolveCurrentBuRole(currentUserId, roleId);
                case CURRENT_PARENT_BU_ROLE -> resolveCurrentParentBuRole(currentUserId, roleId);
                // 基于发起人业务单元的角色分配
                case INITIATOR_BU_ROLE -> resolveInitiatorBuRole(initiatorId, roleId);
                case INITIATOR_PARENT_BU_ROLE -> resolveInitiatorParentBuRole(initiatorId, roleId);
                // 指定业务单元角色分配
                case FIXED_BU_ROLE -> resolveFixedBuRole(businessUnitId, roleId);
                // BU无关型角色分配
                case BU_UNBOUNDED_ROLE -> resolveBuUnboundedRole(roleId);
            };
        } catch (Exception e) {
            log.error("Failed to resolve assignee: type={}, error={}", assigneeType, e.getMessage());
            return ResolveResult.builder()
                    .assigneeType(assigneeType)
                    .requiresClaim(assigneeType.requiresClaim())
                    .errorMessage("Failed to resolve assignee: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 验证参数
     */
    private String validateParameters(AssigneeType assigneeType, String roleId, 
                                       String businessUnitId, String initiatorId, 
                                       String currentUserId) {
        // 验证 roleId
        if (assigneeType.requiresRoleId() && (roleId == null || roleId.isEmpty())) {
            return "Assignee type " + assigneeType.getName() + " requires a role ID";
        }
        
        // 验证 businessUnitId
        if (assigneeType.requiresBusinessUnitId() && (businessUnitId == null || businessUnitId.isEmpty())) {
            return "Assignee type " + assigneeType.getName() + " requires a business unit ID";
        }
        
        // 验证 initiatorId（发起人相关类型需要）
        if (assigneeType.isInitiatorBased() && (initiatorId == null || initiatorId.isEmpty())) {
            return "Assignee type " + assigneeType.getName() + " requires process initiator ID";
        }
        
        // 验证 currentUserId（当前人相关类型需要）
        if (assigneeType.isCurrentUserBased() && (currentUserId == null || currentUserId.isEmpty())) {
            return "Assignee type " + assigneeType.getName() + " requires current user ID";
        }
        
        return null;
    }

    // ==================== 直接分配类型 ====================
    
    /**
     * 1. 解析职能经理
     */
    private ResolveResult resolveFunctionManager(String initiatorId) {
        Map<String, Object> userInfo = adminCenterClient.getUserInfo(initiatorId);
        if (userInfo == null) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.FUNCTION_MANAGER)
                    .requiresClaim(false)
                    .errorMessage("Cannot get user info: " + initiatorId)
                    .build();
        }
        
        String functionManagerId = (String) userInfo.get("functionManagerId");
        if (functionManagerId == null || functionManagerId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.FUNCTION_MANAGER)
                    .requiresClaim(false)
                    .errorMessage("User has no function manager set: " + initiatorId)
                    .build();
        }
        
        log.info("Resolved function manager: {} for user: {}", functionManagerId, initiatorId);
        return ResolveResult.builder()
                .assignee(functionManagerId)
                .assigneeType(AssigneeType.FUNCTION_MANAGER)
                .requiresClaim(false)
                .build();
    }
    
    /**
     * 2. 解析实体经理
     */
    private ResolveResult resolveEntityManager(String initiatorId) {
        Map<String, Object> userInfo = adminCenterClient.getUserInfo(initiatorId);
        if (userInfo == null) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.ENTITY_MANAGER)
                    .requiresClaim(false)
                    .errorMessage("Cannot get user info: " + initiatorId)
                    .build();
        }
        
        String entityManagerId = (String) userInfo.get("entityManagerId");
        if (entityManagerId == null || entityManagerId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.ENTITY_MANAGER)
                    .requiresClaim(false)
                    .errorMessage("User has no entity manager set: " + initiatorId)
                    .build();
        }
        
        log.info("Resolved entity manager: {} for user: {}", entityManagerId, initiatorId);
        return ResolveResult.builder()
                .assignee(entityManagerId)
                .assigneeType(AssigneeType.ENTITY_MANAGER)
                .requiresClaim(false)
                .build();
    }
    
    /**
     * 3. 解析流程发起人
     */
    private ResolveResult resolveInitiator(String initiatorId) {
        log.info("Resolved initiator: {}", initiatorId);
        return ResolveResult.builder()
                .assignee(initiatorId)
                .assigneeType(AssigneeType.INITIATOR)
                .requiresClaim(false)
                .build();
    }


    // ==================== 基于当前人业务单元的角色分配 ====================
    
    /**
     * 4. 解析当前人业务单元角色
     * 分配给当前处理人所在业务单元中拥有指定角色的用户
     */
    private ResolveResult resolveCurrentBuRole(String currentUserId, String roleId) {
        // 获取当前用户的业务单元
        String businessUnitId = adminCenterClient.getUserBusinessUnitId(currentUserId);
        if (businessUnitId == null || businessUnitId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.CURRENT_BU_ROLE)
                    .requiresClaim(true)
                    .errorMessage("User has no business unit: " + currentUserId)
                    .build();
        }
        
        // 获取业务单元中拥有指定角色的用户
        List<String> candidates = adminCenterClient.getUsersByBusinessUnitAndRole(businessUnitId, roleId);
        if (candidates.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.CURRENT_BU_ROLE)
                    .requiresClaim(true)
                    .errorMessage("No users with role " + roleId + " in business unit " + businessUnitId)
                    .build();
        }
        
        log.info("Resolved current BU role: {} candidates for BU {} with role {}", 
                candidates.size(), businessUnitId, roleId);
        return ResolveResult.builder()
                .candidateUsers(candidates)
                .assigneeType(AssigneeType.CURRENT_BU_ROLE)
                .requiresClaim(true)
                .build();
    }
    
    /**
     * 5. 解析当前人上级业务单元角色
     * 分配给当前处理人上级业务单元中拥有指定角色的用户
     */
    private ResolveResult resolveCurrentParentBuRole(String currentUserId, String roleId) {
        // 获取当前用户的业务单元
        String businessUnitId = adminCenterClient.getUserBusinessUnitId(currentUserId);
        if (businessUnitId == null || businessUnitId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.CURRENT_PARENT_BU_ROLE)
                    .requiresClaim(true)
                    .errorMessage("User has no business unit: " + currentUserId)
                    .build();
        }
        
        // 获取父业务单元
        String parentBuId = adminCenterClient.getParentBusinessUnitId(businessUnitId);
        if (parentBuId == null || parentBuId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.CURRENT_PARENT_BU_ROLE)
                    .requiresClaim(true)
                    .errorMessage("Business unit " + businessUnitId + " has no parent unit")
                    .build();
        }
        
        // 获取父业务单元中拥有指定角色的用户
        List<String> candidates = adminCenterClient.getUsersByBusinessUnitAndRole(parentBuId, roleId);
        if (candidates.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.CURRENT_PARENT_BU_ROLE)
                    .requiresClaim(true)
                    .errorMessage("No users with role " + roleId + " in parent business unit " + parentBuId)
                    .build();
        }
        
        log.info("Resolved current parent BU role: {} candidates for parent BU {} with role {}", 
                candidates.size(), parentBuId, roleId);
        return ResolveResult.builder()
                .candidateUsers(candidates)
                .assigneeType(AssigneeType.CURRENT_PARENT_BU_ROLE)
                .requiresClaim(true)
                .build();
    }

    // ==================== 基于发起人业务单元的角色分配 ====================
    
    /**
     * 6. 解析发起人业务单元角色
     * 分配给流程发起人所在业务单元中拥有指定角色的用户
     */
    private ResolveResult resolveInitiatorBuRole(String initiatorId, String roleId) {
        // 获取发起人的业务单元
        String businessUnitId = adminCenterClient.getUserBusinessUnitId(initiatorId);
        if (businessUnitId == null || businessUnitId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.INITIATOR_BU_ROLE)
                    .requiresClaim(true)
                    .errorMessage("Initiator has no business unit: " + initiatorId)
                    .build();
        }
        
        // 获取业务单元中拥有指定角色的用户
        List<String> candidates = adminCenterClient.getUsersByBusinessUnitAndRole(businessUnitId, roleId);
        if (candidates.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.INITIATOR_BU_ROLE)
                    .requiresClaim(true)
                    .errorMessage("No users with role " + roleId + " in business unit " + businessUnitId)
                    .build();
        }
        
        log.info("Resolved initiator BU role: {} candidates for BU {} with role {}", 
                candidates.size(), businessUnitId, roleId);
        return ResolveResult.builder()
                .candidateUsers(candidates)
                .assigneeType(AssigneeType.INITIATOR_BU_ROLE)
                .requiresClaim(true)
                .build();
    }
    
    /**
     * 7. 解析发起人上级业务单元角色
     * 分配给流程发起人上级业务单元中拥有指定角色的用户
     */
    private ResolveResult resolveInitiatorParentBuRole(String initiatorId, String roleId) {
        // 获取发起人的业务单元
        String businessUnitId = adminCenterClient.getUserBusinessUnitId(initiatorId);
        if (businessUnitId == null || businessUnitId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.INITIATOR_PARENT_BU_ROLE)
                    .requiresClaim(true)
                    .errorMessage("Initiator has no business unit: " + initiatorId)
                    .build();
        }
        
        // 获取父业务单元
        String parentBuId = adminCenterClient.getParentBusinessUnitId(businessUnitId);
        if (parentBuId == null || parentBuId.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.INITIATOR_PARENT_BU_ROLE)
                    .requiresClaim(true)
                    .errorMessage("Business unit " + businessUnitId + " has no parent unit")
                    .build();
        }
        
        // 获取父业务单元中拥有指定角色的用户
        List<String> candidates = adminCenterClient.getUsersByBusinessUnitAndRole(parentBuId, roleId);
        if (candidates.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.INITIATOR_PARENT_BU_ROLE)
                    .requiresClaim(true)
                    .errorMessage("No users with role " + roleId + " in parent business unit " + parentBuId)
                    .build();
        }
        
        log.info("Resolved initiator parent BU role: {} candidates for parent BU {} with role {}", 
                candidates.size(), parentBuId, roleId);
        return ResolveResult.builder()
                .candidateUsers(candidates)
                .assigneeType(AssigneeType.INITIATOR_PARENT_BU_ROLE)
                .requiresClaim(true)
                .build();
    }

    // ==================== 指定业务单元角色分配 ====================
    
    /**
     * 8. 解析指定业务单元角色
     * 分配给指定业务单元中拥有指定角色的用户
     * 角色必须是该业务单元的准入角色
     */
    private ResolveResult resolveFixedBuRole(String businessUnitId, String roleId) {
        // 验证角色是否是业务单元的准入角色
        if (!adminCenterClient.isEligibleRole(businessUnitId, roleId)) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.FIXED_BU_ROLE)
                    .requiresClaim(true)
                    .errorMessage("Role " + roleId + " is not an eligible role for business unit " + businessUnitId)
                    .build();
        }
        
        // 获取业务单元中拥有指定角色的用户
        List<String> candidates = adminCenterClient.getUsersByBusinessUnitAndRole(businessUnitId, roleId);
        if (candidates.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.FIXED_BU_ROLE)
                    .requiresClaim(true)
                    .errorMessage("No users with role " + roleId + " in business unit " + businessUnitId)
                    .build();
        }
        
        log.info("Resolved fixed BU role: {} candidates for BU {} with role {}", 
                candidates.size(), businessUnitId, roleId);
        return ResolveResult.builder()
                .candidateUsers(candidates)
                .assigneeType(AssigneeType.FIXED_BU_ROLE)
                .requiresClaim(true)
                .build();
    }

    // ==================== BU无关型角色分配 ====================
    
    /**
     * 9. 解析BU无关型角色
     * 分配给拥有指定BU无关型角色的用户（通过虚拟组）
     */
    private ResolveResult resolveBuUnboundedRole(String roleId) {
        // 获取拥有该角色的用户（通过虚拟组）
        List<String> candidates = adminCenterClient.getUsersByUnboundedRole(roleId);
        if (candidates.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(AssigneeType.BU_UNBOUNDED_ROLE)
                    .requiresClaim(true)
                    .errorMessage("No users with role " + roleId + " (via virtual groups)")
                    .build();
        }
        
        log.info("Resolved BU unbounded role: {} candidates for role {}", candidates.size(), roleId);
        return ResolveResult.builder()
                .candidateUsers(candidates)
                .assigneeType(AssigneeType.BU_UNBOUNDED_ROLE)
                .requiresClaim(true)
                .build();
    }
    
    // ==================== 兼容旧版本的方法（已废弃） ====================
    
    /**
     * 解析任务处理人（旧版本，仅用于向后兼容）
     * @deprecated 使用 {@link #resolve(String, String, String, String, String)} 代替
     */
    @Deprecated
    public ResolveResult resolve(String assigneeTypeCode, String assigneeValue, String initiatorId) {
        AssigneeType assigneeType = AssigneeType.fromCode(assigneeTypeCode);
        
        if (assigneeType == null) {
            log.warn("Unknown assignee type: {}", assigneeTypeCode);
            return ResolveResult.builder()
                    .errorMessage("Unknown assignee type: " + assigneeTypeCode)
                    .build();
        }
        
        // 旧版本的 assigneeValue 可能是 roleId 或 businessUnitId
        // 根据类型判断如何使用
        String roleId = null;
        String businessUnitId = null;
        
        if (assigneeType.requiresRoleId()) {
            roleId = assigneeValue;
        }
        if (assigneeType.requiresBusinessUnitId()) {
            businessUnitId = assigneeValue;
        }
        
        // 对于旧类型，currentUserId 默认使用 initiatorId
        return resolve(assigneeType, roleId, businessUnitId, initiatorId, initiatorId);
    }
}
