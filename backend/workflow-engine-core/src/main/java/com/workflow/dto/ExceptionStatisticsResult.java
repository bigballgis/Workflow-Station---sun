package com.workflow.dto;

import java.util.Map;

/**
 * 异常统计结果DTO
 */
public class ExceptionStatisticsResult {
    
    private Long totalCount;
    private Long unresolvedCount;
    private Long resolvedCount;
    private Long criticalCount;
    private Long highCount;
    private Long mediumCount;
    private Long lowCount;
    private Long pendingCount;
    private Long processingCount;
    private Long ignoredCount;
    private Map<String, Long> countByProcessDefinition;
    private Map<String, Long> countByExceptionType;
    private Double averageResolutionTimeMinutes;
    private Long todayCount;
    private Long thisWeekCount;
    private Long thisMonthCount;
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final ExceptionStatisticsResult result = new ExceptionStatisticsResult();
        
        public Builder totalCount(Long totalCount) { result.totalCount = totalCount; return this; }
        public Builder unresolvedCount(Long unresolvedCount) { result.unresolvedCount = unresolvedCount; return this; }
        public Builder resolvedCount(Long resolvedCount) { result.resolvedCount = resolvedCount; return this; }
        public Builder criticalCount(Long criticalCount) { result.criticalCount = criticalCount; return this; }
        public Builder highCount(Long highCount) { result.highCount = highCount; return this; }
        public Builder mediumCount(Long mediumCount) { result.mediumCount = mediumCount; return this; }
        public Builder lowCount(Long lowCount) { result.lowCount = lowCount; return this; }
        public Builder pendingCount(Long pendingCount) { result.pendingCount = pendingCount; return this; }
        public Builder processingCount(Long processingCount) { result.processingCount = processingCount; return this; }
        public Builder ignoredCount(Long ignoredCount) { result.ignoredCount = ignoredCount; return this; }
        public Builder countByProcessDefinition(Map<String, Long> map) { result.countByProcessDefinition = map; return this; }
        public Builder countByExceptionType(Map<String, Long> map) { result.countByExceptionType = map; return this; }
        public Builder averageResolutionTimeMinutes(Double avg) { result.averageResolutionTimeMinutes = avg; return this; }
        public Builder todayCount(Long todayCount) { result.todayCount = todayCount; return this; }
        public Builder thisWeekCount(Long thisWeekCount) { result.thisWeekCount = thisWeekCount; return this; }
        public Builder thisMonthCount(Long thisMonthCount) { result.thisMonthCount = thisMonthCount; return this; }
        public ExceptionStatisticsResult build() { return result; }
    }
    
    // Getters and Setters
    public Long getTotalCount() { return totalCount; }
    public void setTotalCount(Long totalCount) { this.totalCount = totalCount; }
    
    public Long getUnresolvedCount() { return unresolvedCount; }
    public void setUnresolvedCount(Long unresolvedCount) { this.unresolvedCount = unresolvedCount; }
    
    public Long getResolvedCount() { return resolvedCount; }
    public void setResolvedCount(Long resolvedCount) { this.resolvedCount = resolvedCount; }
    
    public Long getCriticalCount() { return criticalCount; }
    public void setCriticalCount(Long criticalCount) { this.criticalCount = criticalCount; }
    
    public Long getHighCount() { return highCount; }
    public void setHighCount(Long highCount) { this.highCount = highCount; }
    
    public Long getMediumCount() { return mediumCount; }
    public void setMediumCount(Long mediumCount) { this.mediumCount = mediumCount; }
    
    public Long getLowCount() { return lowCount; }
    public void setLowCount(Long lowCount) { this.lowCount = lowCount; }
    
    public Long getPendingCount() { return pendingCount; }
    public void setPendingCount(Long pendingCount) { this.pendingCount = pendingCount; }
    
    public Long getProcessingCount() { return processingCount; }
    public void setProcessingCount(Long processingCount) { this.processingCount = processingCount; }
    
    public Long getIgnoredCount() { return ignoredCount; }
    public void setIgnoredCount(Long ignoredCount) { this.ignoredCount = ignoredCount; }
    
    public Map<String, Long> getCountByProcessDefinition() { return countByProcessDefinition; }
    public void setCountByProcessDefinition(Map<String, Long> countByProcessDefinition) { this.countByProcessDefinition = countByProcessDefinition; }
    
    public Map<String, Long> getCountByExceptionType() { return countByExceptionType; }
    public void setCountByExceptionType(Map<String, Long> countByExceptionType) { this.countByExceptionType = countByExceptionType; }
    
    public Double getAverageResolutionTimeMinutes() { return averageResolutionTimeMinutes; }
    public void setAverageResolutionTimeMinutes(Double averageResolutionTimeMinutes) { this.averageResolutionTimeMinutes = averageResolutionTimeMinutes; }
    
    public Long getTodayCount() { return todayCount; }
    public void setTodayCount(Long todayCount) { this.todayCount = todayCount; }
    
    public Long getThisWeekCount() { return thisWeekCount; }
    public void setThisWeekCount(Long thisWeekCount) { this.thisWeekCount = thisWeekCount; }
    
    public Long getThisMonthCount() { return thisMonthCount; }
    public void setThisMonthCount(Long thisMonthCount) { this.thisMonthCount = thisMonthCount; }
}
