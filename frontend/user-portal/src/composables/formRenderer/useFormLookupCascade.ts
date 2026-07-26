import type { Ref } from 'vue'
import type { LookupConfig, LookupDerivedFrom } from '@/api/relationTable'
import type { LookupFilterCondition } from '@/utils/lookupFilterConditions'
import type { FormField } from '@/components/formRendererHelpers'
import {
  buildDerivedFilterConditions,
  resolveDerivedLookup,
  type FieldLike,
} from '@/components/lookup/useLookupBehaviors'

export function buildLookupConfigFromFormField(field: FormField): LookupConfig | undefined {
  if (field.type !== 'lookup') return undefined
  const tableId = Number((field as { _lookupTableId?: number })._lookupTableId || 0)
  if (!tableId && !(field as { _lookupDerivedFrom?: LookupDerivedFrom })._lookupDerivedFrom) {
    return undefined
  }
  return {
    refTableId: tableId || undefined,
    tableId: tableId || undefined,
    searchFields: (field as { _lookupSearchFields?: string[] })._lookupSearchFields || [],
    displayFields: (field as { _lookupDisplayFields?: string[] })._lookupDisplayFields || [],
    selectedDisplayField: (field as { _lookupSelectedDisplayField?: string })._lookupSelectedDisplayField,
    filterConditions: (field as { _lookupFilterConditions?: LookupFilterCondition[] })._lookupFilterConditions || [],
    derivedFrom: (field as { _lookupDerivedFrom?: LookupDerivedFrom })._lookupDerivedFrom,
    showBackfillView: (field as { _lookupShowBackfillView?: boolean })._lookupShowBackfillView,
    multiple: (field as { _lookupMultiple?: boolean })._lookupMultiple === true,
  }
}

export function formFieldsAsLookupLike(allFields: FormField[]): FieldLike[] {
  return allFields
    .filter(f => f.type === 'lookup')
    .map(f => ({
      fieldName: f.key,
      dataType: 'LOOKUP',
      lookupConfig: buildLookupConfigFromFormField(f),
    }))
}

export function lookupFilterConditionsForField(
  field: FormField,
  lookupSelectedData: Record<string, Record<string, unknown> | null | undefined>,
): LookupFilterCondition[] {
  const base = (field as { _lookupFilterConditions?: LookupFilterCondition[] })._lookupFilterConditions || []
  const cfg = buildLookupConfigFromFormField(field)
  const parent = cfg?.derivedFrom?.parentField
  if (!parent) return base
  return buildDerivedFilterConditions(base, cfg, lookupSelectedData[parent] ?? null)
}

export async function processLookupCascadeSelect(
  fieldKey: string,
  row: Record<string, unknown> | null,
  allFields: FormField[],
  formData: Ref<Record<string, unknown>>,
  lookupSelectedData: Ref<Record<string, Record<string, unknown>>>,
  onFieldChange: (key: string, value: unknown) => void,
): Promise<void> {
  lookupSelectedData.value = { ...lookupSelectedData.value, [fieldKey]: row }
  if (row == null) return
  for (const dep of allFields) {
    if (dep.type !== 'lookup') continue
    const df = (dep as { _lookupDerivedFrom?: LookupDerivedFrom })._lookupDerivedFrom
    if (df?.parentField !== fieldKey) continue
    const cfg = buildLookupConfigFromFormField(dep)
    const res = await resolveDerivedLookup(
      {
        fieldName: dep.key,
        dataType: 'LOOKUP',
        lookupConfig: cfg,
      },
      row,
      formFieldsAsLookupLike(allFields),
    )
    if (res.skip) continue
    // Portal form LOOKUP stores full row object(s) so LookupField can render designer
    // selectedDisplayField tags immediately (PK-only → synthetic row → tag shows "-").
    if (cfg?.multiple) {
      const filledRows = (res.rows as Record<string, unknown>[] | undefined) ?? []
      formData.value[dep.key] = filledRows
      onFieldChange(dep.key, filledRows)
      if (filledRows[0]) {
        lookupSelectedData.value = { ...lookupSelectedData.value, [dep.key]: filledRows[0] }
      } else {
        const cleared = { ...lookupSelectedData.value }
        delete cleared[dep.key]
        lookupSelectedData.value = cleared
      }
      continue
    }
    const filled = (res.row as Record<string, unknown> | null | undefined) ?? null
    if (filled) {
      lookupSelectedData.value = { ...lookupSelectedData.value, [dep.key]: filled }
    } else {
      const cleared = { ...lookupSelectedData.value }
      delete cleared[dep.key]
      lookupSelectedData.value = cleared
    }
    formData.value[dep.key] = filled
    onFieldChange(dep.key, filled)
  }
}

/** Empty model value for a LOOKUP field after clear (multi → [], single → null). */
export function emptyLookupModelValue(field: FormField | undefined): unknown[] | null {
  return (field as { _lookupMultiple?: boolean } | undefined)?._lookupMultiple === true ? [] : null
}

export function processLookupCascadeClear(
  fieldKey: string,
  allFields: FormField[],
  formData: Ref<Record<string, unknown>>,
  lookupSelectedData: Ref<Record<string, Record<string, unknown>>>,
  onFieldChange: (key: string, value: unknown) => void,
): void {
  const next = { ...lookupSelectedData.value }
  delete next[fieldKey]
  lookupSelectedData.value = next
  for (const dep of allFields) {
    if (dep.type !== 'lookup') continue
    const df = (dep as { _lookupDerivedFrom?: LookupDerivedFrom })._lookupDerivedFrom
    if (df?.parentField !== fieldKey || df.derivedMode !== 'autofill') continue
    const empty = emptyLookupModelValue(dep)
    formData.value[dep.key] = empty
    onFieldChange(dep.key, empty)
    delete lookupSelectedData.value[dep.key]
  }
}

export function createLookupCascadeHandlers(deps: {
  allFields: () => FormField[]
  formData: Ref<Record<string, unknown>>
  lookupSelectedData: Ref<Record<string, Record<string, unknown>>>
  onFieldChange: (key: string, value: unknown) => void
}) {
  return {
    lookupFilterConditionsFor: (field: FormField) =>
      lookupFilterConditionsForField(field, deps.lookupSelectedData.value),
    handleLookupSelect: async (fieldKey: string, row: Record<string, unknown>) => {
      const field = deps.allFields().find((f) => f.key === fieldKey)
      const isMulti = (field as { _lookupMultiple?: boolean } | undefined)?._lookupMultiple === true
      // Multi LOOKUP modelValue is already updated via @update:modelValue (row array / PKs).
      // Do not overwrite that array with the last-clicked single row — but still flush
      // onFieldChange so parent/submit paths see the array immediately.
      if (!isMulti) {
        deps.formData.value[fieldKey] = row
        deps.onFieldChange(fieldKey, row)
      } else {
        deps.onFieldChange(fieldKey, deps.formData.value[fieldKey])
      }
      await processLookupCascadeSelect(
        fieldKey,
        row,
        deps.allFields(),
        deps.formData,
        deps.lookupSelectedData,
        deps.onFieldChange,
      )
    },
    handleLookupClear: (fieldKey: string) => {
      processLookupCascadeClear(
        fieldKey,
        deps.allFields(),
        deps.formData,
        deps.lookupSelectedData,
        deps.onFieldChange,
      )
      const empty = emptyLookupModelValue(deps.allFields().find((f) => f.key === fieldKey))
      deps.formData.value[fieldKey] = empty
      deps.onFieldChange(fieldKey, empty)
    },
  }
}
