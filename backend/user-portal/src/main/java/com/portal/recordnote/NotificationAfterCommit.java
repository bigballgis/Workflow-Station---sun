package com.portal.recordnote;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** An unread notification is durable before any realtime signal can expose it. */
@Slf4j
public final class NotificationAfterCommit {
    private NotificationAfterCommit() {}
    public static void run(Runnable push) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { push.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { push.run(); }
                catch (RuntimeException e) {
                    // FALLBACK(ux): persistent notification is already committed; clients also fetch unread records.
                    log.warn("Committed notification realtime push failed: {}",e.getClass().getSimpleName());
                }
            }
        });
    }
}
