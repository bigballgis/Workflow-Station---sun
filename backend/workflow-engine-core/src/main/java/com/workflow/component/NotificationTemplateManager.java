package com.workflow.component;

import com.workflow.component.NotificationManagerComponent.NotificationTemplate;
import com.workflow.dto.response.NotificationResult;
import com.workflow.exception.WorkflowBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Notification template definition, rendering and default-template bootstrapping for the
 * notification subsystem.
 *
 * <p>Extracted from {@link NotificationManagerComponent}; behaviour is preserved verbatim. Stateless:
 * reads/writes the shared {@link NotificationContext} passed on each call.</p>
 */
@Slf4j
@Component
class NotificationTemplateManager {

    /**
     * Define a notification template
     */
    NotificationResult defineNotificationTemplate(NotificationContext ctx, NotificationTemplate template) {
        log.info("Defining notification template: templateId={}, eventType={}", template.getTemplateId(), template.getEventType());

        try {
            if (template.getCreatedTime() == null) {
                template.setCreatedTime(LocalDateTime.now());
            }

            ctx.notificationTemplates.put(template.getTemplateId(), template);

            // Cache to Redis
            String cacheKey = NotificationContext.NOTIFICATION_PREFIX + "template:" + template.getTemplateId();
            String templateJson = ctx.objectMapper.writeValueAsString(template);
            ctx.stringRedisTemplate.opsForValue().set(cacheKey, templateJson, Duration.ofDays(30));

            return NotificationResult.builder()
                    .success(true)
                    .message("Notification template defined successfully")
                    .build();

        } catch (Exception e) {
            log.error("Failed to define notification template: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("TEMPLATE_DEFINE_FAILED", "Failed to define notification template: " + e.getMessage());
        }
    }

    /**
     * Render notification template content
     */
    Map<String, String> renderNotificationTemplate(NotificationContext ctx, String templateId, Map<String, Object> variables, String language) {
        log.info("Rendering notification template: templateId={}, language={}", templateId, language);

        NotificationTemplate template = ctx.notificationTemplates.get(templateId);
        if (template == null) {
            throw new WorkflowBusinessException("TEMPLATE_NOT_FOUND", "Notification template not found: " + templateId);
        }

        // Get localized content
        String subject = template.getSubject();
        String body = template.getBodyTemplate();

        if (language != null && template.getLocalizedSubjects() != null) {
            subject = template.getLocalizedSubjects().getOrDefault(language, subject);
        }
        if (language != null && template.getLocalizedBodies() != null) {
            body = template.getLocalizedBodies().getOrDefault(language, body);
        }

        // Replace variables
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            subject = subject.replace(placeholder, value);
            body = body.replace(placeholder, value);
        }

        Map<String, String> result = new HashMap<>();
        result.put("subject", subject);
        result.put("body", body);
        return result;
    }

    /**
     * Initialize default notification templates
     */
    void initializeDefaultTemplates(NotificationContext ctx) {
        log.info("Initializing default notification templates");

        // Task assignment template
        NotificationTemplate taskAssignedTemplate = new NotificationTemplate();
        taskAssignedTemplate.setTemplateId("TASK_ASSIGNED_DEFAULT");
        taskAssignedTemplate.setTemplateName("Task Assignment Notification");
        taskAssignedTemplate.setEventType("TASK_ASSIGNED");
        taskAssignedTemplate.setSubject("You have a new task to process");
        taskAssignedTemplate.setBodyTemplate("Task ${taskName} has been assigned to you, please process it promptly.");
        taskAssignedTemplate.setLocalizedSubjects(Map.of("en", "You have a new task"));
        taskAssignedTemplate.setLocalizedBodies(Map.of("en", "Task ${taskName} has been assigned to you."));
        taskAssignedTemplate.setChannels(Set.of("WEBSOCKET", "IN_APP", "EMAIL"));
        taskAssignedTemplate.setEnabled(true);
        defineNotificationTemplate(ctx, taskAssignedTemplate);

        // Task overdue template
        NotificationTemplate taskOverdueTemplate = new NotificationTemplate();
        taskOverdueTemplate.setTemplateId("TASK_OVERDUE_DEFAULT");
        taskOverdueTemplate.setTemplateName("Task Overdue Notification");
        taskOverdueTemplate.setEventType("TASK_OVERDUE");
        taskOverdueTemplate.setSubject("Task is overdue");
        taskOverdueTemplate.setBodyTemplate("Task ${taskName} is overdue, please process it as soon as possible.");
        taskOverdueTemplate.setLocalizedSubjects(Map.of("en", "Task overdue"));
        taskOverdueTemplate.setLocalizedBodies(Map.of("en", "Task ${taskName} is overdue."));
        taskOverdueTemplate.setChannels(Set.of("WEBSOCKET", "IN_APP", "EMAIL", "SMS"));
        taskOverdueTemplate.setEnabled(true);
        defineNotificationTemplate(ctx, taskOverdueTemplate);

        // Process completed template
        NotificationTemplate processCompletedTemplate = new NotificationTemplate();
        processCompletedTemplate.setTemplateId("PROCESS_COMPLETED_DEFAULT");
        processCompletedTemplate.setTemplateName("Process Completed Notification");
        processCompletedTemplate.setEventType("PROCESS_COMPLETED");
        processCompletedTemplate.setSubject("Process completed");
        processCompletedTemplate.setBodyTemplate("Process ${processDefinitionKey} (business key: ${businessKey}) has been completed.");
        processCompletedTemplate.setLocalizedSubjects(Map.of("en", "Process completed"));
        processCompletedTemplate.setLocalizedBodies(Map.of("en", "Process ${processDefinitionKey} (business key: ${businessKey}) has been completed."));
        processCompletedTemplate.setChannels(Set.of("WEBSOCKET", "IN_APP"));
        processCompletedTemplate.setEnabled(true);
        defineNotificationTemplate(ctx, processCompletedTemplate);

        log.info("Default notification templates initialized");
    }
}
