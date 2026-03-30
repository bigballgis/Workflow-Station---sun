<template>
  <div class="lookup-preview-wrapper">
    <div class="lookup-form-item">
      <label class="lookup-label-text">
        <el-icon class="lookup-label-icon"><Search /></el-icon>
        {{ label }}
      </label>
      <div class="lookup-field" ref="wrapperRef">
        <el-input
          v-model="searchKeyword"
          :placeholder="placeholder"
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
    </div>

    <!-- View display after selection (matches user-portal LookupViewDisplay) -->
    <div v-if="selectedRow && displayViewFields.length > 0" class="lookup-view-display">
      <el-descriptions :column="1" border size="small" direction="horizontal">
        <el-descriptions-item
          v-for="field in displayViewFields"
          :key="field.fieldName"
          :label="field.displayLabel || field.fieldName"
          label-class-name="lookup-view-label"
          class-name="lookup-view-value"
        >
          {{ selectedRow[field.fieldName] ?? '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { Search } from '@element-plus/icons-vue'

interface ViewField {
  fieldName: string
  displayLabel: string
  columnWidth?: number
  sortOrder: number
  visible: boolean
}

interface FieldDef {
  fieldName: string
  dataType?: string
  comment?: string
  description?: string
}

const props = defineProps<{
  label: string
  placeholder?: string
  searchFields: string[]
  displayFields: string[]
  viewFields: ViewField[]
  fieldDefs: FieldDef[]
}>()

const wrapperRef = ref<HTMLElement>()
const dropdownVisible = ref(false)
const searchKeyword = ref('')
const selectedRow = ref<Record<string, any> | null>(null)

// Columns shown in the dropdown table: use displayFields from lookup config
const visibleColumns = computed(() => {
  // 1. Use displayFields (from lookup config "Display Fields")
  if (props.displayFields?.length > 0) {
    return props.displayFields.map(f => {
      const fd = props.fieldDefs.find(d => d.fieldName === f)
      return { prop: f, label: fd?.comment || fd?.description || f, width: undefined as number | undefined }
    })
  }
  // 2. Fallback: searchFields
  if (props.searchFields?.length > 0) {
    return props.searchFields.map(f => {
      const fd = props.fieldDefs.find(d => d.fieldName === f)
      return { prop: f, label: fd?.comment || fd?.description || f, width: undefined as number | undefined }
    })
  }
  return []
})

// View fields for the descriptions display after selection
const displayViewFields = computed(() => {
  if (props.viewFields?.length > 0) {
    return props.viewFields
      .filter(f => f.visible !== false)
      .sort((a, b) => a.sortOrder - b.sortOrder)
  }
  return []
})

// Generate mock data rows
const mockRows = computed(() => {
  const cols = visibleColumns.value
  if (cols.length === 0) return []
  const rows: Record<string, any>[] = []
  for (let i = 1; i <= 3; i++) {
    const row: Record<string, any> = {}
    for (const col of cols) {
      const fd = props.fieldDefs.find(d => d.fieldName === col.prop)
      row[col.prop] = getMockValue(fd?.dataType || 'VARCHAR', i)
    }
    // Also populate viewFields that may not be in visible columns
    if (props.viewFields?.length) {
      for (const vf of props.viewFields) {
        if (!(vf.fieldName in row)) {
          const fd = props.fieldDefs.find(d => d.fieldName === vf.fieldName)
          row[vf.fieldName] = getMockValue(fd?.dataType || 'VARCHAR', i)
        }
      }
    }
    rows.push(row)
  }
  return rows
})

const filteredResults = computed(() => {
  const kw = searchKeyword.value?.trim().toLowerCase()
  if (!kw) return mockRows.value
  // Only search within configured searchFields
  const fields = props.searchFields?.length ? props.searchFields : []
  if (fields.length === 0) return mockRows.value
  return mockRows.value.filter(row =>
    fields.some(f => row[f] != null && String(row[f]).toLowerCase().includes(kw))
  )
})

function getMockValue(dataType: string, index: number): string {
  const type = (dataType || '').toUpperCase()
  if (type.includes('INT') || type === 'BIGINT') return String(index)
  if (type.includes('DECIMAL') || type.includes('NUMERIC') || type.includes('FLOAT') || type.includes('DOUBLE')) return (index * 100).toFixed(2)
  if (type === 'BOOLEAN' || type === 'BOOL') return index % 2 === 0 ? 'true' : 'false'
  if (type === 'DATE') return `2026-01-0${index}`
  if (type.includes('TIMESTAMP') || type === 'DATETIME') return `2026-01-0${index} 00:00:00`
  if (type.includes('TIME')) return `0${index}:00:00`
  return `Sample ${index}`
}

function handleFocus() {
  dropdownVisible.value = true
}

function handleSelect(row: Record<string, any>) {
  const displayField = props.displayFields?.[0] || visibleColumns.value[0]?.prop
  searchKeyword.value = displayField ? String(row[displayField] ?? '') : ''
  selectedRow.value = row
  dropdownVisible.value = false
}

function onClickOutside(e: MouseEvent) {
  if (wrapperRef.value && !wrapperRef.value.contains(e.target as Node)) {
    dropdownVisible.value = false
  }
}

onMounted(() => document.addEventListener('mousedown', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('mousedown', onClickOutside))
</script>

<style lang="scss" scoped>
.lookup-preview-wrapper {
  margin-bottom: 18px;
}

.lookup-form-item {
  display: flex;
  align-items: flex-start;
}

.lookup-label-text {
  white-space: nowrap;
  width: auto;
  min-width: fit-content;
  max-width: 200px;
  height: auto;
  line-height: 1.5;
  padding-top: 6px;
  padding-right: 12px;
  font-size: 14px;
  color: #606266;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 4px;
}

.lookup-label-icon {
  color: #409eff;
  font-size: 14px;
}

.lookup-field {
  flex: 1;
  min-width: 0;
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

.lookup-view-display {
  margin-top: 8px;

  :deep(.lookup-view-label) {
    width: 40%;
    font-weight: 500;
    color: #606266;
    background: #fafafa;
  }

  :deep(.lookup-view-value) {
    color: #303133;
  }
}
</style>
