package com.portal.properties;

import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.PermissionComponent;
import com.portal.component.RoleAccessComponent;
import com.portal.component.VirtualGroupAccessComponent;
import com.platform.common.i18n.I18nService;
import com.portal.entity.PermissionRequest;
import com.portal.enums.PermissionRequestStatus;
import com.portal.repository.PermissionRequestRepository;
import net.jqwik.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 权限申请属性测试
 */
class PermissionRequestProperties {

    private PermissionComponent permissionComponent;
    private PermissionRequestRepository permissionRequestRepository;
    private RoleAccessComponent roleAccessComponent;
    private VirtualGroupAccessComponent virtualGroupAccessComponent;

    @BeforeTry
    void setUp() {
        permissionRequestRepository = Mockito.mock(PermissionRequestRepository.class);
        roleAccessComponent = Mockito.mock(RoleAccessComponent.class);
        virtualGroupAccessComponent = Mockito.mock(VirtualGroupAccessComponent.class);
        I18nService i18nService = Mockito.mock(I18nService.class);
        Mockito.when(i18nService.getMessage(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        FunctionUnitAccessComponent functionUnitAccessComponent = Mockito.mock(FunctionUnitAccessComponent.class);
        permissionComponent = new PermissionComponent(
                permissionRequestRepository,
                roleAccessComponent,
                virtualGroupAccessComponent,
                functionUnitAccessComponent,
                Mockito.mock(JdbcTemplate.class),
                i18nService);
        // PermissionComponent 拆分后目录查询委托给 @Lazy @Autowired 的 PermissionCatalogComponent，
        // 直接 new 时该字段为 null——注入真实实现（复用同一批 mock），保持原测试语义。
        org.springframework.test.util.ReflectionTestUtils.setField(permissionComponent, "catalogComponent",
                new com.portal.component.PermissionCatalogComponent(
                        roleAccessComponent, virtualGroupAccessComponent, functionUnitAccessComponent));

        // Mock default behavior for role and virtual group access
        when(roleAccessComponent.getUserBusinessRoles(any())).thenReturn(Collections.emptyList());
        when(virtualGroupAccessComponent.getUserVirtualGroups(any())).thenReturn(Collections.emptyList());
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
        assertThat(request.getStatus()).isEqualTo(PermissionRequestStatus.CANCELLED);
        verify(permissionRequestRepository).save(request);
    }

    @Property(tries = 20)
    @Label("属性: 代办提交人可取消待审批申请")
    void submitterMayCancelPendingOnBehalfRequest(
            @ForAll("validUserIds") String submitterId,
            @ForAll("validUserIds") String beneficiaryId,
            @ForAll("requestIds") Long requestId) {
        Assume.that(!submitterId.equals(beneficiaryId));

        PermissionRequest request = new PermissionRequest();
        request.setId(requestId);
        request.setApplicantId(beneficiaryId);
        request.setSubmittedByUserId(submitterId);
        request.setStatus(PermissionRequestStatus.PENDING);

        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        boolean result = permissionComponent.cancelRequest(submitterId, requestId);

        assertThat(result).isTrue();
        assertThat(request.getStatus()).isEqualTo(PermissionRequestStatus.CANCELLED);
        verify(permissionRequestRepository).save(request);
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

    @Provide
    Arbitrary<String> validUserIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> "user-" + s);
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
