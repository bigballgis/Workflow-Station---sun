package com.developer.repository;

import com.developer.entity.CommonTableDeployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommonTableDeploymentRepository extends JpaRepository<CommonTableDeployment, Long> {

    List<CommonTableDeployment> findByCommonTable_IdOrderByDeployedAtDesc(Long commonTableId);

    List<CommonTableDeployment> findAllByOrderByDeployedAtDesc();
}
