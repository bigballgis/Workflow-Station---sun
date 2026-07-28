<template>
  <div
    ref="wrapperRef"
    class="lookup-field"
    :class="{ readonly }"
  >
    <!-- Multi-select: multiple removable tags + an input row that opens the dropdown. -->
    <div
      v-if="multiple"
      class="lookup-selected-wrapper lookup-multi-wrapper"
      :class="{ 'is-readonly': readonly }"
      @click="!readonly && handleFocus()"
    >
      <span
        v-for="(row, i) in selectedRows"
        :key="i"
        class="lookup-selected-tag"
      >
        <span class="lookup-selected-text">{{ tagTextFor(row) }}</span>
        <el-icon
          v-if="!readonly"
          class="lookup-selected-close"
          @click.stop="removeSelectedAt(i)"
        ><Close /></el-icon>
      </span>
      <input
        v-if="!readonly"
        v-model="searchKeyword"
        class="lookup-multi-input"
        :placeholder="selectedRows.length ? '' : (placeholder || 'Click to search')"
        @focus="handleFocus"
      >
      <span
        v-if="readonly && !selectedRows.length"
        class="lookup-readonly-empty"
      >-</span>
    </div>
    <!-- Single-select: input container with inner tag -->
    <div
      v-else-if="selectedRow"
      class="lookup-selected-wrapper"
      :class="{ 'is-readonly': readonly }"
    >
      <span class="lookup-selected-tag">
        <span class="lookup-selected-text">{{ searchKeyword }}</span>
        <el-icon
          v-if="!readonly"
          class="lookup-selected-close"
          @click.stop="handleClear"
        ><Close /></el-icon>
      </span>
    </div>
    <!-- Search input (hidden when a value is selected or in readonly mode) -->
    <el-input
      v-else-if="!readonly"
      v-model="searchKeyword"
      :placeholder="placeholder || 'Click to search'"
      @focus="handleFocus"
    />
    <!-- Readonly empty: keep disabled input chrome (border) like other fields -->
    <el-input
      v-else
      model-value=""
      placeholder="-"
      class="lookup-input"
      disabled
    />

    <!-- Teleport to body so the dropdown is never clipped/occluded by following form cards
         (the field's own .form-layout-card creates a stacking context that z-index can't escape). -->
    <Teleport to="body">
      <div
        v-if="dropdownVisible"
        ref="dropdownRef"
        class="lookup-dropdown lookup-dropdown--floating"
        :style="dropdownStyle"
      >
        <el-table
          v-loading="loading"
          :data="filteredResults"
          size="small"
          highlight-current-row
          max-height="260"
          @row-click="handleSelect"
        >
          <el-table-column
            v-if="multiple"
            width="40"
            align="center"
          >
            <template #default="{ row }">
              <el-icon
                v-if="isRowSelected(row)"
                class="lookup-check"
              ><Check /></el-icon>
            </template>
          </el-table-column>
          <el-table-column
            v-for="col in visibleColumns"
            :key="col.prop"
            :prop="col.prop"
            :label="col.label"
            :min-width="col.width || 120"
            show-overflow-tooltip
          />
        </el-table>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useZIndex } from 'element-plus'
import { Close, Check } from '@element-plus/icons-vue'
import { relationTableDataApi, type LookupFilterCondition } from '@/api/relationTable'
import { fetchLookupRowByPrimaryKey } from './fetchLookupRowByPrimaryKey'
import { resolveLookupCellTagText } from './lookupHelpers'

export interface LookupViewField {
  fieldName: string
  displayLabel?: string
  columnWidth?: number
  sortOrder: number
  visible: boolean
}

const props = defineProps<{
  modelValue?: any
  tableId: number
  searchFields: string[]
  displayField: string
  displayFields?: string[]
  selectedDisplayField?: string
  lookupConfig?: string | Record<string, unknown>
  filterConditions?: LookupFilterCondition[]
  viewFields?: LookupViewField[]
  placeholder?: string
  readonly?: boolean
  /** Multi-select: value is an array of PKs; multiple tags; dropdown toggles rows. */
  multiple?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
  (e: 'select', row: Record<string, any>): void
  (e: 'clear'): void
  (e: 'viewFieldsLoaded', fields: LookupViewField[]): void
}>()

const wrapperRef = ref<HTMLElement>()
const dropdownRef = ref<HTMLElement>()
const dropdownVisible = ref(false)
// Floating dropdown position (teleported to body, position: fixed) — recomputed from the
// field's viewport rect on open and on scroll/resize so it tracks the input.
const dropdownStyle = ref<Record<string, string>>({})
const searchKeyword = ref('')
const selectedRow = ref<Record<string, any> | null>(null)
// Multi-select: the picked rows (order preserved). modelValue is an array of their PKs.
const selectedRows = ref<Record<string, any>[]>([])
const allRows = ref<Record<string, any>[]>([])
const loading = ref(false)
const dataLoaded = ref(false)
const loadedViewFields = ref<LookupViewField[]>([])

// Use prop viewFields if provided, otherwise use loaded ones
const effectiveViewFields = computed(() =>
  props.viewFields?.length ? props.viewFields : loadedViewFields.value
)

const visibleColumns = computed(() => {
  // 1. Use displayFields (from lookup config "Display Fields") — matches developer-workstation LookupPreview
  if (props.displayFields && props.displayFields.length > 0) {
    return props.displayFields.map(f => ({ prop: f, label: f, width: undefined as number | undefined }))
  }
  // 2. Fallback: searchFields
  if (props.searchFields?.length > 0) {
    return props.searchFields.map(f => ({ prop: f, label: f, width: undefined as number | undefined }))
  }
  // 3. Fallback: displayField
  const cols = new Set<string>()
  if (props.displayField) cols.add(props.displayField)
  return Array.from(cols).map(f => ({ prop: f, label: f, width: undefined as number | undefined }))
})

// Client-side filtering on the loaded data
const filteredResults = computed(() => {
  const kw = searchKeyword.value?.trim().toLowerCase()
  if (!kw) return allRows.value

  const fields = props.searchFields?.length ? props.searchFields : null
  return allRows.value.filter(row => {
    const values = fields
      ? fields.map(f => row[f])
      : Object.values(row)
    return values.some(v => v != null && String(v).toLowerCase().includes(kw))
  })
})

// Server caps each page at 200; page through until exhausted so the dropdown
// holds the full table. The hard stop only guards against runaway tables.
const LOOKUP_PAGE_SIZE = 200
const LOOKUP_MAX_ROWS = 10000

async function fetchAllLookupRows(): Promise<Record<string, any>[]> {
  const rows: Record<string, any>[] = []
  for (let offset = 0; offset < LOOKUP_MAX_ROWS; offset += LOOKUP_PAGE_SIZE) {
    const res = await relationTableDataApi.searchForLookup(props.tableId, {
      keyword: '',
      searchFields: props.searchFields || [],
      displayField: props.displayField || '',
      filterConditions: props.filterConditions || [],
      limit: LOOKUP_PAGE_SIZE,
      offset
    })
    const batch = res || []
    rows.push(...batch)
    if (batch.length < LOOKUP_PAGE_SIZE) return rows
  }
  console.warn(`[LookupField] table ${props.tableId} exceeds ${LOOKUP_MAX_ROWS} rows; dropdown truncated`)
  return rows
}

async function loadAllData() {
  if (!props.tableId || dataLoaded.value) return
  loading.value = true
  try {
    // Load data and view fields in parallel
    const [dataRows, vfRes] = await Promise.all([
      fetchAllLookupRows(),
      (!effectiveViewFields.value.length)
        ? relationTableDataApi.getViewFields(props.tableId)
        : Promise.resolve([] as LookupViewField[])
    ])
    allRows.value = dataRows
    if (vfRes?.length) {
      loadedViewFields.value = vfRes as LookupViewField[]
      emit('viewFieldsLoaded', loadedViewFields.value)
    }
    dataLoaded.value = true
  } catch (e) {
    console.error('[LookupField] load error:', e)
    allRows.value = []
  } finally {
    loading.value = false
  }
}

const { nextZIndex } = useZIndex()
const dropdownZIndex = ref(3000)

// ---- z-index hardening ported from user-portal LookupField (keep the two copies in sync). ----
// Absolute floor: keep the dropdown above the base app chrome even if no overlay is present.
const LOOKUP_DROPDOWN_Z_FLOOR = 3000
// Safety margin above the current top overlay so the dropdown clears not just the overlay
// but its own popper children (el-scrollbar, tooltips) that Element Plus stacks a few above it.
const LOOKUP_DROPDOWN_Z_OFFSET = 10

// Highest z-index among the currently active dialogs / overlays / poppers on the page.
// The lookup is teleported to <body>, so it competes directly with these for stacking; a single
// nextZIndex() is not enough because Element Plus can hand later overlays (or their poppers) a
// higher counter value, re-covering the dropdown after it opened.
function currentTopLayerZIndex(): number {
  const SELECTOR = '.el-overlay, .el-overlay-dialog, .el-dialog, .el-popper, .el-picker__popper, .el-select__popper'
  let top = 0
  document.querySelectorAll<HTMLElement>(SELECTOR).forEach(node => {
    // Skip our own dropdown so re-computation doesn't chase its own z-index upward.
    if (node === dropdownRef.value) return
    const z = parseInt(window.getComputedStyle(node).zIndex, 10)
    if (Number.isFinite(z) && z > top) top = z
  })
  return top
}

// Resolve the dropdown's z-index against the live stacking context: above the current top overlay
// + margin, never below the absolute floor. `base` seeds the lower bound — at open we pass a fresh
// nextZIndex() (bumps EP's shared counter once); on scroll/resize we pass the current value so the
// dropdown never drops below where it already sits and we don't burn the counter per scroll tick.
function resolveDropdownZIndex(base: number): number {
  return Math.max(base, currentTopLayerZIndex() + LOOKUP_DROPDOWN_Z_OFFSET, LOOKUP_DROPDOWN_Z_FLOOR)
}

// Position the teleported dropdown under the field using its viewport rect.
function updateDropdownPosition() {
  const el = wrapperRef.value
  if (!el) return
  const r = el.getBoundingClientRect()
  dropdownStyle.value = {
    position: 'fixed',
    top: `${r.bottom + 4}px`,
    left: `${r.left}px`,
    width: `${r.width}px`,
    zIndex: String(dropdownZIndex.value),
  }
}

function handleFocus() {
  if (props.readonly) return
  // Compute the z-index against the current top overlay so the dropdown sits above it — critical
  // when the lookup is rendered inside an el-dialog (Add Record), whose overlay z-index would
  // otherwise cover a fixed-z dropdown.
  dropdownZIndex.value = resolveDropdownZIndex(nextZIndex())
  dropdownVisible.value = true
  updateDropdownPosition()
  loadAllData()
}

function pkField(): string {
  return String(props.searchFields?.[0] || 'id').trim() || 'id'
}

function rowPk(row: Record<string, any>): any {
  const pk = pkField()
  return row?.[pk] ?? row?.id
}

function isRowSelected(row: Record<string, any>): boolean {
  const pk = rowPk(row)
  return selectedRows.value.some(r => String(rowPk(r)) === String(pk))
}

function tagTextFor(row: Record<string, any>): string {
  return String(getDisplayValue(row) ?? '')
}

function emitMultiModel() {
  emit('update:modelValue', selectedRows.value.map(r => rowPk(r)))
}

function handleSelect(row: Record<string, any>) {
  if (props.multiple) {
    const pk = rowPk(row)
    const idx = selectedRows.value.findIndex(r => String(rowPk(r)) === String(pk))
    if (idx >= 0) {
      selectedRows.value.splice(idx, 1)
    } else {
      selectedRows.value.push(row)
    }
    searchKeyword.value = ''
    emitMultiModel()
    emit('select', row)
    // Keep the dropdown open for further picks.
    return
  }
  const displayVal = getDisplayValue(row)
  searchKeyword.value = String(displayVal ?? '')
  selectedRow.value = row
  emit('update:modelValue', row)
  emit('select', row)
  dropdownVisible.value = false
}

function removeSelectedAt(i: number) {
  selectedRows.value.splice(i, 1)
  emitMultiModel()
}

function handleClear() {
  searchKeyword.value = ''
  selectedRow.value = null
  emit('update:modelValue', null)
  emit('clear')
}

// Sync empty parent modelValue into local UI only — never emit('clear').
// emit('clear') is reserved for user-initiated handleClear (cascade wipe in parent).
function clearLookupSelectionFromModel() {
  selectedRow.value = null
  searchKeyword.value = ''
}

function initFromModelValue(val: any) {
  if (props.multiple) {
    initMultiFromModelValue(val)
    return
  }
  // Sync from model only — never emit('clear') on empty init (avoids cascade wipe before parent hydrate).
  if (val == null || val === '') {
    clearLookupSelectionFromModel()
    return
  }
  // Process variables often persist lookup as a scalar id/string — readonly inline rows otherwise render "-" forever.
  if (typeof val === 'number' || typeof val === 'bigint' || typeof val === 'boolean') {
    const scalarRow = buildSyntheticLookupRow(val)
    selectedRow.value = scalarRow
    searchKeyword.value = String(getDisplayValue(scalarRow) || '')
    emit('select', scalarRow)
    void hydrateScalarFromRelationTable(String(val))
    return
  }
  if (typeof val === 'string') {
    const trimmed = val.trim()
    if (trimmed === '') {
      clearLookupSelectionFromModel()
      return
    }
    const scalarRow = buildSyntheticLookupRow(trimmed)
    selectedRow.value = scalarRow
    searchKeyword.value = String(getDisplayValue(scalarRow) || '')
    emit('select', scalarRow)
    void hydrateScalarFromRelationTable(trimmed)
    return
  }
  if (typeof val === 'object' && Object.keys(val).length > 0) {
    selectedRow.value = val
    const displayVal = getDisplayValue(val)
    searchKeyword.value = String(displayVal ?? '')
    emit('select', val)
  }
}

/** Multi-select init: modelValue is an array of PKs (or a JSON-array string). */
function initMultiFromModelValue(val: any) {
  let pks: any[] = []
  if (Array.isArray(val)) {
    pks = val
  } else if (typeof val === 'string' && val.trim() !== '') {
    // Tolerate a persisted JSON-array string or a single scalar.
    try {
      const parsed = JSON.parse(val)
      pks = Array.isArray(parsed) ? parsed : [val]
    } catch {
      pks = [val]
    }
  } else if (val != null && val !== '') {
    pks = [val]
  }
  pks = pks.filter(p => p != null && String(p).trim() !== '')
  if (!pks.length) {
    selectedRows.value = []
    searchKeyword.value = ''
    return
  }
  // Seed synthetic rows immediately so tags render, then hydrate each to a full row.
  selectedRows.value = pks.map(p => buildSyntheticLookupRow(p))
  pks.forEach((p, idx) => void hydrateMultiRowAt(idx, p))
}

async function hydrateMultiRowAt(index: number, scalar: any) {
  if (!props.tableId) return
  try {
    const row = await fetchLookupRowByPrimaryKey(props.tableId, scalar, {
      searchFields: props.searchFields || [],
      displayField: props.displayField || '',
      filterConditions: props.filterConditions || []
    })
    if (!row || !Object.keys(row).length) return
    // Only replace if the slot still holds the same PK (guard against races/edits).
    const cur = selectedRows.value[index]
    if (cur && String(rowPk(cur)) === String(scalar)) {
      selectedRows.value.splice(index, 1, row)
    }
  } catch {
    /* keep synthetic row */
  }
}

/**
 * 将仅存的主键标量解析为完整行，用于只读标签/回填视图；不写回 modelValue，避免改变流程持久化形态。
 */
async function hydrateScalarFromRelationTable(scalar: string | number) {
  if (!props.tableId) return
  const want = String(scalar).trim()
  try {
    const row = await fetchLookupRowByPrimaryKey(props.tableId, scalar, {
      searchFields: props.searchFields || [],
      displayField: props.displayField || '',
      filterConditions: props.filterConditions || []
    })
    if (!row || !Object.keys(row).length) return
    const cur = props.modelValue
    if (cur == null || cur === '') return
    if (typeof cur === 'object') return
    if (String(cur).trim() !== want) return
    selectedRow.value = row
    searchKeyword.value = String(getDisplayValue(row) ?? '')
    emit('select', row)
  } catch {
    /* 保持合成行回退 */
  }
}

function refreshSearchKeywordFromSelectedRow() {
  if (!selectedRow.value) return
  searchKeyword.value = String(getDisplayValue(selectedRow.value) ?? '')
}

/** Minimal row shape for persisted scalar PK — do not copy PK into selectedDisplayField column. */
function buildSyntheticLookupRow(raw: number | bigint | boolean | string): Record<string, any> {
  const pk = String(props.searchFields?.[0] || 'id').trim() || 'id'
  const row: Record<string, any> = { [pk]: raw }
  if (pk !== 'id') row.id = raw
  return row
}

function getDisplayValue(row: Record<string, any>) {
  return resolveLookupCellTagText(props, row)
}

// Watch for external modelValue changes (e.g. form data loaded after mount).
// Lookup values change by wholesale replacement (select → new object, clear → null);
// initFromModelValue re-derives from the value as a whole, so a shallow (reference) watch suffices.
watch(
  () => props.modelValue,
  val => {
    initFromModelValue(val)
  },
  { immediate: true },
)

watch(
  () => [props.tableId, props.searchFields, props.displayField, props.filterConditions],
  () => {
    allRows.value = []
    dataLoaded.value = false
  },
  { deep: true }
)

watch(
  () => [
    props.selectedDisplayField,
    props.displayField,
    props.displayFields,
    props.lookupConfig,
  ],
  () => {
    refreshSearchKeywordFromSelectedRow()
  },
)

function onClickOutside(e: MouseEvent) {
  const target = e.target as Node
  // Dropdown is teleported to body, so a click on a result row is "outside" the wrapper —
  // treat clicks inside either the wrapper or the floating dropdown as inside.
  const insideWrapper = wrapperRef.value?.contains(target)
  const insideDropdown = dropdownRef.value?.contains(target)
  if (!insideWrapper && !insideDropdown) {
    dropdownVisible.value = false
  }
}

// Keep the floating dropdown aligned while it's open; close on far scroll is acceptable but we just reposition.
// Also re-resolve the z-index: scrolling/resizing the host dialog can raise a sibling overlay above us,
// so refresh stacking together with position to avoid the dropdown slipping back under the dialog.
function onViewportChange() {
  if (dropdownVisible.value) {
    dropdownZIndex.value = resolveDropdownZIndex(dropdownZIndex.value)
    updateDropdownPosition()
  }
}

onMounted(() => {
  document.addEventListener('mousedown', onClickOutside)
  window.addEventListener('scroll', onViewportChange, true)
  window.addEventListener('resize', onViewportChange)

  // Eagerly load view fields so LookupViewDisplay can show them after selection
  if (props.tableId && !effectiveViewFields.value.length) {
    relationTableDataApi.getViewFields(props.tableId).then(res => {
      if (res?.length) {
        loadedViewFields.value = res as LookupViewField[]
        emit('viewFieldsLoaded', loadedViewFields.value)
      }
    }).catch(() => {})
  } else if (effectiveViewFields.value.length) {
    // Props already have view fields — emit them so FormRenderer's lookupLoadedViewFields is populated
    emit('viewFieldsLoaded', effectiveViewFields.value as LookupViewField[])
  }
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onClickOutside)
  window.removeEventListener('scroll', onViewportChange, true)
  window.removeEventListener('resize', onViewportChange)
})

defineExpose({ effectiveViewFields })
</script>

<style lang="scss" scoped>
.lookup-field {
  width: 100%;
  position: relative;

  &.readonly {
    cursor: not-allowed;

    .lookup-input :deep(.el-input__wrapper) {
      background-color: var(--el-disabled-bg-color, #f5f7fa);
      box-shadow: 0 0 0 1px var(--el-disabled-border-color, #e4e7ed) inset;
      cursor: not-allowed;
      pointer-events: none;
    }
  }

  .lookup-selected-wrapper {
    display: flex;
    align-items: center;
    min-height: 32px;
    padding: 4px 8px;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    background: #fff;

    &.is-readonly {
      background: var(--el-disabled-bg-color, #f5f7fa);
      border-color: var(--el-disabled-border-color, #e4e7ed);
      cursor: not-allowed;
      pointer-events: none;
    }
  }

  .lookup-selected-tag {
    display: inline-flex;
    align-items: center;
    max-width: 100%;
    height: 24px;
    padding: 0 8px;
    border-radius: 4px;
    background: #f0f2f5;
    font-size: 13px;
    color: #909399;
    line-height: 24px;

    .lookup-selected-text {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .lookup-selected-close {
      flex-shrink: 0;
      margin-left: 4px;
      font-size: 13px;
      color: #909399;
      cursor: pointer;

      &:hover {
        color: #606266;
      }
    }
  }

  .lookup-multi-wrapper {
    flex-wrap: wrap;
    gap: 4px;
    cursor: text;

    .lookup-selected-tag {
      background: var(--el-color-primary-light-9, #ecf5ff);
      color: var(--el-color-primary, #409eff);
    }
  }

  .lookup-multi-input {
    flex: 1;
    min-width: 60px;
    border: none;
    outline: none;
    background: transparent;
    font-size: 13px;
    line-height: 24px;
    color: #606266;
  }

  .lookup-readonly-empty {
    color: #606266;
    line-height: 32px;
  }

  .lookup-input {
    width: 100%;
  }
}
</style>

<!-- Floating dropdown is teleported to <body>, so its styles must be global (scoped styles
     would not reach it). z-index above form cards / dialogs; position is set inline. -->
<style lang="scss">
.lookup-dropdown--floating {
  z-index: 3000;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);

  .lookup-check {
    color: var(--el-color-primary, #409eff);
  }
}
</style>
