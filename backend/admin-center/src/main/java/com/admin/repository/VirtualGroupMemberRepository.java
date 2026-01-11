package com.admin.repository;

import com.admin.entity.VirtualGroupMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 虚拟组成员仓库接口
 */
@Repository
public interface VirtualGroupMemberRepository extends JpaRepository<VirtualGroupMember, String> {
    
    /**
     * 根据虚拟组ID查找成员
     */
    @Query("SELECT m FROM VirtualGroupMember m WHERE m.virtualGroup.id = :groupId")
    List<VirtualGroupMember> findByVirtualGroupId(@Param("groupId") String groupId);
    
    /**
     * 根据虚拟组ID分页查找成员
     */
    @Query("SELECT m FROM VirtualGroupMember m WHERE m.virtualGroup.id = :groupId")
    Page<VirtualGroupMember> findByVirtualGroupId(@Param("groupId") String groupId, Pageable pageable);
    
    /**
     * 根据用户ID查找成员关系
     */
    @Query("SELECT m FROM VirtualGroupMember m WHERE m.user.id = :userId")
    List<VirtualGroupMember> findByUserId(@Param("userId") String userId);
    
    /**
     * 根据虚拟组ID和用户ID查找成员关系
     */
    @Query("SELECT m FROM VirtualGroupMember m WHERE m.virtualGroup.id = :groupId AND m.user.id = :userId")
    Optional<VirtualGroupMember> findByVirtualGroupIdAndUserId(@Param("groupId") String groupId, @Param("userId") String userId);
    
    /**
     * 检查用户是否是虚拟组成员
     */
    @Query("SELECT COUNT(m) > 0 FROM VirtualGroupMember m WHERE m.virtualGroup.id = :groupId AND m.user.id = :userId")
    boolean existsByVirtualGroupIdAndUserId(@Param("groupId") String groupId, @Param("userId") String userId);
    
    /**
     * 统计虚拟组成员数量
     */
    @Query("SELECT COUNT(m) FROM VirtualGroupMember m WHERE m.virtualGroup.id = :groupId")
    long countByVirtualGroupId(@Param("groupId") String groupId);
    
    /**
     * 删除虚拟组的所有成员
     */
    @Modifying
    @Query("DELETE FROM VirtualGroupMember m WHERE m.virtualGroup.id = :groupId")
    void deleteByVirtualGroupId(@Param("groupId") String groupId);
    
    /**
     * 删除用户的所有虚拟组成员关系
     */
    @Modifying
    @Query("DELETE FROM VirtualGroupMember m WHERE m.user.id = :userId")
    void deleteByUserId(@Param("userId") String userId);
    
    /**
     * 查找用户在有效虚拟组中的成员关系
     */
    @Query("SELECT m FROM VirtualGroupMember m " +
           "JOIN m.virtualGroup vg " +
           "WHERE m.user.id = :userId AND vg.status = 'ACTIVE'")
    List<VirtualGroupMember> findActiveGroupMembershipsByUserId(@Param("userId") String userId);
    
    /**
     * 获取用户所属的所有虚拟组ID
     */
    @Query("SELECT m.virtualGroup.id FROM VirtualGroupMember m WHERE m.user.id = :userId")
    List<String> findVirtualGroupIdsByUserId(@Param("userId") String userId);
}
