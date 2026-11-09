package com.developer.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Developer permission codes come from admin-center only: membership of a team gives no read-only baseline any more
 * (platform access model §6.1). When admin-center is down and nothing is cached the answer is unknown, which callers
 * report as 503 instead of passing it off as "no permission".
 */
@ExtendWith(MockitoExtension.class)
class DeveloperPermissionCheckerTest {

    @Mock private RestTemplate restTemplate;

    private DeveloperPermissionChecker checker;

    @BeforeEach
    void setUp() {
        checker = new DeveloperPermissionChecker(restTemplate);
        ReflectionTestUtils.setField(checker, "adminCenterUrl", "http://admin");
    }

    @Test
    void permissionsAreTheOnesAdminCenterReturns() {
        when(restTemplate.exchange(anyString(), any(), any(),
                org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<List<String>>>any()))
                .thenReturn(ResponseEntity.ok(List.of("function_unit:view")));

        assertEquals(Set.of("function_unit:view"), checker.getUserPermissions("u-auditor"));
    }

    @Test
    void adminCenterDownWithNothingCachedIsAnOutageNotARefusal() {
        when(restTemplate.exchange(anyString(), any(), any(),
                org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<List<String>>>any()))
                .thenThrow(new RestClientException("down"));

        assertThrows(DeveloperPermissionUnavailableException.class, () -> checker.getUserPermissions("u-member"));
    }

    @Test
    void aFreshCacheAnswersWithoutAskingAdminCenter() {
        when(restTemplate.exchange(anyString(), any(), any(),
                org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<List<String>>>any()))
                .thenReturn(ResponseEntity.ok(List.of("function_unit:view")));
        checker.clock = () -> 1_000L;
        checker.getUserPermissions("u-member");

        checker.clock = () -> 1_000L + TimeUnit.MINUTES.toMillis(4);
        assertEquals(Set.of("function_unit:view"), checker.getUserPermissions("u-member"));
        verify(restTemplate, times(1)).exchange(anyString(), any(), any(),
                org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<List<String>>>any());
    }

    /**
     * Once the cache has expired, an Admin Center outage is an outage: the old permissions are not reused, or someone
     * taken off a team's list during the outage would keep working with them (out-of-scope list row 42).
     */
    @Test
    void adminCenterDownAfterTheCacheExpiredIsAnOutageNotTheOldPermissions() {
        when(restTemplate.exchange(anyString(), any(), any(),
                org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<List<String>>>any()))
                .thenReturn(ResponseEntity.ok(List.of("function_unit:update")))
                .thenThrow(new RestClientException("down"));
        checker.clock = () -> 1_000L;
        checker.getUserPermissions("u-member");

        checker.clock = () -> 1_000L + TimeUnit.MINUTES.toMillis(6);
        assertThrows(DeveloperPermissionUnavailableException.class, () -> checker.getUserPermissions("u-member"));
    }
}
