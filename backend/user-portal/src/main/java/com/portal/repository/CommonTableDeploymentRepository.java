package com.portal.repository;

import com.portal.entity.CommonTableDeployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommonTableDeploymentRepository extends JpaRepository<CommonTableDeployment, Long> {

    /**
     * 获取指定表最新的 COMPLETED 部署记录（用于读取字段快照）
     */
    Optional<CommonTableDeployment> findTopByCommonTableIdAndStatusOrderByDeployedAtDesc(
            Long commonTableId, String status);
}
