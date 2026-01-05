package com.admin.repository;

import com.admin.entity.VirtualGroupMember;
import com.admin.enums.VirtualGroupMemberRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
    List<VirtualGroupMember> findByVirtualGroupId(String groupId);
    
    /**
     * 根据虚拟组ID分页查找成员
     */
    Page<VirtualGroupMember> findByVirtualGroupId(String groupId, Pageable pageable);
    
    /**
     * 根据用户ID查找成员关系
     */
    List<VirtualGroupMember> findByUserId(String userId);
    
    /**
     * 根据虚拟组ID和用户ID查找成员关系
     */
    Optional<VirtualGroupMember> findByVirtualGroupIdAndUserId(String groupId, String userId);
    
    /**
     * 检查用户是否是虚拟组成员
     */
    boolean existsByVirtualGroupIdAndUserId(String groupId, String userId);
    
    /**
     * 根据虚拟组ID和角色查找成员
     */
    List<VirtualGroupMember> findByVirtualGroupIdAndRole(String groupId, VirtualGroupMemberRole role);
    
    /**
     * 统计虚拟组成员数量
     */
    long countByVirtualGroupId(String groupId);
    
    /**
     * 统计虚拟组中某角色的成员数量
     */
    long countByVirtualGroupIdAndRole(String groupId, VirtualGroupMemberRole role);
    
    /**
     * 删除虚拟组的所有成员
     */
    void deleteByVirtualGroupId(String groupId);
    
    /**
     * 删除用户的所有虚拟组成员关系
     */
    void deleteByUserId(String userId);
    
    /**
     * 查找用户在有效虚拟组中的成员关系
     */
    @Query("SELECT m FROM VirtualGroupMember m " +
           "JOIN m.virtualGroup vg " +
           "WHERE m.user.id = :userId AND vg.status = 'ACTIVE'")
    List<VirtualGroupMember> findActiveGroupMembershipsByUserId(@Param("userId") String userId);
    
    /**
     * 查找虚拟组的组长
     */
    @Query("SELECT m FROM VirtualGroupMember m " +
           "WHERE m.virtualGroup.id = :groupId AND m.role = 'LEADER'")
    List<VirtualGroupMember> findLeadersByGroupId(@Param("groupId") String groupId);
}
