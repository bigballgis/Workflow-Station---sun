import { computed, ref } from 'vue'
import type { FormField } from '@/components/formRendererHelpers'
import {
  applySensitiveMask,
  shouldShowMaskedDisplay,
  type SensitiveMaskConfig,
} from '@/utils/sensitiveMask'

/**
 * Display-only mask binding for FieldRenderer text/input.
 * Uses only this form field's own sensitiveMask — forms do not affect each other.
 * Never writes the masked string back to the model.
 */
export function useFieldSensitiveMask(
  field: () => FormField,
  modelValue: () => unknown,
  isDisabled: () => boolean,
  onUpdate: (value: unknown) => void,
  onBlur: () => void,
) {
  const focused = ref(false)

  const maskConfig = computed((): SensitiveMaskConfig | undefined => field().sensitiveMask)

  const showMasked = computed(() =>
    shouldShowMaskedDisplay(maskConfig.value, {
      isReadonly: isDisabled(),
      isFocused: focused.value,
    }),
  )

  // 显式标注 string | number | undefined：此前推断成 `{} | null | undefined`
  // （modelValue() 是宽类型），绑到 el-input 的 model-value 上不兼容。
  // null 一并归一成 undefined —— el-input 对两者的渲染结果相同（都是空），
  // 但只有 undefined 在其 prop 类型内。
  const displayValue = computed<string | number | undefined>(() => {
    const raw = modelValue()
    if (raw == null || raw === '') return raw == null ? undefined : (raw as string)
    if (!showMasked.value || !maskConfig.value) {
      return typeof raw === 'number' ? raw : String(raw)
    }
    return applySensitiveMask(String(raw), maskConfig.value)
  })

  const inputReadonly = computed(() => isDisabled() || showMasked.value)

  function onMaskedInput(value: unknown) {
    if (showMasked.value) return
    onUpdate(value)
  }

  function onMaskedFocus() {
    focused.value = true
  }

  function onMaskedBlur() {
    focused.value = false
    onBlur()
  }

  return {
    displayValue,
    inputReadonly,
    showMasked,
    onMaskedInput,
    onMaskedFocus,
    onMaskedBlur,
  }
}
