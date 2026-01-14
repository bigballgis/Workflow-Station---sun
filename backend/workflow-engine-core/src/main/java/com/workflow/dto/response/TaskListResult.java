package com.workflow.dto.response;

import com.workflow.enums.AssignmentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务列表查询结果
 */
@Data
@Builder
public class TaskListResult {
    
    /**
     * 任务列表
     */
    private List<TaskInfo> tasks;
    
    /**
     * 总数量
     */
    private Long totalCount;
    
    /**
     * 当前页码
     */
    private Integer currentPage;
    
    /**
     * 页面大小
     */
    private Integer pageSize;
    
    /**
     * 总页数
     */
    private Integer totalPages;
    
    /**
     * 是否成功
     */
    @Builder.Default
    private Boolean success = true;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 任务信息
     */
    @Data
    @Builder
    public static class TaskInfo {
        
        /**
         * 任务ID
         */
        private String taskId;
        
        /**
         * 任务名称
         */
        private String taskName;
        
        /**
         * 任务描述
         */
        private String taskDescription;
        
        /**
         * 流程实例ID
         */
        private String processInstanceId;
        
        /**
         * 流程定义ID
         */
        private String processDefinitionId;
        
        /**
         * 流程定义Key（从processDefinitionId中提取）
         */
        private String processDefinitionKey;
        
        /**
         * 流程定义名称
         */
        private String processDefinitionName;
        
        /**
         * 分配类型
         */
        private AssignmentType assignmentType;
        
        /**
         * 分配目标
         */
        private String assignmentTarget;
        
        /**
         * 当前处理人
         */
        private String currentAssignee;
        
        /**
         * 优先级
         */
        private Integer priority;
        
        /**
         * 到期时间
         */
        private LocalDateTime dueDate;
        
        /**
         * 任务状态
         */
        private String status;
        
        /**
         * 创建时间
         */
        private LocalDateTime createdTime;
        
        /**
         * 是否已委托
         */
        private Boolean isDelegated;
        
        /**
         * 是否已认领
         */
        private Boolean isClaimed;
        
        /**
         * 是否过期
         */
        private Boolean isOverdue;
        
        /**
         * 表单键
         */
        private String formKey;
        
        /**
         * 业务键
         */
        private String businessKey;
        
        /**
         * 流程发起人ID
         */
        private String initiatorId;
        
        /**
         * 流程发起人名称
         */
        private String initiatorName;
        
        /**
         * 分配类型描述
         */
        public String getAssignmentTypeDescription() {
            return assignmentType != null ? assignmentType.getDescription() : "未知";
        }
        
        /**
         * 获取任务状态标签
         */
        public String getStatusLabel() {
            if (isDelegated != null && isDelegated) {
                return "已委托";
            }
            if (isClaimed != null && isClaimed) {
                return "已认领";
            }
            if (isOverdue != null && isOverdue) {
                return "已过期";
            }
            return switch (status != null ? status : "UNKNOWN") {
                case "CREATED" -> "已创建";
                case "ASSIGNED" -> "已分配";
                case "IN_PROGRESS" -> "处理中";
                case "COMPLETED" -> "已完成";
                case "CANCELLED" -> "已取消";
                default -> "未知状态";
            };
        }
    }
}