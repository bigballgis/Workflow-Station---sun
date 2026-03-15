package com.admin.bi.repository;

import com.admin.bi.entity.BiSupersetRole;
import com.admin.bi.enums.SupersetRoleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Superset 角色本地注册表 Repository
 */
@Repository
public interface BiSupersetRoleRepository extends JpaRepository<BiSupersetRole, Integer> {

    /**
     * 按 Superset Role ID 查询
     */
    Optional<BiSupersetRole> findBySupersetRoleId(Integer supersetRoleId);

    /**
     * 按状态筛选
     */
    List<BiSupersetRole> findByStatus(SupersetRoleStatus status);

    /**
     * 按多个 Superset Role ID 批量查询
     */
    List<BiSupersetRole> findBySupersetRoleIdIn(List<Integer> supersetRoleIds);
}
