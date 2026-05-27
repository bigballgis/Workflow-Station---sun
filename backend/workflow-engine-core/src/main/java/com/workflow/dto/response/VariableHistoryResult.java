package com.workflow.dto.response;

import com.workflow.entity.ProcessVariable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Variable history result DTO
 * 
 * Returns the history change records of process variables
 * Includes complete change trail and statistics
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableHistoryResult {

    /**
     * Process instance ID
     */
    private String processInstanceId;

    /**
     * Variable name
     */
    private String variableName;

    /**
     * History record list (in reverse chronological order)
     */
    private List<ProcessVariable> history;

    /**
     * Total record count
     */
    private Integer totalCount;

    /**
     * Change count
     */
    private Integer changeCount;

    /**
     * First creation time
     */
    private LocalDateTime firstCreatedTime;

    /**
     * Last update time
     */
    private LocalDateTime lastUpdatedTime;

    /**
     * Current value
     */
    private Object currentValue;

    /**
     * Initial value
     */
    private Object initialValue;

    /**
     * Create a successful history result
     * 
     * @param processInstanceId Process instance ID
     * @param variableName Variable name
     * @param history History records
     * @return History result
     */
    public static VariableHistoryResult success(String processInstanceId, String variableName, 
                                              List<ProcessVariable> history) {
        VariableHistoryResultBuilder builder = VariableHistoryResult.builder()
                .processInstanceId(processInstanceId)
                .variableName(variableName)
                .history(history)
                .totalCount(history.size());

        if (!history.isEmpty()) {
            // Latest records first (reverse order)
            ProcessVariable latest = history.get(0);
            ProcessVariable earliest = history.get(history.size() - 1);
            
            builder.changeCount(history.size())
                   .firstCreatedTime(earliest.getCreatedTime())
                   .lastUpdatedTime(latest.getUpdatedTime())
                   .currentValue(latest.getValue())
                   .initialValue(earliest.getValue());
        } else {
            builder.changeCount(0);
        }

        return builder.build();
    }

    /**
     * Create an empty history result
     * 
     * @param processInstanceId Process instance ID
     * @param variableName Variable name
     * @return Empty history result
     */
    public static VariableHistoryResult empty(String processInstanceId, String variableName) {
        return VariableHistoryResult.builder()
                .processInstanceId(processInstanceId)
                .variableName(variableName)
                .history(List.of())
                .totalCount(0)
                .changeCount(0)
                .build();
    }

    /**
     * Check if history records exist
     * 
     * @return true if history records exist
     */
    public boolean hasHistory() {
        return history != null && !history.isEmpty();
    }

    /**
     * Get variable change frequency (changes per hour)
     * 
     * @return Change frequency
     */
    public double getChangeFrequency() {
        if (!hasHistory() || firstCreatedTime == null || lastUpdatedTime == null) {
            return 0.0;
        }
        
        long hours = java.time.Duration.between(firstCreatedTime, lastUpdatedTime).toHours();
        if (hours == 0) {
            return changeCount.doubleValue(); // changes within 1 hour
        }
        
        return changeCount.doubleValue() / hours;
    }

    /**
     * Check if variable value has changed
     * 
     * @return true if value has changed
     */
    public boolean hasValueChanged() {
        if (!hasHistory() || history.size() < 2) {
            return false;
        }
        
        Object initial = initialValue;
        Object current = currentValue;
        
        if (initial == null && current == null) {
            return false;
        }
        
        if (initial == null || current == null) {
            return true;
        }
        
        return !initial.equals(current);
    }

    /**
     * Get recent change records
     * 
     * @param count Number of records
     * @return Recent change records
     */
    public List<ProcessVariable> getRecentChanges(int count) {
        if (!hasHistory()) {
            return List.of();
        }
        
        int endIndex = Math.min(count, history.size());
        return history.subList(0, endIndex);
    }
}
