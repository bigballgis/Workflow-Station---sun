import type { InjectionKey, Ref } from 'vue'
import type { FormField, PortalViewContext } from './formRendererHelpers'

export interface FormRendererFieldsContext {
  formData: Ref<Record<string, unknown>>
  readonly: Ref<boolean>
  labelWidth: Ref<string> | string
  labelPosition?: Ref<'left' | 'right' | 'top'> | 'left' | 'right' | 'top'
  uploadUrl?: string
  taskId?: string
  viewContext: PortalViewContext
  subTableBindings: Ref<unknown[]> | unknown[]
  linkableSubTableBindings: Ref<unknown[]> | unknown[]
  enableSubTablePolling?: boolean
  subTablePollingInterval?: number
  suppressLinkFormInitialData?: boolean
  showLinkFormDialogFooter?: boolean
  lookupSelectedData: Ref<Record<string, unknown>>
  lookupLoadedViewFields: Ref<Record<string, unknown[]>>
  engineVisibility: Ref<Map<string, boolean>>
  isFieldVisible: (fieldKey: string) => boolean
  /** Script `api.required` overlay + designer/linkage fallback. */
  isFieldRequired: (field: FormField) => boolean
  /** Script `api.disabled` overlay + engine + whole-form/audit readonly. */
  isFieldDisabled: (field: FormField) => boolean
  /** Script `api.setLabel` overlay, else designer label. */
  fieldLabel: (field: FormField) => string
  /** Script `api.setOptions` overlay, else engine filtered options. */
  fieldOptions: (field: FormField) => unknown
  engineFieldStates: Ref<Map<string, { disabled?: boolean; required?: boolean }>>
  engineOptions: Ref<Map<string, unknown>>
  userSearchResults: Ref<Map<string, unknown>>
  isFieldReadonly: (field: FormField) => boolean
  resolveBinding: (bindingId?: number) => Record<string, unknown> | undefined
  isSubTableEditable: (bindingId?: number) => boolean
  /** Task-node field permissions (`TaskFormData.fieldPermissions`) — passed through to SubTableField for Add/Edit dialog gating. */
  fieldPermissions?: Record<string, string> | null
  getSubFormRowFormulas: (bindingId?: number) => unknown
  getSummaryColumns: (bindingId?: number) => unknown
  getSummaryAggregations: (bindingId?: number) => unknown
  getSubTableValidation: (bindingId?: number) => unknown
  subTableAssigneeField: (bindingId?: number) => string | undefined
  showSubTableAssignColumn: (bindingId?: number) => boolean
  subTableShowTaskStatus: (field: FormField) => boolean
  subTableCompactLookupCells: (field: FormField) => boolean
  /**
   * Inline Form widget (`inlineSubForm`) — the bound sub-table's form rendered in place.
   * Distinct from the form-below-table helpers above, which hang off a subTable grid.
   * NOTE: FormRenderer.vue casts the provide object, so a method declared here but missing
   * there is NOT a compile error — it silently becomes undefined at runtime.
   */
  resolveInlineSubFormFields: (field: FormField) => FormField[]
  resolveInlineSubFormRow: (field: FormField) => Record<string, unknown> | null
  resolveInlineSubFormTitle: (field: FormField) => string
  inlineSubFormReadonly: (field: FormField) => boolean
  handleInlineSubFormUpdate: (field: FormField, row: Record<string, unknown>) => void
  lookupShowBackfillView: (field: FormField) => boolean
  primaryFormData?: Ref<Record<string, unknown>> | Record<string, unknown>
  primaryTableDisplayName?: Ref<string> | string
  primaryTableId?: Ref<number | null> | number | null
  parentTablesById?: Ref<Record<number, { fieldDefinitions: unknown[] }>>
  subTableBindingsForContext?: Ref<unknown[]> | unknown[]
  functionUnitId?: Ref<string | undefined> | string
  /** Current process instance id — RecordNote RECORD scope target on main forms. */
  processInstanceId?: Ref<string | undefined> | string
  resolveMiParticipantSeedForSubTableAdd?: (bindingId?: number) => {
    rowId: string | number | null
    parentRow: Record<string, unknown> | null
    parentTableId: number | null
  }
  handlePrimaryFormDataPatch?: (patch: Record<string, unknown>) => void
  handleSubTableUpdate: (bindingId: number, rows: unknown[]) => void
  handleLookupSelect: (key: string, row: unknown) => void
  /** Multi LOOKUP model sync (tag remove / toggle) — optional for older inject sites. */
  handleLookupModelUpdate?: (key: string, value: unknown) => void
  handleLookupClear: (key: string) => void
  lookupFilterConditionsFor: (field: FormField) => unknown[]
  lookupRefreshNonce: Ref<Record<string, number>>
  hasScriptLookupFilter: (fieldKey: string) => boolean
  handleFieldChange: (key: string, val: unknown) => void
  handleFieldBlur: (key: string) => void
  /**
   * Per-field error messages keyed by field binding key: form-event script errors (setFieldError)
   * plus computed-field formulas the server would refuse to save. Read-only because some sources
   * are derived — write through the composable that owns them.
   */
  scriptFieldErrors: Readonly<Ref<Record<string, string>>>
  clearScriptFieldError?: (fieldKey: string) => void
  handleUploadSuccess: (res: unknown, file: unknown, key: string) => void
  handleUploadRemove: (file: unknown, key: string) => void
  handleUserSearch: (query: string, key: string) => void
  emitViewSubtaskDetail: (row: unknown, siblingRows?: unknown[]) => void
}

export const FORM_RENDERER_FIELDS_CTX: InjectionKey<FormRendererFieldsContext> =
  Symbol('formRendererFieldsContext')
