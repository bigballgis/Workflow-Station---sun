package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.ProcessStartRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessStartBatchContextGateTest {
    private final ProcessStartComponent component = mock(ProcessStartComponent.class, CALLS_REAL_METHODS);
    private final ProcessComponent content = mock(ProcessComponent.class);
    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
    private final FunctionUnitAccessComponent access = mock(FunctionUnitAccessComponent.class);
    private final ProcessStartFormEnricherComponent enricher = mock(ProcessStartFormEnricherComponent.class);
    private final RestTemplate rest = mock(RestTemplate.class);
    private final ProcessStartComponent.ActiveCatalogPin pin = new ProcessStartComponent.ActiveCatalogPin("synthetic-catalog", "synthetic", "1");

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(component, "processComponent", content);
        ReflectionTestUtils.setField(component, "workflowEngineClient", engine);
        ReflectionTestUtils.setField(component, "functionUnitAccessComponent", access);
        ReflectionTestUtils.setField(component, "restTemplate", rest);
        ReflectionTestUtils.setField(component, "processStartFormEnricherComponent", enricher);
        I18nService i18n = mock(I18nService.class);
        when(i18n.getMessage("portal.batch.start_context_changed")).thenReturn("Refresh the request form");
        ReflectionTestUtils.setField(component, "i18nService", i18n);
        when(access.resolveFunctionUnitIdAlignedWithActiveCatalog("synthetic", "synthetic-catalog")).thenReturn("synthetic-catalog");
        when(rest.exchange(anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("id", "synthetic-catalog", "code", "synthetic", "version", "1")));
    }

    @Test void missingContextStopsBeforeDeploymentOrAnyEnrichment() throws Exception {
        when(content.getPublishedStartContent("synthetic-catalog")).thenReturn(SubTableBatchStartContextTest.content(true, "Current"));
        assertThatThrownBy(() -> component.startProcess("synthetic-user", "synthetic", ProcessStartRequest.builder().formData(Map.of()).build()))
                .hasMessage("Refresh the request form");
        verifyNoInteractions(engine, enricher);
        verify(access).checkFunctionUnitAccess("synthetic-user", "synthetic-catalog");
        verify(content, never()).getFunctionUnitContent(anyString());
    }

    @Test void sameCatalogOverwriteUsesFreshPublishedContentNotThePageOrCache() throws Exception {
        var previous = SubTableBatchStartContext.capture(SubTableBatchStartContextTest.content(true, "Previous"), pin, new ObjectMapper());
        when(content.getPublishedStartContent("synthetic-catalog")).thenReturn(SubTableBatchStartContextTest.content(true, "Current"));
        assertThatThrownBy(() -> component.startProcess("synthetic-user", "synthetic", ProcessStartRequest.builder()
                .formData(Map.of()).batchStartContext(previous).build())).hasMessage("Refresh the request form");
        verifyNoInteractions(engine, enricher);
        verify(content).getPublishedStartContent("synthetic-catalog");
        verify(content, never()).getFunctionUnitContent(anyString());
    }

    @Test void legacyAndServerProducedRequestsPassContextGateButKeepNormalAuthorization() throws Exception {
        when(content.getPublishedStartContent("synthetic-catalog"))
                .thenReturn(SubTableBatchStartContextTest.content(false, "Legacy"))
                .thenReturn(SubTableBatchStartContextTest.content(true, "Server"));
        // Engine availability stops these calls immediately after the gate, before any real start.
        assertThatThrownBy(() -> component.startProcess("synthetic-user", "synthetic", ProcessStartRequest.builder().build()))
                .hasMessageContaining("Workflow engine unavailable");
        assertThatThrownBy(() -> component.startProcessFromServer("synthetic-user", "synthetic", ProcessStartRequest.builder().build()))
                .hasMessageContaining("Workflow engine unavailable");
        verify(access, times(2)).checkFunctionUnitAccess("synthetic-user", "synthetic-catalog");
        verify(engine, times(2)).isAvailable();
        verifyNoInteractions(enricher);
    }
}
