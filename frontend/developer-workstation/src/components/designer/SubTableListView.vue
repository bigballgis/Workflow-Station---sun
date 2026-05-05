<template>
  <div class="relation-table-view sub-table-list-view">
    <!-- Left: Table columns and extend action panel -->
    <div class="columns-panel" v-if="columnsPanelOpen">
      <div class="panel-section">
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
            :class="{ active: isFieldInView(field.fieldName), dragging: dragSourceKey === field.fieldName }"
            draggable="true"
            @dragstart="onFieldDragStart($event, field)"
            @dragend="onDragEnd"
            @click="addFieldToView(field)"
          >
            <el-icon class="field-icon"><component :is="getFieldIcon(field.dataType)" /></el-icon>
            <span class="field-name">{{ field.comment || field.fieldName }}</span>
          </div>
          <el-empty v-if="!loadingFields && filteredAvailableFields.length === 0" description="No fields" :image-size="40" />
        </div>
      </div>

      <div class="panel-section extend-action-section">
        <div class="columns-panel-header">
          <div class="columns-panel-title">
            <el-icon style="margin-right: 6px;"><Operation /></el-icon>
            <span>{{ t('subTableView.extendAction') }}</span>
          </div>
        </div>
        <div class="columns-field-list extend-action-list">
          <div
            class="field-item link-form-item"
            :class="{ dragging: dragSourceKey === genericLinkFormKey }"
            draggable="true"
            @dragstart="onLinkFormDragStart($event)"
            @dragend="onDragEnd"
            @click="addLinkFormToView"
          >
            <el-icon class="field-icon"><Link /></el-icon>
            <span class="field-name">Link Form</span>
          </div>
          <div
            class="field-item lookup-action-item"
            :class="{ dragging: dragSourceKey === genericLookupKey }"
            draggable="true"
            @dragstart="onLookupDragStart($event)"
            @dragend="onDragEnd"
            @click="addLookupToView"
          >
            <el-icon class="field-icon"><Search /></el-icon>
            <span class="field-name">Lookup</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Toggle button when collapsed -->
    <div v-else class="columns-toggle" @click="columnsPanelOpen = true">
      <el-icon><DArrowRight /></el-icon>
    </div>

    <div class="list-view-workspace">
      <!-- Right: Data grid -->
      <div class="data-grid-panel"
        @dragover.prevent="onGridDragOver"
        @drop="onGridDrop"
      >
        <!-- Toolbar with Preview and Clear -->
        <div class="grid-toolbar" v-if="viewColumns.length > 0">
          <div class="toolbar-left">
            <span class="field-count">{{ viewColumns.length }} {{ t('subTableView.columns') }}</span>
          </div>
          <div class="toolbar-right">
            <el-button size="small" @click="handlePreview">{{ t('common.preview') }}</el-button>
            <el-button size="small" type="danger" plain @click="handleClear">{{ t('common.clear') }}</el-button>
          </div>
        </div>

        <!-- Column headers (draggable) -->
        <div class="column-headers" v-if="viewColumns.length > 0">
          <div
            v-for="(column, index) in viewColumns"
            :key="getColumnKey(column)"
            class="column-header"
            :class="{ 'drag-over': dragOverIndex === index, 'link-column': isLinkColumn(column) }"
            draggable="true"
            @dragstart="onColDragStart($event, index)"
            @dragover.prevent="onColDragOver($event, index)"
            @dragleave="onColDragLeave"
            @drop.stop="onColDrop($event, index)"
            @dragend="onColDragEnd"
          >
            <span class="col-name">{{ getColumnLabel(column) }}</span>
            <span class="col-actions">
              <el-icon v-if="isConfigurableActionColumn(column)" class="col-edit" @click.stop="openActionColumnConfig(column, index)"><EditPen /></el-icon>
              <el-icon class="col-remove" @click.stop="removeField(index)"><Close /></el-icon>
            </span>
          </div>
        </div>

        <!-- Data row (mock) -->
        <div class="data-row" v-if="viewColumns.length > 0">
          <div v-for="column in viewColumns" :key="getColumnKey(column)" class="data-cell">
            <el-link
              v-if="isLinkColumn(column)"
              type="primary"
              :underline="false"
              @click.stop="openLinkFormDialog(column)"
            >
              {{ getLinkText(column) }}
            </el-link>
            <LookupPreview
              v-else-if="isLookupColumn(column)"
              class="list-view-lookup-preview"
              :label="''"
              :placeholder="getLookupPreviewConfig(column).placeholder"
              :search-fields="getLookupPreviewConfig(column).searchFields"
              :display-fields="getLookupPreviewConfig(column).displayFields"
              :selected-display-field="getLookupPreviewConfig(column).selectedDisplayField"
              :filter-conditions="getLookupPreviewConfig(column).filterConditions"
              :view-fields="getLookupPreviewConfig(column).viewFields"
              :field-defs="getLookupPreviewConfig(column).fieldDefs"
              :show-backfill-view="getLookupPreviewConfig(column).showBackfillView"
            />
            <span v-else>{{ getMockValue(column) }}</span>
          </div>
        </div>

        <el-empty v-if="viewColumns.length === 0" :description="t('subTableView.noFieldsImported')" :image-size="60" />
      </div>
    </div>

    <!-- Preview dialog -->
    <el-dialog v-model="showPreview" :title="t('common.preview')" width="800px" destroy-on-close>
      <el-table :data="previewFieldRows" border style="width: 100%;">
        <el-table-column prop="label" :label="t('subTableView.displayLabel')" min-width="200" />
        <el-table-column prop="value" :label="t('subTableView.previewValue')" min-width="200" />
      </el-table>
    </el-dialog>

    <el-dialog
      v-model="showLinkFormDialog"
      :title="linkFormDialogTitle"
      width="700px"
      destroy-on-close
      :close-on-click-modal="false"
      @closed="handleLinkFormDialogClosed"
    >
      <div v-if="selectedSubTableFormDesign.rule && selectedSubTableFormDesign.rule.length" class="link-form-dialog-body">
        <form-create
          v-if="formCreateMounted"
          v-model="linkFormData"
          :rule="selectedSubTableFormDesign.rule"
          :option="linkFormOption"
        />
      </div>
      <el-empty v-else :description="t('subTable.noFormDesign')" :image-size="60" />
      <template #footer>
        <el-button @click="showLinkFormDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleLinkFormSave" :loading="savingLinkForm">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showActionColumnConfig"
      :title="editingActionColumnType === 'lookup' ? 'Lookup' : t('linkForm.componentName')"
      width="420px"
    >
      <el-form v-if="editingActionColumnType === 'linkForm'" :model="linkColumnConfig" label-width="120px" label-position="left">
        <el-form-item :label="t('linkForm.boundSubTable')">
          <el-select
            v-model="linkColumnConfig.boundSubTableBindingId"
            :placeholder="t('linkForm.selectSubTable')"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="subTable in subTableBindingOptions"
              :key="subTable.bindingId"
              :label="subTable.tableName"
              :value="subTable.bindingId"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('linkForm.columnLabel')">
          <el-input v-model="linkColumnConfig.columnLabel" :placeholder="t('linkForm.columnLabelPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('linkForm.linkText')">
          <el-input v-model="linkColumnConfig.linkText" :placeholder="t('linkForm.linkTextPlaceholder')" />
        </el-form-item>
      </el-form>
      <el-form v-else :model="lookupColumnConfig" label-width="120px" label-position="left">
        <el-form-item :label="t('linkForm.columnLabel')">
          <el-input v-model="lookupColumnConfig.columnLabel" :placeholder="t('linkForm.columnLabelPlaceholder')" />
        </el-form-item>
        <el-form-item label="Lookup Config">
          <LookupBindingSelect v-model="lookupColumnConfig.lookupConfig" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showActionColumnConfig = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveActionColumnConfig">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { Search, Close, Menu, DArrowRight, EditPen, Calendar, Document, Coin, Switch as SwitchIcon, Link, Operation } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { subTableViewApi, type SubTableFieldDTO } from '@/api/subTableView'
import { linkFormComponentApi } from '@/api/linkFormComponent'
import LookupBindingSelect from './LookupBindingSelect.vue'
import LookupPreview from './LookupPreview.vue'

interface LinkFormComponentInfo {
  id: number
  componentName: string
  linkedFormId: number
  linkedFormName?: string
  displayField?: string
  linkText?: string
  columnLabel?: string
}

interface SubTableBindingOption {
  bindingId: number
  tableName: string
  tableDescription?: string
}

interface SubTableFormDesign {
  rule: any[]
  options?: Record<string, unknown>
}

interface LookupPreviewConfig {
  placeholder: string
  searchFields: string[]
  displayFields: string[]
  selectedDisplayField: string
  filterConditions: any[]
  viewFields: any[]
  fieldDefs: any[]
  showBackfillView: boolean
}

export interface SubTableListColumnDTO extends SubTableFieldDTO {
  columnType?: 'field' | 'linkForm' | 'lookup'
  componentId?: number
  linkedFormId?: number
  linkedFormName?: string
  linkText?: string
  columnLabel?: string
  boundSubTableBindingId?: number
  boundSubTableName?: string
  lookupConfig?: string
}

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
  modelValue?: SubTableListColumnDTO[]
  linkFormComponents?: LinkFormComponentInfo[]
  subTableBindings?: SubTableBindingOption[]
  resolveSubTableFormDesign?: (bindingId: number) => SubTableFormDesign
  resolveLookupPreviewConfig?: (lookupConfig: string) => LookupPreviewConfig
  /** Sub-table form design rendered when a Link Form column is clicked */
  formRule?: any[]
  formOption?: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', fields: SubTableListColumnDTO[]): void
  (e: 'update:availableFields', fields: SubTableFieldDTO[]): void
  (e: 'save'): void
}>()

const columnsPanelOpen = ref(true)
const fieldSearchKeyword = ref('')
const showPreview = ref(false)
const loadingFields = ref(false)
const showLinkFormDialog = ref(false)
const formCreateMounted = ref(false)
const savingLinkForm = ref(false)
const selectedLinkColumn = ref<SubTableListColumnDTO | null>(null)
const linkFormData = ref<Record<string, any>>({})
const showActionColumnConfig = ref(false)
const editingActionColumnIndex = ref<number | null>(null)
const editingActionColumnType = ref<'linkForm' | 'lookup'>('linkForm')
const linkColumnConfig = ref({ boundSubTableBindingId: 0, columnLabel: '', linkText: '' })
const lookupColumnConfig = ref({ columnLabel: 'Lookup', lookupConfig: '{}' })

// Local fallback: when parent doesn't yet have allFields, store the loaded value here
const localAvailableFields = ref<SubTableFieldDTO[]>([])
const mockSubTableRowId = 1
const genericLinkFormComponentId = computed(() => -Math.abs(props.binding.bindingId || 0))
const genericLinkFormKey = computed(() => getLinkColumnKey(genericLinkFormComponentId.value))
const genericLookupKey = computed(() => `lookup:${props.binding.bindingId || 0}`)

// All available fields: prefer prop (parent-managed), fall back to locally loaded
const allFields = computed(() => props.availableFields?.length ? props.availableFields : localAvailableFields.value)
const subTableBindingOptions = computed(() => {
  if (props.subTableBindings?.length) return props.subTableBindings
  return [{
    bindingId: props.binding.bindingId,
    tableName: props.binding.tableName,
    tableDescription: props.binding.tableDescription
  }]
})

// Fields currently in the view (user-selected, ordered)
const viewColumns = computed({
  get: () => props.modelValue || [],
  set: (val) => emit('update:modelValue', val)
})

// Drag state
const dragSourceKey = ref<string | null>(null)
const dragColIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)
const isDraggingFromPanel = ref(false)
type DragPayload = { kind: 'field'; fieldName: string } | { kind: 'linkForm' } | { kind: 'lookup' }
const dragPayload = ref<DragPayload | null>(null)
const dragMime = 'application/x-sub-table-list-column'
const linkFormOption = computed(() => {
  const saved = { ...((selectedSubTableFormDesign.value.options || props.formOption || {}) as Record<string, unknown>) }
  // Persisted designer option often includes `title`; form-create renders it inside the dialog and
  // it may still be the legacy "ADD + …" string — remove so only `el-dialog` shows `linkFormDialogTitle`.
  delete saved.title
  return {
    resetBtn: false,
    submitBtn: false,
    showMsg: true,
    form: {
      labelPosition: 'left',
      labelWidth: '140px',
    },
    ...saved,
    resetBtn: false,
    submitBtn: false,
  }
})

const selectedSubTableFormDesign = computed<SubTableFormDesign>(() => {
  const bindingId = selectedLinkColumn.value?.boundSubTableBindingId || props.binding.bindingId
  return props.resolveSubTableFormDesign?.(bindingId) || {
    rule: props.formRule || [],
    options: props.formOption
  }
})

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

const filteredAvailableFields = computed(() => {
  const kw = fieldSearchKeyword.value.trim().toLowerCase()
  // Only show fields NOT already in the view
  const inView = new Set(viewColumns.value.filter(c => !isLinkColumn(c)).map(f => f.fieldName))
  let list = allFields.value.filter(f => !inView.has(f.fieldName))
  if (kw) {
    list = list.filter(f => f.fieldName.toLowerCase().includes(kw) || (f.comment || '').toLowerCase().includes(kw))
  }
  return list
})

const isFieldInView = (fieldName: string) => viewColumns.value.some(f => !isLinkColumn(f) && f.fieldName === fieldName)
const isLinkColumn = (column: SubTableListColumnDTO) => column.columnType === 'linkForm'
const isLookupColumn = (column: SubTableListColumnDTO) => column.columnType === 'lookup'
const isConfigurableActionColumn = (column: SubTableListColumnDTO) => isLinkColumn(column) || isLookupColumn(column)
const getLinkColumnKey = (componentId: number) => `linkForm:${componentId}`
const getColumnKey = (column: SubTableListColumnDTO) => isLinkColumn(column)
  ? getLinkColumnKey(column.componentId || 0)
  : isLookupColumn(column)
    ? column.fieldName
    : column.fieldName
const getColumnLabel = (column: SubTableListColumnDTO) => {
  if (isLinkColumn(column)) {
    return column.columnLabel || column.comment || column.linkText || t('linkForm.defaultLinkText')
  }
  if (isLookupColumn(column)) {
    return column.columnLabel || column.comment || 'Lookup'
  }
  return column.comment || column.fieldName
}
const getLinkText = (column: SubTableListColumnDTO) => column.linkText || t('linkForm.defaultLinkText')

function getLinkFormBoundTableName(column: SubTableListColumnDTO | null): string {
  if (!column || !isLinkColumn(column)) {
    return props.binding.tableName
  }
  return (
    column.boundSubTableName
    || subTableBindingOptions.value.find(o => o.bindingId === column.boundSubTableBindingId)?.tableName
    || props.binding.tableName
  )
}

/** Legacy titles used "ADD + name"; strip if that prefix was stored on the table display name. */
function linkFormTitleTableName(raw: string): string {
  return String(raw || '')
    .trim()
    .replace(/^ADD\s*\+\s*/i, '')
    .trim()
}

const linkFormDialogTitle = computed(() => {
  const tableName = linkFormTitleTableName(getLinkFormBoundTableName(selectedLinkColumn.value))
  if (!tableName) return t('linkForm.linkedForm')
  return t('linkForm.dialogTitleAddTable', { tableName })
})

const defaultLookupPreviewConfig: LookupPreviewConfig = {
  placeholder: 'Click to search',
  searchFields: [],
  displayFields: [],
  selectedDisplayField: '',
  filterConditions: [],
  viewFields: [],
  fieldDefs: [],
  showBackfillView: true
}
const getLookupPreviewConfig = (column: SubTableListColumnDTO): LookupPreviewConfig => {
  return props.resolveLookupPreviewConfig?.(column.lookupConfig || '{}') || defaultLookupPreviewConfig
}

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
  return viewColumns.value.map(f => ({
    label: getColumnLabel(f),
    value: isLinkColumn(f) ? getLinkText(f) : isLookupColumn(f) ? 'Lookup' : getMockValue(f)
  }))
})

// --- Field operations ---
const addFieldToView = (field: SubTableFieldDTO) => {
  if (!isFieldInView(field.fieldName)) {
    emit('update:modelValue', [...viewColumns.value, { ...field, columnType: 'field' }])
    emit('save')
  }
}

const makeLinkFormColumn = (): SubTableListColumnDTO => ({
  columnType: 'linkForm',
  fieldName: genericLinkFormKey.value,
  dataType: 'LINK_FORM',
  nullable: true,
  isPrimaryKey: false,
  componentId: genericLinkFormComponentId.value,
  comment: 'Link Form',
  columnLabel: 'Link Form',
  linkText: t('linkForm.defaultLinkText'),
  boundSubTableBindingId: props.binding.bindingId,
  boundSubTableName: props.binding.tableName
})

const addLinkFormToView = () => {
  if (!viewColumns.value.some(c => isLinkColumn(c) && c.componentId === genericLinkFormComponentId.value)) {
    emit('update:modelValue', [...viewColumns.value, makeLinkFormColumn()])
    emit('save')
  }
}

const makeLookupColumn = (): SubTableListColumnDTO => ({
  columnType: 'lookup',
  fieldName: genericLookupKey.value,
  dataType: 'LOOKUP',
  nullable: true,
  isPrimaryKey: false,
  comment: 'Lookup',
  columnLabel: 'Lookup',
  lookupConfig: '{}'
})

const addLookupToView = () => {
  if (!viewColumns.value.some(c => isLookupColumn(c))) {
    emit('update:modelValue', [...viewColumns.value, makeLookupColumn()])
    emit('save')
  }
}

const removeField = (index: number) => {
  emit('update:modelValue', viewColumns.value.filter((_, i) => i !== index))
  emit('save')
}

const handlePreview = () => { showPreview.value = true }

const handleClear = () => {
  emit('update:modelValue', [])
  emit('save')
}

// --- Drag from left panel to grid ---
const onFieldDragStart = (e: DragEvent, field: SubTableFieldDTO) => {
  dragSourceKey.value = field.fieldName
  dragPayload.value = { kind: 'field', fieldName: field.fieldName }
  isDraggingFromPanel.value = true
  e.dataTransfer!.effectAllowed = 'copy'
  e.dataTransfer!.setData(dragMime, JSON.stringify(dragPayload.value))
  e.dataTransfer!.setData('text/plain', field.fieldName)
}

const onLinkFormDragStart = (e: DragEvent) => {
  dragSourceKey.value = genericLinkFormKey.value
  dragPayload.value = { kind: 'linkForm' }
  isDraggingFromPanel.value = true
  e.dataTransfer!.effectAllowed = 'copy'
  e.dataTransfer!.setData(dragMime, JSON.stringify(dragPayload.value))
  e.dataTransfer!.setData('text/plain', genericLinkFormKey.value)
}

const onLookupDragStart = (e: DragEvent) => {
  dragSourceKey.value = genericLookupKey.value
  dragPayload.value = { kind: 'lookup' }
  isDraggingFromPanel.value = true
  e.dataTransfer!.effectAllowed = 'copy'
  e.dataTransfer!.setData(dragMime, JSON.stringify(dragPayload.value))
  e.dataTransfer!.setData('text/plain', genericLookupKey.value)
}

const onDragEnd = () => {
  dragSourceKey.value = null
  dragPayload.value = null
  isDraggingFromPanel.value = false
}

const onGridDragOver = (e: DragEvent) => {
  if (isDraggingFromPanel.value) {
    e.dataTransfer!.dropEffect = 'copy'
  }
}

const onGridDrop = (e: DragEvent) => {
  if (!isDraggingFromPanel.value) return
  let payload = dragPayload.value
  const rawPayload = e.dataTransfer?.getData(dragMime)
  if (!payload && rawPayload) {
    try {
      payload = JSON.parse(rawPayload) as DragPayload
    } catch {
      payload = null
    }
  }

  if (payload?.kind === 'field') {
    const field = allFields.value.find(f => f.fieldName === payload.fieldName)
    if (field && !isFieldInView(payload.fieldName)) {
      emit('update:modelValue', [...viewColumns.value, { ...field, columnType: 'field' }])
      emit('save')
    }
  } else if (payload?.kind === 'linkForm') {
    addLinkFormToView()
  } else if (payload?.kind === 'lookup') {
    addLookupToView()
  }
  onDragEnd()
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
    const arr = [...viewColumns.value]
    const [moved] = arr.splice(dragColIndex.value, 1)
    arr.splice(targetIndex, 0, moved)
    emit('update:modelValue', arr)
    emit('save')
  }
  dragColIndex.value = null
  dragOverIndex.value = null
}

async function openLinkFormDialog(column: SubTableListColumnDTO) {
  if (column.componentId === undefined || column.componentId === null) return
  selectedLinkColumn.value = column
  linkFormData.value = {}
  formCreateMounted.value = false
  showLinkFormDialog.value = true

  try {
    const res = await linkFormComponentApi.getFormData(props.functionUnitId, column.componentId, mockSubTableRowId)
    linkFormData.value = res.data?.formData || {}
  } catch (e: any) {
    if (e?.response?.status !== 404) {
      console.error('[SubTableListView] failed to load link form data:', e)
    }
  }

  nextTick(() => {
    formCreateMounted.value = true
  })
}

async function handleLinkFormSave() {
  if (selectedLinkColumn.value?.componentId === undefined || selectedLinkColumn.value?.componentId === null) return
  savingLinkForm.value = true
  try {
    await linkFormComponentApi.saveFormData(props.functionUnitId, {
      componentId: selectedLinkColumn.value.componentId,
      subTableRowId: mockSubTableRowId,
      formData: linkFormData.value
    })
    ElMessage.success(t('common.saveSuccess'))
    showLinkFormDialog.value = false
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || t('common.saveFailed'))
  } finally {
    savingLinkForm.value = false
  }
}

function openActionColumnConfig(column: SubTableListColumnDTO, index: number) {
  editingActionColumnIndex.value = index
  editingActionColumnType.value = isLookupColumn(column) ? 'lookup' : 'linkForm'
  if (isLookupColumn(column)) {
    lookupColumnConfig.value = {
      columnLabel: column.columnLabel || column.comment || 'Lookup',
      lookupConfig: column.lookupConfig || '{}'
    }
  } else {
    linkColumnConfig.value = {
      boundSubTableBindingId: column.boundSubTableBindingId || props.binding.bindingId,
      columnLabel: column.columnLabel || column.comment || 'Link Form',
      linkText: column.linkText || t('linkForm.defaultLinkText')
    }
  }
  showActionColumnConfig.value = true
}

function saveActionColumnConfig() {
  if (editingActionColumnIndex.value === null) return
  const columns = [...viewColumns.value]
  const current = columns[editingActionColumnIndex.value]
  if (!current || !isConfigurableActionColumn(current)) return
  columns[editingActionColumnIndex.value] = isLookupColumn(current)
    ? {
      ...current,
      comment: lookupColumnConfig.value.columnLabel || 'Lookup',
      columnLabel: lookupColumnConfig.value.columnLabel || 'Lookup',
      lookupConfig: lookupColumnConfig.value.lookupConfig || '{}'
    }
    : {
      ...current,
      comment: linkColumnConfig.value.columnLabel || 'Link Form',
      columnLabel: linkColumnConfig.value.columnLabel || 'Link Form',
      linkText: linkColumnConfig.value.linkText || t('linkForm.defaultLinkText'),
      boundSubTableBindingId: linkColumnConfig.value.boundSubTableBindingId || props.binding.bindingId,
      boundSubTableName: subTableBindingOptions.value.find(
        option => option.bindingId === linkColumnConfig.value.boundSubTableBindingId
      )?.tableName
  }
  emit('update:modelValue', columns)
  emit('save')
  showActionColumnConfig.value = false
  editingActionColumnIndex.value = null
}

function handleLinkFormDialogClosed() {
  formCreateMounted.value = false
  selectedLinkColumn.value = null
  linkFormData.value = {}
}

const onColDragEnd = () => {
  dragColIndex.value = null
  dragOverIndex.value = null
}

// --- Expose for parent (getters for save) ---
defineExpose({
  getViewFields: () => viewColumns.value,
  getAllFields: () => allFields.value,
  getListColumns: () => viewColumns.value,
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
.panel-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.panel-section:first-child {
  flex: 1;
}
.extend-action-section {
  max-height: 40%;
  border-top: 1px solid var(--el-border-color-light);
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
.link-form-item { color: var(--el-color-primary, #409eff); }
.extend-action-list { padding-top: 4px; }

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

.list-view-workspace {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.data-grid-panel {
  min-height: 140px;
  padding: 12px;
  display: flex;
  flex-direction: column;
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
.column-header.link-column { color: var(--el-color-primary, #409eff); }
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

.list-view-lookup-preview {
  width: 100%;
  min-width: 220px;
  margin-bottom: 0;
}

.list-view-lookup-preview :deep(.lookup-label-text) {
  display: none;
}

.link-form-dialog-body {
  min-height: 200px;
  max-height: 60vh;
  overflow-y: auto;
}
</style>
