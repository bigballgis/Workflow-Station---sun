package com.admin.repository;

import com.admin.entity.DataPermissionRule;
import com.admin.enums.DataPermissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DataPermissionRuleRepository extends JpaRepository<DataPermissionRule, String> {
    
    List<DataPermissionRule> findByTargetTypeAndTargetIdAndEnabledOrderByPriority(
            String targetType, String targetId, Boolean enabled);
    
    List<DataPermissionRule> findByResourceTypeAndEnabledOrderByPriority(String resourceType, Boolean enabled);
    
    @Query("SELECT r FROM DataPermissionRule r WHERE r.enabled = true " +
           "AND r.resourceType = :resourceType " +
           "AND ((r.targetType = 'ROLE' AND r.targetId IN :roleIds) " +
           "OR (r.targetType = 'DEPARTMENT' AND r.targetId IN :deptIds) " +
           "OR (r.targetType = 'USER' AND r.targetId = :userId)) " +
           "ORDER BY r.priority")
    List<DataPermissionRule> findApplicableRules(
            @Param("resourceType") String resourceType,
            @Param("roleIds") List<String> roleIds,
            @Param("deptIds") List<String> deptIds,
            @Param("userId") String userId);
}
