# Testing & Code Quality Audit — Workflow Station
Date: 2026-07-18. Branch: common_0701_timeline. All paths absolute under
`/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/` (prefix omitted below as `<root>/`).
Status labels: [VERIFIED] = confirmed in code; [INFERRED] = strong signal, not exhaustively proven; [GAP] = missing.

---

## 1. Backend test inventory (per Maven module)

Counted via `find */src/test/java -name '*.java'` and annotation greps. The suite is **overwhelmingly
jqwik property-based + Mockito unit tests**; very little Spring-context integration testing.

| Module | Test files | Main files | jqwik property files (`@Property`) | `*PropertyTest` / `*Properties` naming | @SpringBootTest | @WebMvcTest | @DataJpaTest | EndToEnd files |
|---|---|---|---|---|---|---|---|---|
| admin-center | 66 | 419 | 47 | 17 / 29 | 0 | 0 | 0 | 0 |
| developer-workstation | 136 | 345 | 76 | 52 / 25 | 3 | 1 | 2 | 2 |
| user-portal | 71 | 198 | 38 | 30 / 12 | 0 | 0 | 0 | 0 |
| workflow-engine-core | 66 | 214 | 21 | 11 / 19 | 9 | 0 | 0 | 2 |
| platform-security | 12 | 57 | 10 | 9 / 2 | 0 | 0 | 1 | 0 |
| platform-common | 29 | 108 | 18 | 16 / 0 | 2 | 0 | 0 | 0 |
| platform-cache | 1 | 4 | 1 | 1 / 0 | 0 | 0 | 0 | 0 |
| platform-messaging | 2 | 12 | 2 | 2 / 0 | 0 | 0 | 0 | 0 |

Evidence & notes:
- jqwik is a declared dependency in **all 8 module poms** [VERIFIED] (`grep -rl jqwik backend --include=pom.xml`).
- `*Properties.java` files are jqwik suites, not config classes — surefire explicitly includes them:
  `<include>**/*Properties.java</include>` in e.g. `<root>backend/admin-center/pom.xml:289-297` (same block in dw/up/wec/ps poms) [VERIFIED].
- Mockito is used in 243 test files [VERIFIED] — the dominant style is mock-heavy unit/property tests.
- **"EndToEnd" is a misnomer**: `<root>backend/developer-workstation/src/test/java/com/developer/integration/AiGenerationEndToEndTest.java`
  uses `@ExtendWith(MockitoExtension.class)` + `@Mock` for every collaborator — it is a Mockito unit
  test of the AI pipeline, not a running-app E2E [VERIFIED, file header lines 1-30]. No Testcontainers
  anywhere (0 hits) [VERIFIED].
- workflow-engine-core is the only module with meaningful Spring integration tests: 9 `@SpringBootTest`
  files (e.g. `ProcessInstanceCreationConsistencyProperties.java`, `MultiDimensionalTaskAssignmentProperties.java`,
  `DatabaseTransactionAtomicityProperties.java`) plus `application-test.yml` [VERIFIED].
- admin-center: 66 test files, 419 main files — worst test-to-code ratio of the big modules, and
  **zero** Spring/MVC/JPA integration tests [VERIFIED].

## 2. Untested critical areas (zero test references)

Method: for every `*Controller` / `*ServiceImpl` / `*ComponentImpl` in `src/main`, grep the module's
`src/test` tree for the class name. "NO-TEST-REF" = name appears nowhere in tests [VERIFIED].

**admin-center — ~40 controllers with zero test refs**, including security-critical ones:
`AuthController` (which also contains 3 empty-catch logout calls, see §4), `SsoAuthController`,
`AuthSsoExchangeController`, `SsoInternalController`, `SecurityAuditController`, `PermissionController`,
`TaskAssignmentController`, `UserController`, `RoleController`, `BusinessUnitController`,
`LdapSyncController`, all 5 `Bi*` (Superset) controllers, `AuthServiceImpl`. [GAP]

**developer-workstation — 28 controllers with zero test refs**, notably:
`DeploymentController` and `DeploymentJobServiceImpl` (deployment pipeline **untested at the
controller/service layer**), `ExportImportController`, `FunctionUnitController`, `FormDesignController`,
`ProcessDesignController`, `TableDesignController`, `SubTableViewServiceImpl`, `FileStorageServiceImpl`. [GAP]
- Rollback nuance: `VersionComponentImpl` has 4 test references [VERIFIED] (version snapshot/rollback
  logic has some coverage), but the HTTP deployment path does not.

**user-portal — 17 controllers with zero test refs**, notably the portal form runtime:
`TaskFormController`, `ProcessFormController`, `TaskController`, `ApiDataController`,
`PortalMainTableViewController`, `PortalRelationTableController`, `RecordNoteController` (newest feature),
`DelegationController`, `InternalRuntimeController`, `ChangeHistoryController`. [GAP]
- Mitigating: runtime *components* have refs — `TaskPermissionEvaluator` (1 test ref),
  `MainTableViewAccessResolver` (2), and the heavy portal form logic is tested on the **frontend**
  (76 vitest files in `frontend/user-portal`, incl. MI regression suite).

**workflow-engine-core — ALL 7 controllers have zero test refs**:
`ProcessController`, `TaskController`, `HistoryController`, `MonitoringController`,
`ApExecutionController`, `DecisionExecutionController`, `HealthAliasController`. [GAP]
- Mitigating: task assignment core logic tested — `TaskAssignmentListener` has 3 test refs;
  `MultiDimensionalTaskAssignmentProperties` is a @SpringBootTest [VERIFIED].

**Auth filters**: `JwtAuthenticationFilter` (`<root>backend/platform-security/src/main/java/com/platform/security/filter/JwtAuthenticationFilter.java`)
has 3 test refs (`JwtFilterPreservationPropertyTest`, `JwtTokenPropertyTest` etc.) [VERIFIED — covered].

**Summary of the pattern**: components/util layers get property tests; the **entire HTTP layer
(controllers) is effectively untested** — only 1 `@WebMvcTest` in the whole backend (DW) and one
`@RestControllerAdvice`-annotated test (`NotificationControllerTest` in user-portal).

## 3. CI / build enforcement

- **No GitHub Actions**: no `.github/` directory at all [VERIFIED].
- **No GitLab/Circle/generic CI** for build+test: `deploy/ci/` contains only two Jenkinsfiles for
  Activepieces flow export/publish (`Jenkinsfile.ap-flows-export`, `Jenkinsfile.ap-flows-publish`) —
  neither runs `mvn test` or `npm test`; both are full of `TODO(n)` placeholders for agent/credentials/URLs
  (i.e., not yet operational as written) [VERIFIED].
- Builds are done by PowerShell scripts: `deploy/scripts/build.ps1`, `build-and-push-k8s.ps1` (mvn
  package; no evidence of enforced test gate in CI) [INFERRED — scripts exist, no server config in repo].
- **Conclusion: tests, lint, and any "pass" claims are enforced only by developer discipline /
  Claude-session convention, not CI.** [GAP]

## 4. Frontend tests

| App | Test files | Runner | Property-based | Config |
|---|---|---|---|---|
| user-portal | 76 | vitest 2.x | fast-check ^4.6 | `vitest.config.ts` + `vitest.mi-regression.config.ts` |
| developer-workstation | 69 | vitest | fast-check ^3.23 | `vitest.config.ts` |
| admin-center | 5 | vitest (via `test:` block in `vite.config.ts`, which imports `vitest/config`) | fast-check ^4.6 | no separate vitest.config [VERIFIED] |
| login / workflow-mfe / notification-mfe / delegation-mfe / gateway-mfe / shared / packages | **0** | — | — | — [GAP] |

- 150 `*.test.*` files total under `frontend/*/src`; **51 are `*.property.test.ts`** (fast-check);
  63 files import fast-check [VERIFIED].
- Notable DW meta-tests: `src/__tests__/i18n-no-chinese-strings.test.ts`, `i18n-locale-key-consistency.test.ts`,
  `kong-nginx-config.property.test.ts` (tests infra config from unit tests) [VERIFIED].
- **MI regression suite** (user-portal): dedicated `vitest.mi-regression.config.ts` with a curated file
  list + Playwright screenshot phase; root scripts `npm run regression:mi`, docs at
  `frontend/user-portal/MI_REGRESSION.md`, scenarios in `frontend/scripts/mi-regression-scenarios.mjs` [VERIFIED].
- **Playwright**: devDependency in `frontend/package.json` (`playwright ^1.61.0`), but **no
  playwright.config.\* and no `*.spec.ts` e2e suites anywhere** [VERIFIED]. Playwright is used only via
  ad-hoc verification scripts (`frontend/scripts/verify-page-screenshot.mjs`, `verify-dw-assignee-multi-role.mjs`,
  `verify-mi-regression-all.mjs`, `playwright-login.mjs`, ~10 more) driven by the
  `.claude/skills/verify-ui-fix-with-screenshot/SKILL.md` skill, saving into per-app
  `verification-screenshots/` dirs (present in admin/portal/dw) [VERIFIED]. So: screenshot-verification
  workflow exists and is codified; assertion-based browser e2e does **not** exist. [GAP]
- admin-center at 5 test files vs its 419-file backend + full admin UI is the weakest-tested app. [GAP]

## 5. Code quality signals

### TODO/FIXME/HACK
Remarkably clean — **0 actual `// TODO`/`// FIXME`/`// HACK` comment markers in backend main+test Java
and 0 in frontend src** [VERIFIED: `grep -rnE '//\s*(TODO|FIXME|HACK)'` = 0]. Case-insensitive word
matches (31 backend / 32 frontend) are all false positives: domain word "todo list" (task inbox),
`xxx` placeholders in Javadoc examples, and a platform-common *test* that asserts APIs don't return
"TODO" placeholders (`CompleteApiImplementationPropertyTest.java`). The only real TODOs live in
`deploy/ci/Jenkinsfile.ap-flows-*` (7 deliberate `TODO(n)` environment placeholders) [VERIFIED].

### Largest files (top 20+ across backend main + frontend src, wc -l)
| Lines | File |
|---|---|
| 2253 | frontend/developer-workstation/src/components/designer/FormDesigner.vue |
| 2071 | frontend/developer-workstation/src/i18n/locales/en.ts |
| 2067 | frontend/developer-workstation/src/i18n/locales/zh-TW.ts |
| 2066 | frontend/developer-workstation/src/i18n/locales/zh-CN.ts |
| 1410 | frontend/user-portal/src/views/permissions/index.vue |
| 1373 | frontend/user-portal/src/components/SubTableField.vue |
| 1229 | frontend/developer-workstation/src/components/designer/TableDesigner.vue |
| 1085 | backend/user-portal/.../service/PortalRelationTableServiceImpl.java |
| 1040 | frontend/user-portal/src/i18n/locales/en.ts |
| 1035 | frontend/user-portal/src/i18n/locales/zh-CN.ts |
| 1034 | frontend/user-portal/src/i18n/locales/zh-TW.ts |
| 1018 | frontend/user-portal/src/views/processes/start.vue |
| 987 | frontend/developer-workstation/src/components/ai/ChatDialog.vue |
| 961 | frontend/admin-center/src/i18n/locales/en.ts |
| 952 | backend/user-portal/.../component/ProcessStartComponent.java |
| 952 | backend/admin-center/.../ldap/LdapSyncService.java |
| 948 | frontend/user-portal/src/views/tasks/detail.vue |
| 943/941 | frontend/admin-center/src/i18n/locales/zh-TW.ts / zh-CN.ts |
| 895 | backend/user-portal/.../service/impl/PortalMainTableViewServiceImpl.java |
| 892 | frontend/user-portal/src/views/relation-tables/index.vue |

God-class candidates (excluding i18n data files): **FormDesigner.vue (2253)**, permissions/index.vue,
SubTableField.vue, TableDesigner.vue, PortalRelationTableServiceImpl (1085, also has an
`catch (Exception ignored) {}` at line 373), ProcessStartComponent, LdapSyncService. Only 8 files
exceed 1000 lines repo-wide — moderate, not pathological. [VERIFIED]

### Silent fallbacks — recorded baseline (fallback-audit skill)
`.claude/skills/fallback-audit/SKILL.md` records a fixed-methodology 2026-07-10 baseline [VERIFIED]:
- Frontend `|| []`/`|| {}` swallow-empty: user-portal 310, admin-center 59, dw 345 (714 total);
  log-only catch: 72/13/52; catch→default: 14/12/14.
- Backend: **922 broad `catch (Exception...)` in src/main** (wec 314, up 286, admin 138, dw 107,
  common 46, security 25); ~294 log-only catch; ~60 catch→default; 150 `.orElse(null)`-style.
- Governance rule exists: `.cursor/rules/error-handling-governance.mdc` (alwaysApply) — "兜底即 bug"
  with a 3-category exemption whitelist and `// FALLBACK(external|migration|ux):` annotation protocol [VERIFIED].
- Current spot-check confirms live instances: empty catches in
  `backend/admin-center/.../controller/AuthController.java:94,97,127` (`catch (Exception ignored) {}` around logout),
  `backend/user-portal/.../component/MiCollectionVariableBuilder.java:366,419,454`,
  `PortalRelationTableServiceImpl.java:373`, `WorkflowEngineClient.java:171` [VERIFIED].
  Today's raw count of `catch (Exception` in src/main = 884 (vs baseline-adjacent 922 broad-catch metric,
  different regex — trend roughly flat) [INFERRED].
- Duplication signal tracked by the same rule: **217 "mirrors / aligns with / 与…一致" comments**
  across frontend+backend — the rule itself brands these parity-copy comments as duplication smell [VERIFIED].
- Commented-out code: only ~13 lines matching commented-out-statement patterns in backend main — negligible [VERIFIED].

## 6. Error-handling governance (@RestControllerAdvice)

| Module | Advice class | Response shape |
|---|---|---|
| platform-common | `GlobalExceptionHandler` (340 lines) | `ResponseEntity<ApiResponse<?>>` wrapping `ErrorResponse.builder()` |
| admin-center | `AdminApiExceptionHandler` | same `ApiResponse.error(ErrorResponse)` [CONSISTENT] |
| workflow-engine-core | `WorkflowExceptionControllerAdvice` | same [CONSISTENT] |
| developer-workstation | `WorkspaceExceptionHandler` + `AiExceptionHandler` | mostly same, **but** `onWorkspaceDenied` returns raw `ResponseEntity<Map<String,Object>>` (line 37) — one shape deviation [VERIFIED] |
| user-portal | **no module-local @RestControllerAdvice in src/main** — relies on platform-common `GlobalExceptionHandler` [VERIFIED] |
| platform-security | none (library module; its AuthController relies on GlobalExceptionHandler) |

Overall: strong convergence on shared `ApiResponse`/`ErrorResponse` from platform-common; two
deviations worth noting (DW workspace-denied Map response; user-portal having no portal-specific
error mapping layer).

## 7. Lint/format tooling matrix

| Tool | Present | Enforced in CI |
|---|---|---|
| ESLint (flat config) | Yes — `eslint.config.js` in admin-center, developer-workstation, user-portal, login; `npm run lint` scripts | No (no CI) |
| Prettier | **No config anywhere** (`.prettierrc*` absent) [GAP] | — |
| husky / lint-staged | **None** [GAP] | — |
| Checkstyle / SpotBugs / PMD | **None in any pom** (root `pom.xml` + 8 module poms grepped) [GAP] | — |
| JaCoCo coverage | **None** [GAP] | — |
| Surefire | Configured (3.2.3) with custom includes `*Test/*Tests/*Properties` in 5 module poms | manual only |
| TypeScript | `tsconfig.json` per app; vite build implies vue-tsc-less builds (not verified for `vue-tsc` gate) | No |
| SAST | External Checkmarx/Cyberflows process exists (see `.claude/skills/secure-coding-sast`) — findings-driven, not repo-integrated | External |

## 8. Bottom-line assessment

Strengths: unusually heavy property-based testing culture (jqwik backend, fast-check frontend, 51 FE
property files); zero TODO debt; codified error-governance rules + fallback baseline; codified
screenshot verification workflow; consistent shared error envelope.
Weaknesses: no CI at all (nothing enforces the 383 backend / 150 frontend test files); HTTP/controller
layer near-universally untested (only 1 @WebMvcTest); admin-center frontend nearly untested (5 files);
no assertion-based browser e2e (Playwright used only for screenshots); no static analysis
(checkstyle/spotbugs/jacoco/prettier); 922-broad-catch / 714-swallow-empty fallback debt still
outstanding against the 2026-07-10 baseline; MFE apps (login, workflow-mfe, notification-mfe,
delegation-mfe, gateway-mfe) and shared/packages have zero tests.
