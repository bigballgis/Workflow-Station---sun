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
import java.util.Collections;
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
 * 配置以 JSONB 存于 {@code dw_table_definitions.request_id_config}。
 *
 * <p>运行时按配置的 {@code fieldNames} 顺序,从流程变量(扁平 {@code fieldName: value})取值,
 * 用 {@code separator} 拼接,空值字段跳过。主表未配置 → 返回 {@code null}(前端列表渲染 '-')。
 *
 * <p>批量场景:同一 {@code functionUnitCode} 的所有实例共享同一份 config,故按 code 缓存解析结果,
 * 避免逐行查库(无 N+1)。
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

    private static final long SPEC_TTL_MS = 5 * 60 * 1000L;
    private static final int MAX_CACHED_SPECS = 128;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ProcessInstanceRepository processInstanceRepository;
    private final Map<String, CachedSpec> specCache = Collections.synchronizedMap(
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedSpec> eldest) {
                    return size() > MAX_CACHED_SPECS;
                }
            });

    /** 解析后的 Request ID 配置;{@code EMPTY} 表示该功能单元主表未配置(可缓存的负结果)。 */
    private record RequestIdSpec(List<String> fieldNames, String separator) {
        static final RequestIdSpec EMPTY = new RequestIdSpec(Collections.emptyList(), "");

        boolean isConfigured() {
            return fieldNames != null && !fieldNames.isEmpty();
        }
    }

    /** Public Request ID config shipped to the portal frontend so it can recompute the field live. */
    public record RequestIdConfigView(List<String> fieldNames, String separator) {}

    /**
     * 解析功能单元主表的 Request ID 配置并以可序列化形式返回(供前端实时拼接)。
     * 未配置 → 返回 {@code null}(前端不显示该字段值来源)。
     */
    public RequestIdConfigView resolveConfigView(String functionUnitCode) {
        if (functionUnitCode == null || functionUnitCode.isBlank()) {
            return null;
        }
        RequestIdSpec spec = resolveSpec(functionUnitCode);
        if (!spec.isConfigured()) {
            return null;
        }
        return new RequestIdConfigView(spec.fieldNames(), spec.separator());
    }

    /**
     * 给一个功能单元主表的 config + 一行流程变量,拼出 Request ID。
     * 主表未配置或所有字段都为空 → 返回 {@code null}。
     */
    public String buildRequestId(String functionUnitCode, Map<String, Object> variables) {
        if (functionUnitCode == null || functionUnitCode.isBlank() || variables == null) {
            return null;
        }
        return applySpec(resolveSpec(functionUnitCode), variables);
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
     */
    public void stampRequestId(String functionUnitCode, Map<String, Object> variables) {
        if (variables == null) {
            return;
        }
        String requestId = buildRequestId(functionUnitCode, variables);
        if (requestId != null) {
            variables.put(REQUEST_ID_FIELD, requestId);
        } else {
            variables.remove(REQUEST_ID_FIELD);
        }
    }

    /**
     * 批量构建:返回 {@code functionUnitCode → spec} 的解析缓存,供调用方在循环里复用,避免逐行查库。
     * 调用方通常这样用:先收集本页所有 code,调 {@link #resolveSpecs}, 然后对每行调 {@link #buildRequestId(SpecCache, String, Map)}。
     */
    public SpecCache resolveSpecs(Iterable<String> functionUnitCodes) {
        Map<String, RequestIdSpec> byCode = new LinkedHashMap<>();
        if (functionUnitCodes != null) {
            for (String code : functionUnitCodes) {
                if (code == null || code.isBlank() || byCode.containsKey(code)) {
                    continue;
                }
                byCode.put(code, resolveSpec(code));
            }
        }
        return new SpecCache(byCode);
    }

    /**
     * 为一页任务(To-Do / 已完成)填充 {@link TaskInfo#getRequestId()} 与 Function Unit。
     *
     * <p>引擎任务负载不含主表字段值,故按 {@code processInstanceId} 单次批量取关联流程实例的
     * variables(已完成流程的 variables 冻结在 {@code up_process_instance}),再按主表表级配置
     * (每个功能单元只解析一次)拼出 Request ID,并带上发起时钉死的 function unit code/name。无 N+1。
     */
    public void enrichTaskRequestIds(List<TaskInfo> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Map<String, ProcessInstance> instancesById = loadInstancesByTask(tasks);
        if (instancesById.isEmpty()) {
            return;
        }
        Set<String> functionUnitCodes = instancesById.values().stream()
                .map(ProcessInstance::getFunctionUnitCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        SpecCache specs = resolveSpecs(functionUnitCodes);
        Map<String, String> names = loadFunctionUnitNames(functionUnitCodes);
        applyRequestIdAndFunctionUnit(tasks, instancesById, specs, names);
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
            SpecCache specs,
            Map<String, String> names) {
        for (TaskInfo task : tasks) {
            ProcessInstance pi = instancesById.get(task.getProcessInstanceId());
            if (pi == null) {
                continue;
            }
            String code = pi.getFunctionUnitCode();
            task.setFunctionUnitCode(code);
            if (code != null && !code.isBlank()) {
                String name = names.get(code);
                task.setFunctionUnitName(name != null && !name.isBlank() ? name : null);
            }
            task.setRequestId(buildRequestId(specs, code, pi.getVariables()));
        }
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

    /** 用预解析的缓存拼接(零查库)。 */
    public String buildRequestId(SpecCache cache, String functionUnitCode, Map<String, Object> variables) {
        if (cache == null || functionUnitCode == null || variables == null) {
            return null;
        }
        RequestIdSpec spec = cache.byCode.getOrDefault(functionUnitCode, RequestIdSpec.EMPTY);
        return applySpec(spec, variables);
    }

    /** 预解析缓存句柄(按 functionUnitCode 键)。 */
    public static final class SpecCache {
        private final Map<String, RequestIdSpec> byCode;

        private SpecCache(Map<String, RequestIdSpec> byCode) {
            this.byCode = byCode;
        }
    }

    // ── 内部 ────────────────────────────────────────────────────────────────

    private String applySpec(RequestIdSpec spec, Map<String, Object> variables) {
        if (spec == null || !spec.isConfigured()) {
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

    /**
     * 通过 functionUnitCode 解析 PRIMARY MAIN 表的 request_id_config。
     * 走 PROCESS 表单的 PRIMARY 绑定 → dw_table_definitions,以拿到该表单真正的主表。
     * Config is per FU, not per row; a short TTL avoids repeating the join on every page/filter.
     */
    private RequestIdSpec resolveSpec(String functionUnitCode) {
        String code = functionUnitCode.trim();
        long now = System.currentTimeMillis();
        CachedSpec cached = specCache.get(code);
        if (cached != null && now - cached.cachedAt < SPEC_TTL_MS) {
            return cached.spec;
        }
        try {
            RequestIdSpec spec = loadSpec(code);
            specCache.put(code, new CachedSpec(spec, now));
            return spec;
        } catch (Exception e) {
            // 表不可达不应阻断列表;退化为未配置。不写入缓存,下次筛选/翻页会重试。
            log.debug("Could not resolve Request ID config for functionUnitCode={}: {}", code, e.getMessage());
            return RequestIdSpec.EMPTY;
        }
    }

    private RequestIdSpec loadSpec(String functionUnitCode) {
        List<String> rows = jdbcTemplate.query(
                """
                        SELECT td.request_id_config::text AS cfg
                        FROM dw_function_units fu
                        INNER JOIN dw_form_definitions fd
                            ON fd.function_unit_id = fu.id AND fd.form_type = 'PROCESS'
                        INNER JOIN dw_form_table_bindings ftb
                            ON ftb.form_id = fd.id AND ftb.binding_type = 'PRIMARY'
                        INNER JOIN dw_table_definitions td
                            ON td.id = ftb.table_id
                        WHERE fu.code = ?
                        ORDER BY ftb.sort_order NULLS LAST, ftb.id
                        LIMIT 1
                        """,
                (rs, rowNum) -> rs.getString("cfg"),
                functionUnitCode);
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            return RequestIdSpec.EMPTY;
        }
        return parseSpec(rows.get(0));
    }

    private record CachedSpec(RequestIdSpec spec, long cachedAt) {
    }

    private RequestIdSpec parseSpec(String json) {
        try {
            Map<String, Object> cfg = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Object fieldNamesRaw = cfg.get("fieldNames");
            if (!(fieldNamesRaw instanceof List<?> list) || list.isEmpty()) {
                return RequestIdSpec.EMPTY;
            }
            List<String> fieldNames = list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .filter(s -> !s.isBlank())
                    .toList();
            if (fieldNames.isEmpty()) {
                return RequestIdSpec.EMPTY;
            }
            Object sepRaw = cfg.get("separator");
            String separator = sepRaw == null ? "" : String.valueOf(sepRaw);
            return new RequestIdSpec(fieldNames, separator);
        } catch (Exception e) {
            log.debug("Malformed request_id_config json: {}", e.getMessage());
            return RequestIdSpec.EMPTY;
        }
    }
}
