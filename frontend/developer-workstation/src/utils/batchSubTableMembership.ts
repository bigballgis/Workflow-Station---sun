import type { FieldDefinition, TableBinding } from '@/api/functionUnit'

interface MembershipRule { _bindingId?: unknown; props?: Record<string, unknown> }
interface BatchForm { rule?: unknown }

/** Add/Delete stay on business bindings; Add must expose every required user-entered field. */
export function validateBatchMembershipDesign(
  rules: MembershipRule[], forms: Record<string, BatchForm>, bindings: TableBinding[],
  fieldsForTable: (tableId: number) => FieldDefinition[],
  permissions: Record<string, string> = {},
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
      if ((field.nullable === false || field.isPrimaryKey) && permissions[`${bindingId}:${field.fieldName}`] === 'READONLY'
        && !verifiedReadonlyDefault(field)) return false
      if ((field.nullable === false || field.isPrimaryKey) && !represented.has(field.fieldName)) return false
    }
  }
  return true
}

function verifiedReadonlyDefault(field: FieldDefinition): boolean {
  const raw = field.defaultValue
  if (raw == null || raw === '' || field.isPrimaryKey) return false
  if (['INTEGER', 'INT', 'BIGINT', 'DECIMAL', 'NUMERIC', 'FLOAT', 'DOUBLE'].includes(field.dataType)) {
    if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(raw)) return false
    const number = Number(raw)
    if (!Number.isFinite(number)) return false
    if (['INTEGER', 'INT', 'BIGINT'].includes(field.dataType) && !Number.isSafeInteger(number)) return false
    if (['INTEGER', 'INT'].includes(field.dataType) && (number < -2147483648 || number > 2147483647)) return false
    if (['DECIMAL', 'NUMERIC'].includes(field.dataType) && field.precision != null && field.scale != null) {
      const [coefficient, exponent = '0'] = raw.replace(/^[+-]/, '').split(/[eE]/)
      const digits = coefficient!.replace('.', '')
      const scale = (coefficient!.split('.')[1]?.length ?? 0) - Number(exponent)
      const precision = digits.replace(/^0+/, '').length || 1
      if (Math.max(scale, 0) > field.scale || precision - scale > field.precision - field.scale) return false
    }
    return true
  }
  if (field.dataType === 'BOOLEAN') return ['true', 'false', '1', '0'].includes(String(raw))
  if (['VARCHAR', 'TEXT', 'CHAR'].includes(field.dataType)) return !field.length || raw.length <= field.length
  if (field.dataType === 'DATE') return validCalendarDate(raw)
  if (field.dataType === 'TIMESTAMP') return /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d+)?$/.test(raw)
    && validCalendarDate(raw.slice(0, 10)) && /^(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?$/.test(raw.slice(11))
  return false
}

function validCalendarDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const stamp = Date.parse(`${value}T00:00:00Z`)
  return !Number.isNaN(stamp) && new Date(stamp).toISOString().slice(0, 10) === value
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
