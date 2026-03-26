package com.admin.service;

import com.admin.dto.request.RollbackRequest;
import com.admin.dto.response.RelationTableResponse;
import com.admin.dto.response.RelationTableVersionResponse;

import java.util.List;

/**
 * Relation Table 部署与回滚服务接口
 */
public interface RelationTableDeployService {

    /**
     * 部署表结构
     * 读取当前表定义 → 生成 DDL → 执行 DDL → 创建版本快照 → 更新状态为 DEPLOYED
     */
    RelationTableResponse deploy(Long tableId);

    /**
     * 回滚到指定历史版本
     * 读取目标版本快照 → 覆盖当前表定义和字段定义 → 生成新版本号 → 更新状态为 ROLLBACK
     */
    RelationTableResponse rollback(Long tableId, RollbackRequest request);

    /**
     * 获取版本历史列表（按版本号降序）
     */
    List<RelationTableVersionResponse> getVersionHistory(Long tableId);
}
