<template>
  <div
    ref="wrapperRef"
    class="lookup-field"
  >
    <!-- Selected value: input container with inner tag -->
    <div
      v-if="selectedRow"
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
    <!-- Readonly empty state -->
    <span
      v-else
      class="lookup-readonly-empty"
    >-</span>

    <div
      v-if="dropdownVisible"
      class="lookup-dropdown"
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
          v-for="col in visibleColumns"
          :key="col.prop"
          :prop="col.prop"
          :label="col.label"
          :min-width="col.width || 120"
          show-overflow-tooltip
        />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { Search, Close } from '@element-plus/icons-vue'
import { relationTableApi } from '@/api/relationTable'
import { fetchLookupRowByPrimaryKey } from './fetchLookupRowByPrimaryKey'

export interface LookupViewField {
  fieldName: string
  displayLabel?: string
  columnWidth?: number
  sortOrder: number
  visible: boolean
}

export interface LookupFilterCondition {
  fieldName: string
  value: string
}

const props = defineProps<{
  modelValue?: any
  tableId: number
  searchFields: string[]
  displayField: string
  displayFields?: string[]
  selectedDisplayField?: string
  filterConditions?: LookupFilterCondition[]
  viewFields?: LookupViewField[]
  placeholder?: string
  readonly?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
  (e: 'select', row: Record<string, any>): void
  (e: 'clear'): void
  (e: 'viewFieldsLoaded', fields: LookupViewField[]): void
}>()

const wrapperRef = ref<HTMLElement>()
const dropdownVisible = ref(false)
const searchKeyword = ref('')
const selectedRow = ref<Record<string, any> | null>(null)
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

async function loadAllData() {
  if (!props.tableId || dataLoaded.value) return
  loading.value = true
  try {
    // Load data and view fields in parallel
    const [dataRes, vfRes] = await Promise.all([
      relationTableApi.searchForLookup(props.tableId, {
        keyword: '',
        searchFields: props.searchFields || [],
        displayField: props.displayField || '',
        filterConditions: props.filterConditions || [],
        limit: 200
      }),
      (!effectiveViewFields.value.length)
        ? relationTableApi.getViewFields(props.tableId)
        : Promise.resolve({ data: [] })
    ])
    allRows.value = dataRes.data || []
    if (vfRes.data?.length) {
      loadedViewFields.value = vfRes.data as LookupViewField[]
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

function handleFocus() {
  if (props.readonly) return
  dropdownVisible.value = true
  loadAllData()
}

function handleSelect(row: Record<string, any>) {
  const displayVal = getDisplayValue(row)
  searchKeyword.value = String(displayVal ?? '')
  selectedRow.value = row
  emit('update:modelValue', row)
  emit('select', row)
  dropdownVisible.value = false
}

function handleClear() {
  searchKeyword.value = ''
  selectedRow.value = null
  emit('update:modelValue', null)
  emit('clear')
}

// Initialize selectedRow and searchKeyword from modelValue (for saved form data)
function initFromModelValue(val: any) {
  if (val == null || val === '') {
    selectedRow.value = null
    searchKeyword.value = ''
    return
  }
  // Process variables often persist lookup as a scalar id/string — readonly inline rows otherwise render "-" forever.
  if (typeof val === 'number' || typeof val === 'bigint' || typeof val === 'boolean') {
    const scalarRow = buildSyntheticLookupRow(val)
    selectedRow.value = scalarRow
    searchKeyword.value = String(getDisplayValue(scalarRow) ?? val ?? '')
    emit('select', scalarRow)
    void hydrateScalarFromRelationTable(val)
    return
  }
  if (typeof val === 'string') {
    const t = val.trim()
    if (t === '') {
      selectedRow.value = null
      searchKeyword.value = ''
      return
    }
    const scalarRow = buildSyntheticLookupRow(t)
    selectedRow.value = scalarRow
    searchKeyword.value = String(getDisplayValue(scalarRow) ?? t)
    emit('select', scalarRow)
    void hydrateScalarFromRelationTable(t)
    return
  }
  if (typeof val === 'object' && Object.keys(val).length > 0) {
    selectedRow.value = val
    const displayVal = getDisplayValue(val)
    searchKeyword.value = String(displayVal ?? '')
    emit('select', val)
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

/** Minimal row shape so getDisplayValue / LookupViewDisplay have stable keys for persisted scalar lookups */
function buildSyntheticLookupRow(raw: number | bigint | boolean | string): Record<string, any> {
  const df = props.selectedDisplayField || props.displayField
  const row: Record<string, any> = {}
  if (df && String(df).trim()) row[String(df)] = raw
  if (row.id === undefined) row.id = raw
  return row
}

function getDisplayValue(row: Record<string, any>) {
  const displayField = props.selectedDisplayField || props.displayField
  return displayField ? row[displayField] : Object.values(row)[0]
}

// Watch for external modelValue changes (e.g. form data loaded after mount)
watch(
  () => props.modelValue,
  val => {
    initFromModelValue(val)
  },
  { immediate: true, deep: true },
)

watch(
  () => [props.tableId, props.searchFields, props.displayField, props.filterConditions],
  () => {
    allRows.value = []
    dataLoaded.value = false
  },
  { deep: true }
)

function onClickOutside(e: MouseEvent) {
  if (wrapperRef.value && !wrapperRef.value.contains(e.target as Node)) {
    dropdownVisible.value = false
  }
}

onMounted(() => {
  document.addEventListener('mousedown', onClickOutside)

  // Eagerly load view fields so LookupViewDisplay can show them after selection
  if (props.tableId && !effectiveViewFields.value.length) {
    relationTableApi.getViewFields(props.tableId).then(res => {
      if (res.data?.length) {
        loadedViewFields.value = res.data as LookupViewField[]
        emit('viewFieldsLoaded', loadedViewFields.value)
      }
    }).catch(() => {})
  } else if (effectiveViewFields.value.length) {
    // Props already have view fields — emit them so FormRenderer's lookupLoadedViewFields is populated
    emit('viewFieldsLoaded', effectiveViewFields.value as LookupViewField[])
  }
})
onBeforeUnmount(() => document.removeEventListener('mousedown', onClickOutside))

defineExpose({ effectiveViewFields })
</script>

<style lang="scss" scoped>
.lookup-field {
  width: 100%;
  position: relative;

  .lookup-selected-wrapper {
    display: flex;
    align-items: center;
    min-height: 32px;
    padding: 4px 8px;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    background: #fff;

    &.is-readonly {
      background: #f5f7fa;
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

  .lookup-readonly-empty {
    color: #606266;
    line-height: 32px;
  }

  .lookup-dropdown {
    position: absolute;
    z-index: 2050;
    left: 0;
    right: 0;
    margin-top: 4px;
    background: #fff;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  }
}
</style>
