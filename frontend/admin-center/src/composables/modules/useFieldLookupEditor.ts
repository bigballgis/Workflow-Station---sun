import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type {
  FieldDefinitionResponse,
  LookupConfig,
  LookupMatchType,
  RelationTableResponse,
} from '@/api/relationTable'

export interface FieldLookupEditorRowLike {
  fieldName: string
  displayName?: string
  dataType: string
  lookupConfig?: LookupConfig
}

export interface UseFieldLookupEditorProps {
  modelValue?: LookupConfig
  refTables: RelationTableResponse[]
  allFields: FieldLookupEditorRowLike[]
  currentFieldName?: string
  disabled?: boolean
}

export type FieldLookupEditorEmit = {
  'update:modelValue': [value: LookupConfig]
}

const matchTypes: LookupMatchType[] = ['eq', 'contains', 'startsWith', 'endsWith']

function emptyConfig(): LookupConfig {
  return {
    refTableId: undefined,
    searchFields: [],
    displayFields: [],
    selectedDisplayField: undefined,
    filterConditions: [],
    showBackfillView: true,
    multiple: false,
    derivedFrom: undefined,
  }
}

export function useFieldLookupEditor(
  props: UseFieldLookupEditorProps,
  emit: (event: 'update:modelValue', value: LookupConfig) => void,
) {
  const { t } = useI18n()

  const cfg = reactive<LookupConfig>({ ...emptyConfig(), ...(props.modelValue || {}) })
  cfg.searchFields = cfg.searchFields || []
  cfg.displayFields = cfg.displayFields || []
  cfg.filterConditions = cfg.filterConditions || []
  if (cfg.showBackfillView === undefined) cfg.showBackfillView = true
  if (cfg.multiple === undefined) cfg.multiple = false

  const derivedParentField = ref<string>(props.modelValue?.derivedFrom?.parentField || '')
  const derivedMode = ref<'autofill' | 'filter'>(props.modelValue?.derivedFrom?.derivedMode || 'autofill')
  const derivedJoins = reactive(
    (props.modelValue?.derivedFrom?.joins || []).map(j => ({ ...j })),
  )

  watch(
    () => props.modelValue,
    (v) => {
      Object.assign(cfg, emptyConfig(), v || {})
      cfg.searchFields = cfg.searchFields || []
      cfg.displayFields = cfg.displayFields || []
      cfg.filterConditions = cfg.filterConditions || []
      if (cfg.showBackfillView === undefined) cfg.showBackfillView = true
      if (cfg.multiple === undefined) cfg.multiple = false
      derivedParentField.value = v?.derivedFrom?.parentField || ''
      derivedMode.value = v?.derivedFrom?.derivedMode || 'autofill'
      derivedJoins.splice(0, derivedJoins.length, ...(v?.derivedFrom?.joins || []).map(j => ({ ...j })))
    },
  )

  const refTable = computed<RelationTableResponse | undefined>(() =>
    props.refTables.find(tb => tb.id === cfg.refTableId),
  )

  const refFieldOptions = computed<FieldDefinitionResponse[]>(() =>
    (refTable.value?.fieldDefinitions ?? []).filter(f => f.fieldName?.trim()),
  )

  const parentFieldOptions = computed<FieldLookupEditorRowLike[]>(() =>
    props.allFields.filter(
      f => f.dataType === 'LOOKUP'
        && f.fieldName?.trim()
        && f.fieldName !== props.currentFieldName,
    ),
  )

  const parentLookupField = computed<FieldLookupEditorRowLike | undefined>(() =>
    props.allFields.find(f => f.fieldName === derivedParentField.value),
  )

  const thisLookupField = computed<FieldLookupEditorRowLike | undefined>(() =>
    props.allFields.find(f => f.fieldName === props.currentFieldName),
  )

  function resolveRefTable(lookupCfg: LookupConfig | undefined): RelationTableResponse | undefined {
    if (!lookupCfg?.refTableId && !lookupCfg?.refTableName) return undefined
    return (
      props.refTables.find(tb => tb.id === lookupCfg.refTableId)
      || props.refTables.find(tb => tb.tableName === lookupCfg.refTableName)
    )
  }

  const parentRefTable = computed(() => resolveRefTable(parentLookupField.value?.lookupConfig))

  const parentRefFieldOptions = computed<FieldDefinitionResponse[]>(() =>
    (parentRefTable.value?.fieldDefinitions ?? []).filter(f => f.fieldName?.trim()),
  )

  const tableLabel = (
    tb: RelationTableResponse | undefined,
    lookupCfg?: LookupConfig,
  ): string =>
    lookupCfg?.refTableName || tb?.tableName || tb?.displayName || '?'

  const fieldNameLabel = (f: FieldLookupEditorRowLike | undefined, fallback?: string): string =>
    f?.fieldName?.trim() || fallback?.trim() || '?'

  const parentLookupFieldLabel = computed(() =>
    fieldNameLabel(parentLookupField.value, derivedParentField.value),
  )

  const thisLookupFieldLabel = computed(() =>
    fieldNameLabel(thisLookupField.value, props.currentFieldName),
  )

  const parentRefTableLabel = computed(() =>
    tableLabel(parentRefTable.value, parentLookupField.value?.lookupConfig),
  )

  const thisRefTableLabel = computed(() =>
    tableLabel(refTable.value, cfg),
  )

  const parentJoinSourceLabel = computed(
    () => `${parentLookupFieldLabel.value} . ${parentRefTableLabel.value}`,
  )
  const thisJoinSourceLabel = computed(
    () => `${thisLookupFieldLabel.value} . ${thisRefTableLabel.value}`,
  )

  const isConfigured = computed(() => !!cfg.refTableId && (cfg.searchFields?.length ?? 0) > 0)

  const summaryLabel = computed(() => {
    if (!cfg.refTableId) return t('form.lookupConfigure')
    const name = refTable.value?.displayName || refTable.value?.tableName || '?'
    return derivedParentField.value ? `${name} · ⇐ ${derivedParentField.value}` : name
  })

  function onRefTableChange() {
    cfg.searchFields = []
    cfg.displayFields = []
    cfg.selectedDisplayField = undefined
    cfg.filterConditions = []
    derivedJoins.splice(0, derivedJoins.length)
    emitChange()
  }

  function onParentFieldChange() {
    if (!derivedParentField.value) {
      derivedJoins.splice(0, derivedJoins.length)
    }
    emitChange()
  }

  function addFilter() {
    cfg.filterConditions = cfg.filterConditions || []
    cfg.filterConditions.push({ fieldName: '', value: '', matchType: 'eq' })
    emitChange()
  }

  function removeFilter(i: number) {
    cfg.filterConditions?.splice(i, 1)
    emitChange()
  }

  function addJoin() {
    derivedJoins.push({ fromColumn: '', toColumn: '', matchType: 'eq' })
    emitChange()
  }

  function removeJoin(i: number) {
    derivedJoins.splice(i, 1)
    emitChange()
  }

  function emitChange() {
    const out: LookupConfig = {
      refTableId: cfg.refTableId,
      refTableName: refTable.value?.tableName,
      searchFields: [...(cfg.searchFields || [])],
      displayFields: [...(cfg.displayFields || [])],
      selectedDisplayField: cfg.selectedDisplayField || undefined,
      filterConditions: (cfg.filterConditions || []).map(f => ({ ...f })),
      showBackfillView: cfg.showBackfillView !== false,
      multiple: !!cfg.multiple,
      derivedFrom: derivedParentField.value
        ? {
            parentField: derivedParentField.value,
            derivedMode: derivedMode.value,
            joins: derivedJoins.map(j => ({ ...j })),
          }
        : undefined,
    }
    emit('update:modelValue', out)
  }

  return {
    t,
    matchTypes,
    cfg,
    derivedParentField,
    derivedMode,
    derivedJoins,
    refFieldOptions,
    parentFieldOptions,
    parentRefFieldOptions,
    parentLookupFieldLabel,
    thisLookupFieldLabel,
    parentRefTableLabel,
    thisRefTableLabel,
    parentJoinSourceLabel,
    thisJoinSourceLabel,
    isConfigured,
    summaryLabel,
    onRefTableChange,
    onParentFieldChange,
    addFilter,
    removeFilter,
    addJoin,
    removeJoin,
    emitChange,
  }
}
