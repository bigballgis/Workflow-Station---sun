package com.platform.messaging.support;

import com.platform.messaging.event.NotificationEvent;
import com.platform.messaging.service.EventPublisher;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Publishes {@link NotificationEvent} to Kafka after successful DB commit when a transaction is active.
 *
 * <p>The actual publish runs on a small best-effort thread pool, never on the request thread:
 * even with a bounded producer {@code max.block.ms}, a slow or unreachable broker must not stall
 * the caller (process start, task complete, etc.). Notifications are best-effort — on saturation
 * they are dropped with a warning rather than back-pressuring the request.
 */
@Slf4j
@Component
public class NotificationDispatchHelper {

    private final EventPublisher eventPublisher;

    /** Best-effort dispatch pool; kept off the request thread. Bounded queue, drop-on-saturation. */
    private final ExecutorService dispatchExecutor;

    /** Distinguishes an expected drop during shutdown from a genuine saturation drop, for accurate logging. */
    private volatile boolean shuttingDown = false;

    public NotificationDispatchHelper(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        AtomicInteger threadIndex = new AtomicInteger();
        this.dispatchExecutor = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                runnable -> {
                    Thread thread = new Thread(runnable, "notif-dispatch-" + threadIndex.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                (rejected, executor) -> {
                    if (shuttingDown) {
                        log.debug("Notification dropped during shutdown (best-effort)");
                    } else {
                        log.warn("Notification dispatch pool saturated; dropping a notification (best-effort)");
                    }
                });
    }

    @PreDestroy
    void shutdown() {
        shuttingDown = true;
        dispatchExecutor.shutdown();
    }

    /**
     * Notify a single user after commit (or immediately if no transaction).
     */
    public void publishToUserAfterCommit(String targetUserId, String notificationType,
            String title, String content, String link, String sourceService) {
        if (!StringUtils.hasText(targetUserId) || !StringUtils.hasText(notificationType)
                || !StringUtils.hasText(title)) {
            return;
        }
        runAfterCommit(() -> sendOne(targetUserId, notificationType, title, content, link, sourceService));
    }

    /**
     * Notify multiple users in one post-commit callback (avoids nested transaction synchronization).
     */
    public void publishToUsersAfterCommit(Collection<String> targetUserIds, String notificationType,
            String title, String content, String link, String sourceService) {
        if (targetUserIds == null || targetUserIds.isEmpty()
                || !StringUtils.hasText(notificationType) || !StringUtils.hasText(title)) {
            return;
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String id : targetUserIds) {
            if (StringUtils.hasText(id)) {
                distinct.add(id.trim());
            }
        }
        if (distinct.isEmpty()) {
            return;
        }
        runAfterCommit(() -> {
            for (String uid : distinct) {
                sendOne(uid, notificationType, title, content, link, sourceService);
            }
        });
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchAsync(action);
                }
            });
        } else {
            dispatchAsync(action);
        }
    }

    /** Run the publish off the request thread; the executor's rejection handler logs any drop. */
    private void dispatchAsync(Runnable action) {
        dispatchExecutor.execute(action);
    }

    private void sendOne(String targetUserId, String notificationType, String title,
            String content, String link, String sourceService) {
        try {
            NotificationEvent event = NotificationEvent.builder()
                    .targetUserId(targetUserId)
                    .notificationType(notificationType)
                    .title(title)
                    .content(content != null ? content : "")
                    .link(link)
                    .sourceService(sourceService != null ? sourceService : "platform")
                    .eventType(notificationType)
                    .build();
            eventPublisher.publishNotificationEvent(event).whenComplete((ok, ex) -> {
                if (ex != null) {
                    log.warn("Failed to publish notification for user {}: {}", targetUserId, ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("Failed to build/send notification for user {}: {}", targetUserId, e.getMessage());
        }
    }
}
