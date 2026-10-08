import type { FieldDefinition, TableBinding } from '@/api/functionUnit'

export type FilterBindingLike = Pick<TableBinding, 'id' | 'tableId' | 'bindingType' | 'filterFkFieldId' | 'foreignKeyField'>

export type FilterFieldLike = Pick<FieldDefinition, 'id' | 'fieldName' | 'isForeignKey' | 'refTableId'>

export function siblingSubBindingsOnTable(
  bindings: FilterBindingLike[],
  tableId: number,
  editingBindingId?: number | null,
): FilterBindingLike[] {
  return bindings.filter(b =>
    b.bindingType === 'SUB'
    && b.tableId === tableId
    && (editingBindingId == null || b.id !== editingBindingId),
  )
}

export function declaredFilterFkFields<T extends FilterFieldLike>(fields: T[]): T[] {
  return fields.filter(f => f.isForeignKey === true && f.refTableId != null)
}

export function usedStructuralFkNames(
  siblings: FilterBindingLike[],
  fields: FilterFieldLike[],
): Set<string> {
  const names = new Set<string>()
  const byId = new Map<number, string>()
  for (const field of fields) {
    if (field.id != null) byId.set(field.id, field.fieldName)
  }
  for (const binding of siblings) {
    if (binding.filterFkFieldId != null) {
      const mapped = byId.get(binding.filterFkFieldId)
      if (mapped) names.add(mapped)
    }
    const named = typeof binding.foreignKeyField === 'string' ? binding.foreignKeyField.trim() : ''
    if (named) names.add(named)
  }
  return names
}

/** True when this SUB table still has a declared FK that no sibling binding uses. */
export function subTableStillBindable(
  bindings: FilterBindingLike[],
  tableId: number,
  fields: FilterFieldLike[],
  editingBindingId?: number | null,
): boolean {
  const siblings = siblingSubBindingsOnTable(bindings, tableId, editingBindingId)
  if (siblings.length === 0) return true
  const declared = declaredFilterFkFields(fields)
  if (declared.length === 0) return false
  const used = usedStructuralFkNames(siblings, declared)
  return declared.some(field => !used.has(field.fieldName))
}

export function firstUnusedStructuralFkName(
  fields: FilterFieldLike[],
  usedNames: Set<string>,
): string | undefined {
  return declaredFilterFkFields(fields).find(field => !usedNames.has(field.fieldName))?.fieldName
}
