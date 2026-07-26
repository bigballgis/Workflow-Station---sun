import type { InjectionKey } from 'vue'
import type { LookupDerivedFrom } from '@/utils/lookupCascade'
import type { LookupFilterCondition } from '@/utils/lookupFilterConditions'

/** Shared lookup cascade state for Form Preview (main + nested cards). */
export interface PreviewLookupCascadeCtx {
  lookupSelectedRows: Record<string, Record<string, unknown>>
  filterFor: (
    base: LookupFilterCondition[],
    derivedFrom: LookupDerivedFrom | undefined,
  ) => LookupFilterCondition[]
  notifyLookupChange: (field: string, row: Record<string, unknown> | null) => void
}

export const PREVIEW_LOOKUP_CASCADE_KEY: InjectionKey<PreviewLookupCascadeCtx> =
  Symbol('previewLookupCascade')
