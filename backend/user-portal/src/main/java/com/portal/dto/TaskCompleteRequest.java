package com.portal.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 任务完成请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCompleteRequest {

    /** 任务ID */
    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /** 操作类型：APPROVE, REJECT, TRANSFER, DELEGATE, RETURN */
    @NotBlank(message = "操作类型不能为空")
    private String action;

    /** 处理意见 */
    private String comment;

    /** 表单数据 */
    private Map<String, Object> formData;

    /** 流程变量 */
    private Map<String, Object> variables;

    /** 转办/委托目标用户ID */
    private String targetUserId;

    /** 回退目标节点ID */
    private String returnActivityId;
}
