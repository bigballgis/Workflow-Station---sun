/**
 * Resolve form-create rule default values for Portal FormRenderer and Designer Preview.
 * Designer stores defaults on `rule.value` and/or `rule.props.value` (Basis → Default value).
 */

export function hasMeaningfulFormValue(value: unknown): boolean {
  if (value === undefined || value === null) return false
  if (value === '') return false
  if (Array.isArray(value) && value.length === 0) return false
  return true
}

function ruleOptions(rule: Record<string, unknown>): Array<{ label?: unknown; value?: unknown }> | undefined {
  const props = rule.props as Record<string, unknown> | undefined
  const raw = rule.options ?? props?.options
  if (!Array.isArray(raw)) return undefined
  return raw.map((o) => {
    const opt = o as Record<string, unknown>
    return {
      label: opt.label ?? opt.key ?? opt.text,
      value: opt.value ?? opt.key,
    }
  })
}

/** Coerce table / designer default strings to the value shape expected by controls. */
export function coerceDefaultValueForRuleType(
  ruleType: string,
  raw: unknown,
  rule: Record<string, unknown>,
): unknown {
  if (raw === undefined || raw === null || raw === '') return raw

  const options = ruleOptions(rule)
  if ((ruleType === 'select' || ruleType === 'radio') && options?.length) {
    const s = String(raw).trim()
    const byValue = options.find(o => String(o.value) === s)
    if (byValue) return byValue.value
    const byLabel = options.find(o => String(o.label) === s)
    if (byLabel) return byLabel.value
    const num = Number(raw)
    if (!Number.isNaN(num)) {
      const byNum = options.find(o => o.value === num || String(o.value) === String(num))
      if (byNum) return byNum.value
    }
    return raw
  }

  if (ruleType === 'inputNumber') {
    const n = Number(raw)
    return Number.isNaN(n) ? raw : n
  }

  if (ruleType === 'switch') {
    if (typeof raw === 'boolean') return raw
    if (raw === 'true' || raw === 1 || raw === '1') return true
    if (raw === 'false' || raw === 0 || raw === '0') return false
    return raw
  }

  return raw
}

/** Read designer / persisted default from a form-create rule node. */
export function resolveRuleDefaultValue(rule: Record<string, unknown>): unknown | undefined {
  const props = (rule.props && typeof rule.props === 'object'
    ? rule.props
    : {}) as Record<string, unknown>
  let raw = rule.value
  if (!hasMeaningfulFormValue(raw)) {
    raw = props.value
  }
  if (!hasMeaningfulFormValue(raw)) {
    return undefined
  }
  return coerceDefaultValueForRuleType(String(rule.type ?? ''), raw, rule)
}

/** Walk rule tree and seed formData / previewModel (only fills empty keys when onlyIfEmpty). */
export function seedFormDataFromRules(
  rules: unknown[],
  target: Record<string, unknown>,
  onlyIfEmpty = true,
): void {
  if (!Array.isArray(rules)) return
  for (const raw of rules) {
    if (!raw || typeof raw !== 'object') continue
    const rule = raw as Record<string, unknown>
    const fieldKey = rule.field
    if (typeof fieldKey === 'string' && fieldKey) {
      const def = resolveRuleDefaultValue(rule)
      if (def !== undefined) {
        if (!onlyIfEmpty || !hasMeaningfulFormValue(target[fieldKey])) {
          target[fieldKey] = def
        }
      }
    }
    const children = getRuleChildrenForDefaults(rule)
    if (children.length) seedFormDataFromRules(children, target, onlyIfEmpty)
  }
}

function getRuleChildrenForDefaults(item: Record<string, unknown>): Record<string, unknown>[] {
  const props = item.props as Record<string, unknown> | undefined
  const sources = [
    item.children,
    props?.children,
    props?.list,
    props?.items,
    props?.fields,
  ]
  return (sources.find(children => Array.isArray(children)) as Record<string, unknown>[]) || []
}

/** Apply dw_field_definitions.defaultValue when building rules from Table Design import. */
export function applyTableFieldDefaultToRule(
  rule: Record<string, unknown>,
  field: { defaultValue?: string | null; dataType?: string },
): void {
  const dv = field.defaultValue
  if (dv === undefined || dv === null || String(dv).trim() === '') return
  const type = String(rule.type ?? '')
  const coerced = coerceDefaultValueForRuleType(type, dv, rule)
  rule.value = coerced
  const props = (rule.props && typeof rule.props === 'object'
    ? rule.props
    : {}) as Record<string, unknown>
  props.value = coerced
  rule.props = props
}

export type TableFieldDefLike = {
  fieldName: string
  defaultValue?: string | null
}

function tableDefaultByFieldName(
  fieldDefs: TableFieldDefLike[],
): Map<string, TableFieldDefLike> {
  const map = new Map<string, TableFieldDefLike>()
  for (const f of fieldDefs) {
    if (!f.fieldName) continue
    const dv = f.defaultValue
    if (dv === undefined || dv === null || String(dv).trim() === '') continue
    map.set(f.fieldName, f)
  }
  return map
}

/** Apply Table Design default only when the form rule has no Basis / rule default. */
export function applyTableFieldDefaultToRuleIfUnset(
  rule: Record<string, unknown>,
  field: TableFieldDefLike,
): boolean {
  if (resolveRuleDefaultValue(rule) !== undefined) return false
  applyTableFieldDefaultToRule(rule, field)
  return hasMeaningfulFormValue(rule.value)
}

function walkRulesForTableDefaults(
  rules: unknown[],
  defMap: Map<string, TableFieldDefLike>,
  apply: (rule: Record<string, unknown>, tableField: TableFieldDefLike) => void,
): void {
  if (!Array.isArray(rules) || defMap.size === 0) return
  for (const raw of rules) {
    if (!raw || typeof raw !== 'object') continue
    const rule = raw as Record<string, unknown>
    const fieldKey = rule.field
    if (typeof fieldKey === 'string' && defMap.has(fieldKey)) {
      apply(rule, defMap.get(fieldKey)!)
    }
    const children = getRuleChildrenForDefaults(rule)
    if (children.length) walkRulesForTableDefaults(children, defMap, apply)
  }
}

/** When true, Table Design default wins over stale rule.value / props.value (preview + save). */
export type TableDefaultsApplyOptions = {
  tableOverridesRule?: boolean
}

/**
 * Preview / runtime: fill model + rule.value from dw_field_definitions when Form Design has no default.
 */
export function applyTableFieldDefaultsToRulesAndModel(
  rules: unknown[],
  fieldDefs: TableFieldDefLike[],
  target: Record<string, unknown>,
  onlyIfEmpty = true,
  options?: TableDefaultsApplyOptions,
): void {
  const forceFromTable = options?.tableOverridesRule === true
  const defMap = tableDefaultByFieldName(fieldDefs)
  walkRulesForTableDefaults(rules, defMap, (rule, tableField) => {
    const fieldKey = String(rule.field)
    if (!forceFromTable) {
      if (resolveRuleDefaultValue(rule) !== undefined) return
      if (onlyIfEmpty && hasMeaningfulFormValue(target[fieldKey])) return
    }
    applyTableFieldDefaultToRule(rule, tableField)
    if (hasMeaningfulFormValue(rule.value)) {
      target[fieldKey] = rule.value
    }
  })
}

/** Save: persist Table Design defaults into config_json.rule so Portal reads rule.value. */
export function walkRulesApplyTableFieldDefaultsToPersistedRules(
  rules: unknown[],
  fieldDefs: TableFieldDefLike[],
): void {
  const defMap = tableDefaultByFieldName(fieldDefs)
  walkRulesForTableDefaults(rules, defMap, (rule, tableField) => {
    applyTableFieldDefaultToRule(rule, tableField)
  })
}

/** Copy seeded previewModel / formData values onto rule.value for form-create display. */
export function syncModelValuesOntoRules(
  rules: unknown[],
  model: Record<string, unknown>,
): void {
  if (!Array.isArray(rules)) return
  for (const raw of rules) {
    if (!raw || typeof raw !== 'object') continue
    const rule = raw as Record<string, unknown>
    const fieldKey = rule.field
    if (typeof fieldKey === 'string' && fieldKey && hasMeaningfulFormValue(model[fieldKey])) {
      const v = model[fieldKey]
      rule.value = v
      const props = (rule.props && typeof rule.props === 'object'
        ? { ...(rule.props as Record<string, unknown>) }
        : {}) as Record<string, unknown>
      props.value = v
      rule.props = props
    }
    const children = getRuleChildrenForDefaults(rule)
    if (children.length) syncModelValuesOntoRules(children, model)
  }
}

export function applyRuleDefaultToFormField(
  field: { defaultValue?: unknown },
  rule: Record<string, unknown>,
  tableField?: TableFieldDefLike | null,
): void {
  if (tableField) {
    const dv = tableField.defaultValue
    if (dv !== undefined && dv !== null && String(dv).trim() !== '') {
      const coerced = coerceDefaultValueForRuleType(String(rule.type ?? ''), dv, rule)
      if (hasMeaningfulFormValue(coerced)) {
        field.defaultValue = coerced as string | number | boolean | null
        return
      }
    }
  }
  const resolved = resolveRuleDefaultValue(rule)
  if (resolved !== undefined) {
    field.defaultValue = resolved as string | number | boolean | null
  }
}
