package com.workflow.messaging;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Publisher for sub-table update events via WebSocket
 * 
 * Sends real-time notifications to clients when sub-table data changes,
 * enabling immediate UI updates without polling.
 */
@Slf4j
@Component
public class SubTableUpdatePublisher {

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Publish a sub-table update event
     * 
     * @param taskId Task ID associated with the sub-table
     * @param rowId Sub-table row ID that was updated
     * @param assigneeId Updated assignee ID (optional)
     * @param status Updated status (optional)
     */
    public void publishUpdate(String taskId, Long rowId, String assigneeId, String status) {
        publishUpdate(taskId, rowId, null, assigneeId, status);
    }

    /**
     * @param rowKey optional full primary key map (composite supported); {@code rowId} remains for backward-compatible clients
     */
    public void publishUpdate(String taskId, Long rowId, Map<String, Object> rowKey,
                              String assigneeId, String status) {
        if (messagingTemplate == null) {
            log.warn("WebSocket messaging template not available, skipping update publication");
            return;
        }

        SubTableUpdateMessage message = SubTableUpdateMessage.builder()
                .taskId(taskId)
                .rowId(rowId)
                .rowKey(rowKey)
                .assigneeId(assigneeId)
                .status(status)
                .timestamp(LocalDateTime.now().toString())
                .build();

        String topic = String.format("/topic/tasks/%s/sub-table-updates", taskId);

        try {
            messagingTemplate.convertAndSend(topic, message);
            log.debug("Published sub-table update to topic {}: rowId={}, rowKey={}, assigneeId={}, status={}",
                    topic, rowId, rowKey, assigneeId, status);
        } catch (Exception e) {
            log.error("Failed to publish sub-table update to topic {}: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * Message structure for sub-table updates
     */
    @Data
    @Builder
    public static class SubTableUpdateMessage {
        private String taskId;
        private Long rowId;
        private Map<String, Object> rowKey;
        private String assigneeId;
        private String status;
        private String timestamp;
    }
}
