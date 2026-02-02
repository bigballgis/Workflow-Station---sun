package com.admin.controller;

import com.admin.component.RolePermissionManagerComponent;
import com.admin.component.UserManagerComponent;
import com.admin.component.VirtualGroupManagerComponent;
import com.admin.dto.request.StatusUpdateRequest;
import com.admin.dto.request.UserCreateRequest;
import com.admin.dto.request.UserQueryRequest;
import com.admin.dto.request.UserUpdateRequest;
import com.admin.dto.response.BatchImportResult;
import com.admin.dto.response.PageResult;
import com.admin.dto.response.UserCreateResult;
import com.admin.dto.response.UserDetailInfo;
import com.admin.dto.response.UserInfo;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.Role;
import com.platform.security.entity.User;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.admin.enums.RoleType;
import com.admin.helper.RoleHelper;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.service.UserBusinessUnitService;
import com.admin.service.UserImportService;
import com.admin.service.UserPermissionService;
import com.admin.util.EntityTypeConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户的创建、查询、更新、删除等操作")
public class UserController {
    
    private final UserManagerComponent userManager;
    private final UserImportService userImportService;
    private final RolePermissionManagerComponent rolePermissionManager;
    private final VirtualGroupMemberRepository virtualGroupMemberRepository;
    private final VirtualGroupRepository virtualGroupRepository;
    private final UserBusinessUnitService userBusinessUnitService;
    private final UserPermissionService userPermissionService;
    private final RoleHelper roleHelper;
    
    @PostMapping
    @Operation(summary = "创建用户", description = "创建新用户，立即激活")
    public ResponseEntity<UserCreateResult> createUser(
            @RequestBody @Valid UserCreateRequest request) {
        UserCreateResult result = userManager.createUser(request);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/batch-import")
    @Operation(summary = "批量导入用户", description = "通过Excel或CSV文件批量导入用户")
    public ResponseEntity<BatchImportResult> batchImport(
            @RequestParam("file") MultipartFile file) {
        BatchImportResult result = userImportService.importUsers(file);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping
    @Operation(summary = "查询用户列表", description = "分页查询用户，支持关键词搜索和条件筛选")
    public ResponseEntity<PageResult<UserInfo>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String businessUnitId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        UserQueryRequest request = UserQueryRequest.builder()
                .keyword(keyword)
                .businessUnitId(businessUnitId)
                .status(status != null ? com.platform.security.model.UserStatus.valueOf(status) : null)
                .page(page)
                .size(size)
                .build();
        
        Page<UserInfo> users = userManager.listUsers(request);
        PageResult<UserInfo> result = PageResult.of(
                users.getContent(),
                users.getNumber(),
                users.getSize(),
                users.getTotalElements());
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{userId}")
    @Operation(summary = "获取用户详情", description = "根据用户ID获取用户详细信息，包含角色和登录历史")
    public ResponseEntity<UserDetailInfo> getUser(@PathVariable String userId) {
        UserDetailInfo userDetail = userManager.getUserDetail(userId);
        return ResponseEntity.ok(userDetail);
    }
    
    @PutMapping("/{userId}")
    @Operation(summary = "更新用户信息", description = "更新用户的基本信息")
    public ResponseEntity<Void> updateUser(
            @PathVariable String userId,
            @RequestBody @Valid UserUpdateRequest request) {
        userManager.updateUser(userId, request);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/{userId}/status")
    @Operation(summary = "更新用户状态", description = "更新用户状态（启用、禁用、锁定、解锁）")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable String userId,
            @RequestBody @Valid StatusUpdateRequest request) {
        userManager.updateUserStatus(userId, request.getStatus(), request.getReason());
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{userId}/reset-password")
    @Operation(summary = "重置用户密码", description = "重置用户密码并返回新密码")
    public ResponseEntity<String> resetPassword(@PathVariable String userId) {
        String newPassword = userManager.resetPassword(userId);
        return ResponseEntity.ok(newPassword);
    }
    
    @DeleteMapping("/{userId}")
    @Operation(summary = "删除用户", description = "删除指定用户")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        userManager.deleteUser(userId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/export-template")
    @Operation(summary = "下载导入模板", description = "下载用户批量导入的Excel模板")
    public ResponseEntity<byte[]> exportTemplate() {
        byte[] template = userImportService.generateImportTemplate();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=user_import_template.xlsx")
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(template);
    }
    
    @GetMapping("/{userId}/roles")
    @Operation(summary = "获取用户角色列表", description = "获取用户通过虚拟组继承的角色列表，可按类型筛选")
    public ResponseEntity<List<Map<String, Object>>> getUserRoles(
            @PathVariable String userId,
            @RequestParam(required = false) String type) {
        // 通过虚拟组获取用户角色（角色只能分配给虚拟组，不能直接分配给用户）
        List<Role> roles = userPermissionService.getUserRoles(userId);
        
        // 按类型筛选
        if (type != null && !type.isEmpty()) {
            // 支持 BUSINESS 作为特殊值，返回所有业务角色（BU_BOUNDED 和 BU_UNBOUNDED）
            if ("BUSINESS".equalsIgnoreCase(type)) {
                roles = roles.stream()
                        .filter(roleHelper::isBusinessRole)
                        .collect(Collectors.toList());
            } else {
                String roleTypeStr = EntityTypeConverter.fromRoleType(RoleType.valueOf(type));
                roles = roles.stream()
                        .filter(r -> roleTypeStr.equals(r.getType()))
                        .collect(Collectors.toList());
            }
        }
        
        // 转换为简单的Map格式
        List<Map<String, Object>> result = roles.stream()
                .map(r -> {
                    Map<String, Object> roleMap = new HashMap<>();
                    roleMap.put("id", r.getId());
                    roleMap.put("name", r.getName());
                    roleMap.put("code", r.getCode());
                    roleMap.put("type", r.getType());
                    return roleMap;
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{userId}/virtual-groups")
    @Operation(summary = "获取用户虚拟组列表", description = "获取用户所属的虚拟组列表")
    public ResponseEntity<List<Map<String, Object>>> getUserVirtualGroups(
            @PathVariable String userId) {
        List<VirtualGroupMember> memberships = virtualGroupMemberRepository.findByUserId(userId);
        
        // 批量获取虚拟组
        List<String> groupIds = memberships.stream()
                .map(VirtualGroupMember::getGroupId)
                .distinct()
                .collect(Collectors.toList());
        
        Map<String, VirtualGroup> groupMap = virtualGroupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(VirtualGroup::getId, g -> g));
        
        // 转换为简单的Map格式
        List<Map<String, Object>> result = memberships.stream()
                .map(m -> {
                    VirtualGroup group = groupMap.get(m.getGroupId());
                    if (group == null) {
                        return null;
                    }
                    Map<String, Object> groupInfo = new HashMap<>();
                    groupInfo.put("groupId", group.getId());
                    groupInfo.put("groupName", group.getName());
                    groupInfo.put("groupDescription", group.getDescription());
                    groupInfo.put("joinedAt", m.getJoinedAt());
                    return groupInfo;
                })
                .filter(m -> m != null)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{userId}/business-units")
    @Operation(summary = "获取用户业务单元成员身份", description = "获取用户加入的所有业务单元")
    public ResponseEntity<List<Map<String, Object>>> getUserBusinessUnits(
            @PathVariable String userId) {
        List<BusinessUnit> businessUnits = userBusinessUnitService.getUserBusinessUnits(userId);
        
        // 转换为简单的Map格式
        List<Map<String, Object>> result = businessUnits.stream()
                .map(bu -> {
                    Map<String, Object> buInfo = new HashMap<>();
                    buInfo.put("id", bu.getId());
                    buInfo.put("name", bu.getName());
                    buInfo.put("code", bu.getCode());
                    buInfo.put("path", bu.getPath());
                    return buInfo;
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
}
