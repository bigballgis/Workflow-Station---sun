<template>
  <div class="relation-table-view">
    <!-- Left: Table columns panel -->
    <div
      v-if="columnsPanelOpen"
      class="columns-panel"
    >
      <div class="columns-panel-header">
        <div class="columns-panel-title">
          <el-icon style="margin-right: 6px;">
            <Menu />
          </el-icon>
          <span>Table columns</span>
        </div>
        <el-icon
          class="columns-panel-close"
          @click="columnsPanelOpen = false"
        >
          <Close />
        </el-icon>
      </div>
      <div class="columns-panel-table-name">
        {{ binding.tableName }}
      </div>
      <div class="columns-panel-search">
        <el-input
          v-model="fieldSearchKeyword"
          placeholder="Search"
          clearable
          size="small"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>
      <div
        v-loading="loadingFields"
        class="columns-field-list"
      >
        <div
          v-for="field in filteredAvailableFields"
          v-if="!loadingFields"
          :key="field.fieldName"
          class="field-item"
          :class="{ active: isFieldInView(field.fieldName), dragging: dragSourceField === field.fieldName }"
          draggable="true"
          @dragstart="onFieldDragStart($event, field)"
          @dragend="onFieldDragEnd"
          @click="addFieldToView(field)"
        >
          <el-icon class="field-icon">
            <component :is="getFieldIcon(field.dataType)" />
          </el-icon>
          <span class="field-name">{{ field.comment || field.fieldName }}</span>
        </div>
        <el-empty
          v-if="!loadingFields && filteredAvailableFields.length === 0"
          description="No fields"
          :image-size="40"
        />
      </div>
    </div>

    <!-- Toggle button when collapsed -->
    <div
      v-else
      class="columns-toggle"
      @click="columnsPanelOpen = true"
    >
      <el-icon><DArrowRight /></el-icon>
    </div>

    <!-- Right: Data grid -->
    <div
      class="data-grid-panel"
      @dragover.prevent="onGridDragOver"
      @drop="onGridDrop"
    >
      <!-- Toolbar with Preview and Clear -->
      <div
        v-if="viewFields.length > 0"
        class="grid-toolbar"
      >
        <div class="toolbar-left">
          <span class="field-count">{{ viewFields.length }} columns</span>
        </div>
        <div class="toolbar-right">
          <el-button
            size="small"
            @click="handlePreview"
          >
            Preview
          </el-button>
          <el-button
            size="small"
            type="danger"
            plain
            @click="handleClear"
          >
            Clear
          </el-button>
        </div>
      </div>

      <!-- Column rows (draggable) -->
      <div
        v-if="viewFields.length > 0"
        class="column-rows"
      >
        <div
          v-for="(field, index) in viewFields"
          :key="field.fieldName"
          class="column-row"
          :class="{ 'drag-over': dragOverIndex === index }"
          draggable="true"
          @dragstart="onColDragStart($event, index)"
          @dragover.prevent="onColDragOver($event, index)"
          @dragleave="onColDragLeave"
          @drop.stop="onColDrop($event, index)"
          @dragend="onColDragEnd"
        >
          <div class="column-row-label">
            <span class="col-name">{{ field.comment || field.fieldName }}</span>
            <el-icon
              class="col-remove"
              @click.stop="removeField(index)"
            >
              <Close />
            </el-icon>
          </div>
          <div class="column-row-value">
            {{ getMockValue(field) }}
          </div>
        </div>
      </div>

      <el-empty
        v-if="viewFields.length === 0"
        description="No fields imported"
      />
    </div>

    <!-- Preview dialog -->
    <el-dialog
      v-model="showPreview"
      title="Preview"
      width="800px"
      destroy-on-close
    >
      <el-table
        :data="previewFieldRows"
        border
        style="width: 100%;"
      >
        <el-table-column
          prop="label"
          :label="' '"
          min-width="200"
        />
        <el-table-column
          prop="value"
          :label="' '"
          min-width="200"
        />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { Search, Close, Menu, DArrowRight, EditPen, Calendar, Document, Coin, Switch as SwitchIcon } from '@element-plus/icons-vue'
import { relationTableViewApi, type RelationFieldDTO } from '@/api/relationTable'

const props = defineProps<{
  binding: {
    bindingId: number
    bindingType: string
    bindingMode: string
    tableName: string
    tableId: number
    tableType: string
    tableDescription: string
  }
  functionUnitId: number
  formId: number
  /** All available fields for this relation table */
  availableFields?: RelationFieldDTO[]
  /** Fields currently shown in the view (ordered) */
  modelValue?: RelationFieldDTO[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', fields: RelationFieldDTO[]): void
  (e: 'update:availableFields', fields: RelationFieldDTO[]): void
}>()

const columnsPanelOpen = ref(true)
const fieldSearchKeyword = ref('')
const showPreview = ref(false)
const loadingFields = ref(false)

// Local fallback: when parent doesn't yet have allFields, store the loaded value here
// so the panel still renders while we propagate the update to the parent.
const localAvailableFields = ref<RelationFieldDTO[]>([])

// All available fields: prefer prop (parent-managed), fall back to locally loaded
const allFields = computed(() => props.availableFields?.length ? props.availableFields : localAvailableFields.value)
// Fields currently in the view (user-selected, ordered)
const viewFields = computed({
  get: () => props.modelValue || [],
  set: (val) => emit('update:modelValue', val)
})

// Drag state
const dragSourceField = ref<string | null>(null)
const dragColIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)
const isDraggingFromPanel = ref(false)

async function loadFields() {
  if (!props.formId || !props.binding?.bindingId) return
  loadingFields.value = true
  try {
    const res = await relationTableViewApi.getAvailableFields(props.formId, props.binding.bindingId)
    const fields: RelationFieldDTO[] = res.data || []
    localAvailableFields.value = fields
    emit('update:availableFields', fields)
  } catch (e) {
    console.error('[RelationTableView] failed to load fields:', e)
  } finally {
    loadingFields.value = false
  }
}

// If parent hasn't populated allFields yet, load from API on mount
onMounted(() => {
  if (!props.availableFields?.length) {
    loadFields()
  }
})

// Track if we've auto-initialized the view (to avoid re-initializing)
const autoInitialized = ref(false)

// Watch for allFields changes - if view is empty and fields are loaded, auto-add all
watch(() => allFields.value, (fields) => {
  if (fields.length > 0 && viewFields.value.length === 0 && !autoInitialized.value) {
    // Auto-add all fields to view when view is empty and fields are loaded
    emit('update:modelValue', [...fields])
    autoInitialized.value = true
  }
}, { immediate: true })

const filteredAvailableFields = computed(() => {
  const kw = fieldSearchKeyword.value.trim().toLowerCase()
  // Only show fields NOT already in the view
  const inView = new Set(viewFields.value.map(f => f.fieldName))
  let list = allFields.value.filter(f => !inView.has(f.fieldName))
  if (kw) {
    list = list.filter(f => f.fieldName.toLowerCase().includes(kw) || (f.comment || '').toLowerCase().includes(kw))
  }
  return list
})

const isFieldInView = (fieldName: string) => viewFields.value.some(f => f.fieldName === fieldName)

const getFieldIcon = (dataType: string) => {
  const type = (dataType || '').toUpperCase()
  if (type.includes('INT') || type.includes('DECIMAL') || type.includes('NUMERIC')) return Coin
  if (type.includes('DATE') || type.includes('TIME') || type.includes('TIMESTAMP')) return Calendar
  if (type.includes('BOOL')) return SwitchIcon
  if (type.includes('TEXT') || type.includes('CLOB')) return Document
  return EditPen
}

const getMockValue = (field: RelationFieldDTO): string => {
  const type = (field.dataType || '').toUpperCase()
  if (type.includes('INT') || type === 'BIGINT') return '1'
  if (type.includes('DECIMAL') || type.includes('NUMERIC') || type.includes('FLOAT') || type.includes('DOUBLE')) return '100.00'
  if (type === 'BOOLEAN' || type === 'BOOL') return 'true'
  if (type === 'DATE') return '2026-01-01'
  if (type.includes('TIMESTAMP') || type === 'DATETIME') return '2026-01-01 00:00:00'
  if (type.includes('TIME')) return '00:00:00'
  if (type === 'TEXT' || type.includes('CLOB')) return 'Sample text'
  if (type === 'FILE') return 'file.pdf'
  return 'Sample'
}

const previewRows = computed(() => {
  if (viewFields.value.length === 0) return []
  const row: Record<string, any> = {}
  for (const f of viewFields.value) {
    row[f.fieldName] = getMockValue(f)
  }
  return [row]
})

const previewFieldRows = computed(() => {
  return viewFields.value.map(f => ({
    label: f.comment || f.fieldName,
    value: getMockValue(f)
  }))
})

// --- Field operations ---
const addFieldToView = (field: RelationFieldDTO) => {
  if (!isFieldInView(field.fieldName)) {
    emit('update:modelValue', [...viewFields.value, field])
  }
}

const removeField = (index: number) => {
  emit('update:modelValue', viewFields.value.filter((_, i) => i !== index))
}

const handlePreview = () => { showPreview.value = true }

const handleClear = () => {
  emit('update:modelValue', [])
}

// --- Drag from left panel to grid ---
const onFieldDragStart = (e: DragEvent, field: RelationFieldDTO) => {
  dragSourceField.value = field.fieldName
  isDraggingFromPanel.value = true
  e.dataTransfer!.effectAllowed = 'copy'
  e.dataTransfer!.setData('text/plain', field.fieldName)
}

const onFieldDragEnd = () => {
  dragSourceField.value = null
  isDraggingFromPanel.value = false
}

const onGridDragOver = (e: DragEvent) => {
  if (isDraggingFromPanel.value) {
    e.dataTransfer!.dropEffect = 'copy'
  }
}

const onGridDrop = (e: DragEvent) => {
  if (!isDraggingFromPanel.value) return
  const fieldName = e.dataTransfer!.getData('text/plain')
  const field = allFields.value.find(f => f.fieldName === fieldName)
  if (field && !isFieldInView(fieldName)) {
    emit('update:modelValue', [...viewFields.value, field])
  }
  dragSourceField.value = null
  isDraggingFromPanel.value = false
}

// --- Drag to reorder columns ---
const onColDragStart = (e: DragEvent, index: number) => {
  dragColIndex.value = index
  isDraggingFromPanel.value = false
  e.dataTransfer!.effectAllowed = 'move'
  e.dataTransfer!.setData('text/plain', String(index))
}

const onColDragOver = (_e: DragEvent, index: number) => {
  if (dragColIndex.value !== null && dragColIndex.value !== index) {
    dragOverIndex.value = index
  }
}

const onColDragLeave = () => { dragOverIndex.value = null }

const onColDrop = (_e: DragEvent, targetIndex: number) => {
  if (dragColIndex.value !== null && dragColIndex.value !== targetIndex) {
    const arr = [...viewFields.value]
    const [moved] = arr.splice(dragColIndex.value, 1)
    arr.splice(targetIndex, 0, moved)
    emit('update:modelValue', arr)
  }
  dragColIndex.value = null
  dragOverIndex.value = null
}

const onColDragEnd = () => {
  dragColIndex.value = null
  dragOverIndex.value = null
}

// --- Expose for parent (getters for save) ---
defineExpose({
  getViewFields: () => viewFields.value,
  getAllFields: () => allFields.value,
})
</script>

<style scoped>
.relation-table-view {
  display: flex;
  height: calc(100vh - 260px);
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  overflow: hidden;
}

.columns-panel {
  width: 240px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  background: #fff;
}
.columns-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.columns-panel-title {
  display: flex;
  align-items: center;
  font-weight: 600;
  font-size: 14px;
}
.columns-panel-close { cursor: pointer; color: #999; font-size: 16px; }
.columns-panel-close:hover { color: #333; }
.columns-panel-table-name { padding: 8px 12px 4px; font-size: 13px; color: #666; font-style: italic; }
.columns-panel-search { padding: 4px 8px 8px; }
.columns-field-list { flex: 1; overflow-y: auto; padding: 0 4px; }

.field-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  cursor: grab;
  border-radius: 4px;
  font-size: 13px;
  color: #333;
  transition: background 0.15s;
  user-select: none;
}
.field-item:hover { background: #f5f7fa; }
.field-item.active { background: var(--el-color-primary-light-9, #ecf5ff); color: var(--el-color-primary, #409eff); }
.field-item.dragging { opacity: 0.5; }
.field-icon { margin-right: 8px; font-size: 15px; color: #999; }
.field-item.active .field-icon { color: var(--el-color-primary, #409eff); }
.field-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.columns-toggle {
  width: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  background: #fafafa;
  border-right: 1px solid var(--el-border-color-light);
  color: #999;
  flex-shrink: 0;
}
.columns-toggle:hover { background: #f0f0f0; color: #333; }

.data-grid-panel {
  flex: 1;
  min-width: 0;
  padding: 12px;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.grid-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.toolbar-left { font-size: 13px; color: #666; }
.toolbar-right { display: flex; gap: 8px; }

.column-rows {
  border: 1px solid var(--el-border-color-light);
  min-height: 36px;
}
.column-row {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #333;
  cursor: grab;
  border-bottom: 1px solid var(--el-border-color-lighter);
  user-select: none;
  transition: background 0.15s;
}
.column-row:last-child { border-bottom: none; }
.column-row:hover { background: #f0f0f0; }
.column-row.drag-over { background: var(--el-color-primary-light-9, #ecf5ff); border-left: 2px solid var(--el-color-primary, #409eff); }
.column-row-label {
  width: 240px;
  min-width: 160px;
  align-self: stretch;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  font-weight: 500;
  background: #fafafa;
  border-right: 1px solid var(--el-border-color-lighter);
}
.column-row-value {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  padding: 6px 10px;
  color: #666;
}
.col-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.col-remove { font-size: 12px; color: #ccc; cursor: pointer; flex-shrink: 0; margin-left: 4px; }
.col-remove:hover { color: var(--el-color-danger, #f56c6c); }
</style>
