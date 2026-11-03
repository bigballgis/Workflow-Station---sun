import { batchRuleChildren } from './batchFormRules'
import { isEmptyBatchEvent } from './controlContract'
import { createPortalFormApi, type PortalFormApi, type PortalFormApiOverlays } from '../formEvents/api'
import { firstBatchFieldError } from './validation'
import type { BatchEditableField } from './fields'

export type BatchRule = Record<string, any>
export type BatchEventParser = (raw: unknown) => ((context: { field: string; value: unknown; rule: Record<string, unknown>; api: PortalFormApi; args?: unknown; config?: unknown; data?: unknown }) => unknown) | null
export interface BatchRowEventRunner {
  (row: Record<string, unknown>, rules: BatchRule[], changedFields: string[], readonly: (field: string) => boolean): Record<string, unknown>
  createSession(row: Record<string, unknown>, rules: BatchRule[], readonly: (field: string) => boolean, options?: BatchRule): BatchEventSession
}
export class BatchRowEventError extends Error {
  constructor(message: string, readonly field: string) { super(message) }
}
function copy<T>(value: T): T { return value === undefined ? value : JSON.parse(JSON.stringify(value)) }

/** One record's draft and UI state, using the same API as ordinary forms. */
export class BatchEventSession {
  data: Record<string, unknown>
  readonly api: PortalFormApi
  readonly hidden = new Map<string, boolean>()
  readonly display = new Map<string, boolean>()
  readonly required = new Map<string, boolean>()
  readonly disabled = new Map<string, boolean>()
  readonly labels = new Map<string, string>()
  readonly choices = new Map<string, Array<{ label: string; value: string | number }>>()
  readonly errors = new Map<string, string>()
  readonly notifications = new Map<string, { uniqueId: string; message: string; level: 'ERROR' | 'WARNING' | 'INFO' }>()
  readonly lookupFilters = new Map<string, Array<{ fieldName: string; value: string; matchType?: 'eq' | 'contains' | 'startsWith' | 'endsWith' }>>()
  readonly refreshes = new Map<string, number>()
  focusField = ''
  private readonly fields = new Map<string, BatchRule>()
  private readonly allRules: BatchRule[] = []
  private readonly pending = new Set<string>()
  private dispatching = false
  private bootstrapped = false

  constructor(row: Record<string, unknown>, rules: BatchRule[], private readonly readonlyField: (field: string) => boolean,
    private readonly parse: BatchEventParser, private readonly options: BatchRule = {}) {
    this.data = copy(row)
    const visit = (items: BatchRule[]) => items.forEach(rule => {
      this.allRules.push(rule)
      if (typeof rule.field === 'string' && rule.field) {
        this.fields.set(rule.field, rule)
        if (rule.hidden === true) this.hidden.set(rule.field, true)
        if (rule.display === false) this.display.set(rule.field, false)
        if (this.readonlyField(rule.field)) this.disabled.set(rule.field, true)
      }
      visit(batchRuleChildren(rule))
      for (const control of Array.isArray(rule.control) ? rule.control : []) if (Array.isArray(control.rule)) visit(control.rule)
    })
    visit(rules)
    const keys = () => [...this.fields.keys()]
    const notify = () => {}
    const overlays: PortalFormApiOverlays = {
      disabled: { state: { flags: this.disabled }, notify, getAllFieldKeys: keys },
      labels: { state: this.labels, notify, getDesignerLabel: field => String(this.fields.get(field)?.title ?? field) },
      options: { state: this.choices, notify, getDesignerOptions: field => this.fields.get(field)?.options ?? this.fields.get(field)?.props?.options ?? [] },
      notifications: { set: item => this.notifications.set(item.uniqueId, item), clear: id => this.notifications.delete(id) },
      lookupFilter: { set: (field, conditions) => this.lookupFilters.set(field, copy(conditions)), clear: field => this.lookupFilters.delete(field),
        refresh: field => this.refreshes.set(field, (this.refreshes.get(field) ?? 0) + 1) },
      focus: field => { this.focusField = field },
    }
    this.api = createPortalFormApi(() => copy(this.data), patch => this.patch(patch), name => this.fields.has(name) ? name : [...this.fields].find(([, rule]) => rule.title === name)?.[0] ?? name,
    { state: { hidden: this.hidden, display: this.display }, notify, getAllFieldKeys: keys },
    { setFieldError: (field, message) => { this.errors.set(field, message) }, clearFieldError: field => { this.errors.delete(field) } },
    { state: { flags: this.required }, notify, getAllFieldKeys: keys }, overlays)
  }

  private patch(patch: Record<string, unknown>, userInput = false) {
    for (const field of Object.keys(patch)) {
      const rule = this.fields.get(field)
      if (!rule || this.readonlyField(field) || (!userInput && rule._batchAssignmentOwned === true)) {
        throw new BatchRowEventError('subTable.batchEventWriteDenied', field)
      }
    }
    for (const [field, value] of Object.entries(patch)) {
      if (JSON.stringify(this.data[field]) === JSON.stringify(value)) continue
      this.data[field] = copy(value)
      this.pending.add(field)
    }
    this.flush()
  }
  private run(raw: unknown, field: string, rule: BatchRule = {}, args: unknown[] = [], value: unknown = this.data[field], extra: { config?: unknown; data?: unknown } = {}): unknown {
    if (Array.isArray(raw)) { let result: unknown; for (const handler of raw) { result = this.run(handler, field, rule, args, value, extra); if (result === false) return false } return result }
    if (isEmptyBatchEvent(raw)) return
    const handler = this.parse(raw)
    if (!handler) throw new BatchRowEventError('subTable.batchEventInvalid', field)
    const result = handler({ field, value: copy(value), rule: copy(rule), api: this.api, args, ...extra })
    if (result && typeof (result as PromiseLike<unknown>).then === 'function') throw new BatchRowEventError('subTable.batchEventInvalid', field)
    return result
  }
  private handler(rule: BatchRule, kind: 'on' | 'hook', event: string): unknown {
    const primary = rule[kind]?.[event]
    return isEmptyBatchEvent(primary) ? rule[`_${kind}`]?.[event] : primary
  }
  private flush() {
    if (this.dispatching) return
    this.dispatching = true
    let count = 0
    try {
      while (this.pending.size) {
        if (++count > 1000) throw new BatchRowEventError('subTable.batchEventInvalid', 'change cycle')
        const field = this.pending.values().next().value!
        this.pending.delete(field)
        const rule = this.fields.get(field)!
        this.run(this.handler(rule, 'on', 'change'), field, rule, [copy(this.data[field])])
        this.run(this.handler(rule, 'hook', 'value'), field, rule, [copy(this.data[field])])
        this.run(this.options.onChange, field, rule, [field, copy(this.data[field]), { api: this.api, rule }])
      }
    } finally { this.dispatching = false; this.pending.clear() }
  }
  snapshot() {
    return { data: copy(this.data), maps: [this.hidden, this.display, this.required, this.disabled, this.labels, this.choices,
      this.errors, this.notifications, this.lookupFilters, this.refreshes].map(map => [...map.entries()]), focus: this.focusField }
  }
  restore(snapshot: ReturnType<BatchEventSession['snapshot']>) {
    this.data = copy(snapshot.data)
    const maps: Map<string, any>[] = [this.hidden, this.display, this.required, this.disabled, this.labels, this.choices,
      this.errors, this.notifications, this.lookupFilters, this.refreshes]
    maps.forEach((map, index) => { map.clear(); for (const [key, value] of snapshot.maps[index]!) map.set(key, value) })
    this.focusField = snapshot.focus
  }
  changeValues(values: Record<string, unknown>) { this.patch(values, true) }
  change(field: string, value: unknown) { this.changeValues({ [field]: value }) }
  event(field: string, name: string, args: unknown[] = []) {
    const rule = this.fields.get(field)
    if (rule) this.run(this.handler(rule, 'on', name), field, rule, args)
  }
  ruleEvent(rule: BatchRule, name: string, args: unknown[] = [], kind: 'on' | 'hook' = 'on') { this.run(this.handler(rule, kind, name), String(rule.field ?? rule.type), rule, args) }
  bootstrap() {
    if (this.bootstrapped) return
    this.bootstrapped = true
    this.run(this.options.onCreated, '__form__')
    for (const rule of this.allRules) this.run(this.handler(rule, 'hook', 'load'), String(rule.field ?? rule.type), rule)
    this.run(this.options.onChange, '__bootstrap__')
    for (const rule of this.allRules) this.run(this.handler(rule, 'hook', 'mounted'), String(rule.field ?? rule.type), rule)
    this.run(this.options.onMounted, '__form__')
  }
  beforeFetch(config: unknown, data: unknown) {
    return this.run(this.options.beforeFetch, '__fetch__', {}, [], undefined, { config, data })
  }
  reload() { this.run(this.options.onReload, '__form__') }
  reset() { this.run(this.options.onReset, '__form__') }
  validate(fields: BatchEditableField[]) {
    if (this.errors.size) throw new BatchRowEventError('subTable.batchEventValidation', [...this.errors.values()][0]!)
    const error = firstBatchFieldError([this.data], fields.map(field => ({ ...field,
      required: this.required.get(field.field) ?? field.required,
      options: this.choices.get(field.field) ?? field.options,
    })))
    if (error) throw new BatchRowEventError('subTable.batchEventValidation', error.label)
  }
  submit(fields: BatchEditableField[] = []) {
    if (this.run(this.options.beforeSubmit, '__submit__', {}, [], this.data) === false) throw new BatchRowEventError('subTable.batchEventSubmitRejected', 'beforeSubmit')
    if (this.errors.size) throw new BatchRowEventError('subTable.batchEventValidation', [...this.errors.values()][0]!)
    this.validate(fields)
    this.run(this.options.onSubmit, '__submit__', {}, [], this.data)
    this.validate(fields)
  }
}

/** Compatibility entry point; interactive workspaces retain sessions until Apply/Cancel. */
export function createBatchRowEventRunner(parse: BatchEventParser): BatchRowEventRunner {
  return Object.assign((row: Record<string, unknown>, rules: BatchRule[], changed: string[], readonly: (field: string) => boolean) => {
    const session = new BatchEventSession(row, rules, readonly, parse)
    // The caller has already applied the direct edits; dispatch each changed field once.
    for (const field of changed) session.event(field, 'change')
    return session.data
  }, { createSession: (row: Record<string, unknown>, rules: BatchRule[], readonly: (field: string) => boolean, options?: BatchRule) =>
    new BatchEventSession(row, rules, readonly, parse, options) })
}
