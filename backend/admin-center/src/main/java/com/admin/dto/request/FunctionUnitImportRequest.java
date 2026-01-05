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
     * 功能单元版本（可选，默认从包中解析）
     */
    private String version;
    
    /**
     * 功能单元描述（可选）
     */
    private String description;
}
