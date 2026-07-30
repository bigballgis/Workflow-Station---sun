import { computed, ref } from 'vue'
import type { FormField } from '@/components/formRendererHelpers'
import {
  applySensitiveMask,
  shouldShowMaskedDisplay,
  type SensitiveMaskConfig,
} from '@/utils/sensitiveMask'

/**
 * Display-only mask binding for FieldRenderer text/input.
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

  const displayValue = computed(() => {
    const raw = modelValue()
    if (raw == null || raw === '') return raw
    if (!showMasked.value || !maskConfig.value) return raw
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
