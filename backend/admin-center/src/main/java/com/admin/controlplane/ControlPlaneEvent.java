package com.admin.controlplane;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Canonical control-plane event emitted as a Spring ApplicationEvent.
 * Carries the canonical event type, domain identifier, and optional payload.
 *
 * <p>Listeners can filter on {@link #getEventType()} and {@link #getDomain()}.
 * The {@link #getPayload()} carries domain-specific fields for extensibility.
 */
@Getter
public class ControlPlaneEvent extends ApplicationEvent {

    /** Canonical event type (e.g. "release.publish.started"). */
    private final String eventType;

    /** Domain origin: "GATEWAY" or "MFE". */
    private final String domain;

    /** Tenant identifier. */
    private final String tenantId;

    /** Resource identifier (release ID, module ID, etc.). */
    private final String resourceId;

    /** Optional human-readable label for the resource. */
    private final String resourceLabel;

    /** Domain-specific extension payload — never null. */
    private final Map<String, Object> payload;

    /** Timestamp when the event was created. */
    private final Instant occurredAt;

    /**
     * @param source         the object on which the event initially occurred
     * @param eventType      canonical event type (use {@link ControlPlaneEventType} constants)
     * @param domain         "GATEWAY" or "MFE"
     * @param tenantId       tenant scope
     * @param resourceId     resource identifier
     * @param resourceLabel  human-readable label (may be null)
     * @param payload        domain extension payload (may be null, defaults to empty map)
     */
    public ControlPlaneEvent(Object source,
                             String eventType,
                             String domain,
                             String tenantId,
                             String resourceId,
                             String resourceLabel,
                             Map<String, Object> payload) {
        super(source);
        this.eventType = eventType;
        this.domain = domain;
        this.tenantId = tenantId;
        this.resourceId = resourceId;
        this.resourceLabel = resourceLabel;
        this.payload = payload != null ? Collections.unmodifiableMap(payload) : Collections.emptyMap();
        this.occurredAt = Instant.now();
    }

    @Override
    public String toString() {
        return "ControlPlaneEvent{" +
                "eventType='" + eventType + '\'' +
                ", domain='" + domain + '\'' +
                ", resourceId='" + resourceId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                '}';
    }
}
