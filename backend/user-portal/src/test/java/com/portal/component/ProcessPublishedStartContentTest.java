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

    private void conflict(String body) {
        ReflectionTestUtils.setField(component, "restTemplate", rest);
        ReflectionTestUtils.setField(component, "adminCenterUrl", "http://synthetic-admin");
        when(rest.getForObject(anyString(), eq(Map.class))).thenThrow(org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.CONFLICT, "Conflict", new org.springframework.http.HttpHeaders(),
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8));
    }

    /** A release Admin Center cannot assemble is reported as the version error, not as an unhandled 500. */
    @Test void releaseAdminCannotAssembleIsTheVersionError() {
        conflict("{\"success\":false,\"error\":{\"code\":\"3008\",\"details\":"
                + "{\"reason\":\"VERSION_SNAPSHOT_INCOMPLETE\",\"reference\":\"synthetic-catalog\"}}}");
        assertThatThrownBy(() -> component.getPublishedStartContent("synthetic-catalog"))
                .isInstanceOf(com.platform.common.functionunit.FunctionUnitVersionException.class)
                .satisfies(e -> assertThat(((com.platform.common.functionunit.FunctionUnitVersionException) e).getDetails())
                        .containsEntry("reason", "VERSION_SNAPSHOT_INCOMPLETE").containsEntry("reference", "synthetic-catalog"));
    }

    /** The task page path: an existing request's pinned release that Admin Center cannot assemble. */
    @Test void pinnedReleaseAdminCannotAssembleIsTheVersionError() {
        conflict("{\"success\":false,\"error\":{\"code\":\"3008\",\"details\":"
                + "{\"reason\":\"VERSION_SNAPSHOT_INCOMPLETE\",\"reference\":\"synthetic-catalog\"}}}");
        FunctionUnitAccessComponent access = mock(FunctionUnitAccessComponent.class);
        when(access.resolveFunctionUnitId("synthetic-catalog")).thenReturn("synthetic-catalog");
        ReflectionTestUtils.setField(component, "functionUnitAccessComponent", access);
        ReflectionTestUtils.setField(component, "fuContentCache", new java.util.HashMap<>());

        assertThatThrownBy(() -> component.getFunctionUnitContent("synthetic-catalog"))
                .isInstanceOf(com.platform.common.functionunit.FunctionUnitVersionException.class);
    }

    @Test void otherConflictsAreNotDisguisedAsVersionErrors() {
        conflict("{\"success\":false,\"error\":{\"code\":\"BUSY\"}}");
        assertThatThrownBy(() -> component.getPublishedStartContent("synthetic-catalog"))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.Conflict.class);
    }
}
