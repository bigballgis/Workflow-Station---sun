package com.admin.bi.repository;

import com.admin.bi.entity.BiDashboardRegistry;
import com.admin.bi.enums.DashboardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Dashboard 本地注册表 Repository
 */
@Repository
public interface BiDashboardRegistryRepository extends JpaRepository<BiDashboardRegistry, String>,
        JpaSpecificationExecutor<BiDashboardRegistry> {

    /**
     * 按 Superset Dashboard ID 查询
     */
    Optional<BiDashboardRegistry> findBySupersetDashboardId(Integer supersetDashboardId);

    /**
     * 按状态筛选
     */
    List<BiDashboardRegistry> findByStatus(DashboardStatus status);

    /**
     * 按 title、tags、status 动态筛选（分页）
     * 使用 CAST 避免 Hibernate 6 + PostgreSQL 将 null 参数推断为 bytea 类型
     */
    @Query("SELECT d FROM BiDashboardRegistry d WHERE " +
           "(CAST(:title AS string) IS NULL OR LOWER(d.dashboardTitle) LIKE LOWER(CONCAT('%', CAST(:title AS string), '%'))) AND " +
           "(CAST(:tags AS string) IS NULL OR LOWER(d.tags) LIKE LOWER(CONCAT('%', CAST(:tags AS string), '%'))) AND " +
           "(:status IS NULL OR d.status = :status)")
    Page<BiDashboardRegistry> findByFilters(
            @Param("title") String title,
            @Param("tags") String tags,
            @Param("status") DashboardStatus status,
            Pageable pageable);
}
