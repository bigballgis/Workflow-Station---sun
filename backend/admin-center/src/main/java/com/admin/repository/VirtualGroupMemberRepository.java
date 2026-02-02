package com.admin.repository;

import com.platform.security.entity.VirtualGroupMember;
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
 * Note: VirtualGroupMember entity uses groupId and userId as String fields, not relationships
 */
@Repository
public interface VirtualGroupMemberRepository extends JpaRepository<VirtualGroupMember, String> {
    
    /**
     * 根据虚拟组ID查找成员
     */
    List<VirtualGroupMember> findByGroupId(String groupId);
    
    /**
     * 根据虚拟组ID分页查找成员
     */
    Page<VirtualGroupMember> findByGroupId(String groupId, Pageable pageable);
    
    /**
     * 根据用户ID查找成员关系
     */
    List<VirtualGroupMember> findByUserId(String userId);
    
    /**
     * 根据虚拟组ID和用户ID查找成员关系
     */
    Optional<VirtualGroupMember> findByGroupIdAndUserId(String groupId, String userId);
    
    /**
     * 检查用户是否是虚拟组成员
     */
    boolean existsByGroupIdAndUserId(String groupId, String userId);
    
    /**
     * 统计虚拟组成员数量
     */
    long countByGroupId(String groupId);
    
    /**
     * 删除虚拟组的所有成员
     */
    @Modifying
    void deleteByGroupId(String groupId);
    
    /**
     * 删除用户的所有虚拟组成员关系
     */
    @Modifying
    void deleteByUserId(String userId);
    
    /**
     * 查找用户在有效虚拟组中的成员关系
     * Using subquery since VirtualGroupMember doesn't have relationship to VirtualGroup
     */
    @Query("SELECT m FROM VirtualGroupMember m WHERE m.userId = :userId " +
           "AND m.groupId IN (SELECT vg.id FROM VirtualGroup vg WHERE vg.status = 'ACTIVE')")
    List<VirtualGroupMember> findActiveGroupMembershipsByUserId(@Param("userId") String userId);
    
    /**
     * 获取用户所属的所有虚拟组ID
     */
    @Query("SELECT m.groupId FROM VirtualGroupMember m WHERE m.userId = :userId")
    List<String> findVirtualGroupIdsByUserId(@Param("userId") String userId);
    
    /**
     * 删除指定虚拟组和用户的成员关系
     */
    @Modifying
    void deleteByGroupIdAndUserId(String groupId, String userId);
    
    /**
     * 根据多个虚拟组ID查找所有成员用户ID（去重）
     */
    @Query("SELECT DISTINCT m.userId FROM VirtualGroupMember m WHERE m.groupId IN :groupIds")
    List<String> findUserIdsByVirtualGroupIds(@Param("groupIds") List<String> groupIds);
}
