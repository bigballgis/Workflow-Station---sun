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
     * 导入功能单元。
     * 同名不存在 → 新建；同名已存在 → 加一个版本（快照现有内容后替换为导入包内容）。
     * @param file ZIP文件
     * @param changeLog 同名加版本时写入版本记录的变更说明，可空
     * @return 导入结果
     */
    Map<String, Object> importFunctionUnit(MultipartFile file, String changeLog);
    
    /**
     * 验证导入包
     */
    Map<String, Object> validateImportPackage(MultipartFile file);
    
    /**
     * 检查导入冲突
     */
    Map<String, Object> checkConflicts(MultipartFile file);
}
