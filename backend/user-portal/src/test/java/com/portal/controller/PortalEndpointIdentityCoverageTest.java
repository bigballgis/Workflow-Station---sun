package com.portal.controller;

import com.portal.security.CurrentUserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Portal security lets every request through to the handlers, so a handler that never asks who the caller is stays
 * open to anonymous callers (platform access model §7.2: identity comes from the credential only). Every handler takes
 * {@link CurrentUserId} — which answers 401 without a signed-in user — or is listed here with the reason it needs none.
 */
class PortalEndpointIdentityCoverageTest {

    /** Whole controllers: "Class"; single handlers: "Class#method". */
    private static final Map<String, String> NO_CURRENT_USER_PARAMETER = Map.of(
            "AuthController", "issues, refreshes and reads sessions from the token itself",
            "AuthSsoExchangeController", "exchanges an SSO ticket before any Portal session exists",
            "HealthAliasController", "liveness and readiness probes",
            "InternalAuditController", "Admin Center calls it with the internal service token",
            "InternalRuntimeController", "engine and Admin Center call it with the internal service token",
            "InternalSlaRecalcController", "Admin Center calls it with the internal service token",
            "InternalTaskAssignmentEventController",
            "the engine reports assignment changes it made on its own (D-40, D-42), checked by ServiceTokenVerifier",
            "MemberController", "asApprover reads the signed-in user and answers 401 without one",
            "BiDashboardController", "currentUserId() reads the signed-in user and answers 401 without one",
            "ProcessController#processCompleted", "the engine's completion callback with the internal service token");

    @Test
    void everyHandlerKnowsWhoIsCalling() throws ClassNotFoundException {
        List<String> open = new ArrayList<>();
        Set<String> listedAndFound = new HashSet<>();
        for (Class<?> controller : controllers()) {
            String name = controller.getSimpleName();
            for (Method method : controller.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                String handler = name + "#" + method.getName();
                if (NO_CURRENT_USER_PARAMETER.containsKey(name) || NO_CURRENT_USER_PARAMETER.containsKey(handler)) {
                    listedAndFound.add(NO_CURRENT_USER_PARAMETER.containsKey(handler) ? handler : name);
                    continue;
                }
                if (!takesCurrentUser(method)) {
                    open.add(handler);
                }
            }
        }

        assertThat(open).as("handlers open to anonymous callers").isEmpty();
        assertThat(listedAndFound).as("every listed exception still exists")
                .containsExactlyInAnyOrderElementsOf(NO_CURRENT_USER_PARAMETER.keySet());
    }

    private static boolean takesCurrentUser(Method method) {
        for (Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(CurrentUserId.class)) {
                return true;
            }
        }
        return false;
    }

    private static List<Class<?>> controllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> out = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.portal")) {
            out.add(Class.forName(candidate.getBeanClassName()));
        }
        assertThat(out).as("Portal controllers found").hasSizeGreaterThan(20);
        return out;
    }
}
