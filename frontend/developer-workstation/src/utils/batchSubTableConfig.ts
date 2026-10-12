/**
 * Stable identity for one sub-table control on a form canvas. It is deliberately
 * independent of bindingId: multiple controls may render the same binding with
 * different batch capabilities.
 */
export function createBatchControlId(): string {
  const randomUuid = globalThis.crypto?.randomUUID
  if (typeof randomUuid === 'function') {
    return `stbc_${randomUuid.call(globalThis.crypto)}`
  }

  const bytes = new Uint8Array(16)
  globalThis.crypto?.getRandomValues?.(bytes)
  const suffix = [...bytes].map((value) => value.toString(16).padStart(2, '0')).join('')
  if (!suffix || /^0+$/.test(suffix)) {
    throw new Error('Secure randomness is required to create a batch control id')
  }
  return `stbc_${suffix}`
}

const BATCH_OPERATION_FIELDS = [
  'allowBatchAdd',
  'allowBatchEdit',
  'allowBatchDelete',
  'allowBatchAssign',
] as const

type RuleLike = {
  batchControlId?: unknown
  _bindingId?: unknown
  props?: Record<string, unknown>
}

type BatchFormLike = {
  schemaVersion?: unknown
  rule?: unknown
}

/** Batch permissions are opt-in: legacy strings such as "true" never enable one. */
export function hasEnabledBatchOperation(rule: RuleLike): boolean {
  return BATCH_OPERATION_FIELDS.some((field) => rule.props?.[field] === true)
}

/**
 * A batch-enabled SubTable is addressed by a stable control ID, never by a
 * binding ID or its array position. The props slot is accepted while the
 * form-create panel is editing a rule; the SubTable parser persists it at the
 * rule's top level.
 */
export function validateEnabledBatchControlIds(rules: RuleLike[]): boolean {
  const ids = new Set<string>()
  for (const rule of rules) {
    if (!hasEnabledBatchOperation(rule)) continue
    const rawId = rule.batchControlId ?? rule.props?.batchControlId
    if (typeof rawId !== 'string' || rawId.trim() === '' || ids.has(rawId)) return false
    ids.add(rawId)
  }
  return true
}

/**
 * A batch capability is never publishable without its separate batch form.
 * `subForms` is deliberately not inspected: it remains the legacy single-row
 * editor and cannot become an implicit batch configuration.
 */
export function validateEnabledBatchForms(
  rules: RuleLike[],
  batchForms: Record<string, BatchFormLike> | undefined,
): boolean {
  for (const rule of rules) {
    if (!hasEnabledBatchOperation(rule)) continue
    const rawBindingId = rule._bindingId ?? rule.props?._bindingId
    if (rawBindingId === null || rawBindingId === undefined || String(rawBindingId).trim() === '') return false
    const batchForm = batchForms?.[String(rawBindingId)]
    if (batchForm?.schemaVersion !== 1 || !Array.isArray(batchForm.rule) || batchForm.rule.length === 0) {
      return false
    }
  }
  return true
}
