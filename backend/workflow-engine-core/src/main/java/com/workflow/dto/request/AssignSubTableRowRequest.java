package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * 分配子表行处理人请求
 * 
 * 用于多实例子流程前置任务中，手动为子表行分配处理人。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignSubTableRowRequest {
    
    /**
     * 处理人用户 ID
     */
    @NotBlank(message = "处理人用户ID不能为空")
    private String assigneeId;
}
