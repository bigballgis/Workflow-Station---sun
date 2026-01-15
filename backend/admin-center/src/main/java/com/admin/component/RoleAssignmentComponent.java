package com.admin.component;

import com.admin.dto.request.CreateAssignmentRequest;
import com.admin.dto.response.EffectiveUserResponse;
import com.admin.dto.response.RoleAssignmentResponse;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.RoleNotFoundException;
import com.admin.repository.RoleRepository;
import com.platform.security.dto.RoleSource;
import com.platform.security.entity.RoleAssignment;
import com.platform.security.enums.AssignmentTargetType;
import com.platform.security.repository.RoleAssignmentRepository;
import com.platform.security.resolver.TargetResolver;
import com.platform.security.resolver.TargetResolverFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色分配组件
 * 负责角色分配的创建、删除、查询和有效用户计算
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleAssignmentComponent {
    
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final RoleRepository roleRepository;
    private final TargetResolverFactory targetResolverFactory;
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * 创建角色分配
     */
    @Transactional
    public RoleAssignment createAssignment(CreateAssignmentRequest request, String operatorId) {
        log.info("Creating assignment: roleId={}, targetType={}, targetId={}, by={}", 
                request.getRoleId(), request.getTargetType(), request.getTargetId(), operatorId);
        
        // 验证角色存在
        if (!roleRepository.existsById(request.getRoleId())) {
            throw new RoleNotFoundException(request.getRoleId());
        }
        
        // 验证目标存在
        TargetResolver resolver = targetResolverFactory.getResolver(request.getTargetType());
        if (!resolver.targetExists(request.getTargetId())) {
            throw new AdminBusinessException("TARGET_NOT_FOUND", 
                    "目标不存在: " + request.getTargetType() + " - " + request.getTargetId());
        }
        
        // 检查重复分配
        if (roleAssignmentRepository.existsByRoleIdAndTargetTypeAndTargetId(
                request.getRoleId(), request.getTargetType(), request.getTargetId())) {
            throw new AdminBusinessException("DUPLICATE_ASSIGNMENT", "该分配已存在");
        }
        
        // 创建分配
        RoleAssignment assignment = RoleAssignment.builder()
                .id(UUID.randomUUID().toString())
                .roleId(request.getRoleId())
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .assignedAt(LocalDateTime.now())
                .assignedBy(operatorId)
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .build();
        
        RoleAssignment saved = roleAssignmentRepository.save(assignment);
        log.info("Assignment created: id={}", saved.getId());
        
        return saved;
    }
    
    /**
     * 删除角色分配
     */
    @Transactional
    public void deleteAssignment(String assignmentId, String operatorId) {
        log.info("Deleting assignment: id={}, by={}", assignmentId, operatorId);
        
        RoleAssignment assignment = roleAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AdminBusinessException("ASSIGNMENT_NOT_FOUND", "分配记录不存在"));
        
        roleAssignmentRepository.delete(assignment);
        log.info("Assignment deleted: id={}", assignmentId);
    }
    
    /**
     * 获取角色的所有分配记录
     */
    public List<RoleAssignmentResponse> getAssignmentsForRole(String roleId) {
        log.debug("Getting assignments for role: {}", roleId);
        
        List<RoleAssignment> assignments = roleAssignmentRepository.findByRoleId(roleId);
        
        return assignments.stream()
                .map(this::toAssignmentResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取角色的有效用户列表（包含来源信息）
     * 通过 sys_role_assignments 分配的用户（直接分配、部门、虚拟组等）
     */
    public List<EffectiveUserResponse> getEffectiveUsers(String roleId) {
        log.debug("Getting effective users for role: {}", roleId);
        
        // 用于存储用户ID到EffectiveUserResponse的映射（用于合并来源）
        Map<String, EffectiveUserResponse> userMap = new LinkedHashMap<>();
        
        // 查询通过 sys_role_assignments 分配的用户（包括直接分配、部门、虚拟组等）
        List<RoleAssignment> assignments = roleAssignmentRepository.findByRoleId(roleId);
        
        for (RoleAssignment assignment : assignments) {
            if (!assignment.isValid()) {
                continue; // 跳过无效的分配
            }
            
            TargetResolver resolver = targetResolverFactory.getResolver(assignment.getTargetType());
            var resolvedUsers = resolver.resolveUsers(assignment.getTargetId());
            
            String targetName;
            if (assignment.getTargetType() == AssignmentTargetType.USER) {
                targetName = "直接分配";
            } else {
                targetName = resolver.getTargetDisplayName(assignment.getTargetId());
            }
            
            for (var resolvedUser : resolvedUsers) {
                String userId = resolvedUser.getUserId();
                
                RoleSource source = RoleSource.builder()
                        .sourceType(assignment.getTargetType())
                        .sourceId(assignment.getTargetId())
                        .sourceName(targetName)
                        .assignmentId(assignment.getId())
                        .build();
                
                if (userMap.containsKey(userId)) {
                    userMap.get(userId).getSources().add(source);
                } else {
                    EffectiveUserResponse response = EffectiveUserResponse.builder()
                            .userId(userId)
                            .username(resolvedUser.getUsername())
                            .displayName(resolvedUser.getDisplayName())
                            .employeeId(resolvedUser.getEmployeeId())
                            .businessUnitId(resolvedUser.getDepartmentId())
                            .businessUnitName(resolvedUser.getDepartmentName())
                            .email(resolvedUser.getEmail())
                            .sources(new ArrayList<>(List.of(source)))
                            .build();
                    userMap.put(userId, response);
                }
            }
        }
        
        List<EffectiveUserResponse> result = new ArrayList<>(userMap.values());
        log.debug("Found {} effective users for role {}", result.size(), roleId);
        return result;
    }
    
    /**
     * 获取分配影响的用户数量
     */
    public long getEffectiveUserCount(String assignmentId) {
        RoleAssignment assignment = roleAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AdminBusinessException("ASSIGNMENT_NOT_FOUND", "分配记录不存在"));
        
        if (!assignment.isValid()) {
            return 0;
        }
        
        TargetResolver resolver = targetResolverFactory.getResolver(assignment.getTargetType());
        return resolver.resolveUsers(assignment.getTargetId()).size();
    }
    
    /**
     * 转换为响应DTO
     */
    private RoleAssignmentResponse toAssignmentResponse(RoleAssignment assignment) {
        TargetResolver resolver = targetResolverFactory.getResolver(assignment.getTargetType());
        String targetName = resolver.getTargetDisplayName(assignment.getTargetId());
        long effectiveCount = assignment.isValid() ? 
                resolver.resolveUsers(assignment.getTargetId()).size() : 0;
        
        String roleName = getRoleName(assignment.getRoleId());
        String assignedByName = getUserDisplayName(assignment.getAssignedBy());
        
        return RoleAssignmentResponse.builder()
                .id(assignment.getId())
                .roleId(assignment.getRoleId())
                .roleName(roleName)
                .targetType(assignment.getTargetType())
                .targetId(assignment.getTargetId())
                .targetName(targetName)
                .effectiveUserCount(effectiveCount)
                .assignedAt(assignment.getAssignedAt())
                .assignedBy(assignment.getAssignedBy())
                .assignedByName(assignedByName)
                .validFrom(assignment.getValidFrom())
                .validTo(assignment.getValidTo())
                .build();
    }
    
    /**
     * 获取角色名称
     */
    private String getRoleName(String roleId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM sys_roles WHERE id = ?",
                    String.class,
                    roleId
            );
        } catch (Exception e) {
            return roleId;
        }
    }
    
    /**
     * 获取用户显示名
     */
    private String getUserDisplayName(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT COALESCE(display_name, full_name, username) FROM sys_users WHERE id = ?",
                    String.class,
                    userId
            );
        } catch (Exception e) {
            return userId;
        }
    }
}
