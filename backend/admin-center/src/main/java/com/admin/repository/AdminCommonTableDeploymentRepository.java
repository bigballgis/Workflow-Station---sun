package com.admin.repository;

import com.admin.entity.AdminCommonTableDeployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminCommonTableDeploymentRepository extends JpaRepository<AdminCommonTableDeployment, Long> {

    List<AdminCommonTableDeployment> findAllByOrderByDeployedAtDesc();

    List<AdminCommonTableDeployment> findByCommonTableIdOrderByDeployedAtDesc(Long commonTableId);
}
