package com.developer.component;

import com.developer.dto.DeployRequest;
import com.developer.dto.DeployResponse;

/**
 * 部署组件接口
 */
public interface DeploymentComponent {
    
    /**
     * 一键部署功能单元到管理员中心
     * @param functionUnitId 功能单元ID
     * @param request 部署请求
     * @return 部署响应
     */
    DeployResponse deployToAdminCenter(Long functionUnitId, DeployRequest request);
    
    /**
     * 获取部署状态
     * @param deploymentId 部署ID
     * @return 部署响应
     */
    DeployResponse getDeploymentStatus(String deploymentId);
    
    /**
     * 获取功能单元的部署历史
     * @param functionUnitId 功能单元ID
     * @return 部署历史列表
     */
    java.util.List<DeployResponse> getDeploymentHistory(Long functionUnitId);
}
