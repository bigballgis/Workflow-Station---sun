package com.portal.component;

import com.portal.dto.PermissionRequestDto;
import com.portal.entity.PermissionRequest;
import com.portal.enums.PermissionRequestStatus;
import com.portal.enums.PermissionRequestType;
import com.portal.repository.PermissionRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class PermissionComponent {

    private final PermissionRequestRepository permissionRequestRepository;

    /**
     * 获取用户当前权限
     */
    public List<Map<String, Object>> getUserPermissions(String userId) {
        List<Map<String, Object>> permissions = new ArrayList<>();
        
        // 模拟用户权限数据
        Map<String, Object> perm1 = new HashMap<>();
        perm1.put("id", "perm-1");
        perm1.put("name", "流程发起");
        perm1.put("type", "FUNCTION");
        perm1.put("validTo", LocalDateTime.now().plusMonths(6));
        permissions.add(perm1);

        Map<String, Object> perm2 = new HashMap<>();
        perm2.put("id", "perm-2");
        perm2.put("name", "部门数据查看");
        perm2.put("type", "DATA");
        perm2.put("validTo", LocalDateTime.now().plusMonths(3));
        permissions.add(perm2);

        return permissions;
    }

    /**
     * 提交权限申请
     */
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
     */
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
     */
    public List<Map<String, Object>> getExpiringPermissions(String userId, int daysBeforeExpiry) {
        List<Map<String, Object>> expiring = new ArrayList<>();
        LocalDateTime threshold = LocalDateTime.now().plusDays(daysBeforeExpiry);
        
        // 模拟即将过期的权限
        List<Map<String, Object>> allPermissions = getUserPermissions(userId);
        for (Map<String, Object> perm : allPermissions) {
            LocalDateTime validTo = (LocalDateTime) perm.get("validTo");
            if (validTo != null && validTo.isBefore(threshold)) {
                expiring.add(perm);
            }
        }
        
        return expiring;
    }
}
