/**
 * Standard audit fields auto-appended to every table by TableDesignComponentImpl.
 * Designers may see them in Table Design but must not edit or remove them.
 */
export const TABLE_AUDIT_FIELD_NAMES = [
  'created_at',
  'created_by',
  'updated_at',
  'updated_by',
] as const

export type TableAuditFieldName = (typeof TABLE_AUDIT_FIELD_NAMES)[number]

const TABLE_AUDIT_FIELD_SET = new Set<string>(TABLE_AUDIT_FIELD_NAMES)

export function isTableAuditField(fieldName: string | null | undefined): boolean {
  if (!fieldName) return false
  return TABLE_AUDIT_FIELD_SET.has(fieldName.trim().toLowerCase())
}

/** Exclude platform-managed audit columns from Form Design canvas / list view / import. */
export function filterOutTableAuditFields<T extends { fieldName?: string | null }>(fields: T[]): T[] {
  return fields.filter((f) => !isTableAuditField(f.fieldName))
}
