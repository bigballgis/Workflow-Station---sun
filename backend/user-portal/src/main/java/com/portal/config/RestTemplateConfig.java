package com.portal.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 配置 + Resilience4j 熔断。
 *
 * <p>用 {@link ClientHttpRequestInterceptor} 把熔断器织入共享 RestTemplate：所有跨服务 client
 * （workflow-engine / admin-center 等）的 HTTP 调用自动受同一熔断器保护，无需逐 client/逐方法加注解。
 * 熔断打开时直接抛异常（不改变现有"失败即抛"的返回语义），调用方按原逻辑处理。
 * 熔断状态/调用指标经 Micrometer 暴露到 actuator/Prometheus。
 */
@Configuration
public class RestTemplateConfig {

    @Value("${portal.http.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    // 默认 10 分钟：外部 n8n 工作流可能跑数分钟（保留原值，可配置）
    @Value("${portal.http.read-timeout-ms:600000}")
    private int readTimeoutMs;

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .failureRateThreshold(50f)              // 50% 失败率触发熔断
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(5)
                .slowCallDurationThreshold(Duration.ofSeconds(20))
                .slowCallRateThreshold(80f)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        // 熔断器指标接入 Micrometer（actuator/prometheus 可见）
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    @Bean
    public RestTemplate restTemplate(CircuitBreakerRegistry circuitBreakerRegistry) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        RestTemplate restTemplate = new RestTemplate(factory);
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("portal-outbound-http");
        restTemplate.getInterceptors().add(circuitBreakerInterceptor(breaker));
        return restTemplate;
    }

    /** 把每次出站调用包进熔断器；打开时抛 CallNotPermittedException（由调用方现有 try/catch 处理）。 */
    private ClientHttpRequestInterceptor circuitBreakerInterceptor(CircuitBreaker breaker) {
        return (request, body, execution) -> {
            try {
                return breaker.decorateCheckedSupplier(() -> execution.execute(request, body)).get();
            } catch (java.io.IOException | RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new java.io.IOException(t);
            }
        };
    }
}
