package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChangeHistorySensitiveMaskResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void extractsEnabledInputMaskFromProcessForms() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        String configJson = """
                {"rule":[
                  {"field":"card","type":"input","props":{
                    "sensitiveMask":{"enabled":true,"preset":"custom","keepPrefix":2,"keepSuffix":4,"maskChar":"*"}
                  }},
                  {"field":"status","type":"input","props":{"sensitiveMask":{"enabled":false,"preset":"last4"}}},
                  {"field":"note","type":"input","props":{"type":"textarea","sensitiveMask":{"enabled":true,"preset":"all"}}}
                ]}
                """;
        when(jdbc.query(anyString(), any(RowMapper.class), eq("pid-1")))
                .thenReturn(List.of(configJson));

        ChangeHistorySensitiveMaskResolver resolver =
                new ChangeHistorySensitiveMaskResolver(jdbc, objectMapper);
        Map<String, Map<String, Object>> masks = resolver.resolveByProcessInstanceId("pid-1");

        assertThat(masks).containsOnlyKeys("card");
        assertThat(masks.get("card")).containsEntry("enabled", true).containsEntry("keepPrefix", 2);
    }

    @Test
    void blankProcessIdYieldsEmptyMap() {
        ChangeHistorySensitiveMaskResolver resolver =
                new ChangeHistorySensitiveMaskResolver(mock(JdbcTemplate.class), objectMapper);
        assertThat(resolver.resolveByProcessInstanceId("  ")).isEmpty();
    }
}
