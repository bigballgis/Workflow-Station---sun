package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 流程定义查询结果
 */
@Data
@Builder
public class ProcessDefinitionResult {
    
    /**
     * 流程定义ID
     */
    private String id;
    
    /**
     * 流程定义Key
     */
    private String key;
    
    /**
     * 流程定义名称
     */
    private String name;
    
    /**
     * 版本号
     */
    private Integer version;
    
    /**
     * 分类
     */
    private String category;
    
    /**
     * 部署ID
     */
    private String deploymentId;
    
    /**
     * 资源文件名
     */
    private String resourceName;
    
    /**
     * 流程图资源文件名
     */
    private String diagramResourceName;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 是否有启动表单
     */
    private Boolean hasStartFormKey;
    
    /**
     * 是否有图形化标记
     */
    private Boolean hasGraphicalNotation;
    
    /**
     * 是否已暂停
     */
    private Boolean suspended;
    
    /**
     * 租户ID
     */
    private String tenantId;
}