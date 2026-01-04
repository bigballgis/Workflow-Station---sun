package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 事件触发结果
 * 用于表示流程事件触发的结果信息
 */
@Data
@Builder
public class EventTriggerResult {
    
    /**
     * 事件ID
     */
    private String eventId;
    
    /**
     * 事件类型
     */
    private String eventType;
    
    /**
     * 流程实例ID
     */
    private String processInstanceId;
    
    /**
     * 被触发的执行实例ID列表
     */
    private List<String> triggeredExecutions;
    
    /**
     * 事件数据
     */
    private Map<String, Object> eventData;
    
    /**
     * 触发时间
     */
    private LocalDateTime triggerTime;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 结果消息
     */
    private String message;
}