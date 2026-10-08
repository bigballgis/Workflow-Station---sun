package com.admin.repository;

import com.admin.entity.EnvironmentVariable;
import com.admin.enums.EnvironmentValueKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentVariableRepository extends JpaRepository<EnvironmentVariable, String> {

    Optional<EnvironmentVariable> findByVarKeyAndDeployEnv(String varKey, String deployEnv);

    List<EnvironmentVariable> findByDeployEnvOrderByVarKeyAsc(String deployEnv);

    List<EnvironmentVariable> findByDeployEnvAndValueKindOrderByVarKeyAsc(
            String deployEnv, EnvironmentValueKind valueKind);

    boolean existsByVarKeyAndDeployEnv(String varKey, String deployEnv);
}
