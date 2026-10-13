/**
 * The portal receives this object only from TaskFormData.  Treat it as untrusted
 * transport data nevertheless: a malformed projection must disable the workspace,
 * never fall back to the live form rule.
 */
import { isAssignmentConfigured, normalizeAssignmentConfig, type AssignmentConfig } from './miAssignmentConfig'
import type { BindingFieldDefinition } from './subTableRowRuntime'
import type { FkFillSourceConfig } from './tableFkRuntime'

export interface BatchRowDesign {
  binding: {
    tableId: number
    tableName?: string
    bindingType: string
    bindingMode: string
    bindingLinkMode: string
    primaryKeyFields: string[]
    fieldDefinitions: BindingFieldDefinition[]
    filterFkFieldName?: string
    filterFkRefTableId?: number
    foreignKeyField?: string
    fkFillSources?: FkFillSourceConfig[]
  }
  fields: Array<{ fieldName: string; dataType?: string; nullable?: boolean; defaultValue?: unknown }>
  parentTablesById: Record<number, { fieldDefinitions: BindingFieldDefinition[] }>
}

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
  /** Present only for a frozen, BPMN-backed Batch Assign control. */
  assignmentConfig?: AssignmentConfig
  rowDesign?: BatchRowDesign
}

export type BatchEditableField = {
  field: string
  label: string
  type: string
  options: Array<{ label: string; value: unknown }>
  required: boolean
  defaultValue?: unknown
  min?: number
  max?: number
  maxlength?: number
  validators?: Array<{ type?: string; min?: number; max?: number; len?: number }>
}

const supportedEditTypes = new Set(['input', 'text', 'inputNumber', 'number', 'switch', 'select', 'date', 'datePicker', 'datetime'])

function record(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}

function parseRowDesign(value: unknown): BatchRowDesign | null {
  const raw = record(value)
  const binding = record(raw?.binding)
  if (!raw || !binding || typeof binding.tableId !== 'number' || !Number.isInteger(binding.tableId)
    || !Array.isArray(binding.primaryKeyFields) || !binding.primaryKeyFields.every(field => typeof field === 'string')
    || !Array.isArray(binding.fieldDefinitions) || !binding.fieldDefinitions.every(field => typeof record(field)?.fieldName === 'string')
    || !Array.isArray(raw.fields) || !raw.fields.every(field => typeof record(field)?.fieldName === 'string')
    || !record(raw.parentTablesById)) return null
  return raw as unknown as BatchRowDesign
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
  const assignmentConfig = normalizeAssignmentConfig(raw.assignmentConfig)
  if (flags.allowBatchAssign && !isAssignmentConfigured(assignmentConfig)) return null
  const rowDesign = parseRowDesign(raw.rowDesign)
  if ((flags.allowBatchAdd || flags.allowBatchDelete) && !rowDesign) return null

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
    ...(assignmentConfig ? { assignmentConfig } : {}),
    ...(rowDesign ? { rowDesign } : {}),
  }
}

/** Batch Assign is a separate frozen capability; it never inherits Batch Edit. */
export function allowsFrozenBatchAssign(capability: SubTableBatchCapability | null, bindingId: number | null | undefined): boolean {
  return capability?.capabilities.allowBatchAssign === true
    && capability.bindingId === String(bindingId ?? '')
    && capability.originalCrud.allowEdit !== false
    && isAssignmentConfigured(capability.assignmentConfig)
}

export function allowsFrozenBatchEdit(capability: SubTableBatchCapability | null, bindingId: number | null | undefined): boolean {
  return capability?.capabilities.allowBatchEdit === true
    && capability.bindingId === String(bindingId ?? '')
    && capability.originalCrud.allowEdit !== false
}

export function allowsFrozenBatchDelete(capability: SubTableBatchCapability | null, bindingId: number | null | undefined): boolean {
  return capability?.capabilities.allowBatchDelete === true
    && capability.bindingId === String(bindingId ?? '')
    && capability.originalCrud.allowDelete !== false
    && capability.rowDesign?.binding.bindingType === 'SUB'
    && capability.rowDesign.binding.bindingMode === 'EDITABLE'
    && capability.rowDesign.binding.bindingLinkMode === 'structuralFk'
}

export function allowsFrozenBatchAdd(capability: SubTableBatchCapability | null, bindingId: number | null | undefined): boolean {
  return capability?.capabilities.allowBatchAdd === true
    && capability.bindingId === String(bindingId ?? '')
    && capability.originalCrud.allowAdd !== false
    && capability.rowDesign?.binding.bindingType === 'SUB'
    && capability.rowDesign.binding.bindingMode === 'EDITABLE'
    && capability.rowDesign.binding.bindingLinkMode === 'structuralFk'
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
    if (rule.type === 'miAssignment') return // Dedicated Assignment intent, never an ordinary editable column.
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
        required: rule.required === true || (Array.isArray(rule.validate) && rule.validate.some(item => record(item)?.required === true)),
        defaultValue: rule.value,
        ...(Array.isArray(rule.validate) ? { validators: rule.validate as NonNullable<BatchEditableField['validators']> } : {}),
        ...(typeof props?.min === 'number' ? { min: props.min } : {}),
        ...(typeof props?.max === 'number' ? { max: props.max } : {}),
        ...(typeof props?.maxlength === 'number' ? { maxlength: props.maxlength } : {}),
      })
    }
    Object.values(rule).forEach(child => {
      if (child !== rule.props && (Array.isArray(child) || record(child))) visit(child)
    })
  }
  visit(capability.batchForm.rule)
  return invalid || !fields.length ? null : fields
}
