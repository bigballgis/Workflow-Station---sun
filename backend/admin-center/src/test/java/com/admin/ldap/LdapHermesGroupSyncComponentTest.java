package com.admin.ldap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The startup and scheduled runs of the AD group sync take the shared LDAP sync lock: with several replicas at most one
 * reconcile runs at a time, and a replica that finds the lock held skips its run and logs a warning.
 */
class LdapHermesGroupSyncComponentTest {

    /** Stands in for the shared Redis lock store: one holder per lock name; a held lock can be extended. */
    static final class InMemoryLockProvider implements ExtensibleLockProvider {
        final Set<String> held = ConcurrentHashMap.newKeySet();
        final List<LockConfiguration> requests = new CopyOnWriteArrayList<>();
        /** The lockAtMostFor of each extension of a held lock. */
        final List<Duration> extensions = new CopyOnWriteArrayList<>();

        @Override
        public Optional<SimpleLock> lock(LockConfiguration configuration) {
            requests.add(configuration);
            if (!held.add(configuration.getName())) {
                return Optional.empty();
            }
            return Optional.of(new HeldLock(configuration.getName()));
        }

        private final class HeldLock implements SimpleLock {
            private final String name;

            private HeldLock(String name) {
                this.name = name;
            }

            @Override
            public void unlock() {
                held.remove(name);
            }

            @Override
            public Optional<SimpleLock> extend(Duration lockAtMostFor, Duration lockAtLeastFor) {
                if (!held.contains(name)) {
                    return Optional.empty();
                }
                extensions.add(lockAtMostFor);
                return Optional.of(this);
            }
        }
    }

    private final InMemoryLockProvider locks = new InMemoryLockProvider();

    private final Logger componentLogger = (Logger) LoggerFactory.getLogger(LdapHermesGroupSyncComponent.class);
    private ListAppender<ILoggingEvent> appender;

    private LdapHermesGroupSyncComponent replica(LdapSyncService syncService) {
        return new LdapHermesGroupSyncComponent(syncService, new LdapSyncLock(locks));
    }

    private ListAppender<ILoggingEvent> captureLogs() {
        appender = new ListAppender<>();
        appender.start();
        componentLogger.addAppender(appender);
        return appender;
    }

    @AfterEach
    void detachLogs() {
        if (appender != null) {
            componentLogger.detachAppender(appender);
        }
    }

    @Test
    void shouldRunTheStartupSyncUnderTheScheduledLock_whenNoOtherInstanceHoldsIt() throws Exception {
        LdapSyncService syncService = mock(LdapSyncService.class);

        replica(syncService).runStartupSync();

        verify(syncService).runHermesAdGroupIncrementalSync();
        assertThat(locks.requests).singleElement().satisfies(request -> {
            assertThat(request.getName()).isEqualTo(LdapSyncLock.NAME);
            assertThat(request.getLockAtMostFor()).isEqualTo(Duration.ofMinutes(20));
            assertThat(request.getLockAtLeastFor()).isEqualTo(Duration.ofMinutes(1));
        });
        assertThat(locks.held).isEmpty();
    }

    @Test
    void shouldRunTheScheduledSyncUnderTheSameLock() {
        LdapSyncService syncService = mock(LdapSyncService.class);

        replica(syncService).onSchedule();

        verify(syncService).runHermesAdGroupIncrementalSync();
        assertThat(locks.requests).singleElement().satisfies(request -> {
            assertThat(request.getName()).isEqualTo(LdapSyncLock.NAME);
            assertThat(request.getLockAtLeastFor()).isEqualTo(Duration.ofMinutes(1));
        });
        assertThat(locks.held).isEmpty();
    }

    @Test
    void shouldSkipTheScheduledAndStartupSyncWithAWarning_whenAnotherSyncHoldsTheLock() {
        LdapSyncService syncService = mock(LdapSyncService.class);
        locks.held.add(LdapSyncLock.NAME);
        ListAppender<ILoggingEvent> logs = captureLogs();

        replica(syncService).onSchedule();
        replica(syncService).runStartupSync();

        verify(syncService, never()).runHermesAdGroupIncrementalSync();
        assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "Scheduled Hermes AD group sync skipped: another LDAP sync holds lock " + LdapSyncLock.NAME,
                        "Startup Hermes AD group sync skipped: another LDAP sync holds lock " + LdapSyncLock.NAME);
    }

    @Test
    void shouldNotRunAndNotThrow_whenTheLockStoreCannotBeReached() {
        LdapSyncService syncService = mock(LdapSyncService.class);
        ExtensibleLockProvider unreachable = configuration -> {
            throw new RedisConnectionFailureException("Unable to connect to Redis");
        };
        LdapHermesGroupSyncComponent component =
                new LdapHermesGroupSyncComponent(syncService, new LdapSyncLock(unreachable));

        component.onSchedule();
        component.runStartupSync();

        verify(syncService, never()).runHermesAdGroupIncrementalSync();
    }

    @Test
    void shouldSkipTheStartupSync_whenAnotherSyncHoldsTheLock() {
        LdapSyncService syncService = mock(LdapSyncService.class);
        locks.held.add(LdapSyncLock.NAME); // another replica is in its scheduled run, or a manual sync runs

        replica(syncService).runStartupSync();

        verify(syncService, never()).runHermesAdGroupIncrementalSync();
    }

    @Test
    void shouldRunOnlyOneReconcile_whenTwoReplicasStartTogether() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        LdapSyncService first = mock(LdapSyncService.class);
        when(first.runHermesAdGroupIncrementalSync()).thenAnswer(invocation -> {
            firstStarted.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        });
        LdapSyncService second = mock(LdapSyncService.class);

        CompletableFuture<Void> firstRun = CompletableFuture.runAsync(replica(first)::runStartupSync);
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
        replica(second).runStartupSync();
        release.countDown();
        firstRun.get(5, TimeUnit.SECONDS);

        verify(first, times(1)).runHermesAdGroupIncrementalSync();
        verify(second, never()).runHermesAdGroupIncrementalSync();
        assertThat(locks.held).isEmpty();
    }

    @Test
    void shouldReleaseTheLockAndNotThrow_whenTheStartupSyncFails() {
        LdapSyncService syncService = mock(LdapSyncService.class);
        when(syncService.runHermesAdGroupIncrementalSync()).thenThrow(new IllegalStateException("AD unreachable"));

        replica(syncService).runStartupSync();

        assertThat(locks.held).isEmpty();
    }
}
