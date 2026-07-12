# frontend/shared — cross-app shared TS sources

Single source for logic that must stay **identical** across the three frontends
(user-portal / developer-workstation / admin-center). Prior to this package the same
code existed as per-app copies kept in sync by hand ("DUPLICATE-BY-DESIGN" comments);
every sync miss became a parity bug.

## How it is consumed

No npm package / build step. Each app maps the alias in its `vite.config.ts` and
`tsconfig.json`:

```ts
'@platform-shared': resolve(__dirname, '../shared/src')
```

Apps keep thin re-export shims at their historical paths (e.g.
`src/utils/tableFkRuntime.ts` → `export * from '@platform-shared/tableFkRuntime'`)
so existing import sites stay untouched.

## Rules

- **Pure TS only** — no Vue SFCs, no app-specific imports (`@/...` from an app will not
  resolve here), no Element Plus. Anything with framework/app coupling stays in the app.
- Behavioral changes here affect all three apps: run each affected app's test suite.
- When migrating another duplicate pair into this package: verify the copies are
  semantically identical first (diff), move the canonical body here, replace the app
  copies with re-export shims, and delete their DUPLICATE-BY-DESIGN comments.

## Current modules

| Module | Consumers | Notes |
|---|---|---|
| `tableFkRuntime.ts` | portal, DW | FK/PK runtime (readonly/hidden decision, FK fill, composite PK encode). Backend row-key counterpart: platform-common `SubTableRowKeySupport`. |
| `pkGenerationConfig.ts` | portal, DW, admin | PK generation strategy parse/serialize. |

## Known candidates not yet migrated

- `subTableRowRuntime` (portal split-directory vs DW single file — structurally diverged,
  MI hot path; needs its own reconciliation + MI regression cycle before merging).
