package com.portal.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 任务统计DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatistics {

    /** 待办任务总数 */
    private long totalTasks;

    /** 直接分配任务数 */
    private long directTasks;

    /** 虚拟组任务数 */
    private long groupTasks;

    /** 部门角色任务数 */
    private long deptRoleTasks;

    /** 委托任务数 */
    private long delegatedTasks;

    /** 逾期任务数 */
    private long overdueTasks;

    /** 紧急任务数 */
    private long urgentTasks;

    /** 高优先级任务数 */
    private long highPriorityTasks;

    /** 今日新增任务数 */
    private long todayNewTasks;

    /** 今日完成任务数 */
    private long todayCompletedTasks;
}
