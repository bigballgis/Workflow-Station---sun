package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogPinnedTaskFormLoaderTest {

    private static final String CATALOG = "cat-leave";
    private static final String STAGE = "UserTask_Approve";

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void snapshotFormWinsOverWhateverLiveDwWouldHaveReturned() {
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("content_type = 'PROCESS'")),
                eq(String.class), eq(CATALOG)))
                .thenReturn(List.of(bpmn("42")));
        when(jdbcTemplate.query(argThat(sql -> sql != null && sql.contains("content_type = 'FORM'")),
                any(RowMapper.class), eq(CATALOG), eq("42")))
                .thenReturn(List.of(Map.of(
                        "formName", "Pinned Leave",
                        "configJson", Map.of("snapshot", true),
                        "fieldPermissions", Map.of(),
                        "readOnly", false)));

        Map<String, Object> form = CatalogPinnedTaskFormLoader.fetch(
                jdbcTemplate, objectMapper, CATALOG, STAGE);

        assertThat(form).containsEntry("formName", "Pinned Leave");
        assertThat(form.get("configJson")).isEqualTo(Map.of("snapshot", true));
    }

    @Test
    void missingProcessContentIsADefinitiveMiss() {
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("content_type = 'PROCESS'")),
                eq(String.class), eq(CATALOG)))
                .thenReturn(List.of());

        assertThat(CatalogPinnedTaskFormLoader.fetch(jdbcTemplate, objectMapper, CATALOG, STAGE))
                .isEmpty();
    }

    @Test
    void bpmnFormIdWithoutCatalogFormIsADefinitiveMiss() {
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("content_type = 'PROCESS'")),
                eq(String.class), eq(CATALOG)))
                .thenReturn(List.of(bpmn("42")));
        when(jdbcTemplate.query(argThat(sql -> sql != null && sql.contains("content_type = 'FORM'")),
                any(RowMapper.class), eq(CATALOG), eq("42")))
                .thenReturn(List.of());

        assertThat(CatalogPinnedTaskFormLoader.fetch(jdbcTemplate, objectMapper, CATALOG, STAGE))
                .isEmpty();
    }

    @Test
    void catalogQueryFailureReturnsNullNotEmpty() {
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("content_type = 'PROCESS'")),
                eq(String.class), eq(CATALOG)))
                .thenThrow(new IllegalStateException("relation does not exist"));

        assertThat(CatalogPinnedTaskFormLoader.fetch(jdbcTemplate, objectMapper, CATALOG, STAGE))
                .isNull();
    }

    @Test
    void blankCatalogOrStageIsAMissNotALiveFallback() {
        assertThat(CatalogPinnedTaskFormLoader.fetch(jdbcTemplate, objectMapper, "  ", STAGE)).isEmpty();
        assertThat(CatalogPinnedTaskFormLoader.fetch(jdbcTemplate, objectMapper, CATALOG, null)).isEmpty();
    }

    private static String bpmn(String formId) {
        return """
                <bpmn:userTask id="UserTask_Approve">
                  <custom:properties>
                    <custom:property name="formId" value="%s" />
                  </custom:properties>
                </bpmn:userTask>
                """.formatted(formId);
    }
}
