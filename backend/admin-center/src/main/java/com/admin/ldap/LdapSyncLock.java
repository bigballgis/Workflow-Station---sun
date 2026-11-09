package com.admin.ldap;

import jakarta.annotation.PreDestroy;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The one ShedLock lock every LDAP / AD sync of Admin Center takes, so at most one runs at a time across replicas:
 * the group reconcile at startup and on schedule ({@link LdapHermesGroupSyncComponent}), and the user and group syncs
 * a System Administrator starts.
 *
 * <p>While a sync holds the lock it is extended every half of {@link #AT_MOST_FOR}, so a sync that runs longer than
 * that keeps it; if the holder dies the extensions stop and the lock lapses within {@link #AT_MOST_FOR}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "ldap", name = {"enabled", "sync-enabled"}, havingValue = "true")
public class LdapSyncLock {

    public static final String NAME = "LdapHermesGroupSync_onSchedule";
    /** Released by then if the holder died; extended while the holder runs. */
    public static final String AT_MOST_FOR = "PT20M";
    /** Kept by the startup and scheduled runs so that replicas with a slightly skewed clock do not repeat them. */
    public static final String AT_LEAST_FOR = "PT1M";

    private final ScheduledExecutorService keepAliveExecutor;
    private final KeepAliveLockProvider lockProvider;

    @Autowired
    public LdapSyncLock(LockProvider lockProvider) {
        this(lockProvider, Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "ldap-sync-lock-keep-alive");
            thread.setDaemon(true);
            return thread;
        }));
    }

    LdapSyncLock(LockProvider lockProvider, ScheduledExecutorService keepAliveExecutor) {
        if (!(lockProvider instanceof ExtensibleLockProvider extensible)) {
            throw new IllegalStateException("The LDAP sync lock is extended while a sync runs, which "
                    + lockProvider.getClass().getName() + " does not support");
        }
        this.keepAliveExecutor = keepAliveExecutor;
        this.lockProvider = new KeepAliveLockProvider(extensible, keepAliveExecutor);
    }

    /**
     * The lock for a run starting now, held at least {@code atLeastFor} after it is released; empty while another
     * run holds it. A lock store that cannot be reached surfaces as the store's exception.
     */
    public Optional<SimpleLock> tryAcquire(Duration atLeastFor) {
        return lockProvider.lock(new LockConfiguration(Instant.now(), NAME, Duration.parse(AT_MOST_FOR), atLeastFor));
    }

    @PreDestroy
    void stopKeepAlive() {
        keepAliveExecutor.shutdownNow();
    }
}
