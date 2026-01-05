package com.portal.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInfo {

    /** 流程实例ID */
    private String processInstanceId;

    /** 流程定义ID */
    private String processDefinitionId;

    /** 流程定义Key */
    private String processDefinitionKey;

    /** 流程定义名称 */
    private String processDefinitionName;

    /** 流程定义版本 */
    private Integer processDefinitionVersion;

    /** 业务Key */
    private String businessKey;

    /** 流程状态 */
    private String status;

    /** 发起人ID */
    private String initiatorId;

    /** 发起人名称 */
    private String initiatorName;

    /** 当前节点 */
    private String currentActivity;

    /** 当前处理人 */
    private List<String> currentAssignees;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 流程变量 */
    private Map<String, Object> variables;

    /** 表单数据 */
    private Map<String, Object> formData;
}
