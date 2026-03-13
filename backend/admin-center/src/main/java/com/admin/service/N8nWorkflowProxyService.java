package com.admin.service;

import com.admin.dto.response.N8nWorkflowDTO;

import java.util.List;

/**
 * N8N 工作流列表代理服务
 * 负责从 N8N API 获取工作流列表，并进行缓存和过滤
 */
public interface N8nWorkflowProxyService {

    /**
     * 获取指定 N8N 配置下的活跃工作流列表
     * 仅返回 active=true 的工作流，结果缓存 5 分钟
     *
     * @param configId N8N 连接配置 ID
     * @return 活跃工作流列表
     */
    List<N8nWorkflowDTO> listWorkflows(String configId);
}
