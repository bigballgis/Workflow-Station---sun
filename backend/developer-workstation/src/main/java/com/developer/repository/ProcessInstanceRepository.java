package com.developer.repository;

import com.developer.entity.ProcessInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ProcessInstance entity.
 * Provides data access methods for process instance operations.
 * 
 * Requirements: 5.5
 */
@Repository
public interface ProcessInstanceRepository extends JpaRepository<ProcessInstance, String> {

    /**
     * Find all process instances bound to a specific function unit version.
     * 
     * Requirements: 5.5 - WHEN querying Process_Instance data, THE System SHALL include 
     *                     the bound Function_Unit version information
     * 
     * @param functionUnitVersionId the ID of the function unit version
     * @return list of process instances bound to the specified version
     */
    @Query("SELECT pi FROM ProcessInstance pi WHERE pi.functionUnitVersion.id = :versionId")
    List<ProcessInstance> findByFunctionUnitVersionId(@Param("versionId") Long functionUnitVersionId);

    /**
     * Count process instances bound to a specific function unit version.
     * Useful for determining impact of rollback operations.
     * 
     * @param functionUnitVersionId the ID of the function unit version
     * @return count of process instances bound to the specified version
     */
    @Query("SELECT COUNT(pi) FROM ProcessInstance pi WHERE pi.functionUnitVersion.id = :versionId")
    long countByFunctionUnitVersionId(@Param("versionId") Long functionUnitVersionId);

    /**
     * Delete all process instances bound to a specific function unit version.
     * Used during rollback operations to clean up processes bound to deleted versions.
     * 
     * Requirements: 6.4 - WHEN a rollback deletes versions, THE System SHALL also delete 
     *                     all Process_Instance records associated with those versions
     * 
     * @param functionUnitVersionId the ID of the function unit version
     */
    void deleteByFunctionUnitVersionId(Long functionUnitVersionId);
}
