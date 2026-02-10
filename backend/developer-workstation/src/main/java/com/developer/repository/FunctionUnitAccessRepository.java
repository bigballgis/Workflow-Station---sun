package com.developer.repository;

import com.developer.entity.FunctionUnitAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for function unit access permissions in Developer Workstation.
 * Supports version-specific permission management.
 */
@Repository
public interface FunctionUnitAccessRepository extends JpaRepository<FunctionUnitAccess, Long> {
    
    /**
     * Find all access permissions for a specific function unit version
     * 
     * @param functionUnitId the function unit version ID
     * @return list of access permissions
     */
    List<FunctionUnitAccess> findByFunctionUnitId(Long functionUnitId);
    
    /**
     * Delete all access permissions for a specific function unit version
     * 
     * @param functionUnitId the function unit version ID
     */
    @Modifying
    @Query("DELETE FROM FunctionUnitAccess a WHERE a.functionUnit.id = :functionUnitId")
    void deleteByFunctionUnitId(@Param("functionUnitId") Long functionUnitId);
    
    /**
     * Check if a specific access configuration exists for a function unit version
     * 
     * @param functionUnitId the function unit version ID
     * @param targetType the target type (ROLE, USER, VIRTUAL_GROUP)
     * @param targetId the target ID
     * @return true if the configuration exists
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM FunctionUnitAccess a " +
           "WHERE a.functionUnit.id = :functionUnitId AND a.targetType = :targetType AND a.targetId = :targetId")
    boolean existsByFunctionUnitIdAndTarget(@Param("functionUnitId") Long functionUnitId, 
                                            @Param("targetType") String targetType,
                                            @Param("targetId") String targetId);
    
    /**
     * Count permissions for a specific function unit version
     * 
     * @param functionUnitId the function unit version ID
     * @return the number of permissions
     */
    long countByFunctionUnitId(Long functionUnitId);
}
