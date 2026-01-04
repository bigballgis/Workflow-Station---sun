package com.workflow.dto.response;

import com.workflow.entity.ProcessVariable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 变量历史结果DTO
 * 
 * 返回流程变量的历史变更记录
 * 包含完整的变更轨迹和统计信息
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
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 变量名称
     */
    private String variableName;

    /**
     * 历史记录列表（按时间倒序）
     */
    private List<ProcessVariable> history;

    /**
     * 总记录数
     */
    private Integer totalCount;

    /**
     * 变更次数
     */
    private Integer changeCount;

    /**
     * 首次创建时间
     */
    private LocalDateTime firstCreatedTime;

    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdatedTime;

    /**
     * 当前值
     */
    private Object currentValue;

    /**
     * 初始值
     */
    private Object initialValue;

    /**
     * 创建成功的历史结果
     * 
     * @param processInstanceId 流程实例ID
     * @param variableName 变量名称
     * @param history 历史记录
     * @return 历史结果
     */
    public static VariableHistoryResult success(String processInstanceId, String variableName, 
                                              List<ProcessVariable> history) {
        VariableHistoryResultBuilder builder = VariableHistoryResult.builder()
                .processInstanceId(processInstanceId)
                .variableName(variableName)
                .history(history)
                .totalCount(history.size());

        if (!history.isEmpty()) {
            // 最新记录在前（倒序）
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
     * 创建空的历史结果
     * 
     * @param processInstanceId 流程实例ID
     * @param variableName 变量名称
     * @return 空历史结果
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
     * 判断是否有历史记录
     * 
     * @return true如果有历史记录
     */
    public boolean hasHistory() {
        return history != null && !history.isEmpty();
    }

    /**
     * 获取变量变更频率（每小时变更次数）
     * 
     * @return 变更频率
     */
    public double getChangeFrequency() {
        if (!hasHistory() || firstCreatedTime == null || lastUpdatedTime == null) {
            return 0.0;
        }
        
        long hours = java.time.Duration.between(firstCreatedTime, lastUpdatedTime).toHours();
        if (hours == 0) {
            return changeCount.doubleValue(); // 1小时内的变更
        }
        
        return changeCount.doubleValue() / hours;
    }

    /**
     * 判断变量值是否发生过变化
     * 
     * @return true如果值发生过变化
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
     * 获取最近的变更记录
     * 
     * @param count 记录数量
     * @return 最近的变更记录
     */
    public List<ProcessVariable> getRecentChanges(int count) {
        if (!hasHistory()) {
            return List.of();
        }
        
        int endIndex = Math.min(count, history.size());
        return history.subList(0, endIndex);
    }
}