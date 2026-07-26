<template>
  <div class="lookup-component">
    <LookupPreview
      v-model="lookupValue"
      :label="''"
      :placeholder="placeholder || previewConfig.placeholder"
      :search-fields="previewConfig.searchFields"
      :display-fields="previewConfig.displayFields"
      :selected-display-field="previewConfig.selectedDisplayField"
      :filter-conditions="effectiveFilterConditions"
      :view-fields="previewConfig.viewFields"
      :field-defs="previewConfig.fieldDefs"
      :ensure-mock-fields="ensureMockFields"
      :show-backfill-view="previewConfig.showBackfillView"
      :readonly="isReadonly"
      :multiple="previewConfig.multiple"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'
import LookupPreview from './LookupPreview.vue'
import { PREVIEW_LOOKUP_CASCADE_KEY } from './previewLookupCascade'
import { lookupStore } from './lookupStore'
import { dispatchLookupComponentFieldEvents } from '@/utils/formCreateLookupComponentEvents'
import { isFormCreateRuleReadonly } from '@/utils/formCreateRuleUtils'
import { walkFormCreateRules } from '@/utils/formDesigner'
import {
  buildDerivedFilterConditions,
  buildPreviewAutofillModelValue,
  normalizeLookupRow,
  type LookupCascadeConfig,
  type LookupDerivedFrom,
} from '@/utils/lookupCascade'

const props = defineProps<{
  modelValue?: any
  placeholder?: string
  lookupConfig?: string
  disabled?: boolean
  formCreateInject?: {
    rule?: Record<string, unknown>
    api?: Record<string, unknown>
    field?: string
    preview?: boolean
  }
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
  /** form-create built-in preview runs rule.on.change only when custom components emit change. */
  (e: 'change', value: any): void
}>()

const cascade = inject(PREVIEW_LOOKUP_CASCADE_KEY, null)

function parseLookupConfig(raw?: string): Record<string, any> {
  if (!raw) return {}
  try {
    return JSON.parse(raw)
  } catch {
    return {}
  }
}

const parsedConfig = computed(() => parseLookupConfig(props.lookupConfig))
const fieldName = computed(() => String(props.formCreateInject?.field || ''))

const previewConfig = computed(() => {
  const config = parsedConfig.value
  const binding = lookupStore.relationBindings.find(item => item.bindingId === config.bindingId)
  const table = lookupStore.tables.find(item => item.id === (config.tableId ?? binding?.tableId))
  const fields = ((table as any)?.fieldDefinitions || (table as any)?.fields || lookupStore.rtFieldCache[config.tableId ?? binding?.tableId] || [])
    .map((field: any) => ({
      fieldName: field.fieldName,
      dataType: field.dataType,
      displayName: field.displayName,
    }))
  const displayFields = config.displayFields || []

  return {
    placeholder: 'Click to search',
    searchFields: config.searchFields || [],
    displayFields,
    selectedDisplayField: config.selectedDisplayField || config.displayField || '',
    filterConditions: Array.isArray(config.filterConditions) ? config.filterConditions : [],
    viewFields: config.showBackfillView === false
      ? []
      : displayFields.map((fieldName: string, index: number) => ({
        fieldName,
        displayLabel: fields.find((field: any) => field.fieldName === fieldName)?.displayName || fieldName,
        sortOrder: index,
        visible: true,
      })),
    fieldDefs: fields,
    showBackfillView: config.showBackfillView !== false,
    multiple: config.multiple === true,
    derivedFrom: config.derivedFrom as LookupDerivedFrom | undefined,
  }
})

function readApiFieldValue(field: string): unknown {
  const api = props.formCreateInject?.api as {
    getValue?: (f: string) => unknown
    form?: Record<string, unknown>
  } | undefined
  if (!api) return undefined
  if (typeof api.getValue === 'function') return api.getValue(field)
  return api.form?.[field]
}

function resolveParentRow(df: LookupDerivedFrom | undefined): Record<string, unknown> | null {
  if (!df?.parentField) return null
  const fromCascade = cascade?.lookupSelectedRows[df.parentField]
  if (fromCascade) return fromCascade
  return normalizeLookupRow(readApiFieldValue(df.parentField))
}

/** Fingerprint so filterConditions recompute when parent LOOKUP changes. */
const parentFingerprint = computed(() => {
  const df = previewConfig.value.derivedFrom
  if (!df?.parentField) return ''
  const row = resolveParentRow(df)
  return row ? JSON.stringify(row) : ''
})

const effectiveFilterConditions = computed(() => {
  void parentFingerprint.value
  const base = previewConfig.value.filterConditions
  const df = previewConfig.value.derivedFrom
  if (cascade) return cascade.filterFor(base, df)
  return buildDerivedFilterConditions(
    base,
    { derivedFrom: df, filterConditions: base },
    resolveParentRow(df),
  )
})

/** Cascade join columns on this field's mock rows (parent fromColumn / child toColumn). */
const ensureMockFields = computed(() => {
  const fields = new Set<string>()
  const me = fieldName.value
  for (const j of previewConfig.value.derivedFrom?.joins || []) {
    if (j.toColumn) fields.add(j.toColumn)
  }
  for (const sib of collectSiblingLookupConfigs()) {
    if (sib.config.derivedFrom?.parentField !== me) continue
    for (const j of sib.config.derivedFrom.joins || []) {
      if (j.fromColumn) fields.add(j.fromColumn)
    }
  }
  return Array.from(fields)
})

function collectSiblingLookupConfigs(): Array<{
  field: string
  config: LookupCascadeConfig
  searchFields?: string[]
  selectedDisplayField?: string
  displayFields?: string[]
  multiple?: boolean
}> {
  const api = props.formCreateInject?.api as { rule?: unknown[] } | undefined
  const rules = Array.isArray(api?.rule) ? api!.rule! : []
  const out: Array<{
    field: string
    config: LookupCascadeConfig
    searchFields?: string[]
    selectedDisplayField?: string
    displayFields?: string[]
    multiple?: boolean
  }> = []
  walkFormCreateRules(rules, (rule) => {
    if (rule?.type !== 'lookup' || !rule.field) return
    const cfg = parseLookupConfig(
      typeof rule.props?.lookupConfig === 'string'
        ? rule.props.lookupConfig
        : JSON.stringify(rule.props?.lookupConfig || {}),
    )
    out.push({
      field: String(rule.field),
      config: {
        filterConditions: Array.isArray(cfg.filterConditions) ? cfg.filterConditions : [],
        derivedFrom: cfg.derivedFrom,
      },
      searchFields: cfg.searchFields,
      selectedDisplayField: cfg.selectedDisplayField || cfg.displayField,
      displayFields: cfg.displayFields,
      multiple: cfg.multiple === true,
    })
  })
  return out
}

function applyLocalFormCreateCascade(field: string, row: Record<string, unknown> | null) {
  const api = props.formCreateInject?.api as {
    setValue?: (f: string, v: unknown) => void
  } | undefined
  if (!api || typeof api.setValue !== 'function') return
  for (const sib of collectSiblingLookupConfigs()) {
    if (sib.config.derivedFrom?.parentField !== field) continue
    if (sib.config.derivedFrom.derivedMode !== 'autofill') continue
    const autofill = row
      ? buildPreviewAutofillModelValue(sib.config, row, {
        searchFields: sib.searchFields,
        selectedDisplayField: sib.selectedDisplayField,
        displayFields: sib.displayFields,
        multiple: sib.multiple === true,
      })
      : (sib.multiple === true ? [] : null)
    api.setValue(sib.field, autofill)
  }
}

const lookupValue = computed({
  get: () => props.modelValue,
  set: (value) => {
    emit('update:modelValue', value)
    emit('change', value)
    dispatchLookupComponentFieldEvents(props.formCreateInject, value)
    const field = fieldName.value
    const row = normalizeLookupRow(value)
    if (!field) return
    // Sync FormPreviewItems kind:'lookup' siblings (main/card extract path)
    if (cascade) cascade.notifyLookupChange(field, row)
    // Autofill sibling lookups still inside the same form-create rule tree
    applyLocalFormCreateCascade(field, row)
  },
})

const isReadonly = computed(
  () => props.disabled === true || isFormCreateRuleReadonly(props.formCreateInject?.rule),
)
</script>

<style lang="scss" scoped>
.lookup-component {
  width: 100%;

  :deep(.lookup-label-text) {
    display: none;
  }
}
</style>
