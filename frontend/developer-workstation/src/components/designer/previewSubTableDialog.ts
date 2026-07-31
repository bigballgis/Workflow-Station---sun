import type { InjectionKey, Ref } from 'vue'
import type { AssignmentConfig } from '@/utils/miAssignmentConfig'

export interface PreviewSubTableRowDialogOpen {
  mode: 'add' | 'edit'
  title: string
  initialData?: Record<string, any>
  formRule?: any[]
  formOption?: any
  columns: any[]
  assignmentConfig?: AssignmentConfig
  onSave: (row: Record<string, any>) => void
}

export interface PreviewSubTableDialogHost {
  /** True while the preview-level Add/Edit row dialog is open */
  rowDialogOpen: Ref<boolean>
  openRowDialog: (payload: PreviewSubTableRowDialogOpen) => void
}

export const PREVIEW_SUBTABLE_DIALOG_KEY: InjectionKey<PreviewSubTableDialogHost> =
  Symbol('previewSubTableDialog')

export type PreviewResolveSubTableFormDesign = (
  bindingId: number,
) => { rule: any[]; options?: any }

export const PREVIEW_RESOLVE_SUBTABLE_FORM_KEY: InjectionKey<PreviewResolveSubTableFormDesign> =
  Symbol('previewResolveSubTableFormDesign')

/** Form Preview: true while any dual-portal sub-table tab is My Requests Display. */
export const PREVIEW_MY_REQUESTS_ACTIVE_KEY: InjectionKey<Ref<boolean>> =
  Symbol('previewMyRequestsActive')
