package com.developer.component;

import com.developer.entity.FunctionUnit;
import com.developer.entity.Version;

import java.util.List;
import java.util.Map;

/**
 * 版本管理组件接口
 */
public interface VersionComponent {
    
    /**
     * 创建版本
     */
    Version createVersion(Long functionUnitId, String changeLog);
    
    /**
     * 获取版本历史
     */
    List<Version> getVersionHistory(Long functionUnitId);
    
    /**
     * 比较两个版本
     */
    Map<String, Object> compare(Long versionId1, Long versionId2);
    
    /**
     * 回滚到指定版本
     */
    FunctionUnit rollback(Long functionUnitId, Long versionId);
    
    /**
     * 导出版本
     */
    byte[] exportVersion(Long versionId);
    
    /**
     * 根据ID获取版本
     */
    Version getById(Long id);
}
