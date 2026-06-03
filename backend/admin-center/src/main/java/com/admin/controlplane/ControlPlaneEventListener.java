package com.admin.controlplane;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Sample listener demonstrating how cross-domain tooling consumes canonical
 * control-plane events.  Replace with real implementations (metrics, notifications,
 * audit aggregation) as needed.
 *
 * <p>All listeners are {@code @Async} — they never block the emitter thread.
 */
@Slf4j
@Component
public class ControlPlaneEventListener {

    @Async
    @EventListener
    public void onControlPlaneEvent(ControlPlaneEvent event) {
        log.info("CP event received: type={}, domain={}, resourceId={}, tenantId={}",
                event.getEventType(), event.getDomain(), event.getResourceId(), event.getTenantId());

        // ── Dispatch by event type ──────────────────────────────────
        switch (event.getEventType()) {
            case ControlPlaneEventType.RELEASE_CREATED ->
                    log.info("  → Release created: {}", event.getResourceLabel());
            case ControlPlaneEventType.RELEASE_VALIDATED ->
                    log.info("  → Release validated: {}", event.getResourceLabel());
            case ControlPlaneEventType.RELEASE_APPROVAL_REQUESTED ->
                    log.info("  → Approval requested for resource: {}", event.getResourceId());
            case ControlPlaneEventType.RELEASE_APPROVAL_DECIDED ->
                    log.info("  → Approval decided for resource: {}", event.getResourceId());
            case ControlPlaneEventType.RELEASE_PUBLISH_STARTED ->
                    log.info("  → Publish started: {}", event.getResourceLabel());
            case ControlPlaneEventType.RELEASE_PUBLISH_SUCCEEDED ->
                    log.info("  → Publish succeeded: {}", event.getResourceLabel());
            case ControlPlaneEventType.RELEASE_PUBLISH_FAILED ->
                    log.warn("  → Publish FAILED: {}", event.getResourceLabel());
            case ControlPlaneEventType.RELEASE_ROLLBACK_STARTED ->
                    log.info("  → Rollback started: {}", event.getResourceLabel());
            case ControlPlaneEventType.RELEASE_ROLLBACK_SUCCEEDED ->
                    log.info("  → Rollback succeeded: {}", event.getResourceLabel());
            case ControlPlaneEventType.RELEASE_ROLLBACK_FAILED ->
                    log.warn("  → Rollback FAILED: {}", event.getResourceLabel());
            case ControlPlaneEventType.POLICY_EVALUATED ->
                    log.info("  → Policy evaluated for resource: {}", event.getResourceId());
            case ControlPlaneEventType.DRIFT_DETECTED ->
                    log.info("  → Drift detected: {}", event.getResourceLabel());
            default ->
                    log.debug("  → Unknown event type: {}", event.getEventType());
        }
    }
}
