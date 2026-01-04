package com.workflow.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计日志统计结果DTO
 */
public class AuditLogStatisticsResult {
    
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long totalOperations;
    private long successfulOperations;
    private long failedOperations;
    private double successRate;
    
    // 按操作类型统计
    private Map<String, Long> operationTypeStats;
    
    // 按用户统计
    private Map<String, Long> userStats;
    
    // 按风险等级统计
    private Map<String, Long> riskLevelStats;
    
    // 按资源类型统计
    private Map<String, Long> resourceTypeStats;
    
    // 异常活跃的IP地址
    private List<IpActivityInfo> activeIpAddresses;
    
    // 时间趋势数据
    private List<TimeSeriesData> timeSeries;
    
    // 构造函数
    public AuditLogStatisticsResult() {}
    
    public AuditLogStatisticsResult(LocalDateTime startTime, LocalDateTime endTime, long totalOperations) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalOperations = totalOperations;
    }
    
    // 内部类：IP活动信息
    public static class IpActivityInfo {
        private String ipAddress;
        private long operationCount;
        private String riskAssessment;
        
        public IpActivityInfo() {}
        
        public IpActivityInfo(String ipAddress, long operationCount) {
            this.ipAddress = ipAddress;
            this.operationCount = operationCount;
            this.riskAssessment = assessRisk(operationCount);
        }
        
        private String assessRisk(long count) {
            if (count > 1000) return "HIGH";
            if (count > 500) return "MEDIUM";
            return "LOW";
        }
        
        // Getters and Setters
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        
        public long getOperationCount() { return operationCount; }
        public void setOperationCount(long operationCount) { this.operationCount = operationCount; }
        
        public String getRiskAssessment() { return riskAssessment; }
        public void setRiskAssessment(String riskAssessment) { this.riskAssessment = riskAssessment; }
    }
    
    // 内部类：时间序列数据
    public static class TimeSeriesData {
        private LocalDateTime timestamp;
        private long operationCount;
        private long failureCount;
        
        public TimeSeriesData() {}
        
        public TimeSeriesData(LocalDateTime timestamp, long operationCount, long failureCount) {
            this.timestamp = timestamp;
            this.operationCount = operationCount;
            this.failureCount = failureCount;
        }
        
        // Getters and Setters
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public long getOperationCount() { return operationCount; }
        public void setOperationCount(long operationCount) { this.operationCount = operationCount; }
        
        public long getFailureCount() { return failureCount; }
        public void setFailureCount(long failureCount) { this.failureCount = failureCount; }
    }
    
    // Getters and Setters
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    
    public long getTotalOperations() { return totalOperations; }
    public void setTotalOperations(long totalOperations) { this.totalOperations = totalOperations; }
    
    public long getSuccessfulOperations() { return successfulOperations; }
    public void setSuccessfulOperations(long successfulOperations) { 
        this.successfulOperations = successfulOperations;
        calculateSuccessRate();
    }
    
    public long getFailedOperations() { return failedOperations; }
    public void setFailedOperations(long failedOperations) { 
        this.failedOperations = failedOperations;
        calculateSuccessRate();
    }
    
    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }
    
    public Map<String, Long> getOperationTypeStats() { return operationTypeStats; }
    public void setOperationTypeStats(Map<String, Long> operationTypeStats) { this.operationTypeStats = operationTypeStats; }
    
    public Map<String, Long> getUserStats() { return userStats; }
    public void setUserStats(Map<String, Long> userStats) { this.userStats = userStats; }
    
    public Map<String, Long> getRiskLevelStats() { return riskLevelStats; }
    public void setRiskLevelStats(Map<String, Long> riskLevelStats) { this.riskLevelStats = riskLevelStats; }
    
    public Map<String, Long> getResourceTypeStats() { return resourceTypeStats; }
    public void setResourceTypeStats(Map<String, Long> resourceTypeStats) { this.resourceTypeStats = resourceTypeStats; }
    
    public List<IpActivityInfo> getActiveIpAddresses() { return activeIpAddresses; }
    public void setActiveIpAddresses(List<IpActivityInfo> activeIpAddresses) { this.activeIpAddresses = activeIpAddresses; }
    
    public List<TimeSeriesData> getTimeSeries() { return timeSeries; }
    public void setTimeSeries(List<TimeSeriesData> timeSeries) { this.timeSeries = timeSeries; }
    
    // 辅助方法
    private void calculateSuccessRate() {
        if (totalOperations > 0) {
            this.successRate = (double) successfulOperations / totalOperations * 100;
        } else {
            this.successRate = 0.0;
        }
    }
}