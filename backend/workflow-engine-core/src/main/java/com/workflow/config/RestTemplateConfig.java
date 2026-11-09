package com.workflow.config;

import com.platform.common.security.SsrfGuardedHttpClient;
import com.platform.security.client.ServiceTokenRequestInterceptor;
import com.workflow.component.ServiceTaskExecutor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    @Value("${admin-center.url}")
    private String adminCenterUrl;

    @Value("${service.internal-token}")
    private String serviceInternalToken;

    /** Webhook calls of Service Tasks; see {@link #webhookRestTemplate}. */
    public static final String WEBHOOK_REST_TEMPLATE = "webhookRestTemplate";

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
                // Stated, not detected: httpclient5 is on the classpath for webhookRestTemplate only.
                .requestFactory(SimpleClientHttpRequestFactory.class)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofMinutes(10)) // External workflows calling LLM APIs can take several minutes
                .build();
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("workflow-outbound-http");
        restTemplate.getInterceptors().add(circuitBreakerInterceptor(breaker));
        // No service token here: this template also calls user-configured webhook URLs. Admin Center is called
        // through internalApiRestTemplate (or with an explicit token, e.g. the flow-ref resolver).
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
                .requestFactory(SimpleClientHttpRequestFactory.class)
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("workflow-internal-http");
        restTemplate.getInterceptors().add(circuitBreakerInterceptor(breaker));
        // Admin Center authorizes every endpoint: calls to it go out as service calls for the current user.
        restTemplate.getInterceptors().add(new ServiceTokenRequestInterceptor(adminCenterUrl, serviceInternalToken));
        return restTemplate;
    }

    /**
     * Service Task webhooks ({@link ServiceTaskExecutor}): the URL may come from the BPMN ({@code ap:webhookUrl}).
     * Every connection resolves its host through the SSRF check and connects to the checked addresses, so a host
     * cannot pass validation and then be answered with an internal address (DNS rebinding), not even on a retry.
     * Only the configured AP runtime's host may be internal. Same timeouts and breaker as {@link #restTemplate}.
     */
    @Bean(WEBHOOK_REST_TEMPLATE)
    public RestTemplate webhookRestTemplate(RestTemplateBuilder builder, CircuitBreakerRegistry circuitBreakerRegistry,
                                            @Value("${service-task.webhook-base-url:http://localhost:8086}")
                                            String webhookBaseUrl) {
        RestTemplate restTemplate = builder
                .requestFactory(() -> SsrfGuardedHttpClient.requestFactory(
                        ServiceTaskExecutor.allowedHostsOf(webhookBaseUrl), Duration.ofSeconds(5), Duration.ofMinutes(10)))
                .build();
        restTemplate.getInterceptors().add(circuitBreakerInterceptor(
                circuitBreakerRegistry.circuitBreaker("workflow-outbound-http")));
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
