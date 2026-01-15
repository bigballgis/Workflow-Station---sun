package com.admin.controller;

import com.admin.component.OrganizationManagerComponent;
import com.admin.dto.request.BusinessUnitCreateRequest;
import com.admin.dto.request.BusinessUnitMoveRequest;
import com.admin.dto.request.BusinessUnitUpdateRequest;
import com.admin.dto.response.BusinessUnitResult;
import com.admin.dto.response.BusinessUnitTree;
import com.admin.dto.response.PageResult;
import com.admin.dto.response.UserInfo;
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
 * 组织架构控制器（兼容旧API路径）
 * @deprecated 请使用 /business-units 路径
 */
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
@Tag(name = "组织架构管理（已废弃）", description = "请使用 /business-units 路径")
@Deprecated
public class DepartmentController {
    
    private final OrganizationManagerComponent organizationManager;
    private final UserRepository userRepository;
    
    @GetMapping("/tree")
    @Operation(summary = "获取组织架构树", description = "获取完整的组织架构树形结构")
    public ResponseEntity<List<BusinessUnitTree>> getOrganizationTree() {
        List<BusinessUnitTree> tree = organizationManager.getBusinessUnitTree();
        return ResponseEntity.ok(tree);
    }
    
    @PostMapping
    @Operation(summary = "创建部门", description = "创建新部门")
    public ResponseEntity<BusinessUnitResult> createDepartment(
            @RequestBody @Valid BusinessUnitCreateRequest request) {
        BusinessUnitResult result = organizationManager.createBusinessUnit(request);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{deptId}")
    @Operation(summary = "获取部门详情", description = "根据部门ID获取部门详细信息")
    public ResponseEntity<BusinessUnitTree> getDepartment(@PathVariable String deptId) {
        BusinessUnitTree dept = organizationManager.getBusinessUnitDetail(deptId);
        return ResponseEntity.ok(dept);
    }
    
    @PutMapping("/{deptId}")
    @Operation(summary = "更新部门信息", description = "更新部门的基本信息")
    public ResponseEntity<Void> updateDepartment(
            @PathVariable String deptId,
            @RequestBody @Valid BusinessUnitUpdateRequest request) {
        organizationManager.updateBusinessUnit(deptId, request);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/{deptId}/move")
    @Operation(summary = "移动部门", description = "调整部门的层级位置")
    public ResponseEntity<Void> moveDepartment(
            @PathVariable String deptId,
            @RequestBody BusinessUnitMoveRequest request) {
        organizationManager.moveBusinessUnit(deptId, request.getNewParentId());
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{deptId}")
    @Operation(summary = "删除部门", description = "删除指定部门（需要先移除子部门和成员）")
    public ResponseEntity<Void> deleteDepartment(@PathVariable String deptId) {
        organizationManager.deleteBusinessUnit(deptId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{deptId}/children")
    @Operation(summary = "获取子部门", description = "获取指定部门的直接子部门")
    public ResponseEntity<List<BusinessUnitTree>> getChildDepartments(@PathVariable String deptId) {
        List<BusinessUnitTree> children = organizationManager.getChildBusinessUnits(deptId);
        return ResponseEntity.ok(children);
    }
    
    @GetMapping("/{deptId}/members")
    @Operation(summary = "获取部门成员", description = "分页获取部门成员列表")
    public ResponseEntity<PageResult<UserInfo>> getDepartmentMembers(
            @PathVariable String deptId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        // 使用关联表查询成员（多对多关系）
        Page<com.admin.entity.User> users = userRepository.findMembersByBusinessUnitId(
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
    public ResponseEntity<List<BusinessUnitTree>> searchDepartments(
            @RequestParam String keyword) {
        List<BusinessUnitTree> results = organizationManager.searchBusinessUnits(keyword);
        return ResponseEntity.ok(results);
    }
}
