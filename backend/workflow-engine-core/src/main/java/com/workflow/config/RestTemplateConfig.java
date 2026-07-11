package com.workflow.config;

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
 * RestTemplate 配置 + Resilience4j 熔断（调用 admin-center / user-portal 等）。
 *
 * <p>熔断器经 {@link ClientHttpRequestInterceptor} 织入共享 RestTemplate，打开时抛异常
 * （不改"失败即抛"语义），指标经 Micrometer 暴露。
 */
@Configuration
public class RestTemplateConfig {

    /** 短超时 RestTemplate 的 bean 名，供 {@code @Qualifier} 引用。 */
    public static final String INTERNAL_API_REST_TEMPLATE = "internalApiRestTemplate";

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
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder, CircuitBreakerRegistry circuitBreakerRegistry) {
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofMinutes(10)) // External workflows calling LLM APIs can take several minutes
                .build();
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("workflow-outbound-http");
        restTemplate.getInterceptors().add(circuitBreakerInterceptor(breaker));
        return restTemplate;
    }

    /**
     * 内部管控面调用（admin-center 查用户等）专用：读超时必须短。
     *
     * <p>不能复用 {@link #restTemplate} —— 那个 10 分钟读超时是给调 LLM 的外部工作流留的。
     * To Do 列表每行都要查用户名，共用长超时会让 admin-center 一慢就把整个 Tomcat 线程池
     * 挂死（表现为请求堆积到网关超时，而非快速失败）。
     */
    @Bean(INTERNAL_API_REST_TEMPLATE)
    public RestTemplate internalApiRestTemplate(RestTemplateBuilder builder,
                                                CircuitBreakerRegistry circuitBreakerRegistry) {
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("workflow-internal-http");
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
