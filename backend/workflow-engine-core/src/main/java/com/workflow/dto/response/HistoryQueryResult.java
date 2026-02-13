package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 历史查询结果DTO
 * 包含流程实例、任务、活动、变量的历史信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryQueryResult {

    // 分页信息
    private Long totalCount;
    private Integer pageSize;
    private Integer currentPage;
    private String searchKeyword;

    // 历史数据
    private List<ProcessInstanceHistory> processInstances;
    private List<TaskInstanceHistory> taskInstances;
    private List<ActivityInstanceHistory> activityInstances;
    private List<VariableInstanceHistory> variableInstances;

    /**
     * 流程实例历史信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessInstanceHistory {
        private String processInstanceId;
        private String processDefinitionId;
        private String processDefinitionKey;
        private String processDefinitionName;
        private Integer processDefinitionVersion;
        private String businessKey;
        private String name;
        private String description;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long durationInMillis;
        private String startUserId;
        private String startActivityId;
        private String endActivityId;
        private String deleteReason;
        private String superProcessInstanceId;
        private String rootProcessInstanceId;
        private String tenantId;
        private Map<String, Object> processVariables;
        
        /**
         * 获取执行状态
         */
        public String getExecutionStatus() {
            if (endTime != null) {
                return deleteReason != null ? "TERMINATED" : "COMPLETED";
            }
            return "RUNNING";
        }

        /**
         * 获取格式化的执行时长
         */
        public String getFormattedDuration() {
            if (durationInMillis == null) {
                return "N/A";
            }
            
            long seconds = durationInMillis / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;
            
            if (days > 0) {
                return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
            } else if (hours > 0) {
                return String.format("%dh %dm", hours, minutes % 60);
            } else if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds % 60);
            } else {
                return String.format("%ds", seconds);
            }
        }
    }

    /**
     * 任务实例历史信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskInstanceHistory {
        private String taskId;
        private String taskName;
        private String taskDescription;
        private String taskDefinitionKey;
        private String processInstanceId;
        private String processDefinitionId;
        private String assignee;
        private String owner;
        private Integer priority;
        private LocalDateTime createTime;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private LocalDateTime claimTime;
        private LocalDateTime dueDate;
        private LocalDateTime followUpDate;
        private Long durationInMillis;
        private Long workTimeInMillis;
        private String formKey;
        private String category;
        private String parentTaskId;
        private String tenantId;
        private String deleteReason;
        private Map<String, Object> taskLocalVariables;
        
        /**
         * 获取任务状态
         */
        public String getTaskStatus() {
            if (endTime != null) {
                return deleteReason != null ? "CANCELLED" : "COMPLETED";
            } else if (claimTime != null) {
                return "CLAIMED";
            } else if (assignee != null) {
                return "ASSIGNED";
            } else {
                return "CREATED";
            }
        }

        /**
         * 获取优先级描述
         */
        public String getPriorityDescription() {
            if (priority == null) {
                return "Normal";
            }
            if (priority >= 80) {
                return "Urgent";
            } else if (priority >= 60) {
                return "High";
            } else if (priority >= 40) {
                return "Normal";
            } else {
                return "Low";
            }
        }
    }

    /**
     * 活动实例历史信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityInstanceHistory {
        private String activityId;
        private String activityName;
        private String activityType;
        private String processInstanceId;
        private String processDefinitionId;
        private String executionId;
        private String taskId;
        private String calledProcessInstanceId;
        private String assignee;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long durationInMillis;
        private String deleteReason;
        private String tenantId;
        
        /**
         * 获取活动状态
         */
        public String getActivityStatus() {
            if (endTime != null) {
                return deleteReason != null ? "CANCELLED" : "COMPLETED";
            }
            return "ACTIVE";
        }

        /**
         * 获取活动类型描述
         */
        public String getActivityTypeDescription() {
            switch (activityType != null ? activityType : "") {
                case "startEvent":
                    return "Start Event";
                case "endEvent":
                    return "End Event";
                case "userTask":
                    return "User Task";
                case "serviceTask":
                    return "Service Task";
                case "exclusiveGateway":
                    return "Exclusive Gateway";
                case "parallelGateway":
                    return "Parallel Gateway";
                case "subProcess":
                    return "Sub Process";
                case "callActivity":
                    return "Call Activity";
                default:
                    return activityType != null ? activityType : "Unknown";
            }
        }
    }

    /**
     * 变量实例历史信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableInstanceHistory {
        private String variableId;
        private String variableName;
        private String variableTypeName;
        private Object value;
        private String processInstanceId;
        private String taskId;
        private String activityInstanceId;
        private LocalDateTime createTime;
        private LocalDateTime lastUpdatedTime;
        private String tenantId;
        
        /**
         * 获取变量作用域
         */
        public String getVariableScope() {
            if (taskId != null) {
                return "Task Variable";
            } else if (activityInstanceId != null) {
                return "Activity Variable";
            } else if (processInstanceId != null) {
                return "Process Variable";
            } else {
                return "Global Variable";
            }
        }

        /**
         * 获取格式化的变量值
         */
        public String getFormattedValue() {
            if (value == null) {
                return "null";
            }
            
            if (value instanceof String) {
                String strValue = (String) value;
                return strValue.length() > 100 ? strValue.substring(0, 100) + "..." : strValue;
            }
            
            return value.toString();
        }

        /**
         * 获取变量类型描述
         */
        public String getVariableTypeDescription() {
            switch (variableTypeName != null ? variableTypeName : "") {
                case "string":
                    return "String";
                case "integer":
                    return "Integer";
                case "long":
                    return "Long";
                case "double":
                    return "Double";
                case "boolean":
                    return "Boolean";
                case "date":
                    return "Date";
                case "json":
                    return "JSON Object";
                case "serializable":
                    return "Serializable Object";
                default:
                    return variableTypeName != null ? variableTypeName : "Unknown";
            }
        }
    }

    /**
     * 获取总页数
     */
    public Integer getTotalPages() {
        if (totalCount == null || pageSize == null || pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalCount / pageSize);
    }

    /**
     * 是否有下一页
     */
    public Boolean hasNextPage() {
        return currentPage != null && currentPage < getTotalPages();
    }

    /**
     * 是否有上一页
     */
    public Boolean hasPreviousPage() {
        return currentPage != null && currentPage > 1;
    }

    /**
     * 获取查询结果摘要
     */
    public String getResultSummary() {
        StringBuilder summary = new StringBuilder();
        
        if (processInstances != null && !processInstances.isEmpty()) {
            summary.append("Process Instances: ").append(processInstances.size());
        }
        
        if (taskInstances != null && !taskInstances.isEmpty()) {
            if (summary.length() > 0) summary.append(", ");
            summary.append("Task Instances: ").append(taskInstances.size());
        }
        
        if (activityInstances != null && !activityInstances.isEmpty()) {
            if (summary.length() > 0) summary.append(", ");
            summary.append("Activity Instances: ").append(activityInstances.size());
        }
        
        if (variableInstances != null && !variableInstances.isEmpty()) {
            if (summary.length() > 0) summary.append(", ");
            summary.append("Variable Instances: ").append(variableInstances.size());
        }
        
        return summary.length() > 0 ? summary.toString() : "No data";
    }
}