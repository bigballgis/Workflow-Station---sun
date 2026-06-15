package com.portal.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * BPMN 用户任务扩展 assigneeType（如 INITIATOR、PROCESS_INITIATOR），与运行时 assignmentType 分离。
     */
    private String bpmnAssigneeType;

    /**
     * BPMN 扩展 businessUnitId（如 FIXED_BU_ROLE 固定 BU），引擎任务列表/详情返回。
     */
    private String bpmnBusinessUnitId;

    /**
     * 引擎分配目标：USER 时为处理人 ID；CANDIDATE_USERS 时为候选人用户 ID 逗号拼接等（与 workflow-engine TaskListResult 一致）
     */
    private String assignmentTarget;

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

    /** 任务定义Key（BPMN元素ID，如 Activity_1abc） */
    private String taskDefinitionKey;

    /** 流程变量 */
    private Map<String, Object> variables;

    /**
     * Request ID:主表配置的有序字段 + 分隔符拼成的人类可读标识(如 HR-2026-001),
     * 由后端 RequestIdEnricher 填充;主表未配置时为 null(前端列表渲染 '-')。
     */
    private String requestId;

    /** 完成时间（已处理任务） */
    private LocalDateTime completedTime;
    
    /** 处理时长（毫秒） */
    private Long durationInMillis;
    
    /** 操作类型（approved, rejected, transferred, delegated, completed） */
    private String action;
    
    /** 可用的操作列表 */
    private List<TaskActionInfo> actions;

    /** Flowable 候选人用户 ID（引擎返回，用于权限与认领判断） */
    private List<String> candidateUserIds;

    /** Flowable 候选组 ID */
    private List<String> candidateGroupIds;

    /** 是否为多实例子任务（前端据此隐藏 Action 列和 Detail 链接） */
    @Builder.Default
    private boolean multiInstanceSubTask = false;
}
