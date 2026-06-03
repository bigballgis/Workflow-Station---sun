package com.admin.controlplane;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Simple facade for publishing canonical control-plane events via Spring's
 * {@link ApplicationEventPublisher}. Both MFE and Gateway services inject this
 * to emit cross-domain-observable events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ControlPlaneEventPublisher {

    private final ApplicationEventPublisher publisher;

    /**
     * Publish a canonical event.
     *
     * @param source        the originating service (use {@code this})
     * @param eventType     canonical event type from {@link ControlPlaneEventType}
     * @param domain        "GATEWAY" or "MFE"
     * @param tenantId      tenant identifier
     * @param resourceId    resource identifier (e.g. releaseId, moduleRegistryId)
     * @param resourceLabel human-readable label (may be null)
     * @param payload       domain-specific extension payload (may be null)
     */
    public void publish(Object source,
                        String eventType,
                        String domain,
                        String tenantId,
                        String resourceId,
                        String resourceLabel,
                        Map<String, Object> payload) {
        ControlPlaneEvent event = new ControlPlaneEvent(
                source, eventType, domain, tenantId, resourceId, resourceLabel, payload);
        log.debug("Publishing CP event: {}", event);
        publisher.publishEvent(event);
    }

    // ── Convenience overloads ─────────────────────────────────────────

    public void publish(Object source, String eventType, String domain,
                        String tenantId, String resourceId) {
        publish(source, eventType, domain, tenantId, resourceId, null, null);
    }

    public void publish(Object source, String eventType, String domain,
                        String tenantId, String resourceId, String resourceLabel) {
        publish(source, eventType, domain, tenantId, resourceId, resourceLabel, null);
    }
}
