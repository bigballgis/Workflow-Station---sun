package com.admin.controller;

import com.admin.component.UserManagerComponent;
import com.admin.dto.request.StatusUpdateRequest;
import com.admin.dto.request.UserCreateRequest;
import com.admin.dto.request.UserQueryRequest;
import com.admin.dto.request.UserUpdateRequest;
import com.admin.dto.response.BatchImportResult;
import com.admin.dto.response.PageResult;
import com.admin.dto.response.UserCreateResult;
import com.admin.dto.response.UserDetailInfo;
import com.admin.dto.response.UserInfo;
import com.admin.entity.User;
import com.admin.service.UserImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        UserQueryRequest request = UserQueryRequest.builder()
                .keyword(keyword)
                .departmentId(departmentId)
                .status(status != null ? com.admin.enums.UserStatus.valueOf(status) : null)
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
}
