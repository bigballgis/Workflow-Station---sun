package com.admin.bi.repository;

import com.admin.bi.entity.BiRbacMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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
    @Modifying
    @Query("DELETE FROM BiRbacMapping m WHERE m.sysRoleId = :sysRoleId")
    void deleteBySysRoleId(String sysRoleId);

    /**
     * 获取所有不重复的 sys_role_id（已映射的系统角色 ID）
     */
    @Query("SELECT DISTINCT m.sysRoleId FROM BiRbacMapping m")
    List<String> findDistinctSysRoleIds();
}
