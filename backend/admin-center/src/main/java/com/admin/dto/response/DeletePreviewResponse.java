package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能单元删除预览响应
 * 显示将被删除的关联数据统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeletePreviewResponse {
    
    /**
     * 功能单元ID
     */
    private String functionUnitId;
    
    /**
     * 功能单元名称
     */
    private String functionUnitName;
    
    /**
     * 功能单元编码
     */
    private String functionUnitCode;
    
    /**
     * 表单数量
     */
    private int formCount;
    
    /**
     * 流程数量
     */
    private int processCount;
    
    /**
     * 数据表数量
     */
    private int dataTableCount;
    
    /**
     * 权限配置数量
     */
    private int accessConfigCount;
    
    /**
     * 部署记录数量
     */
    private int deploymentCount;
    
    /**
     * 依赖数量
     */
    private int dependencyCount;
    
    /**
     * 是否有运行中的流程实例
     */
    private boolean hasRunningInstances;
    
    /**
     * 运行中的流程实例数量
     */
    private int runningInstanceCount;
    
    /**
     * 是否可以删除
     */
    public boolean canDelete() {
        return !hasRunningInstances;
    }
    
    /**
     * 获取总关联数据数量
     */
    public int getTotalRelatedCount() {
        return formCount + processCount + dataTableCount + accessConfigCount + deploymentCount + dependencyCount;
    }
}
