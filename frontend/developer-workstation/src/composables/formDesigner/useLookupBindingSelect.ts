import { ref, computed, watch, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { lookupStore } from '@/components/designer/lookupStore'
import { relationTableBindingApi } from '@/api/relationTable'
import {
  type LookupFilterCondition,
  getLookupFilterMatchOptions,
  normalizeLookupFilterCondition,
  normalizeLookupFilterMatchType,
} from '@/utils/lookupFilterConditions'
import type { LookupDerivedFrom } from '@/utils/lookupCascade'

export interface LookupBindingFieldInfo {
  fieldName: string
  dataType: string
  isPrimaryKey: boolean
  displayName?: string
  scale?: number
}

export interface UseLookupBindingSelectProps {
  modelValue?: string
}

export type UseLookupBindingSelectEmit = {
  (e: 'update:modelValue', val: string): void
}

/**
 * Lookup binding editor state for FormDesigner / fc-designer LookupBindingSelect.
 * Uses module-level lookupStore — fc-designer registers in its own Vue app context.
 */
export function useLookupBindingSelect(
  props: UseLookupBindingSelectProps,
  emit: UseLookupBindingSelectEmit,
) {
  const { t } = useI18n()

  onMounted(() => {
    lookupStore.refreshSiblingLookups?.()
    void loadRtDisplayNames()
  })

  const editingLookupField = computed(() => lookupStore.editingLookupField)

  const parentFieldOptions = computed(() =>
    lookupStore.siblingLookupFields.filter(f => f.field !== editingLookupField.value),
  )

  const parentLookupField = computed(() =>
    lookupStore.siblingLookupFields.find(f => f.field === derivedParentField.value),
  )

  const parentRefFieldOptions = computed<LookupBindingFieldInfo[]>(() => {
    const parent = parentLookupField.value
    if (!parent?.tableId) return []
    const table = lookupStore.tables.find(t => t.id === parent.tableId)
    const localFields = (table as any)?.fieldDefinitions || (table as any)?.fields || []
    if (localFields.length > 0) {
      return localFields.map((f: any) => ({
        fieldName: f.fieldName,
        dataType: f.dataType,
        isPrimaryKey: f.isPrimaryKey ?? false,
        displayName: f.displayName || '',
      }))
    }
    return lookupStore.rtFieldCache[parent.tableId] || []
  })

  const parentRefTableLabel = computed(() =>
    parentLookupField.value?.tableName || parentLookupField.value?.title || '?',
  )

  const parentLookupFieldLabel = computed(() =>
    parentLookupField.value?.field || derivedParentField.value || '?',
  )

  const thisLookupFieldLabel = computed(() => editingLookupField.value || '?')

  const thisRefTableLabel = computed(() => {
    const binding = relationBindings.value.find(b => b.bindingId === selectedBindingId.value)
    if (!binding) return '?'
    return rtDisplayNameById.value[binding.tableId]
      || binding.tableDisplayName
      || binding.tableName
      || '?'
  })

  const parentJoinSourceLabel = computed(
    () => `${parentLookupFieldLabel.value} . ${parentRefTableLabel.value}`,
  )
  const thisJoinSourceLabel = computed(
    () => `${thisLookupFieldLabel.value} . ${thisRefTableLabel.value}`,
  )

  function goToViewDesign() {
    if (selectedBindingId.value != null) lookupStore.switchToBinding?.(selectedBindingId.value)
  }

  const relationBindings = computed(() => lookupStore.relationBindings)

  /** tableId → displayName from rt_table_definitions (via getAvailableTables). */
  const rtDisplayNameById = ref<Record<number, string>>({})

  function bindingOptionLabel(b: {
    tableId: number
    tableName: string
    tableDisplayName?: string
    tableDescription?: string
  }): string {
    const display =
      rtDisplayNameById.value[b.tableId]
      || b.tableDisplayName
      || b.tableName
    return b.tableDescription ? `${display} (${b.tableDescription})` : display
  }

  async function loadRtDisplayNames(): Promise<void> {
    try {
      const res = await relationTableBindingApi.getAvailableTables()
      const tables = (res as { data?: Array<{ id?: number; displayName?: string; tableName?: string }> })?.data
        || (res as unknown as Array<{ id?: number; displayName?: string; tableName?: string }>)
        || []
      const map: Record<number, string> = {}
      for (const t of Array.isArray(tables) ? tables : []) {
        if (t?.id == null) continue
        const label = (t.displayName || t.tableName || '').trim()
        if (label) map[t.id] = label
      }
      rtDisplayNameById.value = map
    } catch {
      // FALLBACK(ux): keep technical tableName labels if RT catalog is unavailable
    }
  }

  const selectedBindingId = ref<number | null>(null)
  const searchFields = ref<string[]>([])
  const displayFields = ref<string[]>([])
  const selectedDisplayField = ref('')
  const filterConditions = ref<LookupFilterCondition[]>([])
  const showBackfillView = ref(true)
  /** Parity with Admin Center FieldLookupEditor — Allow multiple (value = PK array). */
  const allowMultiple = ref(false)
  const derivedParentField = ref('')
  const derivedMode = ref<'autofill' | 'filter'>('autofill')
  const derivedJoins = reactive<Array<{ fromColumn: string; toColumn: string; matchType: string }>>([])
  const derivedMatchTypes = ['eq', 'contains', 'startsWith', 'endsWith'] as const
  const apiFields = ref<LookupBindingFieldInfo[]>([])
  const fieldsLoading = ref(false)

  const availableFields = computed<LookupBindingFieldInfo[]>(() => {
    if (!selectedBindingId.value) return []
    const binding = relationBindings.value.find(b => b.bindingId === selectedBindingId.value)
    if (!binding) return []

    const table = lookupStore.tables.find((t: any) => t.id === binding.tableId)
    if (table) {
      const fields = (table as any).fieldDefinitions || (table as any).fields || []
      if (fields.length > 0) {
        return fields.map((f: any) => ({
          fieldName: f.fieldName,
          dataType: f.dataType,
          isPrimaryKey: f.isPrimaryKey ?? false,
          displayName: f.displayName || '',
          scale: f.scale,
        }))
      }
    }

    return apiFields.value
  })

  async function loadFieldsFromApi(tableId: number) {
    fieldsLoading.value = true
    try {
      const res = await relationTableBindingApi.getAvailableTables()
      const tables = (res as any)?.data || res || []
      const displayMap: Record<number, string> = { ...rtDisplayNameById.value }
      for (const t of Array.isArray(tables) ? tables : []) {
        if (t?.id == null) continue
        const label = String(t.displayName || t.tableName || '').trim()
        if (label) displayMap[t.id] = label
      }
      rtDisplayNameById.value = displayMap
      const rtTable = tables.find((t: any) => t.id === tableId)
      if (rtTable?.fieldDefinitions) {
        const fields = rtTable.fieldDefinitions.map((f: any) => ({
          fieldName: f.fieldName,
          dataType: f.dataType,
          isPrimaryKey: f.isPrimaryKey ?? false,
          displayName: f.displayName || '',
        }))
        apiFields.value = fields
        lookupStore.rtFieldCache[tableId] = fields
      } else {
        apiFields.value = []
      }
    } catch {
      apiFields.value = []
    } finally {
      fieldsLoading.value = false
    }
  }

  function parseModelValue() {
    try {
      const cfg = JSON.parse(props.modelValue || '{}')
      selectedBindingId.value = cfg.bindingId ?? null
      searchFields.value = cfg.searchFields ?? []
      displayFields.value = cfg.displayFields ?? []
      selectedDisplayField.value = cfg.selectedDisplayField || cfg.displayField || ''
      filterConditions.value = Array.isArray(cfg.filterConditions)
        ? cfg.filterConditions
          .map((condition: unknown) => normalizeLookupFilterCondition(condition))
          .filter((condition: LookupFilterCondition | null): condition is LookupFilterCondition => condition != null)
        : []
      showBackfillView.value = cfg.showBackfillView !== false
      allowMultiple.value = cfg.multiple === true
      derivedParentField.value = cfg.derivedFrom?.parentField || ''
      derivedMode.value = cfg.derivedFrom?.derivedMode || 'autofill'
      derivedJoins.splice(0, derivedJoins.length, ...(cfg.derivedFrom?.joins || []).map((j: LookupDerivedFrom['joins'][number]) => ({
        fromColumn: j.fromColumn || '',
        toColumn: j.toColumn || '',
        matchType: j.matchType || 'eq',
      })))
    } catch {
      selectedBindingId.value = null
      searchFields.value = []
      displayFields.value = []
      selectedDisplayField.value = ''
      filterConditions.value = []
      showBackfillView.value = true
      allowMultiple.value = false
      derivedParentField.value = ''
      derivedMode.value = 'autofill'
      derivedJoins.splice(0, derivedJoins.length)
    }
  }

  function emitUpdate() {
    const binding = relationBindings.value.find(b => b.bindingId === selectedBindingId.value)
    const cfg = {
      bindingId: selectedBindingId.value,
      tableId: binding?.tableId ?? null,
      tableName: binding?.tableName ?? '',
      searchFields: searchFields.value,
      displayFields: displayFields.value,
      selectedDisplayField: selectedDisplayField.value,
      filterConditions: filterConditions.value
        .filter(condition => condition.fieldName && condition.value !== '')
        .map(condition => ({
          fieldName: condition.fieldName,
          value: condition.value,
          matchType: normalizeLookupFilterMatchType(condition.matchType),
        })),
      showBackfillView: showBackfillView.value,
      multiple: allowMultiple.value,
      ...(derivedParentField.value
        ? {
            derivedFrom: {
              parentField: derivedParentField.value,
              derivedMode: derivedMode.value,
              joins: derivedJoins
                .filter(j => j.fromColumn && j.toColumn)
                .map(j => ({
                  fromColumn: j.fromColumn,
                  toColumn: j.toColumn,
                  matchType: normalizeLookupFilterMatchType(j.matchType),
                })),
            },
          }
        : {}),
    }
    emit('update:modelValue', JSON.stringify(cfg))
  }

  function getFieldLabel(f: LookupBindingFieldInfo): string {
    const name = f.displayName || f.fieldName
    return f.isPrimaryKey ? `🔑 ${name} (PK)` : name
  }

  function handleBindingChange(val: number | null) {
    selectedBindingId.value = val
    searchFields.value = []
    displayFields.value = []
    selectedDisplayField.value = ''
    filterConditions.value = []
    allowMultiple.value = false
    apiFields.value = []
    if (val) {
      const binding = relationBindings.value.find(b => b.bindingId === val)
      if (binding) {
        const table = lookupStore.tables.find((t: any) => t.id === binding.tableId)
        const localFields = (table as any)?.fieldDefinitions || (table as any)?.fields || []
        if (localFields.length === 0) {
          loadFieldsFromApi(binding.tableId).then(() => {
            autoSelectPK()
            emitUpdate()
          })
          return
        }
      }
      autoSelectPK()
    }
    emitUpdate()
  }

  function autoSelectPK() {
    const pkFields = availableFields.value
      .filter(f => f.isPrimaryKey)
      .map(f => f.fieldName)
    if (pkFields.length > 0) {
      searchFields.value = pkFields
    }
  }

  function handleSearchFieldsChange(val: string[]) {
    searchFields.value = val
    emitUpdate()
  }

  function handleDisplayFieldsChange(val: string[]) {
    displayFields.value = val
    if (selectedDisplayField.value && !availableFields.value.some(f => f.fieldName === selectedDisplayField.value)) {
      selectedDisplayField.value = ''
    }
    emitUpdate()
  }

  function handleSelectedDisplayFieldChange(val: string) {
    selectedDisplayField.value = val
    emitUpdate()
  }

  function getFieldInfo(fieldName: string): LookupBindingFieldInfo | undefined {
    return availableFields.value.find(f => f.fieldName === fieldName)
  }

  function getMatchOptionsForField(fieldName: string) {
    return getLookupFilterMatchOptions(getFieldInfo(fieldName)?.dataType)
  }

  function addFilterCondition() {
    filterConditions.value.push({ fieldName: '', value: '', matchType: 'eq' })
    emitUpdate()
  }

  function handleFilterFieldChange(condition: LookupFilterCondition) {
    const field = getFieldInfo(condition.fieldName)
    const allowed = getLookupFilterMatchOptions(field?.dataType).map(option => option.value)
    if (!allowed.includes(normalizeLookupFilterMatchType(condition.matchType))) {
      condition.matchType = 'eq'
    }
    condition.value = ''
    handleFilterConditionChange()
  }

  function parseFilterNumberValue(raw: string): number | undefined {
    if (raw.trim() === '') return undefined
    const parsed = Number(raw)
    return Number.isFinite(parsed) ? parsed : undefined
  }

  function getNumericPrecision(field?: LookupBindingFieldInfo): number {
    const dt = (field?.dataType || '').toUpperCase()
    return dt.includes('DECIMAL') || dt.includes('NUMERIC') ? (field?.scale ?? 2) : 0
  }

  function removeFilterCondition(index: number) {
    filterConditions.value.splice(index, 1)
    emitUpdate()
  }

  function handleFilterConditionChange() {
    emitUpdate()
  }

  function handleShowBackfillViewChange(val: string | number | boolean) {
    showBackfillView.value = Boolean(val)
    emitUpdate()
  }

  function handleAllowMultipleChange(val: string | number | boolean) {
    allowMultiple.value = Boolean(val)
    emitUpdate()
  }

  function onParentFieldChange() {
    if (!derivedParentField.value) {
      derivedJoins.splice(0, derivedJoins.length)
    }
    emitUpdate()
  }

  function addDerivedJoin() {
    derivedJoins.push({ fromColumn: '', toColumn: '', matchType: 'eq' })
    emitUpdate()
  }

  function removeDerivedJoin(index: number) {
    derivedJoins.splice(index, 1)
    emitUpdate()
  }

  function handleDerivedJoinChange() {
    emitUpdate()
  }

  watch(selectedBindingId, (val) => {
    if (val && availableFields.value.length === 0) {
      const binding = relationBindings.value.find(b => b.bindingId === val)
      if (binding) {
        loadFieldsFromApi(binding.tableId)
      }
    }
    if (val && parentLookupField.value?.tableId) {
      const parentTableId = parentLookupField.value.tableId
      const table = lookupStore.tables.find(t => t.id === parentTableId)
      const localFields = (table as any)?.fieldDefinitions || (table as any)?.fields || []
      if (localFields.length === 0 && !lookupStore.rtFieldCache[parentTableId]) {
        loadFieldsFromApi(parentTableId)
      }
    }
  }, { immediate: true })

  watch(() => props.modelValue, parseModelValue, { immediate: true })

  watch(derivedParentField, () => {
    if (derivedParentField.value && parentLookupField.value?.tableId) {
      const parentTableId = parentLookupField.value.tableId
      if (!lookupStore.rtFieldCache[parentTableId]) {
        const table = lookupStore.tables.find(t => t.id === parentTableId)
        const localFields = (table as any)?.fieldDefinitions || (table as any)?.fields || []
        if (localFields.length === 0) {
          loadFieldsFromApi(parentTableId)
        }
      }
    }
  })

  return {
    t,
    selectedBindingId,
    searchFields,
    displayFields,
    selectedDisplayField,
    filterConditions,
    showBackfillView,
    allowMultiple,
    derivedParentField,
    derivedMode,
    derivedJoins,
    derivedMatchTypes,
    fieldsLoading,
    relationBindings,
    availableFields,
    parentFieldOptions,
    parentRefFieldOptions,
    parentLookupFieldLabel,
    parentRefTableLabel,
    thisLookupFieldLabel,
    thisRefTableLabel,
    parentJoinSourceLabel,
    thisJoinSourceLabel,
    bindingOptionLabel,
    goToViewDesign,
    emitUpdate,
    getFieldLabel,
    handleBindingChange,
    handleSearchFieldsChange,
    handleDisplayFieldsChange,
    handleSelectedDisplayFieldChange,
    getFieldInfo,
    getMatchOptionsForField,
    addFilterCondition,
    handleFilterFieldChange,
    parseFilterNumberValue,
    getNumericPrecision,
    removeFilterCondition,
    handleFilterConditionChange,
    handleShowBackfillViewChange,
    handleAllowMultipleChange,
    onParentFieldChange,
    addDerivedJoin,
    removeDerivedJoin,
    handleDerivedJoinChange,
  }
}
