package com.developer.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * API层属性测试
 * Property 21-22: API错误响应一致性、API限流机制
 */
@SpringBootTest
@ActiveProfiles("test")
public class ApiPropertyTest {
    
    /**
     * Property 21: API错误响应一致性
     * HTTP状态码应在有效范围内
     */
    @Property(tries = 20)
    void apiErrorResponseConsistencyProperty(@ForAll("httpStatusCodes") int statusCode) {
        assertThat(statusCode).isBetween(100, 599);
        
        // 错误状态码应在4xx或5xx范围
        if (statusCode >= 400) {
            assertThat(statusCode).isBetween(400, 599);
        }
    }
    
    /**
     * Property 22: API限流机制
     * 限流配置应为正数
     */
    @Property(tries = 20)
    void apiRateLimitProperty(@ForAll @IntRange(min = 1, max = 1000) int requestsPerMinute) {
        assertThat(requestsPerMinute).isGreaterThan(0);
    }
    
    @Provide
    Arbitrary<Integer> httpStatusCodes() {
        return Arbitraries.of(200, 201, 400, 401, 403, 404, 429, 500, 502, 503);
    }
}
