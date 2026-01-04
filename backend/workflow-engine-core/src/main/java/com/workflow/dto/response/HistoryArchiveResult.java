package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 历史数据归档结果
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryArchiveResult {
    
    /**
     * 操作是否成功
     */
    private boolean success;
    
    /**
     * 归档ID
     */
    private String archiveId;
    
    /**
     * 流程实例ID
     */
    private String processInstanceId;
    
    /**
     * 归档的数据条数
     */
    private int archivedDataCount;
    
    /**
     * 归档时间
     */
    private LocalDateTime archiveTime;
    
    /**
     * 结果消息
     */
    private String message;
    
    /**
     * 错误消息
     */
    private String errorMessage;
}