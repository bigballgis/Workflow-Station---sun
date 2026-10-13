import type { FieldDefinition, TableBinding } from '@/api/functionUnit'

interface MembershipRule { _bindingId?: unknown; props?: Record<string, unknown> }
interface BatchForm { rule?: unknown }

/** Add/Delete stay on business bindings; Add must expose every required user-entered field. */
export function validateBatchMembershipDesign(
  rules: MembershipRule[], forms: Record<string, BatchForm>, bindings: TableBinding[],
  fieldsForTable: (tableId: number) => FieldDefinition[],
): boolean {
  for (const rule of rules) {
    const add = rule.props?.allowBatchAdd === true
    if (!add && rule.props?.allowBatchDelete !== true) continue
    const bindingId = String(rule._bindingId ?? rule.props?._bindingId)
    const binding = bindings.find(item => String(item.id) === bindingId)
    if (!binding || binding.bindingType !== 'SUB' || binding.bindingMode !== 'EDITABLE'
      || (binding.bindingLinkMode ?? 'structuralFk') !== 'structuralFk') return false
    if (!add) continue
    const fields = fieldsForTable(binding.tableId)
    if (!fields.length) return false
    const represented = fieldNames(forms[bindingId]?.rule)
    for (const field of fields) {
      if (field.isForeignKey || field.isComputed || autoPrimaryKey(field)) continue
      if ((field.nullable === false || field.isPrimaryKey) && !represented.has(field.fieldName)) return false
    }
  }
  return true
}

function autoPrimaryKey(field: FieldDefinition): boolean {
  return field.isPrimaryKey && (field.pkGeneration?.strategy ?? field.pkGenerationJson?.strategy ?? 'uuid') !== 'manual'
}

function fieldNames(value: unknown): Set<string> {
  const names = new Set<string>()
  const visit = (item: unknown): void => {
    if (Array.isArray(item)) { item.forEach(visit); return }
    if (!item || typeof item !== 'object') return
    const rule = item as Record<string, unknown>
    if (typeof rule.field === 'string') names.add(rule.field)
    if (Array.isArray(rule.children)) rule.children.forEach(visit)
  }
  visit(value)
  return names
}
