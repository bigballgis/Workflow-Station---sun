package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.util.ApiResponseBodyUnwrap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task Form 定义加载协作类。
 * 单一职责：按 stageId（taskDefinitionKey）解析 Task Form 定义——优先本地共享 PostgreSQL（dw_form_stage_bindings），
 * 不可达时回退 developer-workstation HTTP。行为与拆分前 {@link TaskFormComponent} 中的对应私有方法逐字一致。
 *
 * <p>{@code developerWorkstationUrl} 作为入参传入而非注入字段：门面 {@link TaskFormComponent} 通过
 * {@code @Value} 持有该配置且测试以反射方式覆盖它，故由门面在调用时透传，避免该协作类持有与门面不同步的副本。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskFormDefinitionLoader {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Loads Task Form definition by stageId (taskDefinitionKey), scoped to the process instance's function unit.
     * Prefers developer-workstation; falls back to local {@code dw_form_stage_bindings} when unreachable (shared PostgreSQL with DW).
     *
     * <p>The function unit scope is required for correctness, not just precision: BPMN node ids are unique only
     * within one process, and AI-generated processes reuse readable ids such as {@code UserTask_Approve}, so the
     * same {@code stageId} legitimately exists in several function units.</p>
     */
    public Map<String, Object> fetchTaskFormByStageId(String stageId, String processInstanceId,
                                                      String developerWorkstationUrl) {
        String functionUnitCode = resolveFunctionUnitCode(processInstanceId);
        // Try local DB first (millisecond-level) — avoids expensive cross-container HTTP call.
        Map<String, Object> fromDb = fetchTaskFormFromLocalDw(stageId, functionUnitCode);
        if (fromDb != null) {
            if (fromDb.isEmpty()) {
                // Definitive negative: local DB queried successfully but found no binding.
                // Since both services share the same PostgreSQL, HTTP fallback would also miss — skip it.
                log.debug("No task form binding found in local DB for stage '{}', skipping remote lookup", stageId);
                return null;
            }
            log.debug("Resolved task form for stage '{}' from local dw_form_stage_bindings", stageId);
            return fromDb;
        }
        // fromDb == null means local query threw an exception (e.g. table doesn't exist).
        // Fallback: HTTP call to developer-workstation.
        return fetchTaskFormFromDeveloperWorkstation(stageId, functionUnitCode, developerWorkstationUrl);
    }

    /**
     * Resolves the DW function unit code owning a process instance, or {@code null} when it cannot be
     * matched to a {@code dw_function_units} row.
     *
     * <p>Mirrors the join already used by {@code ChangeHistorySubmissionFilter}: the pinned
     * {@code function_unit_code} wins, with {@code process_definition_key} as the pre-pin fallback.
     * Returning {@code null} rather than the raw column value is what lets callers distinguish
     * "this unit has no binding for the stage" (definitive) from "no unit resolved" (must stay unscoped).</p>
     */
    private String resolveFunctionUnitCode(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return null;
        }
        try {
            List<String> codes = jdbcTemplate.queryForList(
                    """
                            SELECT fu.code
                            FROM up_process_instance pi
                            INNER JOIN dw_function_units fu
                                ON fu.code = COALESCE(NULLIF(pi.function_unit_code, ''), pi.process_definition_key)
                            WHERE pi.id = ?
                            LIMIT 1
                            """,
                    String.class, processInstanceId.trim());
            if (codes.isEmpty()) {
                log.warn("No function unit resolved for process instance {}; task form lookup stays unscoped "
                        + "and may match a stage id bound in another function unit", processInstanceId);
                return null;
            }
            return codes.get(0);
        } catch (Exception e) {
            log.debug("Function unit lookup failed for process instance {}: {}", processInstanceId, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchTaskFormFromDeveloperWorkstation(String stageId, String functionUnitCode,
                                                                      String developerWorkstationUrl) {
        try {
            String base = normalizeDeveloperWorkstationBase(developerWorkstationUrl);
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(base + "/api/v1/form-stage-bindings")
                    .queryParam("stageId", stageId);
            if (functionUnitCode != null && !functionUnitCode.isBlank()) {
                builder.queryParam("functionUnitCode", functionUnitCode);
            }
            String url = builder
                    .encode(StandardCharsets.UTF_8)
                    .toUriString();
            log.debug("Fetching Task Form definition from: {}", url);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) {
                return null;
            }
            Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response);
            if (payload.containsKey("form")) {
                return (Map<String, Object>) payload.get("form");
            }
            if (response.containsKey("form")) {
                return (Map<String, Object>) response.get("form");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Task Form definition for stage {}: {}", stageId, e.getMessage());
        }
        return null;
    }

    /**
     * Local lookup when sharing DB with developer-workstation (avoids localhost misconfiguration inside containers).
     *
     * @param functionUnitCode owning function unit; {@code null} only when it could not be resolved, in which
     *                         case the lookup stays global and warns on cross-unit ambiguity
     */
    private Map<String, Object> fetchTaskFormFromLocalDw(String stageId, String functionUnitCode) {
        if (stageId == null || stageId.isBlank()) {
            return null;
        }
        String stage = stageId.trim();
        try {
            if (functionUnitCode != null && !functionUnitCode.isBlank()) {
                List<Map<String, Object>> rows = jdbcTemplate.query(
                        """
                                SELECT fd.form_name, fd.config_json, fd.field_permissions, b.read_only
                                FROM dw_form_stage_bindings b
                                INNER JOIN dw_form_definitions fd ON fd.id = b.form_id
                                INNER JOIN dw_function_units fu ON fu.id = fd.function_unit_id
                                WHERE b.stage_id = ? AND fu.code = ?
                                ORDER BY fd.id DESC
                                LIMIT 1
                                """,
                        (ResultSet rs, int rowNum) -> mapRowToTaskFormDefinition(rs),
                        stage, functionUnitCode.trim());
                // Empty here is a definitive negative: the unit is known and binds no form to this
                // stage. Retrying unscoped would serve another unit's form.
                return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
            }
            return fetchTaskFormUnscoped(stage);
        } catch (Exception e) {
            log.debug("Local dw_form_stage_bindings lookup failed for stage {}: {}", stage, e.getMessage());
            return null;
        }
    }

    /**
     * Global stage lookup used only when no function unit could be resolved. Deterministic (highest form id
     * wins) and warns when the stage id is bound in more than one unit, so the ambiguity is visible in logs
     * instead of silently deciding which unit's form a user sees.
     */
    private Map<String, Object> fetchTaskFormUnscoped(String stage) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                        SELECT fd.form_name, fd.config_json, fd.field_permissions, b.read_only
                        FROM dw_form_stage_bindings b
                        INNER JOIN dw_form_definitions fd ON fd.id = b.form_id
                        WHERE b.stage_id = ?
                        ORDER BY fd.id DESC
                        """,
                (ResultSet rs, int rowNum) -> mapRowToTaskFormDefinition(rs),
                stage);
        if (rows.size() > 1) {
            log.warn("Stage id '{}' is bound in {} function units and none could be resolved for this process; "
                    + "resolving to the newest form '{}'", stage, rows.size(), rows.get(0).get("formName"));
        }
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    private Map<String, Object> mapRowToTaskFormDefinition(ResultSet rs) throws SQLException {
        Map<String, Object> form = new HashMap<>();
        form.put("formName", rs.getString("form_name"));
        form.put("configJson", readJsonObjectMap(rs, "config_json"));
        form.put("fieldPermissions", readJsonStringMap(rs, "field_permissions"));
        form.put("readOnly", rs.getBoolean("read_only"));
        return form;
    }

    private Map<String, Object> readJsonObjectMap(ResultSet rs, String col) throws SQLException {
        String raw = rs.getString(col);
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("Could not parse JSON object column {}: {}", col, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, String> readJsonStringMap(ResultSet rs, String col) throws SQLException {
        String raw = rs.getString(col);
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.debug("Could not parse JSON string map column {}: {}", col, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static String trimTrailingSlash(String base) {
        if (base == null || base.isEmpty()) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** Supports {@code DEVELOPER_WORKSTATION_URL} as host:port only or with a mistaken {@code /api/v1} suffix. */
    private static String normalizeDeveloperWorkstationBase(String url) {
        String b = trimTrailingSlash(url != null ? url : "");
        if (b.endsWith("/api/v1")) {
            return trimTrailingSlash(b.substring(0, b.length() - "/api/v1".length()));
        }
        return b;
    }
}
