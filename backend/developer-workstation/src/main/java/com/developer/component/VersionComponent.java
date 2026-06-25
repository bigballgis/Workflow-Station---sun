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
     * 为「同名导入即加版本」准备现有功能单元：
     * 先把当前内容快照存入 dw_versions（保留历史、可回滚），再清空其子内容集合
     * （表/字段/表单/动作/决策/流程），并把 currentVersion 递增到下一个补丁号。
     * 返回递增后的版本号。调用方随后把导入包内容重新落库到同一个 FunctionUnit 上。
     *
     * @param functionUnit 已存在、待替换内容的功能单元（受管实体）
     * @param changeLog    快照的变更说明，可空
     * @return 递增后的新版本号
     */
    String snapshotAndClearForReimport(FunctionUnit functionUnit, String changeLog);
    
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
