<template>
  <div class="lookup-preview-wrapper" @click="handleWrapperClick">
    <div class="lookup-form-item">
      <label class="lookup-label-text">
        <el-icon class="lookup-label-icon"><Search /></el-icon>
        {{ label }}
      </label>
      <div class="lookup-field" :class="{ readonly }" @click="handleFieldClick" ref="fieldRef">
        <!-- Selected value: input container with inner tag -->
        <div v-if="selectedRow" class="lookup-selected-wrapper">
          <span class="lookup-selected-tag">
            <span class="lookup-selected-text">{{ searchKeyword }}</span>
            <el-icon v-if="!readonly" class="lookup-selected-close" @click.stop="handleClear"><Close /></el-icon>
          </span>
        </div>
        <span v-else-if="readonly" class="lookup-readonly-empty">-</span>
        <!-- Search input (hidden when a value is selected) -->
        <el-input
          v-else
          v-model="searchKeyword"
          :placeholder="placeholder"
          class="lookup-input"
          @focus="onInputFocus"
        />
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="dropdownVisible"
        class="lookup-dropdown-panel"
        ref="dropdownRef"
        :style="dropdownStyle"
        @mousedown.stop
        @click.stop
      >
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
            min-width="120"
          />
        </el-table>
        <div v-if="filteredResults.length === 0" class="lookup-no-data">No data</div>
      </div>
    </Teleport>

    <!-- View display after selection -->
    <div v-if="showBackfillView && selectedRow && displayViewFields.length > 0" class="lookup-view-display">
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
import { ref, computed, nextTick, onBeforeUnmount, watch } from 'vue'
import { Search, Close } from '@element-plus/icons-vue'

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

const props = withDefaults(defineProps<{
  modelValue?: any
  label: string
  placeholder?: string
  searchFields: string[]
  displayFields: string[]
  viewFields: ViewField[]
  fieldDefs: FieldDef[]
  showBackfillView?: boolean
  readonly?: boolean
}>(), {
  showBackfillView: true,
  readonly: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
}>()

const dropdownRef = ref<HTMLElement>()
const fieldRef = ref<HTMLElement>()
const dropdownVisible = ref(false)
const dropdownStyle = ref<Record<string, string>>({})
const searchKeyword = ref('')
const selectedRow = ref<Record<string, any> | null>(null)

// Columns shown in the dropdown table: use displayFields from lookup config
const visibleColumns = computed(() => {
  if (props.displayFields?.length > 0) {
    return props.displayFields.map(f => {
      const fd = props.fieldDefs.find(d => d.fieldName === f)
      return { prop: f, label: fd?.comment || fd?.description || f }
    })
  }
  if (props.searchFields?.length > 0) {
    return props.searchFields.map(f => {
      const fd = props.fieldDefs.find(d => d.fieldName === f)
      return { prop: f, label: fd?.comment || fd?.description || f }
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

function getPrimaryDisplayField() {
  return props.displayFields?.[0] || visibleColumns.value[0]?.prop || props.searchFields?.[0] || ''
}

function getDisplayText(row: Record<string, any> | null) {
  if (!row) return ''
  const displayField = getPrimaryDisplayField()
  if (displayField && row[displayField] != null) {
    return String(row[displayField])
  }
  const firstValue = Object.values(row).find(value => value != null && value !== '')
  return firstValue == null ? '' : String(firstValue)
}

function normalizeValue(value: any): Record<string, any> | null {
  if (value == null || value === '') return null
  if (typeof value === 'object' && !Array.isArray(value)) return value
  const displayField = getPrimaryDisplayField()
  return displayField ? { [displayField]: value } : { value }
}

watch(
  () => [props.modelValue, props.displayFields, props.searchFields, visibleColumns.value],
  ([value]) => {
    const nextRow = normalizeValue(value)
    selectedRow.value = nextRow
    searchKeyword.value = getDisplayText(nextRow)
  },
  { immediate: true, deep: true }
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
  // Show dropdown when clicking on the lookup field area (but not on the clear button)
  if ((e.target as HTMLElement).closest('.lookup-selected-close')) return
  showDropdown()
}

function handleSelect(row: Record<string, any>) {
  selectedRow.value = row
  searchKeyword.value = getDisplayText(row)
  emit('update:modelValue', row)
  dropdownVisible.value = false
}

function handleClear() {
  if (props.readonly) return
  searchKeyword.value = ''
  selectedRow.value = null
  emit('update:modelValue', null)
}

function onInputFocus() {
  showDropdown()
}

// Close dropdown when clicking outside
function onDocClick(e: MouseEvent) {
  if (!dropdownVisible.value) return
  const target = e.target as Node
  // Also keep dropdown open when clicking inside the input/field container
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

  &.readonly {
    cursor: default;
  }

  .lookup-input {
    width: 100%;
  }

  .lookup-selected-wrapper {
    display: flex;
    align-items: center;
    min-height: 32px;
    padding: 4px 8px;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    background: #fff;
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
}

.lookup-readonly-empty {
  color: #909399;
  line-height: 32px;
}

.lookup-dropdown-panel {
  z-index: 3000;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  overflow: hidden;

  .lookup-no-data {
    padding: 16px;
    text-align: center;
    color: #909399;
    font-size: 13px;
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

