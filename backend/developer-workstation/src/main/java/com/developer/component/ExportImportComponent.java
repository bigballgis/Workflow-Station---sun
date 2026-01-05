package com.developer.component;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 导入导出组件接口
 */
public interface ExportImportComponent {
    
    /**
     * 导出功能单元为ZIP包
     */
    byte[] exportFunctionUnit(Long functionUnitId);
    
    /**
     * 导入功能单元
     * @param file ZIP文件
     * @param conflictStrategy 冲突策略: SKIP, OVERWRITE, RENAME
     * @return 导入结果
     */
    Map<String, Object> importFunctionUnit(MultipartFile file, String conflictStrategy);
    
    /**
     * 验证导入包
     */
    Map<String, Object> validateImportPackage(MultipartFile file);
    
    /**
     * 检查导入冲突
     */
    Map<String, Object> checkConflicts(MultipartFile file);
}
