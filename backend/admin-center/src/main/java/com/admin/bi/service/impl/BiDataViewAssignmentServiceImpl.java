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
import com.admin.service.UserRoleIdResolver;
import com.admin.service.UserBusinessUnitService;
import com.admin.entity.FunctionUnit;
import com.platform.common.functionunit.FunctionUnitVersionException;
import com.platform.security.entity.Role;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private final UserRoleIdResolver userRoleIdResolver;
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
            metadata.requirePublishedView(assignment.getFunctionUnitCode(), assignment.getViewName());
        }
        assignment.setEnabled(enabled);
        return toResponse(assignmentRepository.saveAndFlush(assignment));
    }

    @Override
    @Transactional
    public DataViewAssignmentResponse create(DataViewAssignmentRequest request) {
        requireActiveDashboard(request.getDashboardId());
        metadata.requirePublishedView(request.getFunctionUnitCode(), request.getViewName());
        if (assignmentRepository.existsByDashboardIdAndFunctionUnitCodeAndViewName(
                request.getDashboardId(), request.getFunctionUnitCode(), request.getViewName())) {
            throw new AdminBusinessException(
                    "DUPLICATE_DATA_VIEW_ASSIGNMENT",
                    "This dashboard is already assigned to the selected View");
        }

        BiDataViewAssignment saved = assignmentRepository.saveAndFlush(BiDataViewAssignment.builder()
                .id(UUID.randomUUID().toString())
                .dashboardId(request.getDashboardId())
                .functionUnitCode(request.getFunctionUnitCode())
                .viewName(request.getViewName())
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public List<DataViewAssignmentResponse> createBatch(DataViewAssignmentBatchRequest request) {
        metadata.requirePublishedView(request.getFunctionUnitCode(), request.getViewName());
        List<String> dashboardIds = new LinkedHashSet<>(request.getDashboardIds()).stream().toList();

        Set<String> existingDashboardIds = assignmentRepository
                .findByFunctionUnitCodeAndViewName(request.getFunctionUnitCode(), request.getViewName()).stream()
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
                        .functionUnitCode(request.getFunctionUnitCode())
                        .viewName(request.getViewName())
                        .enabled(request.getEnabled() == null || request.getEnabled())
                        .build())
                .toList();

        List<BiDataViewAssignment> saved = assignmentRepository.saveAllAndFlush(assignments);
        return metadata.responses(saved.stream().map(BiDataViewAssignment::getId).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DataViewAssignmentResponse> list(
            String dashboardTitle, String functionUnitCode, Boolean enabled, String viewName, Pageable pageable) {
        String normalizedTitle = dashboardTitle == null || dashboardTitle.isBlank()
                ? null : dashboardTitle.trim();
        Page<BiDataViewAssignment> page = assignmentRepository.findByFilters(
                normalizedTitle, functionUnitCode, enabled, viewName, pageable);
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
        metadata.requirePublishedView(request.getFunctionUnitCode(), request.getViewName());
        if (assignmentRepository.existsByDashboardIdAndFunctionUnitCodeAndViewNameAndIdNot(
                request.getDashboardId(), request.getFunctionUnitCode(), request.getViewName(), id)) {
            throw new AdminBusinessException(
                    "DUPLICATE_DATA_VIEW_ASSIGNMENT",
                    "This dashboard is already assigned to the selected View");
        }

        assignment.setDashboardId(request.getDashboardId());
        assignment.setFunctionUnitCode(request.getFunctionUnitCode());
        assignment.setViewName(request.getViewName());
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

    /**
     * Units whose active release publishes at least one View. A release whose Views cannot be read is left out and
     * logged rather than failing the whole list; writes and access checks on it still fail explicitly.
     */
    @Override
    @Transactional(readOnly = true)
    public List<DataViewFunctionUnitOptionResponse> listFunctionUnits() {
        List<DataViewFunctionUnitOptionResponse> options = new ArrayList<>();
        for (FunctionUnit release : metadata.activeReleases()) {
            try {
                if (!metadata.publishedViews(release).isEmpty()) {
                    options.add(DataViewFunctionUnitOptionResponse.builder()
                            .code(release.getCode()).name(release.getName()).build());
                }
            } catch (FunctionUnitVersionException e) {
                log.warn("Data View options skip release {} ({} {}): {}", release.getId(), release.getCode(),
                        release.getVersion(), e.getMessage());
            }
        }
        options.sort(Comparator.comparing(DataViewFunctionUnitOptionResponse::name,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(DataViewFunctionUnitOptionResponse::code));
        return options;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataViewTableOptionResponse> listTables(String functionUnitCode) {
        Map<String, DataViewTableOptionResponse> tables = new LinkedHashMap<>();
        for (BiDataViewMetadataRepository.ReleasedView view : activeViews(functionUnitCode)) {
            tables.putIfAbsent(view.tableName(), DataViewTableOptionResponse.builder()
                    .tableName(view.tableName())
                    .tableDisplayName(view.tableDisplayName())
                    .tableType(view.tableType())
                    .build());
        }
        return tables.values().stream()
                .sorted(Comparator.comparing(DataViewTableOptionResponse::tableDisplayName)
                        .thenComparing(DataViewTableOptionResponse::tableName))
                .toList();
    }

    private List<BiDataViewMetadataRepository.ReleasedView> activeViews(String functionUnitCode) {
        return metadata.activeReleases().stream()
                .filter(release -> release.getCode().equals(functionUnitCode))
                .findFirst()
                .map(metadata::publishedViews)
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataViewDashboardResponse> getDashboardsForView(String userId, String functionUnitCode, String viewName) {
        BiDataViewMetadataRepository.ReleasedView view = requireAccessibleView(userId, functionUnitCode, viewName);
        // Same RBAC-mapping role gate as the landing page: a dashboard that carries Superset roles is
        // listed only when the user's mapped Superset roles cover it (see DashboardRoleGate).
        DashboardRoleGate.Check roleGate = dashboardRoleGate.forUser(userId);
        List<BiDataViewAssignment> bindings = assignmentRepository
                .findByFunctionUnitCodeAndViewNameAndEnabledTrueOrderByCreatedAtAscIdAsc(
                        view.functionUnitCode(), view.viewName());
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
    public boolean canAccessDashboardForView(String userId, String dashboardId, String functionUnitCode,
                                             String viewName) {
        BiDataViewMetadataRepository.ReleasedView view = requireAccessibleView(userId, functionUnitCode, viewName);
        if (!assignmentRepository.existsByDashboardIdAndFunctionUnitCodeAndViewNameAndEnabledTrue(
                dashboardId, view.functionUnitCode(), view.viewName())) {
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

    private BiDataViewMetadataRepository.ReleasedView requireAccessibleView(
            String userId, String functionUnitCode, String viewName) {
        if (userId == null || userId.isBlank() || functionUnitCode == null || functionUnitCode.isBlank()
                || viewName == null || viewName.isBlank()) {
            throw new AccessDeniedException("A valid user and data view are required");
        }
        BiDataViewMetadataRepository.ReleasedView view = metadata.publishedView(functionUnitCode, viewName)
                .orElse(null);
        if (view == null || !canUserSeeView(userId, view)) {
            throw new AccessDeniedException("Data view is not accessible to the current user");
        }
        return view;
    }

    /**
     * Mirrors the User Portal's Function Unit + paired BU/Role view visibility rules. A System Administrator is
     * checked like everyone else (view-access-control): no bypass.
     */
    private boolean canUserSeeView(String userId, BiDataViewMetadataRepository.ReleasedView view) {
        List<String> allRoleIds = userRoleIdResolver.allRoleIds(userId);
        if (allRoleIds == null) {
            allRoleIds = List.of();
        }
        List<Role> activeRoles = allRoleIds.isEmpty() ? List.of() : roleRepository.findAllById(allRoleIds).stream()
                .filter(r -> r != null && "ACTIVE".equals(r.getStatus()))
                .toList();
        List<Role> businessRoles = activeRoles.stream()
                .filter(r -> "BU_BOUNDED".equals(r.getType()))
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

        Set<String> allowedRoleIds = new HashSet<>();
        Set<String> allowedBusinessUnitIds = new HashSet<>();
        for (Map<String, Object> rule : view.accessRules()) {
            String targetType = rule.get("targetType") == null ? null : String.valueOf(rule.get("targetType"));
            String targetId = rule.get("targetId") == null ? null : String.valueOf(rule.get("targetId"));
            if ("ROLE".equalsIgnoreCase(targetType)) {
                allowedRoleIds.add(targetId);
            } else if ("BUSINESS_UNIT".equalsIgnoreCase(targetType)) {
                allowedBusinessUnitIds.add(targetId);
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
    public List<DataViewOptionResponse> listViews(String functionUnitCode, String tableName) {
        return activeViews(functionUnitCode).stream()
                .filter(view -> view.tableName().equals(tableName))
                .map(view -> new DataViewOptionResponse(view.viewName()))
                .toList();
    }
}
