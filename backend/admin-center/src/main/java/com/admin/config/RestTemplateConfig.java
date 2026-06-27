package com.admin.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 配置 + Resilience4j 熔断（用于调用 workflow-engine 等外部服务）。
 *
 * <p>熔断器经 {@link ClientHttpRequestInterceptor} 织入共享 RestTemplate：所有跨服务调用自动受保护，
 * 打开时抛异常（不改"失败即抛"语义），指标经 Micrometer 暴露到 actuator/Prometheus。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(5)
                .slowCallDurationThreshold(Duration.ofSeconds(20))
                .slowCallRateThreshold(80f)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, CircuitBreakerRegistry circuitBreakerRegistry) {
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("admin-outbound-http");
        restTemplate.getInterceptors().add(circuitBreakerInterceptor(breaker));
        return restTemplate;
    }

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
