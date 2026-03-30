/**
 * Shared helpers for SnapshotDiffRenderer — extracted so they can be
 * imported by both the Vue component and property tests.
 */
import type { FormField } from './formRendererHelpers'

export interface DiffRow {
  key: string
  label: string
  snapshotValue: unknown
  liveValue: unknown
  changed: boolean
}

/**
 * Compute diff rows from snapshot and live values.
 * Pure function for property testing.
 */
export function computeDiffRows(
  snapshotValues: Record<string, unknown>,
  liveValues: Record<string, unknown>,
  fields: FormField[]
): DiffRow[] {
  return fields
    .filter(f => f.type !== 'subTable')
    .map(field => {
      const sv = snapshotValues[field.key]
      const lv = liveValues[field.key]
      return {
        key: field.key,
        label: field.label,
        snapshotValue: sv,
        liveValue: lv,
        changed: JSON.stringify(sv) !== JSON.stringify(lv),
      }
    })
}
