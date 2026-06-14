import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { linkFormComponentApi } from '@/api/linkFormComponent'
import type {
  SubTableFormDesign,
  SubTableListColumnDTO,
  SubTableListViewProps,
  TFn,
} from './types'

interface UseLinkFormDialogOptions {
  props: SubTableListViewProps
  getLinkFormBoundTableName: (column: SubTableListColumnDTO | null) => string
  t: TFn
}

const mockSubTableRowId = 1

/**
 * Link Form 列点击后的关联表单弹层：装载/保存表单数据、标题归一与 form-create 选项。
 */
export function useLinkFormDialog(options: UseLinkFormDialogOptions) {
  const { props, getLinkFormBoundTableName, t } = options

  const showLinkFormDialog = ref(false)
  const formCreateMounted = ref(false)
  const savingLinkForm = ref(false)
  const selectedLinkColumn = ref<SubTableListColumnDTO | null>(null)
  const linkFormData = ref<Record<string, any>>({})

  const selectedSubTableFormDesign = computed<SubTableFormDesign>(() => {
    const bindingId = selectedLinkColumn.value?.boundSubTableBindingId || props.binding.bindingId
    return props.resolveSubTableFormDesign?.(bindingId) || {
      rule: props.formRule || [],
      options: props.formOption
    }
  })

  const linkFormOption = computed(() => {
    const saved = { ...((selectedSubTableFormDesign.value.options || props.formOption || {}) as Record<string, unknown>) }
    // Persisted designer option often includes `title`; form-create renders it inside the dialog and
    // it may still be the legacy "ADD + …" string — remove so only `el-dialog` shows `linkFormDialogTitle`.
    delete saved.title
    return {
      showMsg: true,
      form: {
        labelPosition: 'left',
        labelWidth: '140px',
      },
      language: {
        en: {
          clickToUpload: t('form.clickToUpload'),
        },
      },
      ...saved,
      resetBtn: false,
      submitBtn: false,
    }
  })

  /** Legacy titles used "ADD + name"; strip if that prefix was stored on the table display name. */
  function linkFormTitleTableName(raw: string): string {
    return String(raw || '')
      .trim()
      .replace(/^ADD\s*\+\s*/i, '')
      .trim()
  }

  const linkFormDialogTitle = computed(() => {
    const tableName = linkFormTitleTableName(getLinkFormBoundTableName(selectedLinkColumn.value))
    if (!tableName) return t('linkForm.linkedForm')
    return t('linkForm.dialogTitleAddTable', { tableName })
  })

  async function openLinkFormDialog(column: SubTableListColumnDTO) {
    if (column.componentId === undefined || column.componentId === null) return
    selectedLinkColumn.value = column
    linkFormData.value = {}
    formCreateMounted.value = false
    showLinkFormDialog.value = true

    try {
      const res = await linkFormComponentApi.getFormData(props.functionUnitId, column.componentId, mockSubTableRowId)
      linkFormData.value = res.data?.formData || {}
    } catch (e: any) {
      if (e?.response?.status !== 404) {
        console.error('[SubTableListView] failed to load link form data:', e)
      }
    }

    nextTick(() => {
      formCreateMounted.value = true
    })
  }

  async function handleLinkFormSave() {
    if (selectedLinkColumn.value?.componentId === undefined || selectedLinkColumn.value?.componentId === null) return
    savingLinkForm.value = true
    try {
      await linkFormComponentApi.saveFormData(props.functionUnitId, {
        componentId: selectedLinkColumn.value.componentId,
        subTableRowId: mockSubTableRowId,
        formData: linkFormData.value
      })
      ElMessage.success(t('common.saveSuccess'))
      showLinkFormDialog.value = false
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || t('common.saveFailed'))
    } finally {
      savingLinkForm.value = false
    }
  }

  function handleLinkFormDialogClosed() {
    formCreateMounted.value = false
    selectedLinkColumn.value = null
    linkFormData.value = {}
  }

  return {
    showLinkFormDialog,
    formCreateMounted,
    savingLinkForm,
    selectedLinkColumn,
    linkFormData,
    selectedSubTableFormDesign,
    linkFormOption,
    linkFormDialogTitle,
    openLinkFormDialog,
    handleLinkFormSave,
    handleLinkFormDialogClosed,
  }
}
