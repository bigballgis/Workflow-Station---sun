package com.admin.ldap;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Runs a sync a System Administrator started, off the request thread, and releases the sync lock the request took
 * once it ends. Its own bean so that {@link Async} applies when {@link LdapSyncAuditService} calls it.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ldap", name = {"enabled", "sync-enabled"}, havingValue = "true")
public class LdapManualSyncRunner {

    @Async
    public void run(String type, Supplier<LdapSyncAudit> sync, SimpleLock lock) {
        try {
            sync.get();
        } catch (Exception e) {
            log.error("Manual LDAP {} sync error: {}", type, e.getMessage());
        } finally {
            lock.unlock();
        }
    }
}
