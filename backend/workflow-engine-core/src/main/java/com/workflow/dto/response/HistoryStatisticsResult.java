package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

/**
 * 历史数据统计结果DTO
 * 提供各种维度的历史数据统计分析
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryStatisticsResult {

    /**
     * 统计时间
     */
    private LocalDateTime statisticsTime;

    /**
     * 已完成的流程实例总数
     */
    private Long completedProcessCount;

    /**
     * 已完成的任务总数
     */
    private Long completedTaskCount;

    /**
     * 平均执行时长（毫秒）
     */
    private Long averageDurationMillis;

    /**
     * 最长执行时长（毫秒）
     */
    private Long maxDurationMillis;

    /**
     * 最短执行时长（毫秒）
     */
    private Long minDurationMillis;

    /**
     * 按流程定义分组的统计
     */
    private Map<String, Long> processDefinitionStats;

    /**
     * 按用户分组的统计
     */
    private Map<String, Long> userStats;

    /**
     * 按业务单元分组的统计
     */
    private Map<String, Long> businessUnitStats;

    /**
     * 按月份分组的统计
     */
    private Map<String, Long> monthlyStats;

    /**
     * 按状态分组的统计
     */
    private Map<String, Long> statusStats;

    /**
     * 性能指标
     */
    private PerformanceMetrics performanceMetrics;

    /**
     * 趋势分析数据
     */
    private List<TrendData> trendData;

    /**
     * 性能指标内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        /**
         * SLA达成率（百分比）
         */
        private Double slaComplianceRate;

        /**
         * 平均任务处理时长（毫秒）
         */
        private Long averageTaskDuration;

        /**
         * 流程完成率（百分比）
         */
        private Double processCompletionRate;

        /**
         * 任务超时率（百分比）
         */
        private Double taskTimeoutRate;

        /**
         * 流程异常率（百分比）
         */
        private Double processErrorRate;

        /**
         * 平均等待时间（毫秒）
         */
        private Long averageWaitTime;

        /**
         * 吞吐量（每小时处理的流程数）
         */
        private Double throughputPerHour;

        /**
         * 获取格式化的SLA达成率
         */
        public String getFormattedSlaComplianceRate() {
            return slaComplianceRate != null ? String.format("%.2f%%", slaComplianceRate) : "N/A";
        }

        /**
         * 获取格式化的完成率
         */
        public String getFormattedCompletionRate() {
            return processCompletionRate != null ? String.format("%.2f%%", processCompletionRate) : "N/A";
        }

        /**
         * 获取格式化的吞吐量
         */
        public String getFormattedThroughput() {
            return throughputPerHour != null ? String.format("%.2f /hour", throughputPerHour) : "N/A";
        }
    }

    /**
     * 趋势数据内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendData {
        /**
         * 时间点
         */
        private LocalDateTime timePoint;

        /**
         * 流程启动数量
         */
        private Long processStartCount;

        /**
         * 流程完成数量
         */
        private Long processCompleteCount;

        /**
         * 任务完成数量
         */
        private Long taskCompleteCount;

        /**
         * 平均执行时长
         */
        private Long averageDuration;

        /**
         * 获取时间点标签
         */
        public String getTimeLabel() {
            return timePoint != null ? timePoint.toString() : "";
        }
    }

    /**
     * 获取格式化的平均执行时长
     */
    public String getFormattedAverageDuration() {
        if (averageDurationMillis == null) {
            return "N/A";
        }

        long seconds = averageDurationMillis / 1000;
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

    /**
     * 获取格式化的最长执行时长
     */
    public String getFormattedMaxDuration() {
        return formatDuration(maxDurationMillis);
    }

    /**
     * 获取格式化的最短执行时长
     */
    public String getFormattedMinDuration() {
        return formatDuration(minDurationMillis);
    }

    /**
     * 格式化时长的通用方法
     */
    private String formatDuration(Long durationMillis) {
        if (durationMillis == null) {
            return "N/A";
        }

        long seconds = durationMillis / 1000;
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

    /**
     * 获取统计摘要
     */
    public String getStatisticsSummary() {
        return String.format("Completed %d process instances, %d tasks, average duration %s",
            completedProcessCount != null ? completedProcessCount : 0,
            completedTaskCount != null ? completedTaskCount : 0,
            getFormattedAverageDuration());
    }

    /**
     * 获取最活跃的流程定义
     */
    public String getMostActiveProcessDefinition() {
        if (processDefinitionStats == null || processDefinitionStats.isEmpty()) {
            return "No data";
        }

        return processDefinitionStats.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(entry -> String.format("%s (%d instances)", entry.getKey(), entry.getValue()))
            .orElse("No data");
    }

    /**
     * 获取最活跃的用户
     */
    public String getMostActiveUser() {
        if (userStats == null || userStats.isEmpty()) {
            return "No data";
        }

        return userStats.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(entry -> String.format("%s (%d tasks)", entry.getKey(), entry.getValue()))
            .orElse("No data");
    }

    /**
     * 检查是否有性能指标
     */
    public boolean hasPerformanceMetrics() {
        return performanceMetrics != null;
    }

    /**
     * 检查是否有趋势数据
     */
    public boolean hasTrendData() {
        return trendData != null && !trendData.isEmpty();
    }

    /**
     * 获取性能等级
     */
    public String getPerformanceGrade() {
        if (performanceMetrics == null) {
            return "No data";
        }

        Double completionRate = performanceMetrics.getProcessCompletionRate();
        Double slaRate = performanceMetrics.getSlaComplianceRate();

        if (completionRate != null && slaRate != null) {
            double avgRate = (completionRate + slaRate) / 2;
            if (avgRate >= 95) {
                return "Excellent";
            } else if (avgRate >= 85) {
                return "Good";
            } else if (avgRate >= 70) {
                return "Average";
            } else {
                return "Needs Improvement";
            }
        }

        return "Unable to evaluate";
    }
}