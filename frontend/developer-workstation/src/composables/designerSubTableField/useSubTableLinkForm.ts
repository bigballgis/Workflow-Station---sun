import { ref } from 'vue'
import type { ColumnConfig, SubTableFieldProps } from './types'

interface UseSubTableLinkFormOptions {
  props: SubTableFieldProps
  editable: { value: boolean }
  t: (key: string, params?: Record<string, unknown>) => string
}

/** 子表 linkForm 单元格的关联表单弹层：标题归一、规则/选项装配。 */
export function useSubTableLinkForm(options: UseSubTableLinkFormOptions) {
  const { props, editable, t } = options

  const linkFormDialogVisible = ref(false)
  const linkFormDialogTitle = ref('')
  const linkFormInitialData = ref<Record<string, any> | undefined>(undefined)
  const linkFormRule = ref<any[]>([])
  const linkFormOption = ref<any>({})

  function linkFormTitleTableName(raw: string): string {
    return String(raw || '')
      .trim()
      .replace(/^ADD\s*\+\s*/i, '')
      .trim()
  }

  function openLinkFormDialog(col: ColumnConfig, row: Record<string, any>) {
    const raw = col.props?.boundSubTableName || props.config.title || ''
    const tableName = linkFormTitleTableName(raw)
    linkFormDialogTitle.value = tableName
      ? t('linkForm.dialogTitleAddTable', { tableName })
      : t('linkForm.linkedForm')
    linkFormInitialData.value = { ...row }
    linkFormRule.value = col.props?.formRule || props.formRule || []
    const opt = { ...((col.props?.formOption || props.formOption || {}) as Record<string, unknown>) }
    delete opt.title
    if (!editable.value) {
      opt.form = {
        ...((opt.form as Record<string, unknown>) || {}),
        disabled: true,
      }
    }
    linkFormOption.value = opt
    linkFormDialogVisible.value = true
  }

  function handleLinkFormSave(rowData: Record<string, any>) {
    linkFormDialogVisible.value = false
    linkFormInitialData.value = rowData
  }

  return {
    linkFormDialogVisible,
    linkFormDialogTitle,
    linkFormInitialData,
    linkFormRule,
    linkFormOption,
    openLinkFormDialog,
    handleLinkFormSave,
  }
}
