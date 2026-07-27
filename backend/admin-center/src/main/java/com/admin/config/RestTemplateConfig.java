package com.admin.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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

    /** Qualifier for {@link #activepiecesRestTemplate}. */
    public static final String AP_REST_TEMPLATE = "activepiecesRestTemplate";

    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder, CircuitBreakerRegistry circuitBreakerRegistry) {
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("admin-outbound-http");
        restTemplate.getInterceptors().add(circuitBreakerInterceptor(breaker));
        return restTemplate;
    }

    /**
     * Activepieces control-plane calls (enable / disable / delete a flow, install a piece).
     *
     * <p>Cannot share {@link #restTemplate}: AP re-installs a flow's pieces when its trigger status
     * changes, which took ~45s from cold in dev — past the shared 30s read timeout. The operation
     * still SUCCEEDED on the AP side while the UI showed "Internal server error" and the audit row
     * recorded {@code success=false}; what the user was told contradicted reality.
     *
     * <p>Its own circuit breaker too, with its own slow-call threshold. On the shared one these
     * slow-by-design calls tripped {@code slowCallDurationThreshold=20s}, and an open breaker there
     * would fail-fast every other admin-center outbound call — workflow-engine included.
     */
    @Bean(AP_REST_TEMPLATE)
    public RestTemplate activepiecesRestTemplate(RestTemplateBuilder builder,
                                                 CircuitBreakerRegistry circuitBreakerRegistry) {
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofMinutes(3))
                .build();
        // Registry default would flag anything over 20s as a slow call; a piece install legitimately
        // takes longer, so raise the bar here instead of inheriting it.
        CircuitBreakerConfig apConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slowCallDurationThreshold(Duration.ofMinutes(2))
                .slowCallRateThreshold(80f)
                .build();
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("admin-activepieces-http", apConfig);
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
