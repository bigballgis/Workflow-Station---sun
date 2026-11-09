package com.portal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The deprecated permission endpoints are gone as a family (phase 1 Q3: legacy endpoints nobody calls are removed):
 * {@code POST /permissions/renew} created TEMPORARY requests no approver can act on; {@code /my} and {@code /expiring}
 * belonged to the same retired model. The live ones stay.
 */
class PermissionControllerLegacyEndpointsRemovedTest {

    private static List<String> paths() {
        return Arrays.stream(PermissionController.class.getDeclaredMethods())
                .map(PermissionControllerLegacyEndpointsRemovedTest::mapping)
                .filter(m -> m != null)
                .flatMap(m -> Stream.concat(Arrays.stream(m.path()), Arrays.stream(m.value())))
                .toList();
    }

    private static RequestMapping mapping(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
    }

    @Test
    void renewMyAndExpiringAreNoLongerMapped() {
        assertThat(paths()).doesNotContain("/renew", "/my", "/expiring");
    }

    @Test
    void theRequestAndApprovalEndpointsRemain() {
        assertThat(paths()).contains("/requests", "/requests/{requestId}", "/approvals/pending", "/my-roles");
    }
}
