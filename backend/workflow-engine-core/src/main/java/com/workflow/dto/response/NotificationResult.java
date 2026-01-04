package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 通知结果响应DTO
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResult {
    
    /**
     * 操作是否成功
     */
    private boolean success;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 错误消息
     */
    private String errorMessage;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 订阅ID
     */
    private String subscriptionId;
    
    /**
     * 事件ID
     */
    private String eventId;
    
    /**
     * 通知ID
     */
    private String notificationId;
    
    /**
     * 活跃会话列表
     */
    private List<Map<String, Object>> activeSessions;
    
    /**
     * 事件订阅列表
     */
    private List<Map<String, Object>> eventSubscriptions;
    
    /**
     * 通知历史记录
     */
    private List<Map<String, Object>> notificationHistory;
    
    /**
     * 统计信息
     */
    private Map<String, Object> statistics;
    
    /**
     * 响应时间戳
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * 创建成功结果
     */
    public static NotificationResult success(String message) {
        return NotificationResult.builder()
                .success(true)
                .message(message)
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static NotificationResult failure(String errorMessage) {
        return NotificationResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}