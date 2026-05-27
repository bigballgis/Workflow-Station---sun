package com.admin.repository;

import com.admin.entity.FunctionUnit;
import com.admin.enums.FunctionUnitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * FunctionUnit repository.
 */
@Repository
public interface FunctionUnitRepository extends JpaRepository<FunctionUnit, String> {
    
    /**
     * Find function unit by code and version.
     */
    Optional<FunctionUnit> findByCodeAndVersion(String code, String version);
    
    /**
     * Whether the code+version pair exists.
     */
    boolean existsByCodeAndVersion(String code, String version);
    
    /**
     * All versions for a code, newest first.
     */
    List<FunctionUnit> findByCodeOrderByVersionDesc(String code);
    
    /**
     * Alias: all versions for a code, newest first.
     */
    List<FunctionUnit> findAllByCodeOrderByVersionDesc(String code);
    
    /**
     * Find by status.
     */
    List<FunctionUnit> findByStatus(FunctionUnitStatus status);
    
    /**
     * Find by status (paged).
     */
    Page<FunctionUnit> findByStatus(FunctionUnitStatus status, Pageable pageable);
    
    /**
     * Latest version row for the given code.
     */
    @Query("SELECT f FROM FunctionUnit f WHERE f.code = :code ORDER BY f.version DESC LIMIT 1")
    Optional<FunctionUnit> findLatestByCode(@Param("code") String code);
    
    /**
     * Paged filter by code/name/status (nullable params = ignored).
     */
    @Query("SELECT f FROM FunctionUnit f WHERE " +
           "(:code IS NULL OR f.code LIKE %:code%) AND " +
           "(:name IS NULL OR f.name LIKE %:name%) AND " +
           "(:status IS NULL OR f.status = :status)")
    Page<FunctionUnit> findByConditions(
            @Param("code") String code,
            @Param("name") String name,
            @Param("status") FunctionUnitStatus status,
            Pageable pageable);
    
    /**
     * Units in VALIDATED or DEPLOYED (deployable).
     */
    @Query("SELECT f FROM FunctionUnit f WHERE f.status IN ('VALIDATED', 'DEPLOYED')")
    List<FunctionUnit> findDeployable();
    
    /**
     * Code contains (substring match).
     */
    List<FunctionUnit> findByCodeContaining(String code);
    
    /**
     * Name contains (substring match).
     */
    List<FunctionUnit> findByNameContaining(String name);
    
    /**
     * By status and enabled flag (paged).
     */
    Page<FunctionUnit> findByStatusAndEnabled(FunctionUnitStatus status, Boolean enabled, Pageable pageable);
    
    /**
     * By status and enabled flag (list).
     */
    List<FunctionUnit> findByStatusAndEnabled(FunctionUnitStatus status, Boolean enabled);
    
    /**
     * By code and enabled flag (list).
     */
    List<FunctionUnit> findByCodeAndEnabled(String code, Boolean enabled);

    /**
     * Paged: status not equal to the given value.
     */
    Page<FunctionUnit> findByStatusNot(FunctionUnitStatus status, Pageable pageable);

    /**
     * By code and status.
     */
    List<FunctionUnit> findByCodeAndStatus(String code, FunctionUnitStatus status);
    
    /**
     * Enabled unit by code.
     */
    Optional<FunctionUnit> findByCodeAndEnabledTrue(String code);
    
    /**
     * Count by code and enabled flag.
     */
    long countByCodeAndEnabled(String code, Boolean enabled);
}
