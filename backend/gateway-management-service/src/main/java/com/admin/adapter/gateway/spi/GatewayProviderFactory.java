package com.admin.adapter.gateway.spi;

import com.admin.entity.gateway.Environment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves the correct GatewayProvider adapter based on the environment's
 * {@code gateway_provider} field. Phase 5 multi-provider architecture.
 *
 * <p>Provider mapping:
 * <ul>
 *   <li>{@code KONG}  → {@code kongGatewayProvider} (given by Spring bean name)</li>
 *   <li>{@code APISIX} → {@code apisixGatewayProvider}</li>
 *   <li>{@code ENVOY}  → {@code envoyGatewayProvider}</li>
 *   <li>default        → {@code stubGatewayProvider}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayProviderFactory {

    private final GatewayProvider kongGatewayProvider;
    private final GatewayProvider apisixGatewayProvider;
    private final GatewayProvider envoyGatewayProvider;
    private final GatewayProvider stubGatewayProvider;

    /**
     * Resolve the appropriate GatewayProvider for the given environment.
     * Falls back to stub if provider is unknown.
     */
    public GatewayProvider resolve(Environment environment) {
        if (environment == null || environment.getGatewayProvider() == null) {
            log.warn("No gateway_provider set on environment, falling back to stub");
            return stubGatewayProvider;
        }

        String provider = environment.getGatewayProvider().toUpperCase();
        return switch (provider) {
            case "KONG"   -> kongGatewayProvider;
            case "APISIX" -> apisixGatewayProvider;
            case "ENVOY"  -> envoyGatewayProvider;
            default -> {
                log.warn("Unknown gateway_provider '{}' for env '{}', falling back to stub",
                        provider, environment.getEnvCode());
                yield stubGatewayProvider;
            }
        };
    }
}
