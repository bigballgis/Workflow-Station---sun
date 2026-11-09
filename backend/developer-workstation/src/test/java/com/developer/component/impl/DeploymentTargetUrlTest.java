package com.developer.component.impl;

import com.developer.exception.DeveloperBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A deployment carries the service token and the user's token, so it may only go to this environment's Admin. */
class DeploymentTargetUrlTest {

    private DeploymentComponentImpl component;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<?> constructor = DeploymentComponentImpl.class.getConstructors()[0];
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes()).map(Mockito::mock).toArray();
        component = (DeploymentComponentImpl) constructor.newInstance(dependencies);
        ReflectionTestUtils.setField(component, "defaultAdminCenterUrl", "http://admin-center:8080");
    }

    @Test
    void noTargetOrThisEnvironmentsAdminIsAccepted() {
        assertThat(component.resolveTargetUrl(null)).isEqualTo("http://admin-center:8080");
        assertThat(component.resolveTargetUrl(" ")).isEqualTo("http://admin-center:8080");
        assertThat(component.resolveTargetUrl("http://admin-center:8080/")).isEqualTo("http://admin-center:8080");
    }

    @Test
    void anyOtherTargetIsRefusedBeforeTokensAreSent() {
        for (String target : new String[] {"http://attacker.example", "http://admin-center:8081", "https://admin-center:8080"}) {
            assertThatThrownBy(() -> component.resolveTargetUrl(target))
                    .as(target)
                    .isInstanceOf(DeveloperBusinessException.class);
        }
    }
}
