package com.admin.bi.service.impl;

import com.admin.bi.component.DashboardRoleGate;
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
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRoleRepository;
import com.admin.service.UserBusinessUnitService;
import com.platform.security.entity.Role;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
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

    private static final String SYS_ADMIN_ROLE_CODE = "SYS_ADMIN";

    private final BiDataViewAssignmentRepository assignmentRepository;
    private final BiDashboardRegistryRepository dashboardRepository;
    private final JdbcTemplate jdbcTemplate;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final UserBusinessUnitService userBusinessUnitService;
    private final DashboardRoleGate dashboardRoleGate;
    // Controller audit can preload this row; refresh it after acquiring the database lock.
    private final EntityManager entityManager;

    private final BiDataViewMetadataRepository metadata;

    @Override
    @Transactional
    public DataViewAssignmentResponse updateEnabled(String id, boolean enabled) {
        BiDataViewAssignment assignment = assignmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AdminBusinessException("ASSIGNMENT_NOT_FOUND", "Assignment not found"));
        entityManager.refresh(assignment);
        if (enabled) {
            requireActiveDashboard(assignment.getDashboardId());
            metadata.requirePublishedView(assignment.getFunctionUnitId(), assignment.getTableId(), assignment.getViewId());
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

        List<BiDataViewAssignment> assignments = dashboardIds.stream()
                .map(dashboardId -> BiDataViewAssignment.builder()
                        .id(UUID.randomUUID().toString())
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
        return jdbcTemplate.query("""
                SELECT DISTINCT fu.id, fu.code, fu.name
                FROM dw_function_units fu
                INNER JOIN dw_main_table_view_configs v ON v.function_unit_id = fu.id
                INNER JOIN dw_table_definitions td
                        ON td.id = v.main_table_id AND td.function_unit_id = fu.id
                WHERE v.status = 'PUBLISHED' AND td.table_type IN ('MAIN', 'SUB')
                ORDER BY fu.name, fu.code
                """, (rs, rowNum) -> DataViewFunctionUnitOptionResponse.builder()
                .id(rs.getLong("id"))
                .code(rs.getString("code"))
                .name(rs.getString("name"))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataViewTableOptionResponse> listTables(Long functionUnitId) {
        if (functionUnitId == null) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT DISTINCT td.id, td.function_unit_id, td.table_name,
                       COALESCE(NULLIF(td.table_display_name, ''), td.table_name) AS table_display_name,
                       td.table_type
                FROM dw_table_definitions td
                INNER JOIN dw_main_table_view_configs v
                        ON v.main_table_id = td.id AND v.function_unit_id = td.function_unit_id
                WHERE td.function_unit_id = ? AND td.table_type IN ('MAIN', 'SUB')
                      AND v.status = 'PUBLISHED'
                ORDER BY table_display_name, td.table_name
                """, (rs, rowNum) -> DataViewTableOptionResponse.builder()
                .id(rs.getLong("id"))
                .functionUnitId(rs.getLong("function_unit_id"))
                .tableName(rs.getString("table_name"))
                .tableDisplayName(rs.getString("table_display_name"))
                .tableType(rs.getString("table_type"))
                .build(), functionUnitId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataViewDashboardResponse> getDashboardsForView(String userId, Long viewId) {
        ViewContext view = requireAccessibleView(userId, viewId);
        // Same RBAC-mapping role gate as the landing page: a dashboard that carries Superset roles is
        // listed only when the user's mapped Superset roles cover it (see DashboardRoleGate).
        DashboardRoleGate.Check roleGate = dashboardRoleGate.forUser(userId);
        List<BiDataViewAssignment> bindings = assignmentRepository.findByViewIdAndEnabledTrueOrderByCreatedAtAscIdAsc(view.id());
        Map<String, BiDashboardRegistry> dashboards = dashboardRepository.findAllById(
                bindings.stream().map(BiDataViewAssignment::getDashboardId).toList()).stream()
                .collect(Collectors.toMap(BiDashboardRegistry::getId, Function.identity()));
        return bindings.stream().map(a -> dashboards.get(a.getDashboardId()))
                .filter(Objects::nonNull)
                .filter(d -> d.getStatus() == DashboardStatus.ACTIVE)
                .filter(roleGate::allows)
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
                .map(dashboardRoleGate.forUser(userId)::allows)
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
        if (userId == null || userId.isBlank() || viewId == null) {
            throw new AccessDeniedException("A valid user and data view are required");
        }
        ViewContext view;
        try {
            view = jdbcTemplate.query("""
                    SELECT v.id, v.main_table_id, fu.code AS function_unit_code
                    FROM dw_main_table_view_configs v
                    INNER JOIN dw_function_units fu ON fu.id = v.function_unit_id
                    INNER JOIN dw_table_definitions td
                            ON td.id = v.main_table_id AND td.function_unit_id = v.function_unit_id
                    WHERE v.id = ? AND v.status = 'PUBLISHED'
                          AND td.table_type IN ('MAIN', 'SUB')
                    """, rs -> rs.next() ? new ViewContext(
                    rs.getLong("id"), rs.getLong("main_table_id"),
                    rs.getString("function_unit_code")) : null, viewId);
        } catch (DataAccessException e) {
            throw new AccessDeniedException("Unable to validate data view access", e);
        }
        if (view == null || !canUserSeeView(userId, view)) {
            throw new AccessDeniedException("Data view is not accessible to the current user");
        }
        return view;
    }

    /** Mirrors the User Portal's Function Unit + paired BU/Role view visibility rules. */
    private boolean canUserSeeView(String userId, ViewContext view) {
        List<String> allRoleIds = userRoleRepository.findAllRoleIdsByUserId(userId);
        if (allRoleIds == null) {
            allRoleIds = List.of();
        }
        List<Role> activeRoles = allRoleIds.isEmpty() ? List.of() : roleRepository.findAllById(allRoleIds).stream()
                .filter(r -> r != null && "ACTIVE".equals(r.getStatus()))
                .toList();
        if (activeRoles.stream().anyMatch(r -> SYS_ADMIN_ROLE_CODE.equals(r.getCode()))) {
            return true;
        }

        List<Role> businessRoles = activeRoles.stream()
                .filter(r -> "BU_BOUNDED".equals(r.getType()) || "BU_UNBOUNDED".equals(r.getType()))
                .toList();
        Set<String> businessRoleIds = new HashSet<>();
        Set<String> businessRoleKeys = new HashSet<>();
        for (Role role : businessRoles) {
            businessRoleIds.add(role.getId());
            businessRoleKeys.add(role.getCode() == null || role.getCode().isBlank()
                    ? role.getId() : role.getCode());
        }

        List<String> allowedFuRoleKeys = jdbcTemplate.query("""
                SELECT COALESCE(NULLIF(r.code, ''), a.target_id) AS role_key
                FROM sys_function_unit_access a
                INNER JOIN sys_function_units fu ON fu.id = a.function_unit_id
                LEFT JOIN sys_roles r ON r.id = a.target_id
                WHERE fu.code = ? AND fu.status = 'DEPLOYED' AND fu.enabled = TRUE
                      AND fu.is_active = TRUE AND a.target_type = 'ROLE'
                """, (rs, rowNum) -> rs.getString("role_key"), view.functionUnitCode());
        if (allowedFuRoleKeys.isEmpty()
                || allowedFuRoleKeys.stream().noneMatch(businessRoleKeys::contains)) {
            return false;
        }

        List<AccessRule> rules = jdbcTemplate.query("""
                SELECT target_type, target_id
                FROM dw_main_table_view_access
                WHERE view_config_id = ?
                """, (rs, rowNum) -> new AccessRule(
                rs.getString("target_type"), rs.getString("target_id")), view.id());
        Set<String> allowedRoleIds = new HashSet<>();
        Set<String> allowedBusinessUnitIds = new HashSet<>();
        for (AccessRule rule : rules) {
            if ("ROLE".equalsIgnoreCase(rule.targetType())) {
                allowedRoleIds.add(rule.targetId());
            } else if ("BUSINESS_UNIT".equalsIgnoreCase(rule.targetType())) {
                allowedBusinessUnitIds.add(rule.targetId());
            }
        }
        if (allowedRoleIds.isEmpty() || allowedBusinessUnitIds.isEmpty()) {
            return false;
        }
        boolean roleAllowed = allowedRoleIds.stream().anyMatch(businessRoleIds::contains);
        boolean buAllowed = userBusinessUnitService.getUserBusinessUnitIds(userId).stream()
                .anyMatch(allowedBusinessUnitIds::contains);
        return roleAllowed && buAllowed;
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

    private record AccessRule(String targetType, String targetId) {
    }
}
