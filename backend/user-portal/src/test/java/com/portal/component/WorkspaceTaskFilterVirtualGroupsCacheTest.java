package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.exception.PortalException;
import com.portal.repository.BusinessUnitRepository;
import com.portal.service.PortalWorkspaceAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * getUserVirtualGroups 的失败语义：查询失败不写缓存、有过期缓存降级复用（last-known-good）、
 * 冷启动+故障叠加时抛 503 而非静默空列表（否则用户误以为"没有待办"）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkspaceTaskFilterComponent virtual-group cache failure semantics")
class WorkspaceTaskFilterVirtualGroupsCacheTest {

    @Mock
    private WorkflowEngineClient workflowEngineClient;
    @Mock
    private VirtualGroupAccessComponent virtualGroupAccessComponent;
    @Mock
    private PortalWorkspaceAuthService portalWorkspaceAuthService;
    @Mock
    private BusinessUnitRepository businessUnitRepository;

    private WorkspaceTaskFilterComponent component;

    private static final String USER = "user-001";

    @BeforeEach
    void setUp() {
        component = new WorkspaceTaskFilterComponent(
                workflowEngineClient, virtualGroupAccessComponent,
                portalWorkspaceAuthService, businessUnitRepository);
    }

    @Test
    @DisplayName("Success populates cache; second call within TTL does not re-hit the engine")
    void successIsCached() {
        when(workflowEngineClient.getUserTaskPermissions(USER))
                .thenReturn(Optional.of(Map.of("virtualGroupIds", List.of("vg-1", "vg-2"))));

        assertThat(component.getUserVirtualGroups(USER)).containsExactly("vg-1", "vg-2");
        assertThat(component.getUserVirtualGroups(USER)).containsExactly("vg-1", "vg-2");

        verify(workflowEngineClient, times(1)).getUserTaskPermissions(USER);
    }

    @Test
    @DisplayName("Successful empty result (user genuinely has no groups) is a cacheable answer, not a failure")
    void successfulEmptyIsCached() {
        when(workflowEngineClient.getUserTaskPermissions(USER))
                .thenReturn(Optional.of(Map.of()));

        assertThat(component.getUserVirtualGroups(USER)).isEmpty();
        assertThat(component.getUserVirtualGroups(USER)).isEmpty();

        verify(workflowEngineClient, times(1)).getUserTaskPermissions(USER);
    }

    @Test
    @DisplayName("Fetch failure with no cache throws 503 PortalException instead of silently returning empty")
    void failureWithoutCacheThrows() {
        when(workflowEngineClient.getUserTaskPermissions(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> component.getUserVirtualGroups(USER))
                .isInstanceOf(PortalException.class)
                .satisfies(e -> assertThat(((PortalException) e).getCode()).isEqualTo("503"));
    }

    @Test
    @DisplayName("Fetch failure is NOT cached: next call retries the engine")
    void failureIsNotCached() {
        when(workflowEngineClient.getUserTaskPermissions(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> component.getUserVirtualGroups(USER))
                .isInstanceOf(PortalException.class);
        assertThatThrownBy(() -> component.getUserVirtualGroups(USER))
                .isInstanceOf(PortalException.class);

        verify(workflowEngineClient, times(2)).getUserTaskPermissions(USER);
    }

    @Test
    @DisplayName("Client exception is treated the same as degraded empty Optional")
    void clientExceptionTreatedAsFailure() {
        when(workflowEngineClient.getUserTaskPermissions(anyString()))
                .thenThrow(new RuntimeException("engine down"));

        assertThatThrownBy(() -> component.getUserVirtualGroups(USER))
                .isInstanceOf(PortalException.class);
    }

    @Test
    @DisplayName("Fetch failure with an expired cache entry reuses the stale value (last-known-good)")
    void failureWithStaleCacheReusesLastKnownGood() {
        // 第一次成功，写入缓存
        when(workflowEngineClient.getUserTaskPermissions(USER))
                .thenReturn(Optional.of(Map.of("virtualGroupIds", List.of("vg-1"))));
        assertThat(component.getUserVirtualGroups(USER)).containsExactly("vg-1");

        // 人为把缓存条目改为已过期（TTL 30s，时间戳拨回 60s 前）
        expireCacheEntry(USER);

        // 引擎故障：应复用过期快照而不是抛错/返回空
        when(workflowEngineClient.getUserTaskPermissions(USER)).thenReturn(Optional.empty());
        assertThat(component.getUserVirtualGroups(USER)).containsExactly("vg-1");
    }

    @SuppressWarnings("unchecked")
    private void expireCacheEntry(String userId) {
        try {
            java.lang.reflect.Field f = WorkspaceTaskFilterComponent.class
                    .getDeclaredField("virtualGroupsCache");
            f.setAccessible(true);
            Map<String, Object> cache = (Map<String, Object>) f.get(component);
            Object entry = cache.get(userId);
            Class<?> entryClass = entry.getClass();
            java.lang.reflect.Constructor<?> ctor = entryClass.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            java.lang.reflect.Method groupIds = entryClass.getDeclaredMethod("groupIds");
            groupIds.setAccessible(true);
            Object stale = ctor.newInstance(groupIds.invoke(entry), System.currentTimeMillis() - 60_000L);
            cache.put(userId, stale);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to expire cache entry via reflection", e);
        }
    }
}
