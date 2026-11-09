package com.admin.ldap;

import com.admin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import javax.naming.NamingException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The incremental user sync reads AD entries changed since the last user sync (full or incremental) that ended in
 * success. A group sync only reads the members of mapped AD groups, so one that succeeded later must not move that
 * starting point; nor does a user sync that ended PARTIAL because some entries could not be stored, while entries
 * skipped for having no employee ID do not stop a run from succeeding. The records live in a real (H2) table so the
 * repository query is the one that runs.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ldap-sync-watermark;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=GROUPS",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"})
class LdapUserSyncWatermarkTest {

    /** Only the sync record repository; see ActionDefinitionRepositoryContainerIT for why the slice is narrowed. */
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = LdapSyncAudit.class)
    @EnableJpaRepositories(
            basePackageClasses = LdapSyncAuditRepository.class,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = LdapSyncAuditRepository.class))
    static class PersistenceOnlyConfig {
    }

    @Autowired
    private LdapSyncAuditRepository auditRepository;

    private static final DateTimeFormatter AD_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final LdapClient ldapClient = mock(LdapClient.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private LdapUserSyncService service;

    @BeforeEach
    void setUp() throws NamingException {
        LdapProperties props = new LdapProperties();
        service = new LdapUserSyncService(ldapClient, new LdapUserMapper(props), props,
                userRepository, auditRepository, mock(PasswordEncoder.class));
        when(ldapClient.fetchUsersWithFilter(anyString())).thenReturn(List.of());
        when(ldapClient.fetchAllUsers()).thenReturn(List.of());
    }

    @Test
    void shouldReadFromTheLastUserSync_whenAGroupSyncSucceededLater() throws Exception {
        record("FULL", "SUCCESS", "2026-11-01T02:00:00Z");
        record("HERMES_AD_INCR", "SUCCESS", "2026-11-06T02:00:00Z");
        record("HERMES_AD_GROUP", "SUCCESS", "2026-11-06T03:00:00Z");
        record("INCREMENTAL", "FAILED", "2026-11-06T04:00:00Z");

        LdapSyncAudit audit = service.runIncrementalSync();

        verify(ldapClient).fetchUsersWithFilter("(whenChanged>=20261101020000.0Z)");
        assertThat(audit.getSyncType()).isEqualTo("INCREMENTAL");
        assertThat(audit.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldReadFromTheLatestSuccessfulUserSync_whetherFullOrIncremental() throws Exception {
        record("FULL", "SUCCESS", "2026-11-01T02:00:00Z");
        record("INCREMENTAL", "SUCCESS", "2026-11-03T05:30:00Z");
        record("HERMES_AD_INCR", "SUCCESS", "2026-11-06T02:00:00Z");

        service.runIncrementalSync();

        verify(ldapClient).fetchUsersWithFilter("(whenChanged>=20261103053000.0Z)");
    }

    @Test
    void shouldEndPartialAndNotMoveTheStartingPoint_whenAnEntryCannotBeStored() throws Exception {
        record("FULL", "SUCCESS", "2026-11-01T02:00:00Z");
        when(ldapClient.fetchUsersWithFilter(anyString())).thenReturn(List.of(entry("100001"), entry("100002")));
        when(userRepository.findById("100001")).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findById("100002")).thenThrow(new IllegalStateException("connection reset"));

        LdapSyncAudit partial = service.runIncrementalSync();
        service.runIncrementalSync();

        assertThat(partial.getStatus()).isEqualTo("PARTIAL");
        assertThat(partial.getFailed()).isEqualTo(1);
        // the second run starts where the first did, so the entry that was not stored is read again
        verify(ldapClient, times(2)).fetchUsersWithFilter("(whenChanged>=20261101020000.0Z)");
    }

    @Test
    void shouldEndFailedAndNotMoveTheStartingPoint_whenNoEntryCanBeStored() throws Exception {
        record("FULL", "SUCCESS", "2026-11-01T02:00:00Z");
        when(ldapClient.fetchUsersWithFilter(anyString())).thenReturn(List.of(entry("100001"), entry("100002")));
        when(userRepository.findById(anyString())).thenThrow(new IllegalStateException("connection reset"));

        LdapSyncAudit failed = service.runIncrementalSync();
        service.runIncrementalSync();

        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getFailed()).isEqualTo(2);
        verify(ldapClient, times(2)).fetchUsersWithFilter("(whenChanged>=20261101020000.0Z)");
    }

    @Test
    void shouldSucceedAndMoveTheStartingPoint_whenEntriesAreOnlySkippedForNoEmployeeId() throws Exception {
        record("FULL", "SUCCESS", "2026-11-01T02:00:00Z");
        when(ldapClient.fetchUsersWithFilter(anyString())).thenReturn(List.of(Map.of("uid", "svc-report")));

        LdapSyncAudit skipped = service.runIncrementalSync();
        service.runIncrementalSync();

        assertThat(skipped.getStatus()).isEqualTo("SUCCESS");
        assertThat(skipped.getSkippedMissingKey()).isEqualTo(1);
        assertThat(skipped.getFailed()).isZero();
        verify(ldapClient).fetchUsersWithFilter("(whenChanged>=20261101020000.0Z)");
        verify(ldapClient).fetchUsersWithFilter("(whenChanged>=" + AD_TIME.format(skipped.getSnapshotAt()) + ".0Z)");
    }

    @Test
    void shouldFallBackToAFullUserSync_whenOnlyGroupSyncsEverSucceeded() throws Exception {
        record("HERMES_AD_INCR", "SUCCESS", "2026-11-06T02:00:00Z");
        record("FULL", "FAILED", "2026-11-06T03:00:00Z");

        LdapSyncAudit audit = service.runIncrementalSync();

        verify(ldapClient).fetchAllUsers();
        verify(ldapClient, never()).fetchUsersWithFilter(anyString());
        assertThat(audit.getSyncType()).isEqualTo("FULL");
    }

    private static Map<String, String> entry(String employeeId) {
        return Map.of("employeeID", employeeId, "uid", "user" + employeeId);
    }

    private void record(String type, String status, String at) {
        Instant instant = Instant.parse(at);
        auditRepository.saveAndFlush(LdapSyncAudit.builder()
                .id(UUID.randomUUID().toString())
                .syncType(type)
                .status(status)
                .snapshotAt(instant)
                .startedAt(instant)
                .finishedAt(instant)
                .build());
    }
}
