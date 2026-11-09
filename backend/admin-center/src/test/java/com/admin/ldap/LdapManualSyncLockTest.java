package com.admin.ldap;

import com.admin.controller.LdapSyncController;
import com.admin.exception.AdminApiExceptionHandler;
import com.platform.common.dto.UserPrincipal;
import com.platform.common.i18n.MessageSourceConfig;
import com.platform.common.i18n.impl.I18nServiceImpl;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /ldap-sync/full|incremental|hermes-groups[/incremental]} take the lock of the startup and scheduled
 * group sync. While another sync holds it the request is refused with 409 {@code LDAP_SYNC_IN_PROGRESS}, worded in
 * the caller's language; it is neither queued nor skipped silently. When the lock store (Redis) cannot be reached the
 * request is refused with 503 {@code LDAP_SYNC_LOCK_UNAVAILABLE} and no sync runs. The runner here is called directly,
 * so the sync runs on the request thread.
 */
class LdapManualSyncLockTest {

    /** Redis is down: the lock store throws the translated connection failure. */
    private static final ExtensibleLockProvider UNREACHABLE = configuration -> {
        throw new RedisConnectionFailureException("Unable to connect to Redis");
    };

    private final LdapHermesGroupSyncComponentTest.InMemoryLockProvider locks =
            new LdapHermesGroupSyncComponentTest.InMemoryLockProvider();
    private final LdapUserSyncService userSync = mock(LdapUserSyncService.class);
    private final LdapSyncService groupSync = mock(LdapSyncService.class);
    private LdapSyncAuditService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = service(new LdapManualSyncRunner());
        mvc = mvcWith(service);
        UserPrincipal admin = UserPrincipal.builder().userId("developer").username("developer")
                .roles(List.of("SYS_ADMIN")).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldStartTheSyncUnderTheSharedLock_whenNoOtherSyncRuns() throws Exception {
        mvc.perform(post("/ldap-sync/hermes-groups"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.type").value("HERMES_AD_GROUP"));
        mvc.perform(post("/ldap-sync/full")).andExpect(status().isAccepted());

        verify(groupSync).runHermesAdGroupSync();
        verify(userSync).runFullSync();
        assertThat(locks.requests).hasSize(2).allSatisfy(request -> {
            assertThat(request.getName()).isEqualTo(LdapSyncLock.NAME);
            // no minimum hold: the next manual sync may start as soon as this one ends
            assertThat(request.getLockAtLeastFor()).isEqualTo(Duration.ZERO);
        });
        assertThat(locks.held).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/ldap-sync/full", "/ldap-sync/incremental", "/ldap-sync/hermes-groups",
            "/ldap-sync/hermes-groups/incremental"})
    void shouldRefuseWith409_whenAnotherSyncHoldsTheLock(String path) throws Exception {
        locks.held.add(LdapSyncLock.NAME); // the startup or scheduled run of some replica

        mvc.perform(post(path).header("Accept-Language", "en"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LDAP_SYNC_IN_PROGRESS"))
                .andExpect(jsonPath("$.error.message")
                        .value("Another LDAP sync is running. Start this one again when it has finished."));

        verifyNoInteractions(userSync, groupSync);
        assertThat(locks.held).containsExactly(LdapSyncLock.NAME); // the holder's lock is left alone
    }

    @Test
    void shouldWordTheRefusalInTheCallersLanguage() throws Exception {
        locks.held.add(LdapSyncLock.NAME);

        mvc.perform(post("/ldap-sync/full").header("Accept-Language", "zh-CN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("另一次 LDAP 同步正在进行，请等它结束后再发起。"));
        mvc.perform(post("/ldap-sync/full").header("Accept-Language", "zh-TW"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("另一次 LDAP 同步正在進行，請等它結束後再發起。"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/ldap-sync/full", "/ldap-sync/incremental", "/ldap-sync/hermes-groups",
            "/ldap-sync/hermes-groups/incremental"})
    void shouldRefuseWith503_whenTheLockStoreCannotBeReached(String path) throws Exception {
        mvc = mvcWith(service(new LdapManualSyncRunner(), UNREACHABLE));

        mvc.perform(post(path).header("Accept-Language", "en"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("LDAP_SYNC_LOCK_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value("The LDAP sync did not start: its lock service (Redis) "
                        + "cannot be reached. Try again later, or ask the platform operator to check Redis."));

        verifyNoInteractions(userSync, groupSync); // no sync runs without the lock
    }

    @Test
    void shouldWordTheLockStoreRefusalInTheCallersLanguage() throws Exception {
        mvc = mvcWith(service(new LdapManualSyncRunner(), UNREACHABLE));

        mvc.perform(post("/ldap-sync/full").header("Accept-Language", "zh-CN"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.message")
                        .value("LDAP 同步未启动：同步锁服务（Redis）无法连接。请稍后重试，或请平台运维检查 Redis。"));
        mvc.perform(post("/ldap-sync/full").header("Accept-Language", "zh-TW"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.message")
                        .value("LDAP 同步未啟動：同步鎖服務（Redis）無法連線。請稍後重試，或請平台維運檢查 Redis。"));
    }

    @Test
    void shouldReleaseTheLock_whenTheSyncFails() throws Exception {
        doThrow(new IllegalStateException("AD unreachable")).when(groupSync).runHermesAdGroupIncrementalSync();

        mvc.perform(post("/ldap-sync/hermes-groups/incremental")).andExpect(status().isAccepted());

        assertThat(locks.held).isEmpty();
    }

    @Test
    void shouldReleaseTheLockAndReport_whenTheSyncCannotBeHandedOff() {
        LdapManualSyncRunner rejecting = mock(LdapManualSyncRunner.class);
        doThrow(new TaskRejectedException("pool full")).when(rejecting).run(anyString(), any(), any());

        assertThatThrownBy(() -> service(rejecting).triggerFullAsync()).isInstanceOf(TaskRejectedException.class);

        assertThat(locks.held).isEmpty();
        verify(userSync, never()).runFullSync();
    }

    @Test
    void shouldSkipTheStartupSync_whileAManualSyncRuns() {
        LdapManualSyncRunner holding = mock(LdapManualSyncRunner.class); // keeps the lock: the sync is still running
        service(holding).triggerHermesGroupFullAsync();

        new LdapHermesGroupSyncComponent(groupSync, new LdapSyncLock(locks)).runStartupSync();

        verify(groupSync, never()).runHermesAdGroupIncrementalSync();
        assertThat(locks.held).containsExactly(LdapSyncLock.NAME);
    }

    private LdapSyncAuditService service(LdapManualSyncRunner runner) {
        return service(runner, locks);
    }

    private LdapSyncAuditService service(LdapManualSyncRunner runner, ExtensibleLockProvider lockStore) {
        return new LdapSyncAuditService(userSync, groupSync, mock(LdapSyncAuditRepository.class),
                new LdapSyncLock(lockStore), runner,
                new I18nServiceImpl(new MessageSourceConfig().messageSource(configuredBasenames(), "UTF-8")));
    }

    private static MockMvc mvcWith(LdapSyncAuditService service) {
        return MockMvcBuilders.standaloneSetup(new LdapSyncController(service))
                .setControllerAdvice(new AdminApiExceptionHandler())
                .build();
    }

    /** The bundle list the service is configured with, so the test reads messages exactly as the app does. */
    private static String configuredBasenames() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject().getProperty("spring.messages.basename");
    }
}
