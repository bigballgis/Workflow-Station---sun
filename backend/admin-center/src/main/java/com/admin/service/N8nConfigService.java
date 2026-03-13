package com.admin.service;

import com.admin.dto.request.N8nConfigCreateRequest;
import com.admin.dto.request.N8nConfigUpdateRequest;
import com.admin.dto.response.N8nConnectionTestResult;
import com.admin.entity.N8nConfig;

import java.util.List;

/**
 * N8N 连接配置管理服务
 */
public interface N8nConfigService {

    /**
     * 创建 N8N 连接配置
     */
    N8nConfig create(N8nConfigCreateRequest dto);

    /**
     * 更新 N8N 连接配置
     */
    N8nConfig update(String id, N8nConfigUpdateRequest dto);

    /**
     * 删除 N8N 连接配置
     */
    void delete(String id);

    /**
     * 获取 N8N 连接配置（apiKey 脱敏）
     */
    N8nConfig getById(String id);

    /**
     * 获取所有 N8N 连接配置列表（apiKey 脱敏）
     */
    List<N8nConfig> list();

    /**
     * 获取 N8N 连接配置（apiKey 解密，供内部 API 使用）
     */
    N8nConfig getByIdInternal(String id);

    /**
     * 测试 N8N 连接可用性
     */
    N8nConnectionTestResult testConnection(String id);
}
