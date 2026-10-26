package com.admin.bi.repository;

import com.admin.bi.entity.BiDataViewAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BiDataViewAssignmentRepository extends JpaRepository<BiDataViewAssignment, String> {

    /** Serialize edits, status changes and deletion of the same assignment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM BiDataViewAssignment a WHERE a.id = :id")
    Optional<BiDataViewAssignment> findByIdForUpdate(@Param("id") String id);

    boolean existsByDashboardIdAndTableId(String dashboardId, Long tableId);

    boolean existsByDashboardIdAndViewIdAndIdNot(String dashboardId, Long viewId, String id);

    boolean existsByDashboardIdAndViewId(String dashboardId, Long viewId);

    boolean existsByDashboardIdAndViewIdAndEnabledTrue(String dashboardId, Long viewId);

    List<BiDataViewAssignment> findByViewIdAndEnabledTrueOrderByCreatedAtAscIdAsc(Long viewId);

    long countByDashboardId(String dashboardId);

    List<BiDataViewAssignment> findByTableIdOrderByCreatedAtAsc(Long tableId);

    List<BiDataViewAssignment> findByViewId(Long viewId);

    @Query("SELECT a FROM BiDataViewAssignment a JOIN BiDashboardRegistry d ON a.dashboardId = d.id WHERE " +
            "(CAST(:dashboardTitle AS string) IS NULL OR LOWER(d.dashboardTitle) LIKE " +
            "LOWER(CONCAT('%', CAST(:dashboardTitle AS string), '%'))) AND " +
            "(:functionUnitId IS NULL OR a.functionUnitId = :functionUnitId) AND " +
            "(:enabled IS NULL OR a.enabled = :enabled) AND (:viewId IS NULL OR a.viewId = :viewId)")
    Page<BiDataViewAssignment> findByFilters(
            @Param("dashboardTitle") String dashboardTitle,
            @Param("functionUnitId") Long functionUnitId,
            @Param("enabled") Boolean enabled,
            @Param("viewId") Long viewId,
            Pageable pageable);
}
