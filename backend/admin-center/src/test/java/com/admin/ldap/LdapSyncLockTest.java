package com.admin.ldap;

import com.admin.config.SchedulerLockConfig;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The LDAP sync lock is extended while its holder runs, so a sync longer than {@link LdapSyncLock#AT_MOST_FOR} keeps
 * it; a holder that dies stops extending and the lock lapses within that time.
 */
class LdapSyncLockTest {

    private final LdapHermesGroupSyncComponentTest.InMemoryLockProvider locks =
            new LdapHermesGroupSyncComponentTest.InMemoryLockProvider();
    private final ScheduledExecutorService keepAlive = mock(ScheduledExecutorService.class);
    private final ScheduledFuture<?> extensions = mock(ScheduledFuture.class);

    @Test
    void shouldExtendTheHeldLockEveryHalfOfItsMaximumHold_untilItIsReleased() {
        doReturn(extensions).when(keepAlive).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
        LdapSyncLock syncLock = new LdapSyncLock(locks, keepAlive);

        SimpleLock lock = syncLock.tryAcquire(Duration.ZERO).orElseThrow();

        LockConfiguration request = locks.requests.get(0);
        assertThat(request.getLockAtMostFor()).isEqualTo(Duration.ofMinutes(20)); // a dead holder's lock lapses by then
        ArgumentCaptor<Runnable> extend = ArgumentCaptor.forClass(Runnable.class);
        long tenMinutes = Duration.ofMinutes(10).toMillis();
        verify(keepAlive).scheduleAtFixedRate(extend.capture(), eq(tenMinutes), eq(tenMinutes),
                eq(TimeUnit.MILLISECONDS));

        extend.getValue().run(); // ten minutes into the sync
        extend.getValue().run(); // twenty minutes: past the first maximum hold, still held

        assertThat(locks.extensions).containsExactly(Duration.ofMinutes(20), Duration.ofMinutes(20));
        assertThat(locks.held).containsExactly(LdapSyncLock.NAME);

        lock.unlock();

        verify(extensions).cancel(false);
        assertThat(locks.held).isEmpty();
    }

    @Test
    void shouldStillRefuse_whileAnotherHolderKeepsTheLock() {
        doReturn(extensions).when(keepAlive).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
        LdapSyncLock syncLock = new LdapSyncLock(locks, keepAlive);
        syncLock.tryAcquire(Duration.ZERO).orElseThrow();

        Optional<SimpleLock> second = syncLock.tryAcquire(Duration.ZERO);

        assertThat(second).isEmpty();
    }

    @Test
    void shouldRefuseALockStoreThatCannotExtendAHeldLock() {
        LockProvider notExtensible = configuration -> Optional.empty();

        assertThatThrownBy(() -> new LdapSyncLock(notExtensible, keepAlive))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("extended while a sync runs");
    }

    @Test
    void shouldAcceptTheRedisLockStoreAdminCenterIsConfiguredWith() {
        LockProvider configured = new SchedulerLockConfig().lockProvider(mock(RedisConnectionFactory.class));

        assertThatCode(() -> new LdapSyncLock(configured).stopKeepAlive()).doesNotThrowAnyException();
    }
}
