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
import com.admin.service.UserBusinessUnitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 业务单元控制器（原组织架构控制器）
 */
@RestController
@RequestMapping("/business-units")
@RequiredArgsConstructor
@Tag(name = "业务单元管理", description = "业务单元的创建、查询、更新、删除等操作")
public class BusinessUnitController {
    
    private final OrganizationManagerComponent organizationManager;
    private final UserRepository userRepository;
    private final UserBusinessUnitService userBusinessUnitService;
    
    @GetMapping("/tree")
    @Operation(summary = "获取业务单元树", description = "获取完整的业务单元树形结构")
    public ResponseEntity<List<BusinessUnitTree>> getOrganizationTree() {
        List<BusinessUnitTree> tree = organizationManager.getBusinessUnitTree();
        return ResponseEntity.ok(tree);
    }
    
    @PostMapping
    @Operation(summary = "创建业务单元", description = "创建新业务单元")
    public ResponseEntity<BusinessUnitResult> createBusinessUnit(
            @RequestBody @Valid BusinessUnitCreateRequest request) {
        BusinessUnitResult result = organizationManager.createBusinessUnit(request);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{unitId}")
    @Operation(summary = "获取业务单元详情", description = "根据业务单元ID获取详细信息")
    public ResponseEntity<BusinessUnitTree> getBusinessUnit(@PathVariable String unitId) {
        BusinessUnitTree unit = organizationManager.getBusinessUnitDetail(unitId);
        return ResponseEntity.ok(unit);
    }
    
    @PutMapping("/{unitId}")
    @Operation(summary = "更新业务单元信息", description = "更新业务单元的基本信息")
    public ResponseEntity<Void> updateBusinessUnit(
            @PathVariable String unitId,
            @RequestBody @Valid BusinessUnitUpdateRequest request) {
        organizationManager.updateBusinessUnit(unitId, request);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/{unitId}/move")
    @Operation(summary = "移动业务单元", description = "调整业务单元的层级位置")
    public ResponseEntity<Void> moveBusinessUnit(
            @PathVariable String unitId,
            @RequestBody BusinessUnitMoveRequest request) {
        organizationManager.moveBusinessUnit(unitId, request.getNewParentId());
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{unitId}")
    @Operation(summary = "删除业务单元", description = "删除指定业务单元（需要先移除子业务单元和成员）")
    public ResponseEntity<Void> deleteBusinessUnit(@PathVariable String unitId) {
        organizationManager.deleteBusinessUnit(unitId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{unitId}/children")
    @Operation(summary = "获取子业务单元", description = "获取指定业务单元的直接子业务单元")
    public ResponseEntity<List<BusinessUnitTree>> getChildBusinessUnits(@PathVariable String unitId) {
        List<BusinessUnitTree> children = organizationManager.getChildBusinessUnits(unitId);
        return ResponseEntity.ok(children);
    }
    
    @GetMapping("/{unitId}/members")
    @Operation(summary = "获取业务单元成员", description = "分页获取业务单元成员列表")
    public ResponseEntity<PageResult<UserInfo>> getBusinessUnitMembers(
            @PathVariable String unitId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        // 使用关联表查询成员（多对多关系）
        Page<com.platform.security.entity.User> users = userRepository.findMembersByBusinessUnitId(
                unitId, PageRequest.of(page, size));
        
        PageResult<UserInfo> result = PageResult.of(
                users.map(UserInfo::fromEntity).getContent(),
                users.getNumber(),
                users.getSize(),
                users.getTotalElements());
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/search")
    @Operation(summary = "搜索业务单元", description = "根据关键词搜索业务单元")
    public ResponseEntity<List<BusinessUnitTree>> searchBusinessUnits(
            @RequestParam String keyword) {
        List<BusinessUnitTree> results = organizationManager.searchBusinessUnits(keyword);
        return ResponseEntity.ok(results);
    }
    
    @PostMapping("/{unitId}/members")
    @Operation(summary = "添加成员", description = "将用户添加到业务单元")
    public ResponseEntity<Map<String, Object>> addMember(
            @PathVariable String unitId,
            @RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        userBusinessUnitService.addUserToBusinessUnit(userId, unitId);
        return ResponseEntity.ok(Map.of("success", true));
    }
    
    @DeleteMapping("/{unitId}/members/{userId}")
    @Operation(summary = "移除成员", description = "从业务单元移除用户")
    public ResponseEntity<Void> removeMember(
            @PathVariable String unitId,
            @PathVariable String userId) {
        userBusinessUnitService.removeUserFromBusinessUnit(userId, unitId);
        return ResponseEntity.ok().build();
    }
}
