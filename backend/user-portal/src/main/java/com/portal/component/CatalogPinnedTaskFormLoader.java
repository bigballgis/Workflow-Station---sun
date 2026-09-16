package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a Task Form from the Admin catalog snapshot pinned on the process instance.
 *
 * <p>BPMN {@code formId} comes from {@code sys_function_unit_contents} PROCESS rows; form JSON
 * from the FORM row whose {@code source_id} matches that id. Misses are empty, not live DW.
 *
 * <p>{@code null} from {@link #fetch} means the catalog query threw; empty means a definitive miss.
 */
@Slf4j
final class CatalogPinnedTaskFormLoader {

    private CatalogPinnedTaskFormLoader() {
    }

    static Map<String, Object> fetch(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                     String catalogId, String stageId) {
        if (jdbcTemplate == null || objectMapper == null
                || !StringUtils.hasText(catalogId) || !StringUtils.hasText(stageId)) {
            return Collections.emptyMap();
        }
        try {
            Long formId = resolveCatalogFormId(jdbcTemplate, catalogId.trim(), stageId.trim());
            if (formId == null) {
                return Collections.emptyMap();
            }
            return loadCatalogForm(jdbcTemplate, objectMapper, catalogId.trim(), formId);
        } catch (RuntimeException e) {
            log.debug("Catalog-pinned task form lookup failed for catalog {}, stage {}: {}",
                    catalogId, stageId, e.getMessage());
            return null;
        }
    }

    private static Long resolveCatalogFormId(JdbcTemplate jdbcTemplate, String catalogId, String stageId) {
        List<String> bpmnRows = jdbcTemplate.queryForList(
                """
                        SELECT content_data
                        FROM sys_function_unit_contents
                        WHERE function_unit_id = ? AND content_type = 'PROCESS'
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                String.class, catalogId);
        if (bpmnRows == null || bpmnRows.isEmpty() || !StringUtils.hasText(bpmnRows.get(0))) {
            return null;
        }
        return ChangeHistoryBpmnFormResolver.resolveTaskFormIdFromStored(bpmnRows.get(0), stageId);
    }

    private static Map<String, Object> loadCatalogForm(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                                       String catalogId, Long formId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                        SELECT content_name, content_data
                        FROM sys_function_unit_contents
                        WHERE function_unit_id = ? AND content_type = 'FORM' AND source_id = ?
                        LIMIT 1
                        """,
                (ResultSet rs, int rowNum) -> mapCatalogForm(rs, objectMapper),
                catalogId, String.valueOf(formId));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        return rows.get(0);
    }

    private static Map<String, Object> mapCatalogForm(ResultSet rs, ObjectMapper objectMapper)
            throws SQLException {
        Map<String, Object> form = new HashMap<>();
        form.put("formName", rs.getString("content_name"));
        form.put("configJson", parseObjectMap(objectMapper, rs.getString("content_data")));
        form.put("fieldPermissions", Collections.emptyMap());
        form.put("readOnly", false);
        return form;
    }

    private static Map<String, Object> parseObjectMap(ObjectMapper objectMapper, String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("Could not parse catalog form content_data: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
