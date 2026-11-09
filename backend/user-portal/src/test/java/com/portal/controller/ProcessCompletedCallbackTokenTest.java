package com.portal.controller;

import com.platform.common.constant.PlatformConstants;
import com.portal.component.ProcessComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The engine's completion callback must present the shared service token; anything else is a real 403. */
class ProcessCompletedCallbackTokenTest {

    private ProcessController controller;
    private ProcessComponent processComponent;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<?> constructor = ProcessController.class.getConstructors()[0];
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes()).map(Mockito::mock).toArray();
        controller = (ProcessController) constructor.newInstance(dependencies);
        processComponent = (ProcessComponent) Arrays.stream(dependencies)
                .filter(ProcessComponent.class::isInstance).findFirst().orElseThrow();
        ReflectionTestUtils.setField(controller, "serviceInternalToken", "svc-secret");
    }

    @Test
    void validTokenMarksTheProcessCompleted() {
        var response = controller.processCompleted("pi-1", "svc-secret", Map.of("lastActivityName", "Approved"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(processComponent).markProcessAsCompleted("pi-1", "Approved");
    }

    @Test
    void wrongOrMissingTokenIsARealForbidden() {
        for (String token : new String[] {"guess", "wf-internal-service-call", "", null}) {
            var response = controller.processCompleted("pi-1", token, Map.of());

            assertThat(response.getStatusCode().value()).as(String.valueOf(token)).isEqualTo(403);
        }
        verify(processComponent, never()).markProcessAsCompleted(anyString(), anyString());
    }

    @Test
    void headerNameIsThePlatformServiceTokenHeader() throws Exception {
        var parameter = ProcessController.class.getMethod("processCompleted", String.class, String.class, Map.class)
                .getParameters()[1];
        var header = parameter.getAnnotation(org.springframework.web.bind.annotation.RequestHeader.class);
        assertThat(header.value()).isEqualTo(PlatformConstants.HEADER_SERVICE_TOKEN);
    }
}
