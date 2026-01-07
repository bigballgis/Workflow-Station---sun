package com.portal.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务流转历史DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskHistoryInfo {

    /** 历史记录ID */
    private String id;

    /** 任务ID */
    private String taskId;

    /** 任务名称 */
    private String taskName;

    /** 活动ID */
    private String activityId;

    /** 活动名称 */
    private String activityName;

    /** 活动类型 */
    private String activityType;

    /** 操作类型：SUBMIT, APPROVE, REJECT, DELEGATE, TRANSFER, CLAIM, RETURN */
    private String operationType;

    /** 操作人ID */
    private String operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 操作时间 */
    private LocalDateTime operationTime;

    /** 处理意见 */
    private String comment;

    /** 耗时（毫秒） */
    private Long duration;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
