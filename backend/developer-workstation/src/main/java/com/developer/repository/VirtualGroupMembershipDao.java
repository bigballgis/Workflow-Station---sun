package com.developer.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 查询用户所属虚拟组（sys_virtual_group_members，与 admin 共用库）
 */
@Repository
@RequiredArgsConstructor
public class VirtualGroupMembershipDao {

    private final JdbcTemplate jdbcTemplate;

    public List<String> findVirtualGroupIdsByUserId(String userId) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT group_id FROM sys_virtual_group_members WHERE user_id = ?",
                String.class,
                userId);
    }
}
