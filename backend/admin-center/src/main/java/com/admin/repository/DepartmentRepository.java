package com.admin.repository;

import com.admin.entity.Department;
import com.admin.enums.DepartmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 部门仓库接口
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, String> {
    
    /**
     * 根据编码查找部门
     */
    Optional<Department> findByCode(String code);
    
    /**
     * 检查编码是否存在
     */
    boolean existsByCode(String code);
    
    /**
     * 检查同级部门名称是否存在
     */
    @Query("SELECT COUNT(d) > 0 FROM Department d WHERE d.name = :name AND " +
           "((:parentId IS NULL AND d.parentId IS NULL) OR d.parentId = :parentId) AND d.id != :excludeId")
    boolean existsByNameAndParentIdExcluding(
            @Param("name") String name, 
            @Param("parentId") String parentId,
            @Param("excludeId") String excludeId);
    
    /**
     * 根据父部门ID查找子部门
     */
    List<Department> findByParentIdOrderBySortOrder(String parentId);
    
    /**
     * 查找根部门
     */
    @Query("SELECT d FROM Department d WHERE d.parentId IS NULL ORDER BY d.sortOrder")
    List<Department> findRootDepartments();
    
    /**
     * 根据状态查找部门
     */
    List<Department> findByStatus(DepartmentStatus status);
    
    /**
     * 检查是否有子部门
     */
    boolean existsByParentId(String parentId);
    
    /**
     * 根据路径前缀查找所有后代部门
     */
    @Query("SELECT d FROM Department d WHERE d.path LIKE :pathPrefix%")
    List<Department> findByPathStartingWith(@Param("pathPrefix") String pathPrefix);
    
    /**
     * 查找所有活跃部门
     */
    @Query("SELECT d FROM Department d WHERE d.status = 'ACTIVE' ORDER BY d.level, d.sortOrder")
    List<Department> findAllActive();
    
    /**
     * 搜索部门
     */
    @Query("SELECT d FROM Department d WHERE " +
           "LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(d.code) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Department> searchDepartments(@Param("keyword") String keyword);
}
