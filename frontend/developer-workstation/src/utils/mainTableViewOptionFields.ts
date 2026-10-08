import type { FormDefinition } from '@/api/functionUnit'
import { resolveFormTableId } from '@/utils/formDesigner'

export interface FormWithBindings {
  form: FormDefinition
  bindings: Array<{ id?: number; tableId: number; bindingType?: string }>
}

function walkRules(node: unknown, visit: (rule: Record<string, unknown>) => void): void {
  if (node == null) return
  if (Array.isArray(node)) {
    for (const child of node) walkRules(child, visit)
    return
  }
  if (typeof node !== 'object') return
  const obj = node as Record<string, unknown>
  visit(obj)
  for (const value of Object.values(obj)) {
    if (value && typeof value === 'object') walkRules(value, visit)
  }
}

function addStaticOptionFields(rules: unknown, fields: Set<string>): void {
  walkRules(rules, (rule) => {
    const field = rule.field
    const options = rule.options
    if (typeof field !== 'string' || !field) return
    if (!Array.isArray(options) || options.length === 0) return
    if (!options.some(o => o && typeof o === 'object' && (o as { value?: unknown }).value != null)) return
    fields.add(field)
  })
}

/**
 * Fields that a form renders for `tableId` with a static option list (select / radio / checkbox
 * widgets carrying `options: [{label, value}]`). Only the part of each form that draws this table
 * counts: the top-level `rule` when the form's PRIMARY binding is the table, and
 * `subForms[bindingId]` for each sub-table binding on it. Only these columns can offer the
 * value / label display choice; the Portal resolves labels from the same widgets at read time.
 */
export function collectStaticOptionFields(forms: FormWithBindings[], tableId: number): Set<string> {
  const fields = new Set<string>()
  for (const { form, bindings } of forms || []) {
    const cfg = (form.configJson || {}) as { rule?: unknown; subForms?: Record<string, unknown> }
    const primaryTableId = resolveFormTableId({ tableBindings: bindings, boundTableId: form.boundTableId })
    if (primaryTableId === tableId) addStaticOptionFields(cfg.rule, fields)
    for (const b of bindings || []) {
      if (b.tableId !== tableId || b.id == null) continue
      addStaticOptionFields(cfg.subForms?.[String(b.id)], fields)
    }
  }
  return fields
}
