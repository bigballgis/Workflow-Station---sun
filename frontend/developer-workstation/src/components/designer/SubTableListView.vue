<template>
  <div class="relation-table-view sub-table-list-view">
    <!-- Left: Table columns panel -->
    <div class="columns-panel" v-if="columnsPanelOpen">
      <div class="columns-panel-header">
        <div class="columns-panel-title">
          <el-icon style="margin-right: 6px;"><Menu /></el-icon>
          <span>{{ t('subTableView.tableColumns') }}</span>
        </div>
        <el-icon class="columns-panel-close" @click="columnsPanelOpen = false"><Close /></el-icon>
      </div>
      <div class="columns-panel-table-name">{{ binding.tableName }}</div>
      <div class="columns-panel-search">
        <el-input v-model="fieldSearchKeyword" placeholder="Search" clearable size="small">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <div class="columns-field-list" v-loading="loadingFields">
        <div
          v-if="!loadingFields"
          v-for="field in filteredAvailableFields"
          :key="field.fieldName"
          class="field-item"
          :class="{ active: isFieldInView(field.fieldName), dragging: dragSourceField === field.fieldName }"
          draggable="true"
          @dragstart="onFieldDragStart($event, field)"
          @dragend="onFieldDragEnd"
          @click="addFieldToView(field)"
        >
          <el-icon class="field-icon"><component :is="getFieldIcon(field.dataType)" /></el-icon>
          <span class="field-name">{{ field.comment || field.fieldName }}</span>
        </div>
        <el-empty v-if="!loadingFields && filteredAvailableFields.length === 0" description="No fields" :image-size="40" />
      </div>
    </div>

    <!-- Toggle button when collapsed -->
    <div v-else class="columns-toggle" @click="columnsPanelOpen = true">
      <el-icon><DArrowRight /></el-icon>
    </div>

    <!-- Right: Data grid -->
    <div class="data-grid-panel"
      @dragover.prevent="onGridDragOver"
      @drop="onGridDrop"
    >
      <!-- Toolbar with Preview and Clear -->
      <div class="grid-toolbar" v-if="viewFields.length > 0">
        <div class="toolbar-left">
          <span class="field-count">{{ viewFields.length }} {{ t('subTableView.columns') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button size="small" @click="handlePreview">{{ t('common.preview') }}</el-button>
          <el-button size="small" type="danger" plain @click="handleClear">{{ t('common.clear') }}</el-button>
        </div>
      </div>

      <!-- Column headers (draggable) -->
      <div class="column-headers" v-if="viewFields.length > 0">
        <div
          v-for="(field, index) in viewFields"
          :key="field.fieldName"
          class="column-header"
          :class="{ 'drag-over': dragOverIndex === index }"
          draggable="true"
          @dragstart="onColDragStart($event, index)"
          @dragover.prevent="onColDragOver($event, index)"
          @dragleave="onColDragLeave"
          @drop.stop="onColDrop($event, index)"
          @dragend="onColDragEnd"
        >
          <span class="col-name">{{ field.comment || field.fieldName }}</span>
          <el-icon class="col-remove" @click.stop="removeField(index)"><Close /></el-icon>
        </div>
      </div>

      <!-- Data row (mock) -->
      <div class="data-row" v-if="viewFields.length > 0">
        <div v-for="field in viewFields" :key="field.fieldName" class="data-cell">
          {{ getMockValue(field) }}
        </div>
      </div>

      <el-empty v-if="viewFields.length === 0" :description="t('subTableView.noFieldsImported')" :image-size="60" />
    </div>

    <!-- Preview dialog -->
    <el-dialog v-model="showPreview" :title="t('common.preview')" width="800px" destroy-on-close>
      <el-table :data="previewFieldRows" border style="width: 100%;">
        <el-table-column prop="label" :label="t('subTableView.displayLabel')" min-width="200" />
        <el-table-column prop="value" :label="t('subTableView.previewValue')" min-width="200" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { Search, Close, Menu, DArrowRight, EditPen, Calendar, Document, Coin, Switch as SwitchIcon } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { subTableViewApi, type SubTableFieldDTO } from '@/api/subTableView'

const { t } = useI18n()

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
  /** All available fields for this sub-table */
  availableFields?: SubTableFieldDTO[]
  /** Fields currently shown in the view (ordered) */
  modelValue?: SubTableFieldDTO[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', fields: SubTableFieldDTO[]): void
  (e: 'update:availableFields', fields: SubTableFieldDTO[]): void
  (e: 'save'): void
}>()

const columnsPanelOpen = ref(true)
const fieldSearchKeyword = ref('')
const showPreview = ref(false)
const loadingFields = ref(false)

// Local fallback: when parent doesn't yet have allFields, store the loaded value here
const localAvailableFields = ref<SubTableFieldDTO[]>([])

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
    const res = await subTableViewApi.getAvailableFields(props.formId, props.binding.bindingId)
    const fields: SubTableFieldDTO[] = res.data || []
    localAvailableFields.value = fields
    emit('update:availableFields', fields)
  } catch (e) {
    console.error('[SubTableListView] failed to load fields:', e)
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

const getMockValue = (field: SubTableFieldDTO): string => {
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

const previewFieldRows = computed(() => {
  return viewFields.value.map(f => ({
    label: f.comment || f.fieldName,
    value: getMockValue(f)
  }))
})

// --- Field operations ---
const addFieldToView = (field: SubTableFieldDTO) => {
  if (!isFieldInView(field.fieldName)) {
    emit('update:modelValue', [...viewFields.value, field])
    emit('save')
  }
}

const removeField = (index: number) => {
  emit('update:modelValue', viewFields.value.filter((_, i) => i !== index))
  emit('save')
}

const handlePreview = () => { showPreview.value = true }

const handleClear = () => {
  emit('update:modelValue', [])
  emit('save')
}

// --- Drag from left panel to grid ---
const onFieldDragStart = (e: DragEvent, field: SubTableFieldDTO) => {
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
    emit('save')
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
    emit('save')
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
  loadFields,
})
</script>

<style scoped>
.sub-table-list-view {
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

.column-headers {
  display: flex;
  border: 1px solid var(--el-border-color-light);
  border-bottom: 2px solid var(--el-border-color);
  background: #fafafa;
  min-height: 36px;
}
.column-header {
  flex: 1;
  min-width: 80px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  font-size: 13px;
  font-weight: 500;
  color: #333;
  cursor: grab;
  border-right: 1px solid var(--el-border-color-lighter);
  user-select: none;
  transition: background 0.15s;
}
.column-header:last-child { border-right: none; }
.column-header:hover { background: #f0f0f0; }
.column-header.drag-over { background: var(--el-color-primary-light-9, #ecf5ff); border-left: 2px solid var(--el-color-primary, #409eff); }
.col-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.col-remove { font-size: 12px; color: #ccc; cursor: pointer; flex-shrink: 0; margin-left: 4px; }
.col-remove:hover { color: var(--el-color-danger, #f56c6c); }

.data-row {
  display: flex;
  border: 1px solid var(--el-border-color-light);
  border-top: none;
  min-height: 36px;
}
.data-cell {
  flex: 1;
  min-width: 80px;
  display: flex;
  align-items: center;
  padding: 6px 10px;
  font-size: 13px;
  color: #666;
  border-right: 1px solid var(--el-border-color-lighter);
}
.data-cell:last-child { border-right: none; }
</style>
