package com.admin.repository;

import com.platform.security.entity.BusinessUnit;
import com.admin.enums.BusinessUnitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 业务单元仓库接口
 */
@Repository
public interface BusinessUnitRepository extends JpaRepository<BusinessUnit, String> {
    
    /**
     * 根据编码查找业务单元
     */
    Optional<BusinessUnit> findByCode(String code);
    
    /**
     * 检查编码是否存在
     */
    boolean existsByCode(String code);
    
    /**
     * 检查同级业务单元名称是否存在
     */
    @Query("SELECT COUNT(b) > 0 FROM BusinessUnit b WHERE b.name = :name AND " +
           "((:parentId IS NULL AND b.parentId IS NULL) OR b.parentId = :parentId) AND b.id != :excludeId")
    boolean existsByNameAndParentIdExcluding(
            @Param("name") String name, 
            @Param("parentId") String parentId,
            @Param("excludeId") String excludeId);
    
    /**
     * 根据父业务单元ID查找子业务单元
     */
    List<BusinessUnit> findByParentIdOrderBySortOrder(String parentId);
    
    /**
     * 查找根业务单元
     */
    @Query("SELECT b FROM BusinessUnit b WHERE b.parentId IS NULL ORDER BY b.sortOrder")
    List<BusinessUnit> findRootBusinessUnits();
    
    /**
     * 根据状态查找业务单元
     */
    List<BusinessUnit> findByStatus(String status);
    
    /**
     * 检查是否有子业务单元
     */
    boolean existsByParentId(String parentId);
    
    /**
     * 根据路径前缀查找所有后代业务单元
     */
    @Query("SELECT b FROM BusinessUnit b WHERE b.path LIKE :pathPrefix%")
    List<BusinessUnit> findByPathStartingWith(@Param("pathPrefix") String pathPrefix);
    
    /**
     * 查找所有活跃业务单元
     */
    @Query("SELECT b FROM BusinessUnit b WHERE b.status = 'ACTIVE' ORDER BY b.level, b.sortOrder")
    List<BusinessUnit> findAllActive();
    
    /**
     * 搜索业务单元
     */
    @Query("SELECT b FROM BusinessUnit b WHERE " +
           "LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.code) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<BusinessUnit> searchBusinessUnits(@Param("keyword") String keyword);
}
