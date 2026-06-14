// ---------------------------------------------------------------------------
// FieldRenderer — core bindings: disabled state, number-as-text fallback,
// resolved options, update / blur emitters, and user search.
// Behaviour copied verbatim from FieldRenderer.vue.
// ---------------------------------------------------------------------------
import { computed } from 'vue'
import type { FormField } from '@/components/formRendererHelpers'
import type { FieldRendererProps, FieldRendererEmit } from './types'

export function useFieldCore(props: FieldRendererProps, emit: FieldRendererEmit) {
  const isDisabled = computed(() => props.readonly || props.disabled)

  /** Prefixed-sequence / uuid PK bound to inputNumber shows blank in el-input-number. */
  const showNumberAsText = computed(() => {
    if (props.field.type !== 'number' || !isDisabled.value) return false
    const v = props.modelValue
    if (v == null || v === '') return false
    if (typeof v === 'number') return Number.isNaN(v)
    const s = String(v).trim()
    return s !== '' && Number.isNaN(Number(s))
  })

  const numberAsTextDisplay = computed(() => {
    const v = props.modelValue
    return v == null ? '' : String(v)
  })

  function onUpdate(value: any) {
    if (props.readonly) return
    emit('update:modelValue', value)
  }

  function onBlur() {
    if (props.readonly) return
    emit('field-blur', props.field.key)
  }

  // -------------------------------------------------------------------------
  // Resolved options — linkage override takes priority (Task 6.1)
  // -------------------------------------------------------------------------
  const resolvedOptions = computed(() => {
    return props.options ?? props.field.options ?? []
  })

  // -------------------------------------------------------------------------
  // User search — emit to parent FormRenderer (Req 11.1, 11.3)
  // -------------------------------------------------------------------------
  function searchUsers(query: string, field: FormField) {
    if (query.length < 2) return
    emit('search:users', query, field.key)
  }

  return {
    isDisabled,
    showNumberAsText,
    numberAsTextDisplay,
    onUpdate,
    onBlur,
    resolvedOptions,
    searchUsers,
  }
}
