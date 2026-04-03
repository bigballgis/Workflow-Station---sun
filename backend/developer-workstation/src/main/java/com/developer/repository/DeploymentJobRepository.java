package com.developer.repository;

import com.developer.entity.DeploymentJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentJobRepository extends JpaRepository<DeploymentJob, String> {

    List<DeploymentJob> findByFunctionUnitIdOrderByStartedAtDesc(Long functionUnitId);
}
