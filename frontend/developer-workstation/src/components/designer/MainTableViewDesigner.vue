<script setup lang="ts">

import { ref, computed, watch, onMounted } from 'vue'

import { useI18n } from 'vue-i18n'

import { ElMessage } from 'element-plus'

import {

  Search,

  Close,

  Menu,

  DArrowRight,

  Plus,

  EditPen,

  Calendar,

  Document,

  Coin,

  Switch as SwitchIcon,

  Filter,

  CaretTop,

  CaretBottom,

} from '@element-plus/icons-vue'

import {

  mainTableViewApi,

  SYSTEM_VIEW_FIELDS,

  type MainTableViewDefinition,

  type MainTableViewField,

  type MainTableFieldCatalogItem,

  type FilterCondition,

  type FilterConfig,

} from '@/api/mainTableView'

import MainTableViewFilterEditor from './MainTableViewFilterEditor.vue'

import {

  flattenFilterConditions,

  parseFilterConfigToEditorRoot,

  removeFieldFromFilterTree,

  removeFlattenedConditionAt,

  serializeFilterEditorRoot,

} from '@/utils/mainTableViewFilter'

import { functionUnitApi, type TableDefinition } from '@/api/functionUnit'



const props = defineProps<{

  functionUnitId: number

  view: MainTableViewDefinition

}>()



const emit = defineEmits<{

  saved: [view: MainTableViewDefinition]

}>()



const { t } = useI18n()



const columnsPanelOpen = ref(true)

const fieldSearchKeyword = ref('')

const saving = ref(false)

const viewName = ref('')

const viewFields = ref<MainTableViewField[]>([])

const sortConfig = ref<Array<{ fieldName: string; direction: string; systemField?: boolean }>>([])

const filterConfig = ref<FilterConfig>({ logic: 'and', conditions: [], groups: [] })
const filterEditorRoot = ref(parseFilterConfigToEditorRoot(null))

const enableExport = ref(true)
const enableImport = ref(true)

const catalogFields = ref<MainTableFieldCatalogItem[]>([])

const mainTableName = ref('')

const filterDialogVisible = ref(false)

const addColumnPopoverVisible = ref(false)
const thenSortField = ref<string>('')



const dragColIndex = ref<number | null>(null)

const dragOverIndex = ref<number | null>(null)

const isDraggingFromPanel = ref(false)

const dragSourceField = ref<string | null>(null)



const visibleColumns = computed(() => viewFields.value.filter(f => f.visible !== false))

const displayFilterConditions = computed(() => flattenFilterConditions(filterEditorRoot.value))



const sortFieldOptions = computed(() =>

  viewFields.value

    .filter(f => f.visible !== false)

    .map(f => ({

      fieldName: f.fieldName,

      label: f.displayLabel || f.fieldName,

      systemField: f.systemField ?? false,

    })),

)



watch(

  () => props.view,

  (v) => {

    if (!v) return

    viewName.value = v.viewName

    viewFields.value = (v.fields || []).map(f => ({ ...f }))

    sortConfig.value = v.sortConfig?.length

      ? v.sortConfig.map(s => ({ ...s }))

      : [{ fieldName: 'start_time', direction: 'DESC', systemField: true }]

    filterConfig.value = v.filterConfig
      ? {
          logic: v.filterConfig.logic === 'or' ? 'or' : 'and',
          conditions: v.filterConfig.conditions?.map(c => ({ ...c })) || [],
          groups: v.filterConfig.groups ? JSON.parse(JSON.stringify(v.filterConfig.groups)) : [],
        }
      : { logic: 'and', conditions: [], groups: [] }
    filterEditorRoot.value = parseFilterConfigToEditorRoot(filterConfig.value)
    enableExport.value = v.filterConfig?.toolbar?.enableExport !== false
    enableImport.value = v.filterConfig?.toolbar?.enableImport !== false

  },

  { immediate: true, deep: true },

)



async function loadCatalog() {

  try {

    const res = await functionUnitApi.getTables(props.functionUnitId)

    const tables: TableDefinition[] = res.data || []

    const main = tables.find(tbl => tbl.tableType === 'MAIN')

    mainTableName.value = main?.tableName || main?.displayName || ''

    const business: MainTableFieldCatalogItem[] = (main?.fieldDefinitions || []).map(f => ({

      fieldName: f.fieldName,

      displayName: f.displayName || f.fieldName,

      dataType: f.dataType,

      systemField: false,

    }))

    catalogFields.value = [...business, ...SYSTEM_VIEW_FIELDS]

  } catch (e) {

    console.error('[MainTableViewDesigner] load catalog failed', e)

    catalogFields.value = [...SYSTEM_VIEW_FIELDS]

  }

}



onMounted(loadCatalog)



const filteredCatalog = computed(() => {

  const inView = new Set(viewFields.value.map(f => f.fieldName))

  const kw = fieldSearchKeyword.value.trim().toLowerCase()

  return catalogFields.value.filter(f => {

    if (inView.has(f.fieldName)) return false

    if (!kw) return true

    return f.fieldName.toLowerCase().includes(kw) || (f.displayName || '').toLowerCase().includes(kw)

  })

})



function fieldLabel(fieldName: string): string {

  const col = viewFields.value.find(f => f.fieldName === fieldName)

  if (col?.displayLabel) return col.displayLabel

  const cat = catalogFields.value.find(f => f.fieldName === fieldName)

  return cat?.displayName || fieldName

}



function getFieldIcon(dataType?: string) {

  const type = (dataType || '').toUpperCase()

  if (type.includes('INT') || type.includes('DECIMAL') || type.includes('NUMERIC')) return Coin

  if (type.includes('DATE') || type.includes('TIME') || type.includes('TIMESTAMP')) return Calendar

  if (type.includes('BOOL')) return SwitchIcon

  if (type.includes('TEXT') || type.includes('CLOB')) return Document

  return EditPen

}



function getMockValue(field: MainTableViewField, rowIndex: number): string {

  const type = (catalogFields.value.find(f => f.fieldName === field.fieldName)?.dataType || '').toUpperCase()

  if (field.fieldName === 'process_status') return rowIndex === 0 ? 'Running' : 'Completed'

  if (field.fieldName === 'initiator') return rowIndex === 0 ? 'Alice Chen' : 'Bob Lee'

  if (field.fieldName === 'current_step') return rowIndex === 0 ? 'Review' : 'Done'

  if (type.includes('INT') || type === 'BIGINT') return String(rowIndex + 1)

  if (type.includes('DECIMAL') || type.includes('NUMERIC')) return '100.00'

  if (type === 'BOOLEAN' || type === 'BOOL') return 'true'

  if (type === 'DATE') return '2026-01-01'

  if (type.includes('TIMESTAMP') || type === 'DATETIME') return '2026-01-01 09:00:00'

  if (type.includes('TIME')) return '09:00:00'

  if (type === 'TEXT' || type.includes('CLOB')) return 'Sample text'

  return `Sample ${rowIndex + 1}`

}



function sortIndicator(fieldName: string): 'ASC' | 'DESC' | null {

  const hit = sortConfig.value.find(s => s.fieldName === fieldName)

  if (!hit) return null

  return hit.direction === 'DESC' ? 'DESC' : 'ASC'

}



function formatFilterTag(cond: FilterCondition): string {

  const label = fieldLabel(cond.fieldName)

  const opMap: Record<string, string> = {

    eq: '等于',
    ne: '不等于',
    contains: t('mainTableView.opContains').toLowerCase(),

    notContains: t('mainTableView.opNotContains').toLowerCase(),
    notStartsWith: t('mainTableView.opNotStartsWith').toLowerCase(),
    endsWith: t('mainTableView.opEndsWith').toLowerCase(),
    notEndsWith: t('mainTableView.opNotEndsWith').toLowerCase(),

    gt: '>',

    lt: '<',

    isNull: t('mainTableView.opIsNull').toLowerCase(),

    isNotNull: t('mainTableView.opIsNotNull').toLowerCase(),

  }

  const op = opMap[cond.operator] || cond.operator

  if (cond.operator === 'isNull' || cond.operator === 'isNotNull') {

    return `${label} ${op}`

  }

  return `${label} ${op} ${cond.value ?? ''}`.trim()

}



function addField(field: MainTableFieldCatalogItem) {

  if (viewFields.value.some(f => f.fieldName === field.fieldName)) return

  viewFields.value.push({

    fieldName: field.fieldName,

    displayLabel: field.displayName || field.fieldName,

    columnWidth: 150,

    sortOrder: viewFields.value.length,

    visible: true,

    systemField: field.systemField ?? false,

  })

  addColumnPopoverVisible.value = false

}



function removeField(index: number) {

  const removed = viewFields.value[index]

  viewFields.value.splice(index, 1)

  viewFields.value.forEach((f, i) => { f.sortOrder = i })

  if (removed) {

    sortConfig.value = sortConfig.value.filter(s => s.fieldName !== removed.fieldName)

    removeFieldFromFilterTree(filterEditorRoot.value, removed.fieldName)
    filterConfig.value = serializeFilterEditorRoot(filterEditorRoot.value)

  }

}



function toggleSortDirection(sort: { direction: string }) {
  sort.direction = sort.direction === 'DESC' ? 'ASC' : 'DESC'
}

function getFieldDataType(fieldName: string): string {
  return (catalogFields.value.find(f => f.fieldName === fieldName)?.dataType || '').toUpperCase()
}

function sortDirectionTooltip(sort: { fieldName: string; direction: string }): string {
  const label = fieldLabel(sort.fieldName)
  const isDesc = sort.direction === 'DESC'
  const type = getFieldDataType(sort.fieldName)
  let hintKey = 'mainTableView.sortTextAsc'
  if (type.includes('DATE') || type.includes('TIME') || type.includes('TIMESTAMP')) {
    hintKey = isDesc ? 'mainTableView.sortDateDesc' : 'mainTableView.sortDateAsc'
  } else if (type.includes('INT') || type.includes('DECIMAL') || type.includes('NUM') || type === 'BIGINT') {
    hintKey = isDesc ? 'mainTableView.sortNumberDesc' : 'mainTableView.sortNumberAsc'
  } else {
    hintKey = isDesc ? 'mainTableView.sortTextDesc' : 'mainTableView.sortTextAsc'
  }
  return `${label} (${t(hintKey)})`
}

function onFilterEditorSave(config: FilterConfig) {
  filterConfig.value = config
  filterEditorRoot.value = parseFilterConfigToEditorRoot(config)
}

function removeDisplayFilterTag(flatIndex: number) {
  removeFlattenedConditionAt(filterEditorRoot.value, flatIndex)
  filterConfig.value = serializeFilterEditorRoot(filterEditorRoot.value)
}

function addSortField(fieldName: string) {

  if (!fieldName || sortConfig.value.some(s => s.fieldName === fieldName)) return

  const opt = sortFieldOptions.value.find(o => o.fieldName === fieldName)

  sortConfig.value.push({ fieldName, direction: 'ASC', systemField: opt?.systemField })

  thenSortField.value = ''

}



function removeSort(index: number) {

  sortConfig.value.splice(index, 1)

}



async function handleSave() {

  saving.value = true

  try {

    const fields = viewFields.value.map((f, i) => ({ ...f, sortOrder: i }))

    const res = await mainTableViewApi.update(props.functionUnitId, props.view.id, {

      viewName: viewName.value.trim() || props.view.viewName,

      sortConfig: sortConfig.value,

      filterConfig: {
        ...filterConfig.value,
        toolbar: {
          enableExport: enableExport.value,
          enableImport: enableImport.value,
        },
      },

      fields,

    })

    ElMessage.success(t('mainTableView.saved'))

    emit('saved', res.data)

  } catch (e: any) {

    ElMessage.error(e?.message || t('common.saveFailed'))

  } finally {

    saving.value = false

  }

}



function onFieldDragStart(e: DragEvent, field: MainTableFieldCatalogItem) {

  dragSourceField.value = field.fieldName

  isDraggingFromPanel.value = true

  e.dataTransfer!.effectAllowed = 'copy'

  e.dataTransfer!.setData('text/plain', field.fieldName)

}



function onFieldDragEnd() {

  dragSourceField.value = null

  isDraggingFromPanel.value = false

}



function onGridDrop(e: DragEvent) {

  if (!isDraggingFromPanel.value) return

  const name = e.dataTransfer!.getData('text/plain')

  const field = catalogFields.value.find(f => f.fieldName === name)

  if (field) addField(field)

  isDraggingFromPanel.value = false

  dragSourceField.value = null

}



function onColDragStart(e: DragEvent, index: number) {

  dragColIndex.value = index

  isDraggingFromPanel.value = false

  e.dataTransfer!.effectAllowed = 'move'

}



function onColDragOver(_e: DragEvent, index: number) {

  if (dragColIndex.value !== null && dragColIndex.value !== index) {

    dragOverIndex.value = index

  }

}



function onColDragLeave() {

  dragOverIndex.value = null

}



function onColDrop(_e: DragEvent, dropIndex: number) {

  if (dragColIndex.value === null || dragColIndex.value === dropIndex) return

  const visible = visibleColumns.value

  const fromField = visible[dragColIndex.value]

  const toField = visible[dropIndex]

  const fromIdx = viewFields.value.findIndex(f => f.fieldName === fromField.fieldName)

  const toIdx = viewFields.value.findIndex(f => f.fieldName === toField.fieldName)

  if (fromIdx < 0 || toIdx < 0) return

  const list = [...viewFields.value]

  const [moved] = list.splice(fromIdx, 1)

  list.splice(toIdx, 0, moved)

  viewFields.value = list.map((f, i) => ({ ...f, sortOrder: i }))

  dragColIndex.value = null

  dragOverIndex.value = null

}



function onColDragEnd() {

  dragColIndex.value = null

  dragOverIndex.value = null

}



const previewRowCount = 3

</script>



<template>

  <div class="main-table-view-designer">

    <div class="designer-toolbar">

      <div class="toolbar-left">

        <el-button

          size="small"

          :icon="Plus"

          @click="columnsPanelOpen = true"

        >

          {{ t('mainTableView.addViewColumn') }}

        </el-button>

      </div>

      <div class="toolbar-right">

        <el-button

          type="primary"

          size="small"

          :loading="saving"

          @click="handleSave"

        >

          {{ t('common.save') }}

        </el-button>

      </div>

    </div>



    <div class="designer-body">

      <!-- Left: Table columns -->

      <div

        v-if="columnsPanelOpen"

        class="columns-panel"

      >

        <div class="columns-panel-header">

          <div class="columns-panel-title">

            <el-icon><Menu /></el-icon>

            <span>{{ t('mainTableView.tableColumns') }}</span>

          </div>

          <el-icon

            class="close-btn"

            @click="columnsPanelOpen = false"

          >

            <Close />

          </el-icon>

        </div>

        <div

          v-if="mainTableName"

          class="columns-panel-table-name"

        >

          {{ mainTableName }}

        </div>

        <div class="columns-panel-search">

          <el-input

            v-model="fieldSearchKeyword"

            :placeholder="t('common.search')"

            clearable

            size="small"

          >

            <template #prefix>

              <el-icon><Search /></el-icon>

            </template>

          </el-input>

        </div>

        <div class="columns-field-list">

          <div

            v-for="field in filteredCatalog"

            :key="field.fieldName"

            class="field-item"

            :class="{ dragging: dragSourceField === field.fieldName }"

            draggable="true"

            @dragstart="onFieldDragStart($event, field)"

            @dragend="onFieldDragEnd"

            @click="addField(field)"

          >

            <el-icon class="field-icon">

              <component :is="getFieldIcon(field.dataType)" />

            </el-icon>

            <span class="field-name">{{ field.displayName || field.fieldName }}</span>

          </div>

          <el-empty

            v-if="!filteredCatalog.length"

            :description="t('mainTableView.noAvailableFields')"

            :image-size="40"

          />

        </div>

      </div>

      <div

        v-else

        class="columns-toggle"

        @click="columnsPanelOpen = true"

      >

        <el-icon><DArrowRight /></el-icon>

      </div>



      <!-- Center: Live preview grid -->

      <div

        class="preview-panel"

        @dragover.prevent

        @drop="onGridDrop"

      >

        <div

          v-if="visibleColumns.length"

          class="preview-grid"

        >

          <div class="column-headers">

            <div

              v-for="(field, index) in visibleColumns"

              :key="field.fieldName"

              class="column-header"

              :class="{ 'drag-over': dragOverIndex === index }"

              :style="{ flex: field.columnWidth ? `0 0 ${field.columnWidth}px` : undefined }"

              draggable="true"

              @dragstart="onColDragStart($event, index)"

              @dragover.prevent="onColDragOver($event, index)"

              @dragleave="onColDragLeave"

              @drop.stop="onColDrop($event, index)"

              @dragend="onColDragEnd"

            >

              <span class="col-name">{{ field.displayLabel || field.fieldName }}</span>

              <span class="col-sort-icons">

                <el-icon

                  v-if="sortIndicator(field.fieldName) === 'ASC'"

                  class="sort-icon active"

                ><CaretTop /></el-icon>

                <el-icon

                  v-else-if="sortIndicator(field.fieldName) === 'DESC'"

                  class="sort-icon active"

                ><CaretBottom /></el-icon>

              </span>

              <el-icon

                class="col-remove"

                @click.stop="removeField(viewFields.findIndex(f => f.fieldName === field.fieldName))"

              >

                <Close />

              </el-icon>

            </div>

            <el-popover

              v-model:visible="addColumnPopoverVisible"

              placement="bottom"

              :width="220"

              trigger="click"

            >

              <template #reference>

                <div class="add-column-header">

                  <el-icon><Plus /></el-icon>

                  <span>{{ t('mainTableView.addViewColumn') }}</span>

                </div>

              </template>

              <div class="add-column-popover">

                <div

                  v-for="field in filteredCatalog"

                  :key="'add-' + field.fieldName"

                  class="field-item compact"

                  @click="addField(field)"

                >

                  {{ field.displayName || field.fieldName }}

                </div>

                <el-empty

                  v-if="!filteredCatalog.length"

                  :description="t('mainTableView.noAvailableFields')"

                  :image-size="32"

                />

              </div>

            </el-popover>

          </div>

          <div

            v-for="rowIdx in previewRowCount"

            :key="'row-' + rowIdx"

            class="data-row"

          >

            <div

              v-for="field in visibleColumns"

              :key="field.fieldName + '-' + rowIdx"

              class="data-cell"

              :style="{ flex: field.columnWidth ? `0 0 ${field.columnWidth}px` : undefined }"

            >

              {{ getMockValue(field, rowIdx - 1) }}

            </div>

            <div class="add-column-spacer" />

          </div>

        </div>

        <el-empty

          v-else

          class="preview-empty"

          :description="t('mainTableView.noColumns')"

          :image-size="64"

        />

      </div>



      <!-- Right: View properties -->

      <div class="properties-panel">

        <div class="properties-header">

          <div class="properties-title">

            {{ viewName || props.view.viewName }}

          </div>

          <div class="properties-subtitle">

            {{ t('mainTableView.entityTypeView') }}

          </div>

        </div>



        <div class="properties-section">

          <label class="section-label">{{ t('mainTableView.viewName') }}</label>

          <el-input

            v-model="viewName"

            size="small"

          />

        </div>



        <div class="properties-section">

          <label class="section-label">{{ t('mainTableView.portalToolbar') }}</label>

          <div class="toolbar-toggles">

            <el-checkbox v-model="enableExport">

              {{ t('mainTableView.enableExport') }}

            </el-checkbox>

            <el-checkbox v-model="enableImport">

              {{ t('mainTableView.enableImport') }}

            </el-checkbox>

          </div>

        </div>



        <div class="properties-section sort-by-section">

          <label class="section-label">{{ t('mainTableView.sortBy') }}</label>

          <div
            v-if="sortConfig.length"
            class="sort-by-list"
          >
            <div
              v-for="(sort, idx) in sortConfig"
              :key="'sort-' + idx"
              class="sort-pill"
            >
              <el-tooltip
                :content="sortDirectionTooltip(sort)"
                placement="bottom"
                :show-after="300"
              >
                <button
                  type="button"
                  class="sort-direction-btn"
                  :aria-label="sortDirectionTooltip(sort)"
                  @click="toggleSortDirection(sort)"
                >
                  <el-icon :size="14">
                    <CaretTop v-if="sort.direction === 'ASC'" />
                    <CaretBottom v-else />
                  </el-icon>
                </button>
              </el-tooltip>
              <span class="sort-pill-label">{{ fieldLabel(sort.fieldName) }}</span>
              <button
                type="button"
                class="sort-pill-remove"
                :aria-label="t('common.delete')"
                @click="removeSort(idx)"
              >
                <el-icon :size="12"><Close /></el-icon>
              </button>
            </div>
          </div>

          <el-select
            v-if="sortFieldOptions.some(o => !sortConfig.some(s => s.fieldName === o.fieldName))"
            v-model="thenSortField"
            size="small"
            class="then-sort-select"
            :placeholder="sortConfig.length ? t('mainTableView.thenSortBy') : t('mainTableView.sortBy')"
            clearable
            @change="(val: string) => { if (val) addSortField(val) }"
          >
            <el-option
              v-for="opt in sortFieldOptions.filter(o => !sortConfig.some(s => s.fieldName === o.fieldName))"
              :key="opt.fieldName"
              :label="opt.label"
              :value="opt.fieldName"
            />
          </el-select>

        </div>



        <div class="properties-section">

          <label class="section-label">{{ t('mainTableView.filters') }}</label>

          <div

            v-if="displayFilterConditions.length"

            class="tag-list"

          >

            <el-tag

              v-for="(cond, idx) in displayFilterConditions"

              :key="'filter-' + idx"

              closable

              size="default"

              class="config-tag"

              @close="removeDisplayFilterTag(idx)"

            >

              {{ formatFilterTag(cond) }}

            </el-tag>

          </div>

          <el-button

            link

            type="primary"

            class="edit-filters-btn"

            @click="filterDialogVisible = true"

          >

            <el-icon><Filter /></el-icon>

            {{ t('mainTableView.editFilters') }}

          </el-button>

        </div>



        <div class="properties-section">

          <label class="section-label">{{ t('mainTableView.columnSettings') }}</label>

          <div

            v-for="field in viewFields"

            :key="'col-set-' + field.fieldName"

            class="column-setting-row"

          >

            <el-checkbox v-model="field.visible" />

            <el-input

              v-model="field.displayLabel"

              size="small"

              class="col-label-input"

            />

            <el-tag

              v-if="field.systemField"

              size="small"

              type="info"

            >

              {{ t('mainTableView.systemField') }}

            </el-tag>

          </div>

        </div>

      </div>

    </div>



    <MainTableViewFilterEditor
      v-model="filterDialogVisible"
      :filter-config="filterConfig"
      :field-options="sortFieldOptions"
      @save="onFilterEditorSave"
    />
  </div>
</template>



<style scoped lang="scss">

.main-table-view-designer {

  display: flex;

  flex-direction: column;

  height: calc(100vh - 280px);

  min-height: 520px;

}



.designer-toolbar {

  display: flex;

  justify-content: space-between;

  align-items: center;

  margin-bottom: 10px;

  padding-bottom: 8px;

  border-bottom: 1px solid var(--el-border-color-lighter);

}



.designer-body {

  display: flex;

  flex: 1;

  min-height: 0;

  border: 1px solid var(--el-border-color-light);

  border-radius: 4px;

  overflow: hidden;

  background: #fff;

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

  gap: 6px;

  font-weight: 600;

  font-size: 14px;

}



.columns-panel-table-name {

  padding: 8px 12px 4px;

  font-size: 13px;

  color: #666;

  font-style: italic;

}



.columns-panel-search {

  padding: 4px 8px 8px;

}



.columns-field-list {

  flex: 1;

  overflow-y: auto;

  padding: 0 4px 8px;

}



.field-item {

  display: flex;

  align-items: center;

  padding: 8px 12px;

  cursor: grab;

  border-radius: 4px;

  font-size: 13px;

  transition: background 0.15s;

  user-select: none;



  &:hover {

    background: var(--el-fill-color-light);

  }



  &.dragging {

    opacity: 0.5;

  }



  &.compact {

    cursor: pointer;

    padding: 6px 8px;

  }

}



.field-icon {

  margin-right: 8px;

  font-size: 15px;

  color: #999;

}



.field-name {

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

}



.columns-toggle {

  width: 20px;

  display: flex;

  align-items: center;

  justify-content: center;

  cursor: pointer;

  background: #fafafa;

  border-right: 1px solid var(--el-border-color-light);

  flex-shrink: 0;

}



.preview-panel {

  flex: 1;

  min-width: 0;

  overflow: auto;

  padding: 16px;

  background: #f5f6f8;

}



.preview-empty {

  height: 100%;

  display: flex;

  align-items: center;

  justify-content: center;

}



.preview-grid {

  background: #fff;

  border: 1px solid var(--el-border-color-light);

  min-width: min-content;

}



.column-headers {

  display: flex;

  border-bottom: 2px solid var(--el-border-color);

  background: #fafafa;

  min-height: 40px;

}



.column-header {

  flex: 1;

  min-width: 120px;

  display: flex;

  align-items: center;

  gap: 4px;

  padding: 8px 10px;

  font-size: 13px;

  font-weight: 600;

  cursor: grab;

  border-right: 1px solid var(--el-border-color-lighter);

  user-select: none;



  &:hover {

    background: #f0f0f0;

  }



  &.drag-over {

    background: var(--el-color-primary-light-9);

    border-left: 2px solid var(--el-color-primary);

  }

}



.col-name {

  flex: 1;

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

}



.col-sort-icons {

  display: flex;

  align-items: center;

}



.sort-icon.active {

  color: var(--el-color-primary);

  font-size: 14px;

}



.col-remove {

  font-size: 12px;

  color: #ccc;

  cursor: pointer;

  flex-shrink: 0;



  &:hover {

    color: var(--el-color-danger);

  }

}



.add-column-header {

  display: flex;

  align-items: center;

  gap: 4px;

  padding: 8px 12px;

  min-width: 140px;

  font-size: 13px;

  color: var(--el-color-primary);

  cursor: pointer;

  white-space: nowrap;

  border-left: 1px solid var(--el-border-color-lighter);



  &:hover {

    background: var(--el-color-primary-light-9);

  }

}



.add-column-spacer {

  flex: 0 0 140px;

  border-left: 1px solid var(--el-border-color-lighter);

}



.data-row {

  display: flex;

  border-bottom: 1px solid var(--el-border-color-lighter);

  min-height: 36px;



  &:last-child {

    border-bottom: none;

  }

}



.data-cell {

  flex: 1;

  min-width: 120px;

  display: flex;

  align-items: center;

  padding: 8px 10px;

  font-size: 13px;

  color: #555;

  border-right: 1px solid var(--el-border-color-lighter);

}



.properties-panel {

  width: 280px;

  flex-shrink: 0;

  border-left: 1px solid var(--el-border-color-light);

  overflow-y: auto;

  padding: 16px 14px;

  background: #fff;

}



.properties-header {

  margin-bottom: 16px;

}



.properties-title {

  font-size: 15px;

  font-weight: 600;

  line-height: 1.3;

}



.properties-subtitle {

  font-size: 12px;

  color: #888;

  margin-top: 2px;

}



.properties-section {

  margin-bottom: 18px;

}

.toolbar-toggles {
  display: flex;
  flex-direction: column;
  gap: 8px;
}



.section-label {

  display: block;

  font-size: 13px;

  font-weight: 600;

  margin-bottom: 8px;

  color: #333;

}



.tag-list {

  display: flex;

  flex-wrap: wrap;

  gap: 6px;

  margin-bottom: 8px;

}



.config-tag {

  max-width: 100%;

}



.tag-direction {

  margin-left: 4px;

  font-size: 11px;

  opacity: 0.75;

}



.then-sort-select {

  width: 100%;

  margin-top: 2px;

  :deep(.el-select__wrapper) {
    box-shadow: none;
    border: none;
    background: transparent;
    padding-left: 0;
    min-height: 28px;
  }

  :deep(.el-select__placeholder),
  :deep(.el-select__selected-item) {
    color: #742774;
    font-size: 13px;
  }

  :deep(.el-select__caret) {
    color: #742774;
  }
}

.sort-by-section {
  .section-label {
    margin-bottom: 8px;
  }
}

.sort-by-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 4px;
}

.sort-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid #d1d1d1;
  border-radius: 4px;
  background: #fff;
}

.sort-direction-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  cursor: pointer;
  color: #323130;
  padding: 0;
  flex-shrink: 0;

  &:hover {
    color: #742774;
  }
}

.sort-pill-label {
  flex: 1;
  font-size: 13px;
  color: #323130;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sort-pill-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  cursor: pointer;
  color: #605e5c;
  padding: 2px;
  flex-shrink: 0;

  &:hover {
    color: #323130;
  }
}



.edit-filters-btn {

  padding-left: 0;

}



.column-setting-row {

  display: flex;

  align-items: center;

  gap: 6px;

  margin-bottom: 6px;

}



.col-label-input {

  flex: 1;

}



.filter-row {

  display: flex;

  align-items: center;

  gap: 8px;

  margin-bottom: 8px;

}



.close-btn {

  cursor: pointer;

  color: #999;



  &:hover {

    color: #333;

  }

}



.add-column-popover {

  max-height: 240px;

  overflow-y: auto;

}

</style>

