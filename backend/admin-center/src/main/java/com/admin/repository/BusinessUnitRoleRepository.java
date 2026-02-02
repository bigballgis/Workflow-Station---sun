package com.admin.repository;

import com.platform.security.entity.BusinessUnitRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 业务单元角色绑定仓库接口
 */
@Repository
public interface BusinessUnitRoleRepository extends JpaRepository<BusinessUnitRole, String> {
    
    /**
     * 根据业务单元ID查找所有角色绑定
     */
    List<BusinessUnitRole> findByBusinessUnitId(String businessUnitId);
    
    /**
     * 根据角色ID查找所有业务单元绑定
     */
    List<BusinessUnitRole> findByRoleId(String roleId);
    
    /**
     * 根据业务单元ID和角色ID查找绑定
     */
    Optional<BusinessUnitRole> findByBusinessUnitIdAndRoleId(String businessUnitId, String roleId);
    
    /**
     * 检查业务单元和角色的绑定是否存在
     */
    boolean existsByBusinessUnitIdAndRoleId(String businessUnitId, String roleId);
    
    /**
     * 删除业务单元的所有角色绑定
     */
    void deleteByBusinessUnitId(String businessUnitId);
    
    /**
     * 删除指定角色的所有业务单元绑定
     */
    void deleteByRoleId(String roleId);
    
    /**
     * 根据业务单元ID查找所有角色绑定（包含角色信息）
     */
    @Query("SELECT bur FROM BusinessUnitRole bur LEFT JOIN FETCH bur.role WHERE bur.businessUnitId = :businessUnitId")
    List<BusinessUnitRole> findByBusinessUnitIdWithRole(@Param("businessUnitId") String businessUnitId);
    
    /**
     * 统计业务单元绑定的角色数量
     */
    long countByBusinessUnitId(String businessUnitId);
}
