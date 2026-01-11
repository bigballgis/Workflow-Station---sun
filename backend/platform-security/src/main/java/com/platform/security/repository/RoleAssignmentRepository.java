package com.platform.security.repository;

import com.platform.security.entity.RoleAssignment;
import com.platform.security.enums.AssignmentTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色分配仓库接口
 */
@Repository
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, String> {
    
    /**
     * 根据角色ID查找所有分配
     */
    List<RoleAssignment> findByRoleId(String roleId);
    
    /**
     * 根据目标类型和目标ID查找分配
     */
    List<RoleAssignment> findByTargetTypeAndTargetId(AssignmentTargetType targetType, String targetId);
    
    /**
     * 根据角色ID和目标类型查找分配
     */
    List<RoleAssignment> findByRoleIdAndTargetType(String roleId, AssignmentTargetType targetType);
    
    /**
     * 检查是否存在重复分配
     */
    boolean existsByRoleIdAndTargetTypeAndTargetId(String roleId, AssignmentTargetType targetType, String targetId);
    
    /**
     * 根据角色ID、目标类型和目标ID查找分配
     */
    Optional<RoleAssignment> findByRoleIdAndTargetTypeAndTargetId(
            String roleId, AssignmentTargetType targetType, String targetId);
    
    /**
     * 查找分配给特定用户的所有角色分配（USER类型）
     */
    @Query("SELECT ra FROM RoleAssignment ra WHERE ra.targetType = 'USER' AND ra.targetId = :userId " +
           "AND (ra.validFrom IS NULL OR ra.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (ra.validTo IS NULL OR ra.validTo >= CURRENT_TIMESTAMP)")
    List<RoleAssignment> findValidUserAssignments(@Param("userId") String userId);
    
    /**
     * 查找分配给特定部门的所有角色分配（DEPARTMENT类型）
     */
    @Query("SELECT ra FROM RoleAssignment ra WHERE ra.targetType = 'DEPARTMENT' AND ra.targetId = :departmentId " +
           "AND (ra.validFrom IS NULL OR ra.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (ra.validTo IS NULL OR ra.validTo >= CURRENT_TIMESTAMP)")
    List<RoleAssignment> findValidDepartmentAssignments(@Param("departmentId") String departmentId);
    
    /**
     * 查找分配给部门层级的所有角色分配（DEPARTMENT_HIERARCHY类型）
     * 需要传入用户所在部门的所有祖先部门ID列表
     */
    @Query("SELECT ra FROM RoleAssignment ra WHERE ra.targetType = 'DEPARTMENT_HIERARCHY' " +
           "AND ra.targetId IN :departmentIds " +
           "AND (ra.validFrom IS NULL OR ra.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (ra.validTo IS NULL OR ra.validTo >= CURRENT_TIMESTAMP)")
    List<RoleAssignment> findValidDepartmentHierarchyAssignments(@Param("departmentIds") List<String> departmentIds);
    
    /**
     * 查找分配给虚拟组的所有角色分配（VIRTUAL_GROUP类型）
     */
    @Query("SELECT ra FROM RoleAssignment ra WHERE ra.targetType = 'VIRTUAL_GROUP' AND ra.targetId IN :groupIds " +
           "AND (ra.validFrom IS NULL OR ra.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (ra.validTo IS NULL OR ra.validTo >= CURRENT_TIMESTAMP)")
    List<RoleAssignment> findValidVirtualGroupAssignments(@Param("groupIds") List<String> groupIds);
    
    /**
     * 删除角色的所有分配
     */
    void deleteByRoleId(String roleId);
    
    /**
     * 删除目标的所有分配（当目标被删除时）
     */
    void deleteByTargetTypeAndTargetId(AssignmentTargetType targetType, String targetId);
    
    /**
     * 统计角色的分配数量
     */
    long countByRoleId(String roleId);
    
    /**
     * 统计特定类型的分配数量
     */
    long countByRoleIdAndTargetType(String roleId, AssignmentTargetType targetType);
}
