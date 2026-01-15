package com.admin.repository;

import com.admin.entity.VirtualGroupRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 虚拟组角色绑定仓库接口
 * 注意：每个虚拟组只能绑定一个角色（单角色绑定）
 */
@Repository
public interface VirtualGroupRoleRepository extends JpaRepository<VirtualGroupRole, String> {
    
    /**
     * 根据虚拟组ID查找角色绑定（单角色绑定，返回Optional）
     */
    Optional<VirtualGroupRole> findByVirtualGroupId(String virtualGroupId);
    
    /**
     * 根据虚拟组ID查找所有角色绑定（兼容旧代码，已废弃）
     * @deprecated 使用 {@link #findByVirtualGroupId(String)} 代替
     */
    @Deprecated
    List<VirtualGroupRole> findAllByVirtualGroupId(String virtualGroupId);
    
    /**
     * 根据角色ID查找所有虚拟组绑定
     */
    List<VirtualGroupRole> findByRoleId(String roleId);
    
    /**
     * 根据虚拟组ID和角色ID查找绑定
     */
    Optional<VirtualGroupRole> findByVirtualGroupIdAndRoleId(String virtualGroupId, String roleId);
    
    /**
     * 检查虚拟组和角色的绑定是否存在
     */
    boolean existsByVirtualGroupIdAndRoleId(String virtualGroupId, String roleId);
    
    /**
     * 删除虚拟组的所有角色绑定
     */
    void deleteByVirtualGroupId(String virtualGroupId);
    
    /**
     * 删除指定角色的所有虚拟组绑定
     */
    void deleteByRoleId(String roleId);
    
    /**
     * 根据虚拟组ID查找角色绑定（包含角色信息，单角色绑定）
     */
    @Query("SELECT vgr FROM VirtualGroupRole vgr LEFT JOIN FETCH vgr.role WHERE vgr.virtualGroupId = :virtualGroupId")
    Optional<VirtualGroupRole> findByVirtualGroupIdWithRole(@Param("virtualGroupId") String virtualGroupId);
    
    /**
     * 统计虚拟组绑定的角色数量
     */
    long countByVirtualGroupId(String virtualGroupId);
    
    /**
     * 根据多个虚拟组ID批量查找角色绑定
     */
    List<VirtualGroupRole> findByVirtualGroupIdIn(List<String> virtualGroupIds);
    
    /**
     * 根据角色ID查找所有绑定了该角色的虚拟组ID
     */
    @Query("SELECT vgr.virtualGroupId FROM VirtualGroupRole vgr WHERE vgr.roleId = :roleId")
    List<String> findVirtualGroupIdsByRoleId(@Param("roleId") String roleId);
}
