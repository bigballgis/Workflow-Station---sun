package com.portal.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskInfo {

    /** 任务ID */
    private String taskId;

    /** 任务名称 */
    private String taskName;

    /** 任务描述 */
    private String description;

    /** 流程实例ID */
    private String processInstanceId;

    /** 流程定义Key */
    private String processDefinitionKey;

    /** 流程定义名称 */
    private String processDefinitionName;

    /** 分配类型：USER, VIRTUAL_GROUP, DEPT_ROLE, DELEGATED */
    private String assignmentType;

    /** 分配人/组ID */
    private String assignee;

    /** 分配人/组名称 */
    private String assigneeName;

    /** 委托人ID（如果是委托任务） */
    private String delegatorId;

    /** 委托人名称 */
    private String delegatorName;

    /** 发起人ID */
    private String initiatorId;

    /** 发起人名称 */
    private String initiatorName;

    /** 优先级 */
    private String priority;

    /** 任务状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 到期时间 */
    private LocalDateTime dueDate;

    /** 是否逾期 */
    private Boolean isOverdue;

    /** 表单Key */
    private String formKey;

    /** 流程变量 */
    private Map<String, Object> variables;
}
