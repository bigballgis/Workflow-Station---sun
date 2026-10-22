package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.functionunit.FunctionUnitVersionException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChangeHistorySensitiveMaskResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CatalogSnapshotReader snapshots = mock(CatalogSnapshotReader.class);
    private final ChangeHistorySensitiveMaskResolver resolver =
            new ChangeHistorySensitiveMaskResolver(jdbc, objectMapper, snapshots);

    @Test
    void extractsEnabledInputMasksFromTheRequestsReleaseProcessFormFirst() throws Exception {
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pid-1"))).thenReturn(List.of("release-1"));
        Map<String, Object> processConfig = objectMapper.readValue("""
                {"rule":[
                  {"field":"card","type":"input","props":{
                    "sensitiveMask":{"enabled":true,"preset":"custom","keepPrefix":2,"keepSuffix":4,"maskChar":"*"}
                  }},
                  {"field":"status","type":"input","props":{"sensitiveMask":{"enabled":false,"preset":"last4"}}},
                  {"field":"note","type":"input","props":{"type":"textarea","sensitiveMask":{"enabled":true,"preset":"all"}}}
                ]}
                """, Map.class);
        Map<String, Object> taskConfig = objectMapper.readValue("""
                {"rule":[{"field":"card","type":"input","props":{"sensitiveMask":{"enabled":true,"preset":"all"}}}]}
                """, Map.class);
        // The release lists its TASK form first; the PROCESS form must still win for a shared field.
        when(snapshots.contents("release-1", "FORM")).thenReturn(Arrays.asList(
                Map.of("formId", 2, "formName", "Review", "formType", "TASK", "configJson", taskConfig),
                Map.of("formId", 1, "formName", "Start", "formType", "PROCESS", "configJson", processConfig)));

        Map<String, Map<String, Object>> masks = resolver.resolveByProcessInstanceId("pid-1");

        assertThat(masks).containsOnlyKeys("card");
        assertThat(masks.get("card")).containsEntry("preset", "custom").containsEntry("keepPrefix", 2);
    }

    @Test
    void requestWithoutAPinnedReleaseIsRefusedRatherThanShownUnmasked() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pid-1"))).thenReturn(java.util.Collections.singletonList(null));
        assertThatThrownBy(() -> resolver.resolveByProcessInstanceId("pid-1"))
                .isInstanceOf(FunctionUnitVersionException.class);
    }

    @Test
    void malformedPublishedFormConfigIsRefusedRatherThanDroppingItsMasks() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pid-1"))).thenReturn(List.of("release-1"));
        when(snapshots.contents("release-1", "FORM")).thenReturn(List.of(
                Map.of("formId", 1, "formName", "Start", "formType", "PROCESS", "configJson", "{not json")));
        assertThatThrownBy(() -> resolver.resolveByProcessInstanceId("pid-1"))
                .isInstanceOf(FunctionUnitVersionException.class);
    }

    @Test
    void unknownRequestYieldsEmptyMap() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq("pid-x"))).thenReturn(List.of());
        assertThat(resolver.resolveByProcessInstanceId("pid-x")).isEmpty();
    }

    @Test
    void blankProcessIdYieldsEmptyMap() {
        assertThat(resolver.resolveByProcessInstanceId("  ")).isEmpty();
    }
}
