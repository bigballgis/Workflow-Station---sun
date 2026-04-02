package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分配子表行处理人响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignSubTableRowResponse {
    
    /**
     * 操作是否成功
     */
    private boolean success;
    
    /**
     * 子表行 ID
     */
    private Long rowId;
    
    /**
     * 处理人用户 ID
     */
    private String assigneeId;
    
    /**
     * 处理人姓名
     */
    private String assigneeName;
    
    /**
     * 错误消息（失败时）
     */
    private String errorMessage;
}
