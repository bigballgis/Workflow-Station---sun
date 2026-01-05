package com.admin.repository;

import com.admin.entity.FunctionUnitDependency;
import com.admin.enums.DependencyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 功能单元依赖关系仓库接口
 */
@Repository
public interface FunctionUnitDependencyRepository extends JpaRepository<FunctionUnitDependency, String> {
    
    /**
     * 根据功能单元ID查找依赖
     */
    List<FunctionUnitDependency> findByFunctionUnitId(String functionUnitId);
    
    /**
     * 根据功能单元ID和依赖类型查找依赖
     */
    List<FunctionUnitDependency> findByFunctionUnitIdAndDependencyType(
            String functionUnitId, DependencyType dependencyType);
    
    /**
     * 查找必需依赖
     */
    @Query("SELECT d FROM FunctionUnitDependency d WHERE d.functionUnit.id = :functionUnitId AND d.dependencyType = 'REQUIRED'")
    List<FunctionUnitDependency> findRequiredDependencies(@Param("functionUnitId") String functionUnitId);
    
    /**
     * 根据依赖代码查找被依赖的功能单元
     */
    @Query("SELECT d FROM FunctionUnitDependency d WHERE d.dependencyCode = :dependencyCode")
    List<FunctionUnitDependency> findByDependencyCode(@Param("dependencyCode") String dependencyCode);
    
    /**
     * 检查依赖是否存在
     */
    boolean existsByFunctionUnitIdAndDependencyCodeAndDependencyVersion(
            String functionUnitId, String dependencyCode, String dependencyVersion);
    
    /**
     * 删除功能单元的所有依赖
     */
    void deleteByFunctionUnitId(String functionUnitId);
}
