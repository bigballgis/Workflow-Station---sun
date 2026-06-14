import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { SubTableFieldProps } from './types'

interface UseSubTableInlineFormOptions {
  props: SubTableFieldProps
  editable: { value: boolean }
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Form Preview：表格下方的只读内联表单（assignee — form below table）。
 * 计算生效的 rule/option，并提供与 Portal SubTableInlineForm 对齐的内联保存。
 */
export function useSubTableInlineForm(options: UseSubTableInlineFormOptions) {
  const { props, editable, t } = options

  const previewInlineFormData = ref<Record<string, unknown>>({})
  const inlineFormBelowRef = ref<HTMLElement | null>(null)
  const effectiveInlineFormRule = computed(
    () => (props.previewInlineFormRule?.length ? props.previewInlineFormRule : props.formRule) || [],
  )
  const effectiveInlineFormOptionSource = computed(
    () => props.previewInlineFormOption ?? props.formOption,
  )
  const previewInlineFormOption = computed(() => {
    const saved = { ...((effectiveInlineFormOptionSource.value || {}) as Record<string, unknown>) }
    delete saved.title
    return {
      showMsg: true,
      form: {
        labelPosition: 'left',
        labelWidth: '140px',
        disabled: true,
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

  /** Form Preview parity with Portal SubTableInlineForm — inline Save below the table. */
  function handleInlineFormBelowSave() {
    if (!editable.value) return
    ElMessage.success(t('common.saveSuccess'))
  }

  return {
    previewInlineFormData,
    inlineFormBelowRef,
    effectiveInlineFormRule,
    previewInlineFormOption,
    handleInlineFormBelowSave,
  }
}
