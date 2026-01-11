package com.admin.controller;

import com.admin.component.OrganizationManagerComponent;
import com.admin.dto.request.DepartmentCreateRequest;
import com.admin.dto.request.DepartmentMoveRequest;
import com.admin.dto.response.DepartmentResult;
import com.admin.dto.response.DepartmentTree;
import com.admin.dto.response.PageResult;
import com.admin.dto.response.UserInfo;
import com.admin.entity.Department;
import com.admin.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 组织架构控制器
 */
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
@Tag(name = "组织架构管理", description = "部门的创建、查询、更新、删除等操作")
public class DepartmentController {
    
    private final OrganizationManagerComponent organizationManager;
    private final UserRepository userRepository;
    
    @GetMapping("/tree")
    @Operation(summary = "获取组织架构树", description = "获取完整的组织架构树形结构")
    public ResponseEntity<List<DepartmentTree>> getOrganizationTree() {
        List<DepartmentTree> tree = organizationManager.getOrganizationTree();
        return ResponseEntity.ok(tree);
    }
    
    @PostMapping
    @Operation(summary = "创建部门", description = "创建新部门")
    public ResponseEntity<DepartmentResult> createDepartment(
            @RequestBody @Valid DepartmentCreateRequest request) {
        DepartmentResult result = organizationManager.createDepartment(request);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{deptId}")
    @Operation(summary = "获取部门详情", description = "根据部门ID获取部门详细信息")
    public ResponseEntity<DepartmentTree> getDepartment(@PathVariable String deptId) {
        DepartmentTree dept = organizationManager.getDepartmentDetail(deptId);
        return ResponseEntity.ok(dept);
    }
    
    @PutMapping("/{deptId}")
    @Operation(summary = "更新部门信息", description = "更新部门的基本信息")
    public ResponseEntity<Void> updateDepartment(
            @PathVariable String deptId,
            @RequestBody @Valid DepartmentCreateRequest request) {
        organizationManager.updateDepartment(deptId, request);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/{deptId}/move")
    @Operation(summary = "移动部门", description = "调整部门的层级位置")
    public ResponseEntity<Void> moveDepartment(
            @PathVariable String deptId,
            @RequestBody DepartmentMoveRequest request) {
        organizationManager.moveDepartment(deptId, request.getNewParentId());
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{deptId}")
    @Operation(summary = "删除部门", description = "删除指定部门（需要先移除子部门和成员）")
    public ResponseEntity<Void> deleteDepartment(@PathVariable String deptId) {
        organizationManager.deleteDepartment(deptId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{deptId}/children")
    @Operation(summary = "获取子部门", description = "获取指定部门的直接子部门")
    public ResponseEntity<List<DepartmentTree>> getChildDepartments(@PathVariable String deptId) {
        List<DepartmentTree> children = organizationManager.getChildDepartments(deptId);
        return ResponseEntity.ok(children);
    }
    
    @GetMapping("/{deptId}/members")
    @Operation(summary = "获取部门成员", description = "分页获取部门成员列表")
    public ResponseEntity<PageResult<UserInfo>> getDepartmentMembers(
            @PathVariable String deptId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Page<com.admin.entity.User> users = userRepository.findByDepartmentId(
                deptId, PageRequest.of(page, size));
        
        PageResult<UserInfo> result = PageResult.of(
                users.map(UserInfo::fromEntity).getContent(),
                users.getNumber(),
                users.getSize(),
                users.getTotalElements());
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/search")
    @Operation(summary = "搜索部门", description = "根据关键词搜索部门")
    public ResponseEntity<List<DepartmentTree>> searchDepartments(
            @RequestParam String keyword) {
        List<DepartmentTree> results = organizationManager.searchDepartments(keyword);
        return ResponseEntity.ok(results);
    }
}
