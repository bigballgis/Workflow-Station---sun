/**
 * Re-export shim — the canonical implementation lives in frontend/shared/src/pkGenerationConfig.ts
 * (single source across portal / developer-workstation / admin-center; edit it there, never fork here).
 * Portal historically only used the parse side; the shared module's serialize exports are harmless extras.
 */
export * from '@platform-shared/pkGenerationConfig'
