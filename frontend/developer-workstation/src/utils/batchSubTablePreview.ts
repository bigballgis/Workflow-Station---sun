import { extractBatchFields } from '@platform-shared/batch/fields'
import type { BatchAssignmentCapability } from '@platform-shared/batch/workspaceTypes'
import { hasEnabledBatchOperation, validateEnabledBatchForms } from './batchSubTableConfig'
import { isAssignmentConfigured, type AssignmentConfig } from './miAssignmentConfig'

export interface BatchPreviewDefinition extends BatchAssignmentCapability {
  invalid: boolean
  flags: { add: boolean; edit: boolean; delete: boolean; assign: boolean }
  rule: Array<Record<string, unknown>>
  options?: Record<string, unknown>
  fieldWriteModes: Record<string, 'EDITABLE' | 'READONLY'>
}

/** Per placed control, never OR capabilities of controls sharing a binding. */
export function buildBatchPreviewDefinition(
  rule: Record<string, unknown>, batchForm: unknown, assignmentConfig?: AssignmentConfig,
  permissions: Record<string, string> = {},
): BatchPreviewDefinition | undefined {
  const placed = rule as { batchControlId?: unknown; _bindingId?: unknown; props?: Record<string, unknown> }
  if (!hasEnabledBatchOperation(placed)) return undefined
  const props = placed.props ?? {}
  const bindingId = String(placed._bindingId ?? props._bindingId ?? '')
  const rawId = placed.batchControlId ?? props.batchControlId
  const batchControlId = typeof rawId === 'string' ? rawId : ''
  const form = batchForm && typeof batchForm === 'object' ? batchForm as Record<string, unknown> : undefined
  const validForm = validateEnabledBatchForms([placed], { [bindingId]: form ?? {} })
  const rules = validForm ? form!.rule as Array<Record<string, unknown>> : []
  const fields = extractBatchFields(rules)
  const assignmentValid = props.allowBatchAssign !== true || isAssignmentConfigured(assignmentConfig)
  const invalid = !batchControlId.trim() || !validForm || !assignmentValid
    || fields === null || (!fields.length && props.allowBatchAssign !== true)
  return {
    batchControlId, bindingId, rule: rules, options: form?.options as Record<string, unknown> | undefined, invalid,
    fieldWriteModes: Object.fromEntries((fields ?? []).map(field => [field.field,
      permissions[`${bindingId}:${field.field}`] === 'READONLY' ? 'READONLY' : 'EDITABLE'])),
    ...(assignmentConfig ? { assignmentConfig } : {}),
    flags: {
      add: props.allowBatchAdd === true && props.allowAdd !== false,
      edit: props.allowBatchEdit === true && props.allowEdit !== false,
      delete: props.allowBatchDelete === true && props.allowDelete !== false,
      assign: props.allowBatchAssign === true && props.allowEdit !== false,
    },
  }
}
