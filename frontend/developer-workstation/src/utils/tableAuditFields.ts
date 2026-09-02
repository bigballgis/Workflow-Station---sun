/**
 * Standard audit fields auto-appended to every table by TableDesignComponentImpl.
 * Designers may see them in Table Design but must not edit or remove them.
 *
 * Name decision delegates to @platform-shared/systemAuditFields (single source across
 * portal / DW frontends, synced with backend platform-common SystemAuditFields);
 * only the DW-specific helpers live here.
 */
import { SYSTEM_AUDIT_FIELDS, isSystemAuditField } from '@platform-shared/systemAuditFields'

export const TABLE_AUDIT_FIELD_NAMES = SYSTEM_AUDIT_FIELDS

export type TableAuditFieldName = (typeof SYSTEM_AUDIT_FIELDS)[number]

export function isTableAuditField(fieldName: string | null | undefined): boolean {
  if (!fieldName) return false
  return isSystemAuditField(fieldName)
}

/**
 * Drop platform-managed audit columns from "import every table field" auto-fill
 * (new binding / empty form). Explicit Import Table Fields still includes them
 * as readonly canvas controls — see {@link shouldIncludeFieldOnFormCanvas}.
 */
export function filterOutTableAuditFields<T extends { fieldName?: string | null }>(fields: T[]): T[] {
  return fields.filter((f) => !isTableAuditField(f.fieldName))
}
