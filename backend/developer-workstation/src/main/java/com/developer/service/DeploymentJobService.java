package com.developer.service;

import com.developer.dto.DeployResponse;

import java.util.List;
import java.util.Optional;

/**
 * 部署任务持久化（独立事务，供异步线程写入）
 */
public interface DeploymentJobService {

    void persistNew(String deploymentId, Long functionUnitId, String targetAdminUrl, DeployResponse snapshot);

    void persistUpdate(Long functionUnitId, String targetAdminUrl, DeployResponse snapshot);

    Optional<DeployResponse> findResponseById(String deploymentId);

    List<DeployResponse> findResponsesByFunctionUnitId(Long functionUnitId);
}
