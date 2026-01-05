package com.admin.repository;

import com.admin.entity.PermissionDelegation;
import com.admin.enums.DelegationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 权限委托数据访问接口
 */
@Repository
public interface PermissionDelegationRepository extends JpaRepository<PermissionDelegation, String> {
    
    /**
     * 根据委托人ID查找委托记录
     */
    List<PermissionDelegation> findByDelegatorId(String delegatorId);
    
    /**
     * 根据受委托人ID查找委托记录
     */
    List<PermissionDelegation> findByDelegateeId(String delegateeId);
    
    /**
     * 根据委托人和权限查找有效的委托记录
     */
    @Query("SELECT pd FROM PermissionDelegation pd WHERE pd.delegatorId = :delegatorId " +
           "AND pd.permission.id = :permissionId AND pd.status = 'ACTIVE' " +
           "AND pd.validFrom <= :now AND (pd.validTo IS NULL OR pd.validTo > :now)")
    List<PermissionDelegation> findActiveDelegationsByDelegatorAndPermission(
            @Param("delegatorId") String delegatorId,
            @Param("permissionId") String permissionId,
            @Param("now") Instant now);
    
    /**
     * 根据受委托人和权限查找有效的委托记录
     */
    @Query("SELECT pd FROM PermissionDelegation pd WHERE pd.delegateeId = :delegateeId " +
           "AND pd.permission.id = :permissionId AND pd.status = 'ACTIVE' " +
           "AND pd.validFrom <= :now AND (pd.validTo IS NULL OR pd.validTo > :now)")
    List<PermissionDelegation> findActiveDelegationsByDelegateeAndPermission(
            @Param("delegateeId") String delegateeId,
            @Param("permissionId") String permissionId,
            @Param("now") Instant now);
    
    /**
     * 查找已过期的委托记录
     */
    @Query("SELECT pd FROM PermissionDelegation pd WHERE pd.status = 'ACTIVE' " +
           "AND pd.validTo IS NOT NULL AND pd.validTo <= :now")
    List<PermissionDelegation> findExpiredDelegations(@Param("now") Instant now);
    
    /**
     * 根据委托类型查找委托记录
     */
    List<PermissionDelegation> findByDelegationType(DelegationType delegationType);
    
    /**
     * 查找用户的所有有效委托权限
     */
    @Query("SELECT pd FROM PermissionDelegation pd WHERE pd.delegateeId = :userId " +
           "AND pd.status = 'ACTIVE' AND pd.validFrom <= :now " +
           "AND (pd.validTo IS NULL OR pd.validTo > :now)")
    List<PermissionDelegation> findActiveUserDelegations(
            @Param("userId") String userId,
            @Param("now") Instant now);
}