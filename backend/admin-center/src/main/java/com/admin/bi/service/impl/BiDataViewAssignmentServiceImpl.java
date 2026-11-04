package com.admin.bi.service.impl;

import com.admin.bi.dto.request.DataViewAssignmentRequest;
import com.admin.bi.dto.request.DataViewAssignmentBatchRequest;
import com.admin.bi.dto.response.DataViewAssignmentResponse;
import com.admin.bi.dto.response.DataViewDashboardResponse;
import com.admin.bi.dto.response.DataViewFunctionUnitOptionResponse;
import com.admin.bi.dto.response.DataViewTableOptionResponse;
import com.admin.bi.entity.BiDashboardRegistry;
import com.admin.bi.entity.BiDataViewAssignment;
import com.admin.bi.enums.DashboardStatus;
import com.admin.bi.repository.BiDashboardRegistryRepository;
import com.admin.bi.repository.BiDataViewAssignmentRepository;
import com.admin.bi.repository.BiDataViewMetadataRepository;
import com.admin.bi.dto.response.DataViewOptionResponse;
import com.admin.bi.service.BiDataViewAssignmentService;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.DashboardInactiveException;
import com.admin.exception.DashboardNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BiDataViewAssignmentServiceImpl implements BiDataViewAssignmentService {


    private final BiDataViewAssignmentRepository assignmentRepository;
    private final BiDashboardRegistryRepository dashboardRepository;
    private final JdbcTemplate jdbcTemplate;
    // Controller audit can preload this row; refresh it after acquiring the database lock.
    private final EntityManager entityManager;

    private final BiDataViewMetadataRepository metadata;
    private final com.admin.dataview.RuntimeDataViewCatalog dataViewCatalog;

    @Override
    @Transactional
    public DataViewAssignmentResponse updateEnabled(String id, boolean enabled) {
        BiDataViewAssignment assignment = assignmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AdminBusinessException("ASSIGNMENT_NOT_FOUND", "Assignment not found"));
        entityManager.refresh(assignment);
        if (enabled) {
            if (!assignment.isRuntimeTarget()) throw new AdminBusinessException("DATA_VIEW_NOT_AVAILABLE", "Select an Admin-published View before enabling this legacy binding");
            requireActiveDashboard(assignment.getDashboardId());
            metadata.requireAvailableView(assignment.getViewId());
        }
        assignment.setEnabled(enabled);
        return toResponse(assignmentRepository.saveAndFlush(assignment));
    }

    @Override
    @Transactional
    public DataViewAssignmentResponse create(DataViewAssignmentRequest request) {
        requireActiveDashboard(request.getDashboardId());
        metadata.requirePublishedView(request.getFunctionUnitId(), request.getTableId(), request.getViewId());
        if (assignmentRepository.existsByDashboardIdAndViewId(request.getDashboardId(), request.getViewId())) {
            throw new AdminBusinessException(
                    "DUPLICATE_DATA_VIEW_ASSIGNMENT",
                    "This dashboard is already assigned to the selected View");
        }

        BiDataViewAssignment saved = assignmentRepository.saveAndFlush(BiDataViewAssignment.builder()
                .id(UUID.randomUUID().toString())
                .runtimeTarget(true)
                .targetSnapshot(metadata.targetSnapshot(request.getViewId()))
                .dashboardId(request.getDashboardId())
                .functionUnitId(request.getFunctionUnitId())
                .tableId(request.getTableId())
                .viewId(request.getViewId())
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public List<DataViewAssignmentResponse> createBatch(DataViewAssignmentBatchRequest request) {
        metadata.requirePublishedView(request.getFunctionUnitId(), request.getTableId(), request.getViewId());
        List<String> dashboardIds = new LinkedHashSet<>(request.getDashboardIds()).stream().toList();

        Set<String> existingDashboardIds = assignmentRepository
                .findByViewId(request.getViewId()).stream()
                .map(BiDataViewAssignment::getDashboardId)
                .collect(Collectors.toSet());
        List<String> duplicates = dashboardIds.stream()
                .filter(existingDashboardIds::contains)
                .toList();
        if (!duplicates.isEmpty()) {
            throw new AdminBusinessException(
                    "DUPLICATE_DATA_VIEW_ASSIGNMENT",
                    "One or more selected dashboards are already assigned to the selected View");
        }

        Map<String, BiDashboardRegistry> dashboards = dashboardRepository.findAllById(dashboardIds).stream()
                .collect(Collectors.toMap(BiDashboardRegistry::getId, Function.identity()));
        for (String dashboardId : dashboardIds) {
            BiDashboardRegistry dashboard = dashboards.get(dashboardId);
            if (dashboard == null) throw new DashboardNotFoundException(dashboardId);
            if (dashboard.getStatus() != DashboardStatus.ACTIVE) throw new DashboardInactiveException(dashboardId);
        }

        Map<String,Object> targetSnapshot=metadata.targetSnapshot(request.getViewId());
        List<BiDataViewAssignment> assignments = dashboardIds.stream()
                .map(dashboardId -> BiDataViewAssignment.builder()
                        .id(UUID.randomUUID().toString())
                        .runtimeTarget(true)
                        .targetSnapshot(targetSnapshot)
                        .dashboardId(dashboardId)
                        .functionUnitId(request.getFunctionUnitId())
                        .tableId(request.getTableId())
                        .viewId(request.getViewId())
                        .enabled(request.getEnabled() == null || request.getEnabled())
                        .build())
                .toList();

        List<BiDataViewAssignment> saved = assignmentRepository.saveAllAndFlush(assignments);
        return metadata.responses(saved.stream().map(BiDataViewAssignment::getId).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DataViewAssignmentResponse> list(
            String dashboardTitle, Long functionUnitId, Boolean enabled, Long viewId, Pageable pageable) {
        String normalizedTitle = dashboardTitle == null || dashboardTitle.isBlank()
                ? null : dashboardTitle.trim();
        Page<BiDataViewAssignment> page = assignmentRepository.findByFilters(
                normalizedTitle, functionUnitId, enabled, viewId, pageable);
        Map<String, DataViewAssignmentResponse> responses = metadata.responses(
                page.getContent().stream().map(BiDataViewAssignment::getId).toList()).stream()
                .collect(Collectors.toMap(DataViewAssignmentResponse::getId, Function.identity()));
        return page.map(a -> responses.get(a.getId()));
    }

    @Override
    @Transactional
    public DataViewAssignmentResponse update(String id, DataViewAssignmentRequest request) {
        BiDataViewAssignment assignment = assignmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AdminBusinessException(
                        "DATA_VIEW_ASSIGNMENT_NOT_FOUND", "Data View Assignment not found: " + id));
        entityManager.refresh(assignment);
        requireActiveDashboard(request.getDashboardId());
        metadata.requirePublishedView(request.getFunctionUnitId(), request.getTableId(), request.getViewId());
        if (assignmentRepository.existsByDashboardIdAndViewIdAndIdNot(
                request.getDashboardId(), request.getViewId(), id)) {
            throw new AdminBusinessException(
                    "DUPLICATE_DATA_VIEW_ASSIGNMENT",
                    "This dashboard is already assigned to the selected View");
        }

        assignment.setRuntimeTarget(true);
        assignment.setTargetSnapshot(metadata.targetSnapshot(request.getViewId()));
        assignment.setDashboardId(request.getDashboardId());
        assignment.setFunctionUnitId(request.getFunctionUnitId());
        assignment.setTableId(request.getTableId());
        assignment.setViewId(request.getViewId());
        if (request.getEnabled() != null) assignment.setEnabled(request.getEnabled());
        return toResponse(assignmentRepository.saveAndFlush(assignment));
    }

    @Override
    @Transactional
    public void delete(String id) {
        BiDataViewAssignment assignment = assignmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AdminBusinessException(
                        "DATA_VIEW_ASSIGNMENT_NOT_FOUND", "Data View Assignment not found: " + id));
        entityManager.refresh(assignment);
        assignmentRepository.delete(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataViewFunctionUnitOptionResponse> listFunctionUnits() {
        return jdbcTemplate.query("SELECT DISTINCT function_unit_id, function_unit_code, function_unit_name "
                + "FROM ac_current_data_views ORDER BY function_unit_name,function_unit_code",
                (rs,n) -> new DataViewFunctionUnitOptionResponse(rs.getLong(1),rs.getString(2),rs.getString(3)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataViewTableOptionResponse> listTables(Long functionUnitId) {
        if (functionUnitId == null) return List.of();
        return jdbcTemplate.query("SELECT DISTINCT table_id,function_unit_id,table_name,table_display_name,table_type "
                + "FROM ac_current_data_views WHERE function_unit_id=? ORDER BY table_name",
                (rs,n) -> new DataViewTableOptionResponse(rs.getLong(1),rs.getLong(2),rs.getString(3),
                        rs.getString(4),rs.getString(5)), functionUnitId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataViewDashboardResponse> getDashboardsForView(String userId, Long viewId) {
        ViewContext view = requireAccessibleView(userId, viewId);
        // A View grant authorizes its active bound dashboards; landing-page roles are independent.
        List<BiDataViewAssignment> bindings = assignmentRepository.findByViewIdAndEnabledTrueOrderByCreatedAtAscIdAsc(view.id());
        Map<String, BiDashboardRegistry> dashboards = dashboardRepository.findAllById(
                bindings.stream().map(BiDataViewAssignment::getDashboardId).toList()).stream()
                .collect(Collectors.toMap(BiDashboardRegistry::getId, Function.identity()));
        return bindings.stream().map(a -> dashboards.get(a.getDashboardId()))
                .filter(Objects::nonNull)
                .filter(d -> d.getStatus() == DashboardStatus.ACTIVE)
                .map(d -> DataViewDashboardResponse.builder()
                        .dashboardId(d.getId())
                        .dashboardTitle(d.getDashboardTitle())
                        .description(d.getDescription())
                        .embedId(d.getEmbedId())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAccessDashboardForView(String userId, String dashboardId, Long viewId) {
        ViewContext view = requireAccessibleView(userId, viewId);
        if (!assignmentRepository.existsByDashboardIdAndViewIdAndEnabledTrue(dashboardId, view.id())) {
            return false;
        }
        return dashboardRepository.findById(dashboardId)
                .map(d -> d.getStatus() == DashboardStatus.ACTIVE)
                .orElse(false);
    }

    private BiDashboardRegistry requireActiveDashboard(String dashboardId) {
        BiDashboardRegistry dashboard = dashboardRepository.findById(dashboardId)
                .orElseThrow(() -> new DashboardNotFoundException(dashboardId));
        if (dashboard.getStatus() != DashboardStatus.ACTIVE) {
            throw new DashboardInactiveException(dashboardId);
        }
        return dashboard;
    }

    private ViewContext requireAccessibleView(String userId, Long viewId) {
        var view = dataViewCatalog.require(userId, viewId);
        return new ViewContext(view.id(), view.tableId(), view.functionUnitCode());
    }

    private DataViewAssignmentResponse toResponse(BiDataViewAssignment assignment) {
        return metadata.responses(List.of(assignment.getId())).get(0);
    }

    @Override
    public List<DataViewOptionResponse> listViews(Long functionUnitId, Long tableId) {
        return metadata.listViews(functionUnitId, tableId);
    }

    private record ViewContext(Long id, Long tableId, String functionUnitCode) {
    }

}
