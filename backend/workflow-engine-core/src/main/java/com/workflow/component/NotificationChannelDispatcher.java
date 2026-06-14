package com.workflow.component;

import com.workflow.component.NotificationManagerComponent.NotificationRecord;
import com.workflow.component.NotificationManagerComponent.NotificationTemplate;
import com.workflow.component.NotificationManagerComponent.UserNotificationPreference;
import com.workflow.component.NotificationManagerComponent.WorkflowEvent;
import com.workflow.dto.response.NotificationResult;
import com.workflow.exception.WorkflowBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Multi-channel notification delivery (email / SMS / in-app / multi-channel fan-out) and in-app
 * message read management for the notification subsystem.
 *
 * <p>Extracted from {@link NotificationManagerComponent}; behaviour is preserved verbatim. Stateless:
 * reads/writes the shared {@link NotificationContext} passed on each call.</p>
 */
@Slf4j
@Component
class NotificationChannelDispatcher {

    /**
     * Send email notification
     */
    NotificationResult sendEmailNotification(NotificationContext ctx, String userId, String email, String subject, String body) {
        log.info("Sending email notification: userId={}, email={}, subject={}", userId, email, subject);

        try {
            // Check user notification preferences
            if (!ctx.isChannelEnabled(userId, "EMAIL")) {
                log.info("User has disabled email notifications: userId={}", userId);
                return NotificationResult.builder()
                        .success(false)
                        .message("User has disabled email notifications")
                        .build();
            }

            // Simulated email sending (should use JavaMailSender in production)
            log.info("Email sent successfully: to={}, subject={}", email, subject);

            // Record notification
            NotificationRecord record = new NotificationRecord(
                    UUID.randomUUID().toString(),
                    userId,
                    "EMAIL",
                    subject + ": " + body
            );
            record.setDelivered(true);
            ctx.notificationHistory.add(record);

            return NotificationResult.builder()
                    .success(true)
                    .message("Email sent successfully")
                    .notificationId(record.getNotificationId())
                    .build();

        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("EMAIL_SEND_FAILED", "Failed to send email notification: " + e.getMessage());
        }
    }

    /**
     * Send SMS notification
     */
    NotificationResult sendSmsNotification(NotificationContext ctx, String userId, String phoneNumber, String message) {
        log.info("Sending SMS notification: userId={}, phoneNumber={}", userId, phoneNumber);

        try {
            // Check user notification preferences
            if (!ctx.isChannelEnabled(userId, "SMS")) {
                log.info("User has disabled SMS notifications: userId={}", userId);
                return NotificationResult.builder()
                        .success(false)
                        .message("User has disabled SMS notifications")
                        .build();
            }

            // Simulated SMS sending (should use SMS service SDK in production)
            log.info("SMS sent successfully: to={}, message={}", phoneNumber, message);

            // Record notification
            NotificationRecord record = new NotificationRecord(
                    UUID.randomUUID().toString(),
                    userId,
                    "SMS",
                    message
            );
            record.setDelivered(true);
            ctx.notificationHistory.add(record);

            return NotificationResult.builder()
                    .success(true)
                    .message("SMS sent successfully")
                    .notificationId(record.getNotificationId())
                    .build();

        } catch (Exception e) {
            log.error("Failed to send SMS notification: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("SMS_SEND_FAILED", "Failed to send SMS notification: " + e.getMessage());
        }
    }

    /**
     * Send in-app notification
     */
    NotificationResult sendInAppNotification(NotificationContext ctx, String userId, String title, String content, String eventType) {
        log.info("Sending in-app notification: userId={}, title={}", userId, title);

        try {
            // Check user notification preferences
            if (!ctx.isChannelEnabled(userId, "IN_APP")) {
                log.info("User has disabled in-app notifications: userId={}", userId);
                return NotificationResult.builder()
                        .success(false)
                        .message("User has disabled in-app notifications")
                        .build();
            }

            // Store in-app message in Redis
            String messageId = UUID.randomUUID().toString();
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("messageId", messageId);
            messageData.put("userId", userId);
            messageData.put("title", title);
            messageData.put("content", content);
            messageData.put("eventType", eventType);
            messageData.put("read", false);
            messageData.put("createdTime", LocalDateTime.now().toString());

            String messageKey = NotificationContext.NOTIFICATION_PREFIX + "in_app:" + userId + ":" + messageId;
            String messageJson = ctx.objectMapper.writeValueAsString(messageData);
            ctx.stringRedisTemplate.opsForValue().set(messageKey, messageJson, Duration.ofDays(30));

            // Record notification
            NotificationRecord record = new NotificationRecord(
                    messageId,
                    userId,
                    "IN_APP",
                    title + ": " + content
            );
            record.setDelivered(true);
            ctx.notificationHistory.add(record);

            // Also send WebSocket notification
            ctx.sendWebSocketNotification(userId, title + ": " + content);

            return NotificationResult.builder()
                    .success(true)
                    .message("In-app notification sent successfully")
                    .notificationId(messageId)
                    .build();

        } catch (Exception e) {
            log.error("Failed to send in-app notification: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("IN_APP_SEND_FAILED", "Failed to send in-app notification: " + e.getMessage());
        }
    }

    /**
     * Send multi-channel notification
     */
    NotificationResult sendMultiChannelNotification(NotificationContext ctx, String userId, WorkflowEvent event) {
        log.info("Sending multi-channel notification: userId={}, eventType={}", userId, event.getEventType());

        try {
            UserNotificationPreference preference = ctx.getUserPreference(userId);
            NotificationTemplate template = ctx.getTemplateForEvent(event.getEventType());

            String message = ctx.buildNotificationMessage(event);
            String subject = template != null ? template.getSubject() : "Workflow Notification";

            int successCount = 0;
            int totalChannels = 0;

            Set<String> channels = preference != null ? preference.getEnabledChannels() :
                    Set.of("WEBSOCKET", "IN_APP");

            for (String channel : channels) {
                totalChannels++;
                try {
                    switch (channel) {
                        case "WEBSOCKET":
                            if (ctx.sendWebSocketNotification(userId, message)) {
                                successCount++;
                            }
                            break;
                        case "IN_APP":
                            sendInAppNotification(ctx, userId, subject, message, event.getEventType());
                            successCount++;
                            break;
                        case "EMAIL":
                            log.info("Email notification requires user email address");
                            break;
                        case "SMS":
                            log.info("SMS notification requires user phone number");
                            break;
                    }
                } catch (Exception e) {
                    log.error("Failed to send {} notification: {}", channel, e.getMessage());
                }
            }

            return NotificationResult.builder()
                    .success(successCount > 0)
                    .message(String.format("Multi-channel notification completed: %d/%d succeeded", successCount, totalChannels))
                    .build();

        } catch (Exception e) {
            log.error("Failed to send multi-channel notification: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("MULTI_CHANNEL_SEND_FAILED", "Failed to send multi-channel notification: " + e.getMessage());
        }
    }

    /**
     * Get unread in-app messages for a user
     */
    List<Map<String, Object>> getUnreadInAppMessages(NotificationContext ctx, String userId, int limit) {
        log.info("Getting unread in-app messages: userId={}, limit={}", userId, limit);

        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            String pattern = NotificationContext.NOTIFICATION_PREFIX + "in_app:" + userId + ":*";
            Set<String> keys = ctx.stringRedisTemplate.keys(pattern);

            if (keys == null || keys.isEmpty()) {
                return messages;
            }

            for (String key : keys) {
                if (messages.size() >= limit) {
                    break;
                }

                String messageJson = ctx.stringRedisTemplate.opsForValue().get(key);
                if (messageJson != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = ctx.objectMapper.readValue(messageJson, Map.class);
                    if (Boolean.FALSE.equals(message.get("read"))) {
                        messages.add(message);
                    }
                }
            }

            return messages;

        } catch (Exception e) {
            log.error("Failed to get unread in-app messages: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("GET_MESSAGES_FAILED", "Failed to get unread in-app messages: " + e.getMessage());
        }
    }

    /**
     * Mark in-app message as read
     */
    NotificationResult markInAppMessageAsRead(NotificationContext ctx, String userId, String messageId) {
        log.info("Marking in-app message as read: userId={}, messageId={}", userId, messageId);

        try {
            String messageKey = NotificationContext.NOTIFICATION_PREFIX + "in_app:" + userId + ":" + messageId;
            String messageJson = ctx.stringRedisTemplate.opsForValue().get(messageKey);

            if (messageJson == null) {
                return NotificationResult.builder()
                        .success(false)
                        .message("Message not found")
                        .build();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> message = ctx.objectMapper.readValue(messageJson, Map.class);
            message.put("read", true);
            message.put("readTime", LocalDateTime.now().toString());

            String updatedJson = ctx.objectMapper.writeValueAsString(message);
            ctx.stringRedisTemplate.opsForValue().set(messageKey, updatedJson, Duration.ofDays(30));

            return NotificationResult.builder()
                    .success(true)
                    .message("Message marked as read")
                    .build();

        } catch (Exception e) {
            log.error("Failed to mark message as read: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("MARK_READ_FAILED", "Failed to mark message as read: " + e.getMessage());
        }
    }
}
