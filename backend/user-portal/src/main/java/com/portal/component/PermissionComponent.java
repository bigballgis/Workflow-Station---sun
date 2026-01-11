package com.portal.component;

import com.portal.dto.PermissionRequestDto;
import com.portal.entity.PermissionRequest;
import com.portal.enums.PermissionRequestStatus;
import com.portal.enums.PermissionRequestType;
import com.portal.repository.PermissionRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限申请组件
 * 支持角色申请和虚拟组加入申请
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionComponent {

    private final PermissionRequestRepository permissionRequestRepository;
    private final RoleAccessComponent roleAccessComponent;
    private final VirtualGroupAccessComponent virtualGroupAccessComponent;

    // ==================== 新的权限申请方法 ====================

    /**
     * 获取用户可申请的业务角色（排除已拥有的）
     */
    public List<Map<String, Object>> getAvailableRoles(String userId) {
        // 获取所有业务角色
        List<Map<String, Object>> allRoles = roleAccessComponent.getBusinessRoles();
        
        // 获取用户已有的角色ID
        List<Map<String, Object>> userRoles = roleAccessComponent.getUserBusinessRoles(userId);
        Set<String> userRoleIds = userRoles.stream()
                .map(r -> (String) r.get("id"))
                .collect(Collectors.toSet());
        
        // 过滤掉已有的角色
        return allRoles.stream()
                .filter(r -> !userRoleIds.contains(r.get("id")))
                .collect(Collectors.toList());
    }

    /**
     * 获取用户可加入的虚拟组（排除已加入的）
     */
    public List<Map<String, Object>> getAvailableVirtualGroups(String userId) {
        // 获取所有虚拟组
        List<Map<String, Object>> allGroups = virtualGroupAccessComponent.getVirtualGroups();
        
        // 获取用户已加入的虚拟组ID
        List<Map<String, Object>> userGroups = virtualGroupAccessComponent.getUserVirtualGroups(userId);
        Set<String> userGroupIds = userGroups.stream()
                .map(g -> (String) g.get("groupId"))
                .collect(Collectors.toSet());
        
        // 过滤掉已加入的虚拟组
        return allGroups.stream()
                .filter(g -> !userGroupIds.contains(g.get("id")))
                .collect(Collectors.toList());
    }

    /**
     * 申请角色分配（自动批准）
     */
    public PermissionRequest requestRoleAssignment(String userId, String roleId, String organizationUnitId, String reason) {
        // 获取角色信息
        Map<String, Object> role = roleAccessComponent.getRoleById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: " + roleId);
        }
        
        // 获取组织单元信息
        Map<String, Object> orgUnit = roleAccessComponent.getDepartmentById(organizationUnitId);
        if (orgUnit == null) {
            throw new IllegalArgumentException("组织单元不存在: " + organizationUnitId);
        }
        
        // 创建申请记录
        PermissionRequest request = PermissionRequest.builder()
                .applicantId(userId)
                .requestType(PermissionRequestType.ROLE_ASSIGNMENT)
                .roleId(roleId)
                .roleName((String) role.get("name"))
                .organizationUnitId(organizationUnitId)
                .organizationUnitName((String) orgUnit.get("name"))
                .reason(reason)
                .status(PermissionRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        
        // 保存申请记录
        request = permissionRequestRepository.save(request);
        
        // 自动批准：调用 Admin Center API 分配角色
        boolean success = roleAccessComponent.assignRoleToUser(userId, roleId, userId, reason);
        
        if (success) {
            request.setStatus(PermissionRequestStatus.APPROVED);
            request.setApproveTime(LocalDateTime.now());
            request.setApproveComment("系统自动批准");
            log.info("Role assignment auto-approved: user={}, role={}", userId, roleId);
        } else {
            request.setStatus(PermissionRequestStatus.REJECTED);
            request.setApproveTime(LocalDateTime.now());
            request.setApproveComment("角色分配失败");
            log.error("Role assignment failed: user={}, role={}", userId, roleId);
        }
        
        return permissionRequestRepository.save(request);
    }

    /**
     * 申请加入虚拟组（自动批准）
     */
    public PermissionRequest requestVirtualGroupJoin(String userId, String virtualGroupId, String reason) {
        // 获取虚拟组信息
        Map<String, Object> group = virtualGroupAccessComponent.getVirtualGroupById(virtualGroupId);
        if (group == null) {
            throw new IllegalArgumentException("虚拟组不存在: " + virtualGroupId);
        }
        
        // 检查是否已是成员
        if (virtualGroupAccessComponent.isUserInVirtualGroup(userId, virtualGroupId)) {
            throw new IllegalArgumentException("您已是该虚拟组成员");
        }
        
        // 创建申请记录
        PermissionRequest request = PermissionRequest.builder()
                .applicantId(userId)
                .requestType(PermissionRequestType.VIRTUAL_GROUP_JOIN)
                .virtualGroupId(virtualGroupId)
                .virtualGroupName((String) group.get("name"))
                .reason(reason)
                .status(PermissionRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        
        // 保存申请记录
        request = permissionRequestRepository.save(request);
        
        // 自动批准：调用 Admin Center API 添加用户到虚拟组
        boolean success = virtualGroupAccessComponent.addUserToVirtualGroup(userId, virtualGroupId, reason);
        
        if (success) {
            request.setStatus(PermissionRequestStatus.APPROVED);
            request.setApproveTime(LocalDateTime.now());
            request.setApproveComment("系统自动批准");
            log.info("Virtual group join auto-approved: user={}, group={}", userId, virtualGroupId);
        } else {
            request.setStatus(PermissionRequestStatus.REJECTED);
            request.setApproveTime(LocalDateTime.now());
            request.setApproveComment("加入虚拟组失败");
            log.error("Virtual group join failed: user={}, group={}", userId, virtualGroupId);
        }
        
        return permissionRequestRepository.save(request);
    }

    /**
     * 获取用户当前的角色列表
     */
    public List<Map<String, Object>> getUserCurrentRoles(String userId) {
        return roleAccessComponent.getUserBusinessRoles(userId);
    }

    /**
     * 获取用户当前的虚拟组成员身份
     */
    public List<Map<String, Object>> getUserCurrentVirtualGroups(String userId) {
        return virtualGroupAccessComponent.getUserVirtualGroups(userId);
    }

    /**
     * 获取所有部门列表
     */
    public List<Map<String, Object>> getDepartments() {
        return roleAccessComponent.getDepartments();
    }

    // ==================== 旧的方法（保留兼容） ====================

    /**
     * 获取用户当前权限
     * @deprecated 使用 getUserCurrentRoles 和 getUserCurrentVirtualGroups 替代
     */
    @Deprecated
    public List<Map<String, Object>> getUserPermissions(String userId) {
        List<Map<String, Object>> permissions = new ArrayList<>();
        
        // 添加角色权限
        List<Map<String, Object>> roles = getUserCurrentRoles(userId);
        for (Map<String, Object> role : roles) {
            Map<String, Object> perm = new HashMap<>();
            perm.put("id", role.get("id"));
            perm.put("name", role.get("name"));
            perm.put("type", "ROLE");
            permissions.add(perm);
        }
        
        // 添加虚拟组权限
        List<Map<String, Object>> groups = getUserCurrentVirtualGroups(userId);
        for (Map<String, Object> group : groups) {
            Map<String, Object> perm = new HashMap<>();
            perm.put("id", group.get("groupId"));
            perm.put("name", group.get("groupName"));
            perm.put("type", "VIRTUAL_GROUP");
            permissions.add(perm);
        }
        
        return permissions;
    }

    /**
     * 提交权限申请
     * @deprecated 使用 requestRoleAssignment 或 requestVirtualGroupJoin 替代
     */
    @Deprecated
    public PermissionRequest submitRequest(String userId, PermissionRequestDto dto) {
        if (dto.getType() == null) {
            throw new IllegalArgumentException("权限类型不能为空");
        }
        if (dto.getPermissions() == null || dto.getPermissions().isEmpty()) {
            throw new IllegalArgumentException("权限范围不能为空");
        }
        if (dto.getReason() == null || dto.getReason().isEmpty()) {
            throw new IllegalArgumentException("申请理由不能为空");
        }

        PermissionRequest request = new PermissionRequest();
        request.setApplicantId(userId);
        request.setRequestType(dto.getType());
        request.setPermissions(dto.getPermissions());
        request.setReason(dto.getReason());
        request.setValidFrom(dto.getValidFrom() != null ? dto.getValidFrom() : LocalDateTime.now());
        request.setValidTo(dto.getValidTo());
        request.setStatus(PermissionRequestStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());

        return permissionRequestRepository.save(request);
    }

    /**
     * 获取用户的权限申请记录
     */
    public Page<PermissionRequest> getMyRequests(String userId, PermissionRequestStatus status, Pageable pageable) {
        if (status != null) {
            List<PermissionRequest> list = permissionRequestRepository.findByApplicantIdAndStatus(userId, status);
            return new PageImpl<>(list, pageable, list.size());
        }
        return permissionRequestRepository.findByApplicantId(userId, pageable);
    }

    /**
     * 获取申请详情
     */
    public Optional<PermissionRequest> getRequestDetail(Long requestId) {
        return permissionRequestRepository.findById(requestId);
    }

    /**
     * 取消申请
     */
    public boolean cancelRequest(String userId, Long requestId) {
        Optional<PermissionRequest> requestOpt = permissionRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            return false;
        }
        PermissionRequest request = requestOpt.get();
        if (!request.getApplicantId().equals(userId)) {
            return false;
        }
        if (request.getStatus() != PermissionRequestStatus.PENDING) {
            return false;
        }
        permissionRequestRepository.delete(request);
        return true;
    }

    /**
     * 续期申请
     * @deprecated 新的权限模型不需要续期
     */
    @Deprecated
    public PermissionRequest renewPermission(String userId, String permissionId, LocalDateTime newValidTo, String reason) {
        PermissionRequest request = new PermissionRequest();
        request.setApplicantId(userId);
        request.setRequestType(PermissionRequestType.TEMPORARY);
        request.setPermissions(Arrays.asList(permissionId));
        request.setReason("续期申请: " + reason);
        request.setValidFrom(LocalDateTime.now());
        request.setValidTo(newValidTo);
        request.setStatus(PermissionRequestStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());

        return permissionRequestRepository.save(request);
    }

    /**
     * 检查权限是否即将过期
     * @deprecated 新的权限模型不需要过期检查
     */
    @Deprecated
    public List<Map<String, Object>> getExpiringPermissions(String userId, int daysBeforeExpiry) {
        // 新的权限模型不需要过期检查，返回空列表
        return Collections.emptyList();
    }
}
