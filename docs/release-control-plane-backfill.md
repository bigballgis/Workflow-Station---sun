# Release Control Plane — 契约回填 (Contract Backfill)

## Status: COMPLETE (2026-05-28)

Canonical contract from `release-control-plane-blueprint.md` backfilled into
existing MFE and Gateway governance code.

---

## 1. What Was Done

### 1.1 Canonical Model (new package: `com.admin.controlplane`)

| File | Purpose |
|---|---|
| `ControlPlaneEventType.java` | 12 canonical event type constants |
| `ControlPlaneState.java` | 9 canonical lifecycle states + domain→canonical mapper |
| `ControlPlaneErrorCode.java` | 6 CP_* error codes + policy decision constants |
| `ControlPlaneEvent.java` | Spring ApplicationEvent carrying type/domain/resource/payload |
| `ControlPlaneEventPublisher.java` | @Component facade for publishing events |
| `ControlPlaneEventListener.java` | Sample @Async listener (replace with real implementations) |

### 1.2 Gateway Governance (ReleaseService)

Events emitted by ReleaseService:

| Operation | Event(s) |
|---|---|
| `createRelease` | `release.created` |
| `submitTesting` | `release.validated` |
| `publishRelease` | `release.publish.started` → `release.publish.succeeded` / `release.publish.failed` |
| `rollbackRelease` | `release.rollback.started` → `release.rollback.succeeded` / `release.rollback.failed` |
| `requestApproval` | `release.approval.requested` |
| `approve` | `release.approval.decided` |

Events emitted by DriftDetectorService:

| Operation | Event |
|---|---|
| `syncEnvironment` (success) | `drift.detected` |

### 1.3 MFE Governance (FrontendModuleService)

Events emitted by FrontendModuleService:

| Operation | Event(s) |
|---|---|
| `switchVersion` | `release.publish.started` → `release.publish.succeeded` |
| `rollbackVersion` | `release.rollback.started` → `release.rollback.succeeded` |

> MFE emits the same canonical event types as Gateway (e.g. `release.publish.started`)
> but with `domain="MFE"` in the event envelope. Cross-domain tooling can filter on
> `event.getDomain()` when domain-specific behavior is needed.

### 1.4 Error Code Layering

Added `com.admin.controlplane.ControlPlaneErrorCode`:

- `CP_INVALID_STATE` — operation blocked by incompatible domain state
- `CP_APPROVAL_REQUIRED` — missing approval ticket
- `CP_POLICY_BLOCKED` — policy gate returned BLOCK
- `CP_PUBLISH_FAILED` — runtime adapter failure
- `CP_NOT_FOUND` — resource not found
- `CP_CONFLICT` — concurrent modification conflict

Existing domain codes (`MFE_*`, `GATEWAY_*`) remain intact. New Phase 4+ features
should use CP_* codes alongside domain codes.

### 1.5 State Mapping

Gateway states mapped to canonical:

| Gateway | Canonical |
|---|---|
| DRAFT | DRAFT |
| TESTING | VALIDATING |
| PUBLISHED | PUBLISHED |
| PROMOTED | PUBLISHED (terminal variant) |
| ROLLED_BACK | ROLLED_BACK |

MFE currently has no internal state machine per module — version switching is
stateless. Canonical events are emitted at the operation level.

---

## 2. Compatibility (per blueprint §6)

1. Existing domain endpoints remain unchanged.
2. Mapping is purely additive — canonical events are emitted alongside
   existing behavior.
3. Domain payload fields preserved:
   - MFE: hostApp, moduleCode, remoteEntryUrl, routePath, exposedModule
   - Gateway: provider, runtimeRevision, policyBundle (in snapshot)
4. No new Phase 4+ APIs in this backfill — contract declaration only.

---

## 3. Files Changed

### Created (6 files)
```
backend/admin-center/src/main/java/com/admin/controlplane/
├── ControlPlaneEventType.java
├── ControlPlaneState.java
├── ControlPlaneErrorCode.java
├── ControlPlaneEvent.java
├── ControlPlaneEventPublisher.java
└── ControlPlaneEventListener.java
```

### Modified (3 files)
```
backend/admin-center/src/main/java/com/admin/service/
├── gateway/ReleaseService.java          (+9 event publishing calls)
├── gateway/DriftDetectorService.java    (+1 event publishing call)
└── module/FrontendModuleService.java    (+4 event publishing calls)
```

---

## 4. Build Verification

```
mvn compile -q  (admin-center)
```
Zero errors from controlplane package or modified services.
(Pre-existing LogManagerComponent errors unaffected by this change.)

---

## 5. Next Steps (per blueprint §8)

1. ✅ Introduce canonical contract and mappings in docs.
2. ✅ Keep existing APIs and state names operational.
3. ⬜ Add mapping notes in MFE/Gateway phase docs. (this document)
4. ⬜ Apply contract-first alignment to new Phase 4+ features.
5. ⬜ Re-evaluate physical control plane consolidation after Phase 4+.

## 6. Consumption Example

```java
// Cross-domain tooling listens to ALL canonical events:
@Component
public class ReleaseMetricsCollector {
    @Async
    @EventListener
    public void onPublishEvent(ControlPlaneEvent event) {
        if (ControlPlaneEventType.RELEASE_PUBLISH_SUCCEEDED.equals(event.getEventType())) {
            String domain = event.getDomain(); // "GATEWAY" or "MFE"
            String resourceId = event.getResourceId();
            // ... increment counters, build dashboards
        }
    }
}
```
