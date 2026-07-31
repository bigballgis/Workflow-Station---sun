import { reactive } from 'vue'
import type { Ref } from 'vue'
import type { DialogColumn } from '@/components/subTableAddDialogHelpers'
import {
  applySensitiveMask,
  isSensitiveMaskActive,
  normalizeSensitiveMaskConfig,
  shouldShowMaskedDisplay,
} from '@/utils/sensitiveMask'

/**
 * Display-only sensitive mask for SubTableAddDialog text inputs.
 * Model (formData) always stays plaintext.
 */
export function useSubTableDialogSensitiveMask(
  formData: Ref<Record<string, unknown>>,
  isColDisabled: (col: DialogColumn) => boolean,
) {
  const focusedFields = reactive(new Set<string>())

  function maskConfig(col: DialogColumn) {
    const inputType = typeof col.props?.type === 'string' ? col.props.type : undefined
    const cfg = normalizeSensitiveMaskConfig(col.props?.sensitiveMask)
    return isSensitiveMaskActive(cfg, inputType) ? cfg : null
  }

  function showMasked(col: DialogColumn): boolean {
    const cfg = maskConfig(col)
    if (!cfg) return false
    return shouldShowMaskedDisplay(cfg, {
      isReadonly: isColDisabled(col),
      isFocused: focusedFields.has(col.field),
    })
  }

  function textDisplay(col: DialogColumn): unknown {
    const raw = formData.value[col.field]
    if (raw == null || raw === '') return raw
    if (!showMasked(col)) return raw
    return applySensitiveMask(String(raw), maskConfig(col)!)
  }

  function onTextUpdate(col: DialogColumn, value: unknown) {
    if (showMasked(col)) return
    formData.value[col.field] = value
  }

  function onTextFocus(col: DialogColumn) {
    focusedFields.add(col.field)
  }

  function onTextBlur(col: DialogColumn) {
    focusedFields.delete(col.field)
  }

  return {
    showMasked,
    textDisplay,
    onTextUpdate,
    onTextFocus,
    onTextBlur,
  }
}
