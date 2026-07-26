import { ref, computed, nextTick, onBeforeUnmount, watch } from 'vue'
import {
  applyLookupFixedFilters,
  type LookupFilterCondition,
} from '@/utils/lookupFilterConditions'
import { buildLookupPreviewMockRows } from '@/utils/lookupPreviewMockRows'

export interface LookupPreviewViewField {
  fieldName: string
  displayLabel: string
  columnWidth?: number
  sortOrder: number
  visible: boolean
}

export interface LookupPreviewFieldDef {
  fieldName: string
  dataType?: string
  comment?: string
  description?: string
  displayName?: string
}

export interface UseLookupPreviewProps {
  modelValue?: unknown
  label: string
  placeholder?: string
  searchFields: string[]
  displayFields: string[]
  selectedDisplayField?: string
  filterConditions?: LookupFilterCondition[]
  viewFields: LookupPreviewViewField[]
  fieldDefs: LookupPreviewFieldDef[]
  showBackfillView?: boolean
  readonly?: boolean
  multiple?: boolean
  ensureMockFields?: string[]
}

export type LookupPreviewEmit = {
  (e: 'update:modelValue', value: unknown): void
  (e: 'select', row: Record<string, unknown>): void
  (e: 'clear'): void
}

export function useLookupPreview(props: UseLookupPreviewProps, emit: LookupPreviewEmit) {
  const dropdownRef = ref<HTMLElement>()
  const fieldRef = ref<HTMLElement>()
  const dropdownVisible = ref(false)
  const dropdownStyle = ref<Record<string, string>>({})
  const searchKeyword = ref('')
  const selectedRow = ref<Record<string, unknown> | null>(null)
  const selectedRows = ref<Record<string, unknown>[]>([])

  const visibleColumns = computed(() => {
    if (props.displayFields?.length > 0) {
      return props.displayFields.map(f => {
        const fd = props.fieldDefs.find(d => d.fieldName === f)
        return { prop: f, label: fd?.displayName || f }
      })
    }
    if (props.searchFields?.length > 0) {
      return props.searchFields.map(f => {
        const fd = props.fieldDefs.find(d => d.fieldName === f)
        return { prop: f, label: fd?.displayName || f }
      })
    }
    return []
  })

  const displayViewFields = computed(() => {
    if (props.viewFields?.length > 0) {
      return props.viewFields
        .filter(f => f.visible !== false)
        .sort((a, b) => a.sortOrder - b.sortOrder)
    }
    return []
  })

  const mockRows = computed(() =>
    buildLookupPreviewMockRows({
      displayFields: props.displayFields,
      searchFields: props.searchFields,
      viewFields: props.viewFields,
      fieldDefs: props.fieldDefs,
      ensureFields: props.ensureMockFields,
      filterConditions: (props.filterConditions || []).map(c => ({
        fieldName: c.fieldName,
        value: '',
        matchType: c.matchType,
      })),
    }),
  )

  const filteredResults = computed(() => {
    const kw = searchKeyword.value?.trim().toLowerCase()
    const fixedFilteredRows = applyLookupFixedFilters(mockRows.value, props.filterConditions)
    if (!kw) return fixedFilteredRows
    const fields = props.searchFields?.length ? props.searchFields : []
    if (fields.length === 0) return fixedFilteredRows
    return fixedFilteredRows.filter(row =>
      fields.some(f => row[f] != null && String(row[f]).toLowerCase().includes(kw)),
    )
  })

  function getPrimaryDisplayField() {
    return props.selectedDisplayField || props.displayFields?.[0] || visibleColumns.value[0]?.prop || props.searchFields?.[0] || ''
  }

  function getDisplayText(row: Record<string, unknown> | null) {
    if (!row) return ''
    const displayField = getPrimaryDisplayField()
    if (displayField && row[displayField] != null && typeof row[displayField] !== 'object') {
      return String(row[displayField])
    }
    const firstValue = Object.values(row).find(
      value => value != null && value !== '' && typeof value !== 'object',
    )
    return firstValue == null ? '' : String(firstValue)
  }

  function pkField(): string {
    return String(props.searchFields?.[0] || 'id').trim() || 'id'
  }

  function rowPk(row: Record<string, unknown>): unknown {
    const pk = pkField()
    return row?.[pk] ?? row?.id
  }

  function isRowSelected(row: Record<string, unknown>): boolean {
    const pk = rowPk(row)
    return selectedRows.value.some(r => String(rowPk(r)) === String(pk))
  }

  function normalizeValue(value: unknown): Record<string, unknown> | null {
    if (value == null || value === '') return null
    if (typeof value === 'object' && !Array.isArray(value)) return value as Record<string, unknown>
    const displayField = getPrimaryDisplayField()
    return displayField ? { [displayField]: value } : { value }
  }

  function initMultiFromModelValue(val: unknown) {
    let items: unknown[] = []
    if (Array.isArray(val)) items = val
    else if (typeof val === 'string' && val.trim() !== '') {
      try {
        const parsed = JSON.parse(val)
        items = Array.isArray(parsed) ? parsed : [val]
      } catch {
        items = [val]
      }
    } else if (val != null && val !== '') {
      items = [val]
    }
    items = items.filter(p => p != null && !(typeof p === 'string' && p.trim() === ''))
    if (!items.length) {
      selectedRows.value = []
      searchKeyword.value = ''
      return
    }
    if (items.every(p => typeof p === 'object' && !Array.isArray(p))) {
      selectedRows.value = items as Record<string, unknown>[]
      searchKeyword.value = ''
      return
    }
    const pks = items
      .map(p => (typeof p === 'object' && p != null ? rowPk(p as Record<string, unknown>) : p))
      .filter(p => p != null && String(p).trim() !== '')
    selectedRows.value = pks.map((pk) => {
      const match = mockRows.value.find(r => String(rowPk(r)) === String(pk))
      if (match) return match
      const displayField = getPrimaryDisplayField()
      return displayField
        ? { [pkField()]: pk, [displayField]: String(pk) }
        : { [pkField()]: pk }
    })
  }

  watch(
    () => [props.modelValue, props.multiple, props.selectedDisplayField, props.displayFields, props.searchFields, visibleColumns.value],
    ([value]) => {
      if (props.multiple) {
        initMultiFromModelValue(value)
        return
      }
      const nextRow = normalizeValue(value)
      selectedRow.value = nextRow
      searchKeyword.value = getDisplayText(nextRow)
    },
    { immediate: true, deep: true },
  )

  function updateDropdownPosition() {
    const rect = fieldRef.value?.getBoundingClientRect()
    if (!rect) return
    dropdownStyle.value = {
      position: 'fixed',
      top: `${rect.bottom + 4}px`,
      left: `${rect.left}px`,
      width: `${rect.width}px`,
      zIndex: '3000',
    }
  }

  function showDropdown() {
    if (props.readonly) return
    if (dropdownVisible.value) {
      updateDropdownPosition()
      return
    }
    dropdownVisible.value = true
    nextTick(updateDropdownPosition)
  }

  function handleWrapperClick() {
    showDropdown()
  }

  function handleFieldClick(e: MouseEvent) {
    if ((e.target as HTMLElement).closest('.lookup-selected-close')) return
    showDropdown()
  }

  function emitMultiModel() {
    emit('update:modelValue', selectedRows.value.map(r => ({ ...r })))
  }

  function handleSelect(row: Record<string, unknown>) {
    if (props.multiple) {
      const pk = rowPk(row)
      const idx = selectedRows.value.findIndex(r => String(rowPk(r)) === String(pk))
      if (idx >= 0) selectedRows.value.splice(idx, 1)
      else selectedRows.value.push(row)
      searchKeyword.value = ''
      emitMultiModel()
      emit('select', row)
      return
    }
    selectedRow.value = row
    searchKeyword.value = getDisplayText(row)
    emit('update:modelValue', row)
    emit('select', row)
    dropdownVisible.value = false
  }

  function removeSelectedAt(i: number) {
    if (props.readonly) return
    selectedRows.value.splice(i, 1)
    emitMultiModel()
    const last = selectedRows.value[selectedRows.value.length - 1]
    if (last) emit('select', last)
    else emit('clear')
  }

  function handleClear() {
    if (props.readonly) return
    searchKeyword.value = ''
    selectedRow.value = null
    selectedRows.value = []
    emit('update:modelValue', props.multiple ? [] : null)
    emit('clear')
  }

  function onInputFocus() {
    showDropdown()
  }

  function onDocClick(e: MouseEvent) {
    if (!dropdownVisible.value) return
    const target = e.target as Node
    const inField = fieldRef.value && fieldRef.value.contains(target)
    const inDropdown = dropdownRef.value && dropdownRef.value.contains(target)
    if (inField || inDropdown) return
    dropdownVisible.value = false
  }

  document.addEventListener('mousedown', onDocClick)
  window.addEventListener('scroll', updateDropdownPosition, true)
  window.addEventListener('resize', updateDropdownPosition)

  onBeforeUnmount(() => {
    document.removeEventListener('mousedown', onDocClick)
    window.removeEventListener('scroll', updateDropdownPosition, true)
    window.removeEventListener('resize', updateDropdownPosition)
  })

  return {
    dropdownRef,
    fieldRef,
    dropdownVisible,
    dropdownStyle,
    searchKeyword,
    selectedRow,
    selectedRows,
    visibleColumns,
    displayViewFields,
    filteredResults,
    getDisplayText,
    isRowSelected,
    handleWrapperClick,
    handleFieldClick,
    handleSelect,
    removeSelectedAt,
    handleClear,
    onInputFocus,
  }
}
