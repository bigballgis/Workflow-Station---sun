package com.admin.bi.repository;

import com.admin.bi.entity.BiRbacMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Sys_Role 与 Superset_Role 映射 Repository
 */
@Repository
public interface BiRbacMappingRepository extends JpaRepository<BiRbacMapping, String> {

    /**
     * 按系统角色 ID 查询映射
     */
    List<BiRbacMapping> findBySysRoleId(String sysRoleId);

    /**
     * 按多个系统角色 ID 批量查询映射
     */
    List<BiRbacMapping> findBySysRoleIdIn(List<String> sysRoleIds);

    /**
     * 按系统角色 ID 删除所有映射（全量替换时使用）
     */
    void deleteBySysRoleId(String sysRoleId);
}
