package com.admin.servicetask;

import com.admin.exception.ServiceTaskActorRequiredException;
import com.admin.exception.ServiceTaskApiException;
import com.admin.servicetask.client.ServiceTaskApiClient;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.platform.common.dto.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 「谁进去就是谁」：AP 会话只能按当前操作人换取。
 * 拿不到操作人时必须 fail-loud（AP_ACTOR_REQUIRED），不得回退任何共享/合成身份。
 */
class CurrentActorTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void failsLoudWhenNoAuthenticatedActor() {
        ServiceTaskApiClient client = new ServiceTaskApiClient(restTemplate, configuredProperties());

        ServiceTaskActorRequiredException e =
                assertThrows(ServiceTaskActorRequiredException.class, client::signInAsCurrentActor);

        assertEquals("AP_ACTOR_REQUIRED", e.getErrorCode());
        // 没有操作人时连 AP 都不该被调用（更不该用别的身份替代）
        verifyNoInteractions(restTemplate);
    }

    /**
     * 服务间调用（C-3 的 X-Service-Token + X-User-Id）经 SecurityConfig 的
     * ServiceCallAuthenticationFilter 落进 SecurityContext，这里模拟其结果：
     * 有操作人 ⇒ 走 managed 换取；未配置签名密钥时以 AP 错误 fail-loud，而不是换个身份继续。
     */
    @Test
    void usesTheServiceCallActorAndNeverFallsBack() {
        authenticate("44027893", "zhangsan");
        ServiceTaskApiClient client = new ServiceTaskApiClient(restTemplate, new ServiceTaskProperties());

        ServiceTaskApiException e =
                assertThrows(ServiceTaskApiException.class, client::signInAsCurrentActor);

        assertTrue(e.getMessage().contains("managed provisioning not configured"),
                "未配置签名密钥必须显式报错，实际: " + e.getMessage());
        verifyNoInteractions(restTemplate);
    }

    private static ServiceTaskProperties configuredProperties() {
        ServiceTaskProperties properties = new ServiceTaskProperties();
        properties.getManaged().setSigningKeyId("key-1");
        properties.getManaged().setPrivateKey("-----BEGIN PRIVATE KEY-----AAAA-----END PRIVATE KEY-----");
        return properties;
    }

    private static void authenticate(String userId, String username) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .username(username)
                .displayName(username)
                .roles(Collections.emptyList())
                .permissions(Collections.emptyList())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));
    }
}
