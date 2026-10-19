import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'
import { PLATFORM_ROW_UUID_FIELD } from '@/utils/subTableRowIdentity'

/** Scalar form editors cannot replace a binding's configured row identity or parent relationships. */
export function miParticipantScalarPatch(keys: Set<string>, values: Record<string, unknown>, mainFields: Set<string>,
  binding: { primaryKeyFields?: string[]; foreignKeyField?: string | null; fieldDefinitions?: BindingFieldDefinition[] }): Record<string, unknown> {
  const protectedFields = new Set([...mainFields, ...(binding.primaryKeyFields || []), PLATFORM_ROW_UUID_FIELD, '_wsRowVersion'])
  if (binding.foreignKeyField) protectedFields.add(binding.foreignKeyField)
  for (const field of binding.fieldDefinitions || []) {
    if (field.isPrimaryKey || field.isForeignKey) protectedFields.add(field.fieldName)
  }
  const patch: Record<string, unknown> = {}
  for (const key of keys) {
    if (!protectedFields.has(key) && values[key] !== undefined) patch[key] = values[key]
  }
  return patch
}
