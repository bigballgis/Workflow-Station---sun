import type { InjectionKey, Ref } from 'vue'
import type { AssignmentConfig } from '@/utils/miAssignmentConfig'
import type { BindingParentSelection } from '@/utils/tableFkRuntime'

export interface PreviewSubTableRowDialogOpen {
  mode: 'add' | 'edit'
  title: string
  initialData?: Record<string, any>
  formRule?: any[]
  formOption?: any
  columns: any[]
  assignmentConfig?: AssignmentConfig
  parentSelection?: BindingParentSelection | null
  onSave: (row: Record<string, any>, selectedParentValue?: string) => boolean | void | Promise<boolean | void>
}

export interface PreviewSubTableDialogHost {
  /** True while the preview-level Add/Edit row dialog is open */
  rowDialogOpen: Ref<boolean>
  openRowDialog: (payload: PreviewSubTableRowDialogOpen) => void
}

export const PREVIEW_SUBTABLE_DIALOG_KEY: InjectionKey<PreviewSubTableDialogHost> =
  Symbol('previewSubTableDialog')

/** Form Preview: true while any dual-portal sub-table tab is My Requests Display. */
export const PREVIEW_MY_REQUESTS_ACTIVE_KEY: InjectionKey<Ref<boolean>> =
  Symbol('previewMyRequestsActive')
