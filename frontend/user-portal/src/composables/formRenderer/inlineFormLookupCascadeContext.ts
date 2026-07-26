import type { InjectionKey } from 'vue'
import type { FormField } from '@/components/formRendererHelpers'
import type { LookupFilterCondition } from '@/utils/lookupFilterConditions'

export interface InlineLookupCascadeContext {
  lookupFilterConditionsFor: (field: FormField) => LookupFilterCondition[]
  handleLookupSelect: (fieldKey: string, row: Record<string, unknown>) => void | Promise<void>
  handleLookupClear: (fieldKey: string) => void
}

export const INLINE_LOOKUP_CASCADE_CTX: InjectionKey<InlineLookupCascadeContext> =
  Symbol('inlineLookupCascade')
