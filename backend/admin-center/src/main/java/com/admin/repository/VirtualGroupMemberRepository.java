package com.admin.repository;
import com.platform.security.entity.VirtualGroupMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
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
     * 虚拟组某一来源（AD / MANUAL）的全部成员行，跨名单。
     */
    List<VirtualGroupMember> findByGroupIdAndSource(String groupId, String source);
    
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

    String ACTIVE_USER_ON_LISTS_SQL = "SELECT EXISTS (SELECT 1 FROM sys_virtual_group_members m "
            + "JOIN sys_users u ON u.id = m.user_id AND u.status = 'ACTIVE' AND COALESCE(u.deleted, false) = false "
            + "JOIN sys_roles r ON r.code = m.list_type AND (r.status = 'ACTIVE' OR r.status IS NULL "
            + "OR TRIM(COALESCE(r.status, '')) = '') "
            + "WHERE m.group_id = :groupId AND m.user_id = :userId AND m.list_type IN (:listTypes))";

    /**
     * Is the user on one of these lists of the group? Like the platform role resolver, a list counts only for an
     * active, not deleted account and while its role (TECH_LEAD / DEVELOPER) is active (platform access model §4.1).
     */
    @Query(value = ACTIVE_USER_ON_LISTS_SQL, nativeQuery = true)
    boolean isActiveUserOnLists(@Param("groupId") String groupId, @Param("userId") String userId,
                                @Param("listTypes") java.util.Collection<String> listTypes);

    /** A team a user leads: the DEVELOPER group's id and code. */
    interface LedTeam {
        String getId();

        String getCode();
    }

    String LED_TEAMS_SQL = "SELECT DISTINCT g.id AS id, g.code AS code FROM sys_virtual_group_members m "
            + "JOIN sys_virtual_groups g ON g.id = m.group_id AND g.type = 'DEVELOPER' AND g.status = 'ACTIVE' "
            + "JOIN sys_users u ON u.id = m.user_id AND u.status = 'ACTIVE' AND COALESCE(u.deleted, false) = false "
            + "JOIN sys_roles r ON r.code = m.list_type AND (r.status = 'ACTIVE' OR r.status IS NULL "
            + "OR TRIM(COALESCE(r.status, '')) = '') "
            + "WHERE m.user_id = :userId AND m.list_type = 'TECH_LEAD'";

    /**
     * The active teams the user is a Technical Lead of, under the same conditions as the platform role resolver: an
     * active, not deleted account and an active TECH_LEAD role (platform access model §4.1, §8.2).
     */
    @Query(value = LED_TEAMS_SQL, nativeQuery = true)
    List<LedTeam> findTeamsLedBy(@Param("userId") String userId);

    /**
     * 统计虚拟组成员数量
     */
    long countByGroupId(String groupId);

    /**
     * 虚拟组的成员人数（一人可在多份名单、两种来源下各有一行）
     */
    @Query("SELECT COUNT(DISTINCT m.userId) FROM VirtualGroupMember m WHERE m.groupId = :groupId")
    long countDistinctUsersByGroupId(@Param("groupId") String groupId);
    
    /**
     * 删除虚拟组的所有成员
     */
    @Modifying
    @Transactional
    void deleteByGroupId(String groupId);
    
    /**
     * 删除用户的所有虚拟组成员关系
     */
    @Modifying
    @Transactional
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
    @Transactional
    void deleteByGroupIdAndUserId(String groupId, String userId);
    
    /**
     * 根据多个虚拟组ID查找所有成员用户ID（去重）
     */
    @Query("SELECT DISTINCT m.userId FROM VirtualGroupMember m WHERE m.groupId IN :groupIds")
    List<String> findUserIdsByVirtualGroupIds(@Param("groupIds") List<String> groupIds);
}