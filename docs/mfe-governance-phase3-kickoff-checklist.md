# MFE Governance Phase 3 Kickoff Checklist

- [x] Phase 2 pilot stability confirmed (delegation-mfe + notification-mfe operational)
- [x] workflow domain boundaries approved (tasks + processes + applications)
- [x] critical switch permission policy approved (`frontend.module:critical:switch` granted to SYS_ADMIN)
- [ ] parity test plan for To Do / My Request / New Request ready
- [ ] rollback runbook for workflow-mfe validated in non-prod

## Phase 3 Implementation Status

### Completed

| Task | ID | Status |
|------|----|--------|
| Extract task pages/components into workflow-mfe | FE-W1 | ✅ |
| Migrate process start flows into workflow-mfe | FE-W2 | ✅ |
| Migrate my applications and detail flows | FE-W3 | ✅ |
| Stabilize shared composables and store boundaries | FE-W4 | ✅ |
| Host route mapping update for workflow routes | FE-H1 | ✅ |
| DDL: warmup_required + preload_priority columns | — | ✅ |
| Permission: frontend.module:critical:switch | — | ✅ |
| Docker: workflow-mfe container + nginx route | — | ✅ |
| DB: workflow-mfe registered in ac_frontend_module_registry | — | ✅ |
| Build: workflow-mfe standalone build passes | — | ✅ |
| Self-bootstrap: shared: [] federation pattern | — | ✅ |
| Locale: reads localStorage, falls back to 'en' | — | ✅ |
| Host runtime: runtime.ts singleton for pinia/router | — | ✅ |
| RemoteLoader: cleanup before retry, simplified mount | — | ✅ |
| Health check: Docker internal URL rewrite (configurable) | — | ✅ |

### Pending

| Task | ID | Priority |
|------|----|----------|
| Full regression for To Do / My Request / New Request | QA-W1 | HIGH |
| Independent deploy and rollback drills for workflow-mfe | OPS-W1 | HIGH |
| Parity test plan | — | MEDIUM |
| Shared SDK extraction (auth/permission/theme/i18n bridge) | — | LOW |

### Architecture Decisions

- **Self-contained MFEs**: All 3 MFEs use `shared: []` with self-bootstrap pattern.
  Host owns runtime via `runtime.ts` but MFEs are independent.
- **Route strategy**: Hardcoded routes (`/tasks`, `/processes`, `/my-applications`)
  redirect to `/mfe/workflow`. MFE uses hash router internally.
- **wangeditor stub**: Rich text editor stubbed to `<textarea>` due to Vue 3 ESM
  incompatibility with `@wangeditor/editor-for-vue`. Full integration deferred.

### Phase 3 Exit Criteria Status

| Criteria | Status |
|----------|--------|
| workflow-mfe independently deployable | ✅ |
| No functional regression in tasks/processes/applications flows | ⚠️ Needs QA-W1 |
| Host shell metrics stable after extraction | ⚠️ Needs QA-W1 |
