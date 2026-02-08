package com.admin.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能单元导入请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitImportRequest {
    
    /**
     * 功能包文件路径
     */
    private String filePath;
    
    /**
     * 功能包文件内容（Base64编码）
     */
    private String fileContent;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 是否覆盖已存在的版本
     */
    @Builder.Default
    private boolean overwrite = false;
    
    /**
     * 导入备注
     */
    private String remark;
    
    /**
     * 功能单元名称（可选，默认从包中解析）
     */
    private String name;
    
    /**
     * 功能单元代码（唯一标识符）
     */
    private String code;
    
    /**
     * 功能单元版本（可选，默认从包中解析）
     */
    private String version;
    
    /**
     * 功能单元描述（可选）
     */
    private String description;
    
    /**
     * 导入后是否启用（默认为 true）
     * 如果为 true，将自动禁用同一 code 的其他版本
     * 如果为 false，新版本将以禁用状态导入
     */
    @Builder.Default
    private Boolean enableOnImport = true;
}
