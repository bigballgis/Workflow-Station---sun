<template>
  <div class="lookup-field" ref="wrapperRef">
    <el-input
      v-model="searchKeyword"
      :placeholder="placeholder || 'Click to search'"
      clearable
      @focus="handleFocus"
    >
      <template #suffix>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>

    <div v-if="dropdownVisible" class="lookup-dropdown">
      <el-table
        :data="filteredResults"
        size="small"
        v-loading="loading"
        @row-click="handleSelect"
        highlight-current-row
        max-height="260"
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
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { relationTableApi } from '@/api/relationTable'

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
  viewFields?: LookupViewField[]
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
  (e: 'select', row: Record<string, any>): void
  (e: 'viewFieldsLoaded', fields: LookupViewField[]): void
}>()

const wrapperRef = ref<HTMLElement>()
const dropdownVisible = ref(false)
const searchKeyword = ref('')
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
  dropdownVisible.value = true
  loadAllData()
}

function handleSelect(row: Record<string, any>) {
  const displayVal = props.displayField ? row[props.displayField] : Object.values(row)[0]
  searchKeyword.value = String(displayVal ?? '')
  emit('update:modelValue', row)
  emit('select', row)
  dropdownVisible.value = false
}

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
