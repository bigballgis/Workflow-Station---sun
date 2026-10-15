package com.developer.security;

import com.developer.component.impl.FunctionUnitComponentImpl;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FunctionUnitAdminMethodSecurityTest {
    private boolean allowed(String method, String role) {
        var annotation = Arrays.stream(FunctionUnitComponentImpl.class.getMethods())
                .filter(candidate -> candidate.getName().equals(method))
                .findFirst().orElseThrow().getAnnotation(PreAuthorize.class);
        assertNotNull(annotation);
        var authentication = new UsernamePasswordAuthenticationToken("review-user", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        var root = new SecurityExpressionRoot(authentication) {};
        return Boolean.TRUE.equals(new SpelExpressionParser().parseExpression(annotation.value())
                .getValue(new StandardEvaluationContext(root), Boolean.class));
    }

    @Test
    void sysAdminPassesCapabilityGatesWhileWorkspaceChecksStillApply() {
        for (String method : List.of("create", "update", "delete", "restore", "publish", "clone")) {
            assertTrue(allowed(method, "SYS_ADMIN"), method);
        }
    }

    @Test
    void developerKeepsEditButNotCreateDeleteCloneCapabilities() {
        assertTrue(allowed("update", "DEVELOPER"));
        assertTrue(allowed("publish", "DEVELOPER"));
        for (String method : List.of("create", "delete", "restore", "clone")) {
            assertFalse(allowed(method, "DEVELOPER"), method);
        }
    }
}
