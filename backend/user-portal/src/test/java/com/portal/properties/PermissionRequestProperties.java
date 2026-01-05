package com.portal.properties;

import com.portal.component.PermissionComponent;
import com.portal.dto.PermissionRequestDto;
import com.portal.entity.PermissionRequest;
import com.portal.enums.PermissionRequestStatus;
import com.portal.enums.PermissionRequestType;
import com.portal.repository.PermissionRequestRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;
import net.jqwik.api.constraints.IntRange;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 权限申请属性测试
 */
class PermissionRequestProperties {

    private PermissionComponent permissionComponent;
    private PermissionRequestRepository permissionRequestRepository;

    @BeforeTry
    void setUp() {
        permissionRequestRepository = Mockito.mock(PermissionRequestRepository.class);
        permissionComponent = new PermissionComponent(permissionRequestRepository);
    }

    @Property(tries = 20)
    @Label("属性: 用户权限列表应包含有效权限")
    void userPermissionsShouldContainValidPermissions(
            @ForAll("validUserIds") String userId) {
        List<Map<String, Object>> permissions = permissionComponent.getUserPermissions(userId);
        
        assertThat(permissions).isNotEmpty();
        assertThat(permissions).allMatch(p -> p.containsKey("id") && p.containsKey("name") && p.containsKey("type"));
    }

    @Property(tries = 20)
    @Label("属性: 提交权限申请应创建待审批记录")
    void submitRequestShouldCreatePendingRecord(
            @ForAll("validUserIds") String userId,
            @ForAll("permissionTypes") PermissionRequestType type,
            @ForAll("reasons") String reason) {
        PermissionRequestDto dto = PermissionRequestDto.builder()
                .type(type)
                .permissions(Arrays.asList("perm-1", "perm-2"))
                .reason(reason)
                .validTo(LocalDateTime.now().plusMonths(3))
                .build();
        
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(inv -> {
                    PermissionRequest req = inv.getArgument(0);
                    req.setId(1L);
                    return req;
                });
        
        PermissionRequest request = permissionComponent.submitRequest(userId, dto);
        
        assertThat(request).isNotNull();
        assertThat(request.getApplicantId()).isEqualTo(userId);
        assertThat(request.getStatus()).isEqualTo(PermissionRequestStatus.PENDING);
    }

    @Property(tries = 20)
    @Label("属性: 空权限类型应抛出异常")
    void nullTypeShouldThrowException(
            @ForAll("validUserIds") String userId) {
        PermissionRequestDto dto = PermissionRequestDto.builder()
                .type(null)
                .permissions(Arrays.asList("perm-1"))
                .reason("测试原因")
                .build();
        
        assertThatThrownBy(() -> permissionComponent.submitRequest(userId, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限类型不能为空");
    }

    @Property(tries = 20)
    @Label("属性: 空权限范围应抛出异常")
    void emptyPermissionsShouldThrowException(
            @ForAll("validUserIds") String userId,
            @ForAll("permissionTypes") PermissionRequestType type) {
        PermissionRequestDto dto = PermissionRequestDto.builder()
                .type(type)
                .permissions(Collections.emptyList())
                .reason("测试原因")
                .build();
        
        assertThatThrownBy(() -> permissionComponent.submitRequest(userId, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限范围不能为空");
    }

    @Property(tries = 20)
    @Label("属性: 空申请理由应抛出异常")
    void emptyReasonShouldThrowException(
            @ForAll("validUserIds") String userId,
            @ForAll("permissionTypes") PermissionRequestType type) {
        PermissionRequestDto dto = PermissionRequestDto.builder()
                .type(type)
                .permissions(Arrays.asList("perm-1"))
                .reason("")
                .build();
        
        assertThatThrownBy(() -> permissionComponent.submitRequest(userId, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("申请理由不能为空");
    }

    @Property(tries = 20)
    @Label("属性: 取消待审批申请应成功")
    void cancelPendingRequestShouldSucceed(
            @ForAll("validUserIds") String userId,
            @ForAll("requestIds") Long requestId) {
        PermissionRequest request = new PermissionRequest();
        request.setId(requestId);
        request.setApplicantId(userId);
        request.setStatus(PermissionRequestStatus.PENDING);
        
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        boolean result = permissionComponent.cancelRequest(userId, requestId);
        
        assertThat(result).isTrue();
        verify(permissionRequestRepository).delete(request);
    }

    @Property(tries = 20)
    @Label("属性: 取消非待审批申请应失败")
    void cancelNonPendingRequestShouldFail(
            @ForAll("validUserIds") String userId,
            @ForAll("requestIds") Long requestId,
            @ForAll("nonPendingStatuses") PermissionRequestStatus status) {
        PermissionRequest request = new PermissionRequest();
        request.setId(requestId);
        request.setApplicantId(userId);
        request.setStatus(status);
        
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        boolean result = permissionComponent.cancelRequest(userId, requestId);
        
        assertThat(result).isFalse();
        verify(permissionRequestRepository, never()).delete(any());
    }

    @Property(tries = 20)
    @Label("属性: 取消他人申请应失败")
    void cancelOthersRequestShouldFail(
            @ForAll("validUserIds") String userId,
            @ForAll("validUserIds") String otherUserId,
            @ForAll("requestIds") Long requestId) {
        Assume.that(!userId.equals(otherUserId));
        
        PermissionRequest request = new PermissionRequest();
        request.setId(requestId);
        request.setApplicantId(otherUserId);
        request.setStatus(PermissionRequestStatus.PENDING);
        
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        boolean result = permissionComponent.cancelRequest(userId, requestId);
        
        assertThat(result).isFalse();
    }

    @Property(tries = 20)
    @Label("属性: 续期申请应创建新记录")
    void renewPermissionShouldCreateNewRequest(
            @ForAll("validUserIds") String userId,
            @ForAll("reasons") String reason) {
        String permissionId = "perm-1";
        LocalDateTime newValidTo = LocalDateTime.now().plusMonths(6);
        
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(inv -> {
                    PermissionRequest req = inv.getArgument(0);
                    req.setId(1L);
                    return req;
                });
        
        PermissionRequest request = permissionComponent.renewPermission(userId, permissionId, newValidTo, reason);
        
        assertThat(request).isNotNull();
        assertThat(request.getApplicantId()).isEqualTo(userId);
        assertThat(request.getValidTo()).isEqualTo(newValidTo);
        assertThat(request.getReason()).contains("续期申请");
    }

    @Property(tries = 20)
    @Label("属性: 即将过期权限检查应返回正确结果")
    void expiringPermissionsCheckShouldReturnCorrectResults(
            @ForAll("validUserIds") String userId,
            @ForAll @IntRange(min = 1, max = 365) int days) {
        List<Map<String, Object>> expiring = permissionComponent.getExpiringPermissions(userId, days);
        
        assertThat(expiring).isNotNull();
        // 所有返回的权限都应该在指定天数内过期
        LocalDateTime threshold = LocalDateTime.now().plusDays(days);
        for (Map<String, Object> perm : expiring) {
            LocalDateTime validTo = (LocalDateTime) perm.get("validTo");
            if (validTo != null) {
                assertThat(validTo).isBefore(threshold);
            }
        }
    }

    @Provide
    Arbitrary<String> validUserIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> "user-" + s);
    }

    @Provide
    Arbitrary<PermissionRequestType> permissionTypes() {
        return Arbitraries.of(PermissionRequestType.values());
    }

    @Provide
    Arbitrary<PermissionRequestStatus> nonPendingStatuses() {
        return Arbitraries.of(PermissionRequestStatus.APPROVED, PermissionRequestStatus.REJECTED);
    }

    @Provide
    Arbitrary<Long> requestIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<String> reasons() {
        return Arbitraries.of("工作需要", "项目要求", "临时授权", "紧急处理");
    }
}
