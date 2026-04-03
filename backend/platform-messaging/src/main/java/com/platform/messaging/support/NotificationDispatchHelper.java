package com.platform.messaging.support;

import com.platform.messaging.event.NotificationEvent;
import com.platform.messaging.service.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Publishes {@link NotificationEvent} to Kafka after successful DB commit when a transaction is active.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchHelper {

    private final EventPublisher eventPublisher;

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
                    action.run();
                }
            });
        } else {
            action.run();
        }
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
