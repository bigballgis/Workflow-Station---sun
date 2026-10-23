package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.TaskInfo;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 计算一条 request 的人类可读标识(Request ID)。
 *
 * <p>Request ID 是主表(PRIMARY MAIN)的表级配置:开发者在 Table Design 上选取若干主表字段、
 * 定义顺序、用分隔符拼成(如 {@code dept}-{@code year}-{@code seq} → {@code HR-2026-001})。
 * 运行时配置取自请求钉住的发布版本(主表 DATA_TABLE 快照的 {@code requestIdConfig})。
 *
 * <p>每次写入 {@code variables} 时按配置的 {@code fieldNames} 顺序,从流程变量(扁平
 * {@code fieldName: value})取值,用 {@code separator} 拼接(空值字段跳过),落到
 * {@link #REQUEST_ID_FIELD}。列表、详情、审计都读这份落库值,不再逐页重算。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestIdEnricher {

    /**
     * 保留 field 名:Request ID 作为派生只读 synthetic 字段在 main form 上的 field code,
     * 与 developer-workstation 前端的 {@code REQUEST_ID_FIELD} 一致(双下划线前缀保留)。
     */
    public static final String REQUEST_ID_FIELD = "__request_id";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ProcessInstanceRepository processInstanceRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.platform.common.functionunit.CatalogSnapshotReader catalogSnapshots;

    /** Public Request ID config shipped to the portal frontend so it can recompute the field live. */
    public record RequestIdConfigView(List<String> fieldNames, String separator) {}

    /**
     * The Request ID a request carries: the value stamped on its variables from its own release at its
     * last write. {@code null} when its release configures none (lists render '-').
     */
    public static String storedRequestId(Map<String, Object> variables) {
        Object stored = variables == null ? null : variables.get(REQUEST_ID_FIELD);
        return stored == null ? null : String.valueOf(stored);
    }

    /**
     * 按主表配置重算 Request ID 并**覆盖**客户端传来的值,用于任何要落库 {@code variables} 的写入路径。
     *
     * <p>Request ID 是只读派生字段,值的所有权在服务端:客户端可能不传、可能用改配置前的旧配置算、
     * 也可能在提交时才分配的自增主键到位前就算好了,这些都不允许落库。未配置或参与字段全空时
     * **移除**该键,避免客户端传的值比它所依据的配置活得更久。
     *
     * <p>调用点约定:放在审计字段填充与计算字段重算之后、{@code setVariables} 之前,
     * 此时所有参与拼接的字段都已是终值。
     *
     * @throws com.platform.common.functionunit.FunctionUnitVersionException VERSION_RECOVERY_REQUIRED when
     *         the variables do not name the request's release
     */
    public void stampRequestId(Map<String, Object> variables) {
        if (variables == null) {
            return;
        }
        Object catalogId = variables.get("functionUnitId");
        if (catalogId == null) {
            throw new com.platform.common.functionunit.FunctionUnitVersionException("VERSION_RECOVERY_REQUIRED", null);
        }
        String requestId = buildCatalogRequestId(String.valueOf(catalogId), variables);
        if (requestId != null) {
            variables.put(REQUEST_ID_FIELD, requestId);
        } else {
            variables.remove(REQUEST_ID_FIELD);
        }
    }

    public String buildCatalogRequestId(String catalogId, Map<String, Object> variables) {
        RequestIdConfigView config = resolveCatalogConfigView(catalogId);
        return config == null ? null : applySpec(config, variables);
    }

    public RequestIdConfigView resolveCatalogConfigView(String catalogId) {
        var reader = catalogSnapshots;
        if (reader == null) reader = new com.platform.common.functionunit.CatalogSnapshotReader(jdbcTemplate, objectMapper);
        var tables = reader.contents(catalogId, "DATA_TABLE").stream()
                .filter(t -> "MAIN".equals(t.get("tableType"))).toList();
        if (tables.size() > 1) throw com.platform.common.functionunit.CatalogSnapshotReader.invalid(catalogId);
        if (tables.isEmpty() || tables.get(0).get("requestIdConfig") == null) return null;
        Map<String, Object> config = objectMapper.convertValue(tables.get(0).get("requestIdConfig"),
                new TypeReference<Map<String, Object>>() { });
        if (!(config.get("fieldNames") instanceof List<?> names)) {
            throw com.platform.common.functionunit.CatalogSnapshotReader.invalid(catalogId);
        }
        return new RequestIdConfigView(names.stream().map(String::valueOf).toList(),
                config.get("separator") == null ? "" : String.valueOf(config.get("separator")));

    }

    /**
     * 为一页任务(To-Do / 已完成)填充 {@link TaskInfo#getRequestId()} 与 Function Unit。
     *
     * <p>引擎任务负载不含主表字段值,故按 {@code processInstanceId} 单次批量取关联流程实例,读其
     * variables 上落库的 Request ID(被调用单元的任务取调用方请求的),并带上发起时钉死的
     * function unit code/name。无 N+1。
     */
    public void enrichTaskRequestIds(List<TaskInfo> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Map<String, ProcessInstance> instancesById = loadInstancesByTask(tasks);
        if (instancesById.isEmpty()) {
            return;
        }
        Map<String, ProcessInstance> requestOf = loadRequestRoots(instancesById);
        Set<String> functionUnitCodes = new LinkedHashSet<>();
        for (Map<String, ProcessInstance> source : List.of(instancesById, requestOf)) {
            source.values().stream()
                    .map(ProcessInstance::getFunctionUnitCode)
                    .filter(code -> code != null && !code.isBlank())
                    .forEach(functionUnitCodes::add);
        }
        Map<String, String> names = loadFunctionUnitNames(functionUnitCodes);
        applyRequestIdAndFunctionUnit(tasks, instancesById, requestOf, names);
    }

    /** How many levels of Function Unit calls are walked up to find the request a task belongs to. */
    private static final int MAX_CALL_DEPTH = 5;

    /**
     * Instance id → the top-level request it is part of, for instances started by a Function Unit
     * call. A called unit's task is work on the caller's request, so it carries that request's ID —
     * the called unit's own main table normally has no Request ID configured, and even when it has,
     * the user looks for the request they submitted. One batched query per call level, no N+1.
     */
    private Map<String, ProcessInstance> loadRequestRoots(Map<String, ProcessInstance> instancesById) {
        Map<String, String> pendingParentOf = new LinkedHashMap<>();
        instancesById.values().forEach(pi -> {
            if (pi.getParentProcessInstanceId() != null) {
                pendingParentOf.put(pi.getId(), pi.getParentProcessInstanceId());
            }
        });
        if (pendingParentOf.isEmpty()) {
            return Map.of();
        }
        Map<String, ProcessInstance> rootOf = new LinkedHashMap<>();
        for (int depth = 0; depth < MAX_CALL_DEPTH && !pendingParentOf.isEmpty(); depth++) {
            Map<String, ProcessInstance> parents = processInstanceRepository
                    .findAllById(new LinkedHashSet<>(pendingParentOf.values())).stream()
                    .collect(Collectors.toMap(ProcessInstance::getId, pi -> pi, (a, b) -> a));
            Map<String, String> next = new LinkedHashMap<>();
            pendingParentOf.forEach((childId, parentId) -> {
                ProcessInstance parent = parents.get(parentId);
                if (parent == null) {
                    return;
                }
                rootOf.put(childId, parent);
                if (parent.getParentProcessInstanceId() != null) {
                    next.put(childId, parent.getParentProcessInstanceId());
                }
            });
            pendingParentOf.clear();
            pendingParentOf.putAll(next);
        }
        return rootOf;
    }

    private Map<String, ProcessInstance> loadInstancesByTask(List<TaskInfo> tasks) {
        Set<String> processInstanceIds = tasks.stream()
                .map(TaskInfo::getProcessInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (processInstanceIds.isEmpty()) {
            return Map.of();
        }
        return processInstanceRepository.findAllById(processInstanceIds).stream()
                .filter(pi -> pi.getId() != null)
                .collect(Collectors.toMap(ProcessInstance::getId, pi -> pi, (a, b) -> a));
    }

    private void applyRequestIdAndFunctionUnit(
            List<TaskInfo> tasks,
            Map<String, ProcessInstance> instancesById,
            Map<String, ProcessInstance> requestOf,
            Map<String, String> names) {
        for (TaskInfo task : tasks) {
            ProcessInstance pi = instancesById.get(task.getProcessInstanceId());
            if (pi == null) {
                continue;
            }
            String code = pi.getFunctionUnitCode();
            task.setFunctionUnitCode(code);
            task.setFunctionUnitCatalogId(pi.getFunctionUnitCatalogId());
            if (code != null && !code.isBlank()) {
                String name = names.get(code);
                task.setFunctionUnitName(name != null && !name.isBlank() ? name : null);
            }
            ProcessInstance request = requestOf.getOrDefault(pi.getId(), pi);
            task.setRequestId(storedRequestId(request.getVariables()));
        }
    }

    /**
     * Release version of each catalog row ({@code sys_function_units.id} → {@code version}, e.g.
     * "1.0.23"), the number Developer Workstation Version Management shows. One query.
     */
    public Map<String, String> loadCatalogVersions(Collection<String> catalogIds) {
        List<String> args = catalogIds == null ? List.of() : catalogIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (args.isEmpty()) {
            return Map.of();
        }
        String placeholders = args.stream().map(c -> "?").collect(Collectors.joining(","));
        Map<String, String> versions = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT id, version FROM sys_function_units WHERE id IN (" + placeholders + ")",
                (RowCallbackHandler) rs -> versions.put(rs.getString("id"), rs.getString("version")),
                args.toArray());
        return versions;
    }

    /**
     * Batch catalog names for the page's function unit codes. Missing names leave the cell
     * on {@code functionUnitCode}.
     */
    private Map<String, String> loadFunctionUnitNames(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        List<String> args = new ArrayList<>(codes);
        String placeholders = args.stream().map(c -> "?").collect(Collectors.joining(","));
        Map<String, String> map = new LinkedHashMap<>();
        try {
            RowCallbackHandler handler = rs -> {
                String code = rs.getString("code");
                String name = rs.getString("name");
                if (code != null && name != null && !name.isBlank()) {
                    map.putIfAbsent(code, name.trim());
                }
            };
            jdbcTemplate.query(
                    "SELECT code, name FROM sys_function_units WHERE code IN (" + placeholders + ")",
                    handler,
                    args.toArray());
        } catch (Exception e) {
            // FALLBACK(ux): show functionUnitCode when catalog name lookup fails
            log.debug("Could not resolve function unit names: {}", e.getMessage());
        }
        return map;
    }

    // ── 内部 ────────────────────────────────────────────────────────────────

    private String applySpec(RequestIdConfigView spec, Map<String, Object> variables) {
        if (spec.fieldNames().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String fieldName : spec.fieldNames()) {
            Object raw = variables.get(fieldName);
            String value = raw == null ? "" : String.valueOf(raw).trim();
            if (value.isEmpty()) {
                continue; // 空字段跳过,避免出现 HR--001
            }
            if (!first) {
                sb.append(spec.separator() == null ? "" : spec.separator());
            }
            sb.append(value);
            first = false;
        }
        String result = sb.toString();
        return result.isEmpty() ? null : result;
    }
}
