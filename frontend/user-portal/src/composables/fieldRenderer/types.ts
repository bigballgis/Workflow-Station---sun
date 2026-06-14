// ---------------------------------------------------------------------------
// FieldRenderer composables — shared prop / emit contracts
// Extracted verbatim from FieldRenderer.vue; behaviour is identical.
// ---------------------------------------------------------------------------
import type { FormField } from '@/components/formRendererHelpers'

export interface FieldRendererProps {
  field: FormField
  modelValue: any
  formData?: Record<string, any>
  readonly?: boolean
  disabled?: boolean
  visible?: boolean
  options?: Array<{ label: string; value: any }>
  uploadUrl?: string
  userSearchResults?: Array<{ id: string; name: string }>
}

/** Mirrors FieldRenderer's `defineEmits` signature so composables can emit. */
export type FieldRendererEmit = {
  (e: 'update:modelValue', value: any): void
  (e: 'field-blur', fieldKey: string): void
  (e: 'upload:success', response: any, file: any, fieldKey: string): void
  (e: 'upload:remove', file: any, fieldKey: string): void
  (e: 'search:users', query: string, fieldKey: string): void
}
