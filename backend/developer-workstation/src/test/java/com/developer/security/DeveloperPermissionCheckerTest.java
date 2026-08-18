package com.developer.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperPermissionCheckerTest {

    @Mock private RestTemplate restTemplate;
    @Mock private FunctionUnitWorkspaceAccessService workspaceAccessService;

    private DeveloperPermissionChecker checker;

    @BeforeEach
    void setUp() {
        checker = new DeveloperPermissionChecker(restTemplate, workspaceAccessService);
        ReflectionTestUtils.setField(checker, "adminCenterUrl", "http://admin");
    }

    @Test
    void fallbackGrantsViewToAuditorWithoutWrite() {
        when(restTemplate.exchange(anyString(), any(), any(),
                org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<java.util.List<String>>>any()))
                .thenThrow(new RestClientException("down"));
        when(workspaceAccessService.canSeeAllGroups("u-auditor")).thenReturn(true);

        Set<String> perms = checker.getUserPermissions("u-auditor");
        assertEquals(Set.of("function_unit:view"), perms);
        assertFalse(perms.contains("function_unit:update"));
    }

    @Test
    void fallbackGrantsViewToTeamMember() {
        when(restTemplate.exchange(anyString(), any(), any(),
                org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<java.util.List<String>>>any()))
                .thenThrow(new RestClientException("down"));
        when(workspaceAccessService.canSeeAllGroups("u-member")).thenReturn(false);
        when(workspaceAccessService.isMemberOfAnyDevTeam("u-member")).thenReturn(true);

        assertTrue(checker.getUserPermissions("u-member").contains("function_unit:view"));
    }
}
