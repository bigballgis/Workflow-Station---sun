import type { InjectionKey, Ref } from 'vue'

export interface PreviewSubTableRowDialogOpen {
  mode: 'add' | 'edit'
  title: string
  initialData?: Record<string, any>
  formRule?: any[]
  formOption?: any
  columns: any[]
  onSave: (row: Record<string, any>) => void
}

export interface PreviewSubTableDialogHost {
  /** True while the preview-level Add/Edit row dialog is open */
  rowDialogOpen: Ref<boolean>
  openRowDialog: (payload: PreviewSubTableRowDialogOpen) => void
}

export const PREVIEW_SUBTABLE_DIALOG_KEY: InjectionKey<PreviewSubTableDialogHost> =
  Symbol('previewSubTableDialog')
