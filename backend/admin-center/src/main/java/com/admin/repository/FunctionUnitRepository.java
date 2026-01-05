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
 * 功能单元仓库接口
 */
@Repository
public interface FunctionUnitRepository extends JpaRepository<FunctionUnit, String> {
    
    /**
     * 根据代码和版本查找功能单元
     */
    Optional<FunctionUnit> findByCodeAndVersion(String code, String version);
    
    /**
     * 检查代码和版本是否存在
     */
    boolean existsByCodeAndVersion(String code, String version);
    
    /**
     * 根据代码查找所有版本
     */
    List<FunctionUnit> findByCodeOrderByVersionDesc(String code);
    
    /**
     * 根据代码查找所有版本（别名方法）
     */
    List<FunctionUnit> findAllByCodeOrderByVersionDesc(String code);
    
    /**
     * 根据状态查找功能单元
     */
    List<FunctionUnit> findByStatus(FunctionUnitStatus status);
    
    /**
     * 根据状态分页查找功能单元
     */
    Page<FunctionUnit> findByStatus(FunctionUnitStatus status, Pageable pageable);
    
    /**
     * 根据代码查找最新版本
     */
    @Query("SELECT f FROM FunctionUnit f WHERE f.code = :code ORDER BY f.version DESC LIMIT 1")
    Optional<FunctionUnit> findLatestByCode(@Param("code") String code);
    
    /**
     * 分页查询功能单元
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
     * 查找可部署的功能单元
     */
    @Query("SELECT f FROM FunctionUnit f WHERE f.status IN ('VALIDATED', 'DEPLOYED')")
    List<FunctionUnit> findDeployable();
    
    /**
     * 根据代码模糊查询
     */
    List<FunctionUnit> findByCodeContaining(String code);
    
    /**
     * 根据名称模糊查询
     */
    List<FunctionUnit> findByNameContaining(String name);
}
