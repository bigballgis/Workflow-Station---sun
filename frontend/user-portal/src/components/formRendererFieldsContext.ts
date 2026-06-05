import type { InjectionKey, Ref } from 'vue'
import type { FormField, PortalViewContext } from './formRendererHelpers'

export interface FormRendererFieldsContext {
  formData: Ref<Record<string, unknown>>
  readonly: Ref<boolean>
  labelWidth: Ref<string> | string
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
  engineFieldStates: Ref<Map<string, { disabled?: boolean }>>
  engineOptions: Ref<Map<string, unknown>>
  userSearchResults: Ref<Map<string, unknown>>
  isFieldReadonly: (field: FormField) => boolean
  resolveBinding: (bindingId?: number) => Record<string, unknown> | undefined
  shouldRenderPlacedSubTableField: (field: FormField) => boolean
  isSubTableEditable: (bindingId?: number) => boolean
  getSubFormRowFormulas: (bindingId?: number) => unknown
  getSummaryColumns: (bindingId?: number) => unknown
  getSummaryAggregations: (bindingId?: number) => unknown
  getSubTableValidation: (bindingId?: number) => unknown
  subTableAssigneeField: (bindingId?: number) => string | undefined
  showSubTableAssignColumn: (bindingId?: number) => boolean
  linkFormScrollToInlineEnabled: (field: FormField) => boolean
  subTableShowTaskStatusInitiator: (field: FormField) => boolean
  subTableShowViewDetailInitiator: (field: FormField) => boolean
  subTableCompactLookupCells: (field: FormField) => boolean
  subTableMode: (field: FormField) => string
  resolveInlineFormTableTitle: (field: FormField) => string
  resolveInlineFormFields: (field: FormField) => FormField[]
  getCurrentRowForInlineForm: (field: FormField) => Record<string, unknown> | null
  inlineSubTableFormReadonly: (field: FormField) => boolean
  lookupShowBackfillView: (field: FormField) => boolean
  primaryFormData?: Ref<Record<string, unknown>> | Record<string, unknown>
  primaryTableDisplayName?: Ref<string> | string
  primaryTableId?: Ref<number | null> | number | null
  parentTablesById?: Ref<Record<number, { fieldDefinitions: unknown[] }>>
  subTableBindingsForContext?: Ref<unknown[]> | unknown[]
  functionUnitId?: Ref<string | undefined> | string
  resolveMiParticipantSeedForSubTableAdd?: (bindingId?: number) => {
    rowId: string | number | null
    parentRow: Record<string, unknown> | null
    parentTableId: number | null
  }
  handlePrimaryFormDataPatch?: (patch: Record<string, unknown>) => void
  handleSubTableUpdate: (bindingId: number, rows: unknown[]) => void
  handleInlineFormUpdate: (field: FormField, row: Record<string, unknown>) => void
  scrollSubTableInlineIntoView: (bindingId?: number) => void
  setSubTableInlineAnchor: (bindingId: number | undefined, el: HTMLElement | null) => void
  handleLookupSelect: (key: string, row: unknown) => void
  handleLookupClear: (key: string) => void
  handleFieldChange: (key: string, val: unknown) => void
  handleUploadSuccess: (res: unknown, file: unknown, key: string) => void
  handleUploadRemove: (file: unknown, key: string) => void
  handleUserSearch: (query: string, key: string) => void
  emitViewSubtaskDetail: (row: unknown, siblingRows?: unknown[]) => void
}

export const FORM_RENDERER_FIELDS_CTX: InjectionKey<FormRendererFieldsContext> =
  Symbol('formRendererFieldsContext')
