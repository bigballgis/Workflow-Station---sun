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
 * FunctionUnit deployment record repository.
 */
@Repository
public interface FunctionUnitDeploymentRepository extends JpaRepository<FunctionUnitDeployment, String> {
    
    /**
     * Deployments for a function unit id.
     */
    List<FunctionUnitDeployment> findByFunctionUnitId(String functionUnitId);
    
    /**
     * Deployments for unit and environment.
     */
    List<FunctionUnitDeployment> findByFunctionUnitIdAndEnvironment(
            String functionUnitId, DeploymentEnvironment environment);
    
    /**
     * Deployments in an environment.
     */
    List<FunctionUnitDeployment> findByEnvironment(DeploymentEnvironment environment);
    
    /**
     * Deployments with the given status.
     */
    List<FunctionUnitDeployment> findByStatus(DeploymentStatus status);
    
    /**
     * Latest SUCCESS deployment for unit+environment (by completedAt).
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
     * Successful deployments in an environment (newest first by completedAt).
     */
    @Query("SELECT d FROM FunctionUnitDeployment d WHERE " +
           "d.environment = :environment AND " +
           "d.status = 'SUCCESS' " +
           "ORDER BY d.completedAt DESC")
    List<FunctionUnitDeployment> findLatestSuccessfulDeploymentsByEnvironment(
            @Param("environment") DeploymentEnvironment environment);
    
    /**
     * Paged filter with FETCH join on function unit (nullable params ignored).
     */
    @Query(value = "SELECT d FROM FunctionUnitDeployment d JOIN FETCH d.functionUnit fu WHERE " +
           "(:functionUnitId IS NULL OR d.functionUnit.id = :functionUnitId) AND " +
           "(:environment IS NULL OR d.environment = :environment) AND " +
           "(:status IS NULL OR d.status = :status) " +
           "ORDER BY d.createdAt DESC",
           countQuery = "SELECT COUNT(d) FROM FunctionUnitDeployment d WHERE " +
           "(:functionUnitId IS NULL OR d.functionUnit.id = :functionUnitId) AND " +
           "(:environment IS NULL OR d.environment = :environment) AND " +
           "(:status IS NULL OR d.status = :status)")
    Page<FunctionUnitDeployment> findByConditions(
            @Param("functionUnitId") String functionUnitId,
            @Param("environment") DeploymentEnvironment environment,
            @Param("status") DeploymentStatus status,
            Pageable pageable);
    
    /**
     * Pending production deployments (PENDING in PRODUCTION).
     */
    @Query("SELECT d FROM FunctionUnitDeployment d WHERE d.status = 'PENDING' AND d.environment = 'PRODUCTION'")
    List<FunctionUnitDeployment> findPendingProductionDeployments();
    
    /**
     * Deployments whose status is in the given list.
     */
    List<FunctionUnitDeployment> findByStatusIn(List<DeploymentStatus> statuses);
    
    /**
     * Active deployment for unit+environment (PENDING / PENDING_APPROVAL / APPROVED / DEPLOYING).
     */
    @Query("SELECT d FROM FunctionUnitDeployment d WHERE " +
           "d.functionUnit.id = :functionUnitId AND " +
           "d.environment = :environment AND " +
           "d.status IN ('PENDING', 'PENDING_APPROVAL', 'APPROVED', 'DEPLOYING')")
    Optional<FunctionUnitDeployment> findActiveDeployment(
            @Param("functionUnitId") String functionUnitId,
            @Param("environment") DeploymentEnvironment environment);
    
    /**
     * Deployments for a unit, newest {@code createdAt} first.
     */
    List<FunctionUnitDeployment> findByFunctionUnitIdOrderByCreatedAtDesc(String functionUnitId);
    
    /**
     * Paged deployments in an environment ({@code createdAt} descending).
     */
    Page<FunctionUnitDeployment> findByEnvironmentOrderByCreatedAtDesc(
            DeploymentEnvironment environment, Pageable pageable);

    @Query("SELECT d FROM FunctionUnitDeployment d JOIN FETCH d.functionUnit WHERE d.id IN :ids")
    List<FunctionUnitDeployment> findByIdInWithFunctionUnit(@Param("ids") List<String> ids);
    
    /**
     * Prior SUCCESS deployment before {@code beforeTime} (by startedAt/completedAt order).
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
