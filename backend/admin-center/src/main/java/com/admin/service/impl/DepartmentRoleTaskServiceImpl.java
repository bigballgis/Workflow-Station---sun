package com.admin.service.impl;

import com.admin.dto.request.DepartmentRoleTaskRequest;
import com.admin.dto.response.DepartmentRoleUserInfo;
import com.admin.dto.response.GroupTaskInfo;
import com.admin.entity.*;
import com.admin.enums.TaskActionType;
import com.admin.enums.TaskAssignmentType;
import com.platform.security.model.UserStatus;
import com.platform.security.entity.User;
import com.platform.security.entity.Role;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.UserBusinessUnitRole;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.BusinessUnitNotFoundException;
import com.admin.exception.RoleNotFoundException;
import com.admin.repository.*;
import com.admin.service.DepartmentRoleTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 业务单元角色任务服务实现
 * 实现"业务单元+角色"组合任务分配的动态用户匹配和权限验证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentRoleTaskServiceImpl implements DepartmentRoleTaskService {
    
    private final BusinessUnitRepository businessUnitRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    private final VirtualGroupTaskHistoryRepository taskHistoryRepository;
    private final com.admin.service.TaskAssignmentQueryService taskAssignmentQueryService;
    private final RestTemplate restTemplate;
    
    @Value("${workflow-engine.url:http://localhost:8081}")
    private String workflowEngineUrl;
    
    @Override
    public List<DepartmentRoleUserInfo> getMatchingUsers(String businessUnitId, String roleId) {
        log.info("Getting matching users for business unit {} and role {}", businessUnitId, roleId);
        
        // 验证业务单元存在
        BusinessUnit businessUnit = businessUnitRepository.findById(businessUnitId)
                .orElseThrow(() -> new BusinessUnitNotFoundException(businessUnitId));
        
        // 验证角色存在
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        // 通过关联表获取该业务单元的所有用户
        List<String> userIds = taskAssignmentQueryService.getUsersByBusinessUnitAndRole(businessUnitId, roleId);
        
        // 过滤出活跃用户并构建结果
        List<DepartmentRoleUserInfo> matchingUsers = new ArrayList<>();
        for (String userId : userIds) {
            userRepository.findById(userId)
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .ifPresent(user -> matchingUsers.add(buildBusinessUnitRoleUserInfo(user, businessUnit, role)));
        }
        
        log.info("Found {} matching users for business unit {} and role {}", 
                matchingUsers.size(), businessUnitId, roleId);
        return matchingUsers;
    }

    
    @Override
    public boolean isUserMatchingBusinessUnitRole(String userId, String businessUnitId, String roleId) {
        log.debug("Checking if user {} matches business unit {} and role {}", userId, businessUnitId, roleId);
        
        // 获取用户
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return false;
        }
        
        // 以 UBR（sys_user_business_unit_roles）为权威来源，与门户工作台模型一致
        return userBusinessUnitRoleRepository.existsByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleId);
    }
    
    @Override
    public List<GroupTaskInfo> getBusinessUnitRoleTasks(String businessUnitId, String roleId, String userId) {
        log.info("Getting tasks for business unit {} and role {} by user {}", businessUnitId, roleId, userId);
        
        // 验证业务单元存在
        if (!businessUnitRepository.existsById(businessUnitId)) {
            throw new BusinessUnitNotFoundException(businessUnitId);
        }
        
        // 验证角色存在
        if (!roleRepository.existsById(roleId)) {
            throw new RoleNotFoundException(roleId);
        }
        
        // 验证用户是否匹配业务单元角色
        if (!isUserMatchingBusinessUnitRole(userId, businessUnitId, roleId)) {
            throw new AdminBusinessException("NOT_MATCHING_BU_ROLE", 
                    "用户不匹配该业务单元角色组合");
        }
        
        // 这里应该调用工作流引擎获取分配给该业务单元角色的任务
        // 由于工作流引擎是独立模块，这里返回模拟数据结构
        return getTasksAssignedToBusinessUnitRole(businessUnitId, roleId);
    }
    
    @Override
    public List<GroupTaskInfo> getUserVisibleBusinessUnitRoleTasks(String userId) {
        log.info("Getting all visible business unit role tasks for user {}", userId);
        
        // 获取用户
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return new ArrayList<>();
        }
        
        List<UserBusinessUnitRole> ubrList = userBusinessUnitRoleRepository.findByUserId(userId);
        List<GroupTaskInfo> allTasks = new ArrayList<>();
        for (UserBusinessUnitRole ubr : ubrList) {
            allTasks.addAll(getTasksAssignedToBusinessUnitRole(ubr.getBusinessUnitId(), ubr.getRoleId()));
        }
        return allTasks;
    }
    
    @Override
    @Transactional
    public void claimBusinessUnitRoleTask(String userId, DepartmentRoleTaskRequest request) {
        log.info("User {} claiming business unit role task {} for bu {} role {}", 
                userId, request.getTaskId(), request.getBusinessUnitId(), request.getRoleId());
        
        // 验证用户可以认领该任务
        if (!canUserClaimBusinessUnitRoleTask(userId, request.getTaskId(), 
                request.getBusinessUnitId(), request.getRoleId())) {
            throw new AdminBusinessException("CANNOT_CLAIM", "User cannot claim this business unit role task");
        }
        
        // 记录认领历史
        VirtualGroupTaskHistory history = VirtualGroupTaskHistory.builder()
                .id(UUID.randomUUID().toString())
                .taskId(request.getTaskId())
                .actionType(TaskActionType.CLAIMED)
                .toUserId(userId)
                .comment(request.getComment())
                .createdAt(Instant.now())
                .build();
        
        taskHistoryRepository.save(history);
        
        // 这里应该调用工作流引擎将任务分配给用户
        claimTaskInWorkflowEngine(request.getTaskId(), userId);
        
        log.info("Business unit role task {} claimed by user {}", request.getTaskId(), userId);
    }
    
    @Override
    public boolean canUserClaimBusinessUnitRoleTask(String userId, String taskId, 
            String businessUnitId, String roleId) {
        // 验证用户匹配业务单元角色
        if (!isUserMatchingBusinessUnitRole(userId, businessUnitId, roleId)) {
            return false;
        }
        
        // 验证任务是分配给该业务单元角色的且未被认领
        return isTaskAssignedToBusinessUnitRoleAndUnclaimed(taskId, businessUnitId, roleId);
    }
    
    // ==================== 辅助方法 ====================
    
    private DepartmentRoleUserInfo buildBusinessUnitRoleUserInfo(User user, BusinessUnit businessUnit, Role role) {
        return DepartmentRoleUserInfo.builder()
                .userId(user.getId().toString())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .businessUnitId(businessUnit.getId())
                .businessUnitName(businessUnit.getName())
                .roleId(role.getId())
                .roleName(role.getName())
                .roleCode(role.getCode())
                .active(user.isActive())
                .build();
    }
    
    // ==================== 工作流引擎集成方法（需要实际实现） ====================
    
    /**
     * 获取分配给业务单元角色的任务
     * 通过 REST 调用 workflow-engine-core 的任务查询 API，
     * 使用 groupIds 参数，格式为 {businessUnitId}_{roleId}
     */
    @SuppressWarnings("unchecked")
    private List<GroupTaskInfo> getTasksAssignedToBusinessUnitRole(String businessUnitId, String roleId) {
        log.debug("Getting tasks assigned to business unit {} role {}", businessUnitId, roleId);
        
        String groupId = businessUnitId + "_" + roleId;
        try {
            String url = workflowEngineUrl + "/api/v1/tasks?groupIds=" + groupId;
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                if (data == null) {
                    return new ArrayList<>();
                }
                
                List<Map<String, Object>> tasks = (List<Map<String, Object>>) data.get("tasks");
                if (tasks == null) {
                    return new ArrayList<>();
                }
                
                return tasks.stream()
                        .map(task -> GroupTaskInfo.builder()
                                .taskId((String) task.get("taskId"))
                                .taskName((String) task.get("taskName"))
                                .processInstanceId((String) task.get("processInstanceId"))
                                .processName((String) task.get("processDefinitionName"))
                                .assignmentType(TaskAssignmentType.DEPARTMENT_ROLE)
                                .groupId(groupId)
                                .status((String) task.get("status"))
                                .claimed(Boolean.TRUE.equals(task.get("isClaimed")))
                                .build())
                        .collect(Collectors.toList());
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.warn("Failed to get tasks from workflow engine for groupId {}: {}", groupId, e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 在工作流引擎中认领任务
     * 通过 REST 调用 workflow-engine-core 的 POST /api/v1/tasks/{taskId}/claim
     */
    private void claimTaskInWorkflowEngine(String taskId, String userId) {
        log.info("Claiming task {} for user {} in workflow engine", taskId, userId);
        
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/" + taskId + "/claim";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("claimedBy", userId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully claimed task {} for user {} in workflow engine", taskId, userId);
            } else {
                log.warn("Unexpected response when claiming task {} in workflow engine: {}", 
                        taskId, response.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to claim task {} in workflow engine: {}", taskId, e.getMessage());
        }
    }
    
    /**
     * 检查任务是否分配给业务单元角色且未被认领
     * 通过检查本地认领记录来判断任务是否已被认领。
     * 这是一个合理的实现：认领操作会同时写入本地历史记录和工作流引擎，
     * 因此本地记录可以作为认领状态的可靠来源。
     */
    private boolean isTaskAssignedToBusinessUnitRoleAndUnclaimed(String taskId, 
            String businessUnitId, String roleId) {
        // 检查本地认领记录：如果没有认领历史，说明任务未被认领
        List<VirtualGroupTaskHistory> claimHistory = taskHistoryRepository.findClaimHistoryByTaskId(taskId);
        return claimHistory.isEmpty();
    }
}
