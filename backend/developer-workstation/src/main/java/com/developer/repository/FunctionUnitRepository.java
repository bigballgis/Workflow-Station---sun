package com.developer.repository;

import com.developer.entity.FunctionUnit;
import com.developer.enums.FunctionUnitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 功能单元仓库
 */
@Repository
public interface FunctionUnitRepository extends JpaRepository<FunctionUnit, Long>, JpaSpecificationExecutor<FunctionUnit> {
    
    boolean existsByName(String name);
    
    boolean existsByNameAndIdNot(String name, Long id);
    
    boolean existsByCode(String code);
    
    Optional<FunctionUnit> findByName(String name);
    
    Optional<FunctionUnit> findByCode(String code);
    
    Page<FunctionUnit> findByStatus(FunctionUnitStatus status, Pageable pageable);
    
    /**
     * 使用 EntityGraph 预加载关联数据，避免懒加载异常
     */
    @EntityGraph(attributePaths = {"icon", "tableDefinitions", "formDefinitions", "actionDefinitions", "processDefinition"})
    @Query("SELECT fu FROM FunctionUnit fu")
    Page<FunctionUnit> findAllWithRelations(Pageable pageable);
    
    /**
     * Find active version by function unit name
     * Requirements: 2.5 - Query for active version
     */
    Optional<FunctionUnit> findByNameAndIsActive(String name, Boolean isActive);
    
    /**
     * Find function unit by name (returns active version for backward compatibility)
     * This method is provided for backward compatibility with legacy code that doesn't specify version.
     * 
     * Requirements: 9.4 - THE System SHALL support queries for Function_Units without specifying version
     *               9.5 - WHEN legacy code queries Function_Units, THE System SHALL return Active_Version data transparently
     */
    default Optional<FunctionUnit> findActiveByName(String name) {
        return findByNameAndIsActive(name, true);
    }
    
    /**
     * Find all versions of a function unit ordered by version descending
     * Requirements: 3.3 - Display all versions ordered by version number
     */
    @Query("SELECT fu FROM FunctionUnit fu WHERE fu.name = :name ORDER BY fu.version DESC")
    List<FunctionUnit> findByNameOrderByVersionDesc(@Param("name") String name);
    
    /**
     * Check if a specific version exists for a function unit
     * Requirements: 1.4 - Prevent deployment of duplicate versions
     */
    boolean existsByNameAndVersion(String name, String version);
}
