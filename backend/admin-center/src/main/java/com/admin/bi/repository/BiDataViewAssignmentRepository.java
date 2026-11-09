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

    boolean existsByDashboardIdAndFunctionUnitCodeAndViewNameAndIdNot(
            String dashboardId, String functionUnitCode, String viewName, String id);

    boolean existsByDashboardIdAndFunctionUnitCodeAndViewName(
            String dashboardId, String functionUnitCode, String viewName);

    boolean existsByDashboardIdAndFunctionUnitCodeAndViewNameAndEnabledTrue(
            String dashboardId, String functionUnitCode, String viewName);

    List<BiDataViewAssignment> findByFunctionUnitCodeAndViewNameAndEnabledTrueOrderByCreatedAtAscIdAsc(
            String functionUnitCode, String viewName);

    long countByDashboardId(String dashboardId);

    List<BiDataViewAssignment> findByFunctionUnitCodeAndViewName(String functionUnitCode, String viewName);

    @Query("SELECT a FROM BiDataViewAssignment a JOIN BiDashboardRegistry d ON a.dashboardId = d.id WHERE " +
            "(CAST(:dashboardTitle AS string) IS NULL OR LOWER(d.dashboardTitle) LIKE " +
            "LOWER(CONCAT('%', CAST(:dashboardTitle AS string), '%'))) AND " +
            "(CAST(:functionUnitCode AS string) IS NULL OR a.functionUnitCode = :functionUnitCode) AND " +
            "(:enabled IS NULL OR a.enabled = :enabled) AND " +
            "(CAST(:viewName AS string) IS NULL OR a.viewName = :viewName)")
    Page<BiDataViewAssignment> findByFilters(
            @Param("dashboardTitle") String dashboardTitle,
            @Param("functionUnitCode") String functionUnitCode,
            @Param("enabled") Boolean enabled,
            @Param("viewName") String viewName,
            Pageable pageable);
}
