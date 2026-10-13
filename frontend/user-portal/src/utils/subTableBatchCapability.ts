/**
 * The portal receives this object only from TaskFormData.  Treat it as untrusted
 * transport data nevertheless: a malformed projection must disable the workspace,
 * never fall back to the live form rule.
 */
export interface SubTableBatchCapability {
  batchControlId: string
  bindingId: string
  capabilities: {
    allowBatchAdd: boolean
    allowBatchEdit: boolean
    allowBatchDelete: boolean
    allowBatchAssign: boolean
  }
  originalCrud: {
    allowAdd?: unknown
    allowEdit?: unknown
    allowDelete?: unknown
  }
  batchForm: {
    schemaVersion: number
    rule: Array<Record<string, unknown>>
    options?: Record<string, unknown>
  }
}

export type BatchEditableField = {
  field: string
  label: string
  type: string
  options: Array<{ label: string; value: unknown }>
}

const supportedEditTypes = new Set(['input', 'text', 'inputNumber', 'number', 'switch', 'select', 'date', 'datePicker', 'datetime'])

function record(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}

/** Parse schema v1 exactly; absent values are never treated as enabled. */
export function parseSubTableBatchCapability(value: unknown): SubTableBatchCapability | null {
  const raw = record(value)
  const capabilityFlags = record(raw?.capabilities)
  const originalCrud = record(raw?.originalCrud)
  const batchForm = record(raw?.batchForm)
  if (
    !raw || !capabilityFlags || !originalCrud || !batchForm
    || typeof raw.batchControlId !== 'string' || !raw.batchControlId.trim()
    || (typeof raw.bindingId !== 'string' && typeof raw.bindingId !== 'number')
    || batchForm.schemaVersion !== 1 || !Array.isArray(batchForm.rule)
  ) return null

  const flags = {
    allowBatchAdd: capabilityFlags.allowBatchAdd === true,
    allowBatchEdit: capabilityFlags.allowBatchEdit === true,
    allowBatchDelete: capabilityFlags.allowBatchDelete === true,
    allowBatchAssign: capabilityFlags.allowBatchAssign === true,
  }
  if (!Object.values(flags).some(Boolean)) return null
  if (!batchForm.rule.every(item => record(item) !== null)) return null

  return {
    batchControlId: raw.batchControlId.trim(),
    bindingId: String(raw.bindingId),
    capabilities: flags,
    originalCrud,
    batchForm: {
      schemaVersion: 1,
      rule: batchForm.rule as Array<Record<string, unknown>>,
      ...(record(batchForm.options) ? { options: batchForm.options as Record<string, unknown> } : {}),
    },
  }
}

export function allowsFrozenBatchEdit(capability: SubTableBatchCapability | null, bindingId: number | null | undefined): boolean {
  return capability?.capabilities.allowBatchEdit === true
    && capability.bindingId === String(bindingId ?? '')
    && capability.originalCrud.allowEdit !== false
}

function optionList(value: unknown): Array<{ label: string; value: unknown }> {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (item !== null && typeof item === 'object' && !Array.isArray(item)) {
      const option = item as Record<string, unknown>
      const optionValue = option.value ?? option.label
      return optionValue === undefined ? [] : [{ label: String(option.label ?? optionValue), value: optionValue }]
    }
    return [{ label: String(item), value: item }]
  })
}

/**
 * Flattens only field-bearing rules that the first Batch Edit renderer can express.
 * A field-bearing unsupported node invalidates the whole workspace rather than being
 * silently omitted. Layout-only containers are traversed.
 */
export function extractBatchEditableFields(capability: SubTableBatchCapability): BatchEditableField[] | null {
  const fields: BatchEditableField[] = []
  const seen = new Set<string>()
  let invalid = false
  const visit = (value: unknown) => {
    if (Array.isArray(value)) return value.forEach(visit)
    const rule = record(value)
    if (!rule) return
    const field = typeof rule.field === 'string' ? rule.field.trim() : ''
    if (field) {
      const type = typeof rule.type === 'string' ? rule.type : 'input'
      if (!supportedEditTypes.has(type) || seen.has(field)) {
        invalid = true
        return
      }
      seen.add(field)
      const props = record(rule.props)
      fields.push({
        field,
        type,
        label: typeof rule.title === 'string' && rule.title.trim() ? rule.title : field,
        options: optionList(props?.options ?? rule.options),
      })
    }
    Object.values(rule).forEach(child => {
      if (child !== rule.props && (Array.isArray(child) || record(child))) visit(child)
    })
  }
  visit(capability.batchForm.rule)
  return invalid || !fields.length ? null : fields
}
