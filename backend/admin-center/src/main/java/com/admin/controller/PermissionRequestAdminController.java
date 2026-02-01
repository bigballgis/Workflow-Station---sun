package com.admin.controller;

import com.admin.dto.response.PageResult;
import com.admin.dto.response.PermissionRequestInfo;
import com.admin.entity.PermissionRequest;
import com.admin.enums.PermissionRequestStatus;
import com.admin.enums.PermissionRequestType;
import com.admin.service.PermissionRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 权限申请管理控制器（Admin Center）
 */
@RestController
@RequestMapping("/permission-requests")
@RequiredArgsConstructor
@Tag(name = "权限申请管理", description = "权限申请记录的查询和管理")
public class PermissionRequestAdminController {
    
    private final PermissionRequestService permissionRequestService;
    
    @GetMapping
    @Operation(summary = "获取所有权限申请记录")
    public ResponseEntity<PageResult<PermissionRequestInfo>> getAllRequests(
            @RequestParam(required = false) PermissionRequestStatus status,
            @RequestParam(required = false) PermissionRequestType requestType,
            @RequestParam(required = false) String applicantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Instant startInstant = startDate != null ? startDate.atZone(ZoneId.systemDefault()).toInstant() : null;
        Instant endInstant = endDate != null ? endDate.atZone(ZoneId.systemDefault()).toInstant() : null;
        
        Page<PermissionRequest> requests = permissionRequestService.getAllRequests(
                status, requestType, applicantId, startInstant, endInstant,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        
        PageResult<PermissionRequestInfo> result = PageResult.of(
                requests.map(PermissionRequestInfo::fromEntity).getContent(),
                requests.getNumber(),
                requests.getSize(),
                requests.getTotalElements());
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{requestId}")
    @Operation(summary = "获取权限申请详情")
    public ResponseEntity<PermissionRequestInfo> getRequestDetail(@PathVariable String requestId) {
        PermissionRequest request = permissionRequestService.getRequestDetail(requestId);
        return ResponseEntity.ok(PermissionRequestInfo.fromEntity(request));
    }
}
