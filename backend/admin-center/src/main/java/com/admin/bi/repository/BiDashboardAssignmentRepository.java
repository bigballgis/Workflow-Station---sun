package com.admin.bi.repository;

import com.admin.bi.entity.BiDashboardAssignment;
import com.admin.bi.enums.AssignmentTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Dashboard 分配记录 Repository
 */
@Repository
public interface BiDashboardAssignmentRepository extends JpaRepository<BiDashboardAssignment, String> {

    /**
     * 按 Dashboard ID 查询分配记录
     */
    List<BiDashboardAssignment> findByDashboardId(String dashboardId);

    /**
     * 按目标类型和目标 ID 查询
     */
    List<BiDashboardAssignment> findByTargetTypeAndTargetId(AssignmentTargetType targetType, String targetId);

    /**
     * 按目标类型和多个目标 ID 查询
     */
    List<BiDashboardAssignment> findByTargetTypeAndTargetIdIn(AssignmentTargetType targetType, List<String> targetIds);

    /**
     * 唯一性校验：检查同一 Dashboard + Target Type + Target ID 是否已存在
     */
    boolean existsByDashboardIdAndTargetTypeAndTargetId(String dashboardId, AssignmentTargetType targetType, String targetId);

    /**
     * 统计某个 Dashboard 的分配数量
     */
    long countByDashboardId(String dashboardId);

    /**
     * 按 targetType 和 dashboardTitle 动态筛选（分页）
     * 使用 CAST 避免 Hibernate 6 + PostgreSQL 将 null 参数推断为 bytea 类型
     */
    @Query("SELECT a FROM BiDashboardAssignment a JOIN BiDashboardRegistry d ON a.dashboardId = d.id WHERE " +
           "(:targetType IS NULL OR a.targetType = :targetType) AND " +
           "(CAST(:dashboardTitle AS string) IS NULL OR LOWER(d.dashboardTitle) LIKE LOWER(CONCAT('%', CAST(:dashboardTitle AS string), '%')))")
    Page<BiDashboardAssignment> findByFilters(
            @Param("targetType") AssignmentTargetType targetType,
            @Param("dashboardTitle") String dashboardTitle,
            Pageable pageable);
}
