package com.developer.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 developer-workstation 在 test profile 下可成功启动 ApplicationContext。
 * 功能单元的工作区权限由 {@link com.developer.security.FunctionUnitWorkspaceAccessService} 判定，
 * 规则见 {@code FunctionUnitWorkspaceAccessServiceTest}。
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
@Tag("integration-test")
class SpringSecurityAnnotationIntegrationTest {

    /** 避免 test profile 下 RedisMessageListenerContainer 连接真实 Redis 导致启动超时 */
    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void applicationContextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}
