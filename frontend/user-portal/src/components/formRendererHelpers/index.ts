/**
 * Shared helpers for FormRenderer — extracted so they can be imported by both
 * the Vue component (which uses <script setup>) and unit/property tests.
 *
 * This barrel re-exports the implementation modules so every existing
 * `from '@/components/formRendererHelpers'` import keeps working unchanged.
 *
 * - formRendererTypes:            interfaces / type aliases (FormField, portalViews, configJson)
 * - formRendererFieldUtils:       readonly/hidden flags + leaf-field flattening
 * - formRendererPortalViews:      sub-table portal display resolution / merge
 * - formRendererRuleParsing:      form-create rule → FormField layout parsing
 * - formRendererSubTableBindings: sub-table binding collection / merge / filter
 */

export * from './formRendererTypes'
export * from './formRendererFieldUtils'
export * from './formRendererPortalViews'
export * from './formRendererRuleParsing'
export * from './formRendererSubTableBindings'
