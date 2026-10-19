package com.portal.component;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessPublishedStartContentTest {
    private final ProcessComponent component = mock(ProcessComponent.class, CALLS_REAL_METHODS);
    private final RestTemplate rest = mock(RestTemplate.class);

    private void response(Map<String, Object> payload) {
        ReflectionTestUtils.setField(component, "restTemplate", rest);
        ReflectionTestUtils.setField(component, "adminCenterUrl", "http://synthetic-admin");
        when(rest.getForObject(anyString(), eq(Map.class))).thenReturn(payload);
    }

    @Test void oldOrCachedAdminResponsesCannotPretendToBeAuthoritative() {
        response(Map.of("code", "synthetic", "forms", List.of()));
        assertThatThrownBy(() -> component.getPublishedStartContent("synthetic-catalog"))
                .hasMessageContaining("Admin Center must support the start-content contract");
        verify(rest).getForObject("http://synthetic-admin/api/v1/admin/function-units/synthetic-catalog/content?forStart=true", Map.class);
        verifyNoMoreInteractions(rest);
    }

    @Test void confirmedFreshContentIsReadAgainOnEveryStart() {
        response(Map.of("startContentAuthoritative", true, "code", "synthetic", "forms", List.of(), "processes", List.of()));
        assertThat(component.getPublishedStartContent("synthetic-catalog")).containsEntry("code", "synthetic");
        assertThat(component.getPublishedStartContent("synthetic-catalog")).containsEntry("startContentAuthoritative", true);
        verify(rest, times(2)).getForObject("http://synthetic-admin/api/v1/admin/function-units/synthetic-catalog/content?forStart=true", Map.class);
    }
}
