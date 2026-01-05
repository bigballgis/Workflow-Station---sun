package com.admin.repository;

import com.admin.entity.ColumnPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ColumnPermissionRepository extends JpaRepository<ColumnPermission, String> {
    
    List<ColumnPermission> findByRuleId(String ruleId);
    
    void deleteByRuleId(String ruleId);
}
