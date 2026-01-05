package com.admin.repository;

import com.admin.entity.FunctionUnitDeployment;
import com.admin.enums.DeploymentEnvironment;
import com.admin.enums.DeploymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 功能单元部署记录仓库接口
 */
@Repository
public interface FunctionUnitDeploymentRepository extends JpaRepository<FunctionUnitDeployment, String> {
    
    /**
     * 根据功能单元ID查找部署记录
     */
    List<FunctionUnitDeployment> findByFunctionUnitId(String functionUnitId);
    
    /**
     * 根据功能单元ID和环境查找部署记录
     */
    List<FunctionUnitDeployment> findByFunctionUnitIdAndEnvironment(
            String functionUnitId, DeploymentEnvironment environment);
    
    /**
     * 根据环境查找部署记录
     */
    List<FunctionUnitDeployment> findByEnvironment(DeploymentEnvironment environment);
    
    /**
     * 根据状态查找部署记录
     */
    List<FunctionUnitDeployment> findByStatus(DeploymentStatus status);
    
    /**
     * 查找最新的成功部署记录
     */
    @Query("SELECT d FROM FunctionUnitDeployment d WHERE " +
           "d.functionUnit.id = :functionUnitId AND " +
           "d.environment = :environment AND " +
           "d.status = 'SUCCESS' " +
           "ORDER BY d.completedAt DESC LIMIT 1")
    Optional<FunctionUnitDeployment> findLatestSuccessfulDeployment(
            @Param("functionUnitId") String functionUnitId,
            @Param("environment") DeploymentEnvironment environment);
    
    /**
     * 查找环境中最新的成功部署
     */
    @Query("SELECT d FROM FunctionUnitDeployment d WHERE " +
           "d.environment = :environment AND " +
           "d.status = 'SUCCESS' " +
           "ORDER BY d.completedAt DESC")
    List<FunctionUnitDeployment> findLatestSuccessfulDeploymentsByEnvironment(
            @Param("environment") DeploymentEnvironment environment);
    
    /**
     * 分页查询部署记录
     */
    @Query("SELECT d FROM FunctionUnitDeployment d WHERE " +
           "(:functionUnitId IS NULL OR d.functionUnit.id = :functionUnitId) AND " +
           "(:environment IS NULL OR d.environment = :environment) AND " +
           "(:status IS NULL OR d.status = :status) " +
           "ORDER BY d.createdAt DESC")
    Page<FunctionUnitDeployment> findByConditions(
            @Param("functionUnitId") String functionUnitId,
            @Param("environment") DeploymentEnvironment environment,
            @Param("status") DeploymentStatus status,
            Pageable pageable);
    
    /**
     * 查找待审批的部署
     */
    @Query("SELECT d FROM FunctionUnitDeployment d WHERE d.status = 'PENDING' AND d.environment = 'PRODUCTION'")
    List<FunctionUnitDeployment> findPendingProductionDeployments();
    
    /**
     * 查找进行中的部署
     */
    List<FunctionUnitDeployment> findByStatusIn(List<DeploymentStatus> statuses);
    
    /**
     * 查找活跃的部署（进行中或待审批）
     */
    @Query("SELECT d FROM FunctionUnitDeployment d WHERE " +
           "d.functionUnit.id = :functionUnitId AND " +
           "d.environment = :environment AND " +
           "d.status IN ('PENDING', 'PENDING_APPROVAL', 'APPROVED', 'DEPLOYING')")
    Optional<FunctionUnitDeployment> findActiveDeployment(
            @Param("functionUnitId") String functionUnitId,
            @Param("environment") DeploymentEnvironment environment);
    
    /**
     * 根据功能单元ID查找部署记录（按创建时间倒序）
     */
    List<FunctionUnitDeployment> findByFunctionUnitIdOrderByCreatedAtDesc(String functionUnitId);
    
    /**
     * 根据环境查找部署记录（分页，按创建时间倒序）
     */
    Page<FunctionUnitDeployment> findByEnvironmentOrderByCreatedAtDesc(
            DeploymentEnvironment environment, Pageable pageable);
    
    /**
     * 查找上一个成功的部署
     */
    @Query("SELECT d FROM FunctionUnitDeployment d WHERE " +
           "d.functionUnit.id = :functionUnitId AND " +
           "d.environment = :environment AND " +
           "d.status = 'SUCCESS' AND " +
           "d.startedAt < :beforeTime " +
           "ORDER BY d.completedAt DESC LIMIT 1")
    Optional<FunctionUnitDeployment> findPreviousSuccessfulDeployment(
            @Param("functionUnitId") String functionUnitId,
            @Param("environment") DeploymentEnvironment environment,
            @Param("beforeTime") java.time.Instant beforeTime);
}
