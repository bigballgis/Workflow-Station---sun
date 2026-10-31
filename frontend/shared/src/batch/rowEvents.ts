import { batchRuleChildren } from './batchFormRules'
import { isEmptyBatchEvent } from './controlContract'

export type BatchRowEventRunner = (row: Record<string, unknown>, rules: Array<Record<string, unknown>>,
  changedFields: string[], readonly: (field: string) => boolean) => Record<string, unknown>

function copy<T>(value: T): T { return value === undefined ? value : JSON.parse(JSON.stringify(value)) }

export class BatchRowEventError extends Error {
  constructor(message: string, readonly field: string) { super(message) }
}

function indexRules(rules: Array<Record<string, unknown>>): Map<string, Record<string, unknown>> {
  const fields = new Map<string, Record<string, unknown>>()
  const visit = (items: Array<Record<string, unknown>>) => items.forEach(rule => {
    if (typeof rule.field === 'string') fields.set(rule.field, rule)
    visit(batchRuleChildren(rule))
  })
  visit(rules)
  return fields
}

/** Each selected row receives an independent API and may write only declared editable fields. */
export function createBatchRowEventRunner<Api extends object>(
  parse: (raw: unknown) => ((context: { field: string; value: unknown; rule: Record<string, unknown>; api: Api }) => void) | null,
  createApi: (get: () => Record<string, unknown>, patch: (values: Record<string, unknown>) => void) => Api,
): BatchRowEventRunner {
  return (row, rules, changedFields, readonly) => {
    const fields = indexRules(rules)
    const next = copy(row)
    const api = createApi(() => copy(next), patch => {
      for (const field of Object.keys(patch)) {
        if (!fields.has(field) || readonly(field)) throw new BatchRowEventError('subTable.batchEventWriteDenied', field)
      }
      Object.assign(next, copy(patch))
    })
    const boundedApi = new Proxy(api, { get(target, key) {
      if (!['form', 'getValue', 'setValue'].includes(String(key))) throw new BatchRowEventError('subTable.batchEventApiUnsupported', String(key))
      return Reflect.get(target, key)
    } })
    for (const field of changedFields) {
      const rule = fields.get(field)
      if (!rule) continue
      const on = rule.on as Record<string, unknown> | undefined
      const stored = rule._on as Record<string, unknown> | undefined
      const raw = isEmptyBatchEvent(on?.change) ? stored?.change : on?.change
      if (isEmptyBatchEvent(raw)) continue
      const run = parse(raw)
      if (!run) throw new BatchRowEventError('subTable.batchEventInvalid', field)
      run({ field, value: copy(next[field]), rule: copy(rule), api: boundedApi })
    }
    return next
  }
}
