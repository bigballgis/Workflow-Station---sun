package com.developer.component;

import com.developer.component.impl.DeploymentComponentImpl;
import com.developer.dto.DeployRequest;
import com.developer.dto.DeployResponse;
import com.developer.entity.FunctionUnit;
import com.developer.repository.FunctionUnitRepository;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.service.DeploymentJobService;
import com.platform.common.i18n.I18nService;
import com.platform.security.config.JwtProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deploy runs on a worker thread. Spring Security 6's request-bound context goes empty
 * when the HTTP request ends; the worker must keep a snapshot of Authentication.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeploymentComponent - async SecurityContext snapshot")
class DeploymentComponentAsyncSecurityContextTest {

    private static final Long FUNCTION_UNIT_ID = 1L;

    @Mock
    private FunctionUnitRepository functionUnitRepository;
    @Mock
    private ExportImportComponent exportImportComponent;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private FunctionUnitComponent functionUnitComponent;
    @Mock
    private ProcessDesignComponent processDesignComponent;
    @Mock
    private I18nService i18nService;
    @Mock
    private DeploymentJobService deploymentJobService;
    @Mock
    private FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;
    @Mock
    private EmailMonitorRuleComponent emailMonitorRuleComponent;

    private DeferredTaskExecutor deferredExecutor;
    private DeploymentComponentImpl deploymentComponent;

    @BeforeEach
    void setUp() {
        deferredExecutor = new DeferredTaskExecutor();
        deploymentComponent = new DeploymentComponentImpl(
                functionUnitRepository,
                exportImportComponent,
                restTemplate,
                functionUnitComponent,
                processDesignComponent,
                i18nService,
                deferredExecutor,
                deploymentJobService,
                functionUnitWorkspaceAccessService,
                new JwtProperties(),
                emailMonitorRuleComponent);
        when(i18nService.getMessage(anyString())).thenReturn("Test Message");
        when(i18nService.getMessage(anyString(), (Object[]) any())).thenReturn("Test Message");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("HTTP 请求结束后，后台 publishForDeployment 仍能看到登录态，且不走 JWT hasAnyRole 的 publish()")
    void workerKeepsAuthenticationAfterRequestBoundContextDies() {
        FunctionUnit functionUnit = new FunctionUnit();
        functionUnit.setId(FUNCTION_UNIT_ID);
        functionUnit.setName("Email Inbound Reply");
        functionUnit.setCurrentVersion("0.5.1");
        when(functionUnitRepository.findById(FUNCTION_UNIT_ID)).thenReturn(Optional.of(functionUnit));

        Authentication login = new UsernamePasswordAuthenticationToken(
                "developer",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")));
        RequestBoundSecurityContext requestContext = new RequestBoundSecurityContext(login);
        SecurityContextHolder.setContext(requestContext);

        AtomicReference<Authentication> seenOnWorker = new AtomicReference<>();
        when(functionUnitComponent.publishForDeployment(eq(FUNCTION_UNIT_ID), any()))
                .thenAnswer(invocation -> {
                    Authentication current = SecurityContextHolder.getContext().getAuthentication();
                    seenOnWorker.set(current);
                    if (current == null || !current.isAuthenticated()) {
                        throw new AccessDeniedException("Access Denied");
                    }
                    return functionUnit;
                });

        DeployRequest request = new DeployRequest();
        request.setChangeLog("redeploy");
        DeployResponse accepted = deploymentComponent.deployToAdminCenter(FUNCTION_UNIT_ID, request);
        assertEquals(DeployResponse.DeployStatus.DEPLOYING, accepted.getStatus());

        requestContext.endRequest();
        SecurityContextHolder.clearContext();
        deferredExecutor.runPending();

        Authentication workerAuth = seenOnWorker.get();
        assertNotNull(workerAuth, "worker must still see the login for workspace/operator");
        assertEquals("developer", workerAuth.getName());
        assertEquals(
                "ROLE_DEVELOPER",
                workerAuth.getAuthorities().iterator().next().getAuthority());
        verify(functionUnitComponent).publishForDeployment(eq(FUNCTION_UNIT_ID), any());
        verify(functionUnitComponent, never()).publish(anyLong(), any());
    }

    /**
     * Models Spring Security 6 deferred / request-bound context: Authentication is
     * visible only while the HTTP request is alive.
     */
    private static final class RequestBoundSecurityContext implements SecurityContext {
        private final Authentication authentication;
        private volatile boolean requestAlive = true;

        private RequestBoundSecurityContext(Authentication authentication) {
            this.authentication = authentication;
        }

        @Override
        public Authentication getAuthentication() {
            return requestAlive ? authentication : null;
        }

        @Override
        public void setAuthentication(Authentication authentication) {
            throw new UnsupportedOperationException("test context is immutable");
        }

        private void endRequest() {
            requestAlive = false;
        }
    }

    /** Queues the deploy worker so the test can end the HTTP request first. */
    private static final class DeferredTaskExecutor implements TaskExecutor {
        private Runnable pending;

        @Override
        public void execute(Runnable task) {
            this.pending = task;
        }

        private void runPending() {
            if (pending == null) {
                throw new IllegalStateException("no queued deploy worker");
            }
            pending.run();
        }
    }
}
