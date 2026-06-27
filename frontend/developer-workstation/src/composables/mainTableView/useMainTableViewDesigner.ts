import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  Search, Close, Menu, DArrowRight, DArrowLeft, Plus, EditPen, Calendar, Document, Coin,
  Switch as SwitchIcon, Filter, CaretTop, CaretBottom, Connection, Key,
} from '@element-plus/icons-vue'
import {
  mainTableViewApi, SYSTEM_VIEW_FIELDS,
  type MainTableViewDefinition, type MainTableViewField, type MainTableFieldCatalogItem,
  type FilterCondition, type FilterConfig,
} from '@/api/mainTableView'
import {
  flattenFilterConditions, parseFilterConfigToEditorRoot, removeFieldFromFilterTree,
  removeFlattenedConditionAt, serializeFilterEditorRoot,
} from '@/utils/mainTableViewFilter'
import { functionUnitApi, type TableDefinition } from '@/api/functionUnit'

export interface MainTableViewDesignerProps {
  functionUnitId: number
  view: MainTableViewDefinition
}

export function useMainTableViewDesigner(
  props: MainTableViewDesignerProps,
  emit: {
    (event: 'saved', view: MainTableViewDefinition): void
    (event: 'navigate-to-table-view', refTableId: number): void
  },
) {
  const { t } = useI18n()



const columnsPanelOpen = ref(true)
const propsPanelOpen = ref(true)
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
const fieldMetaMap = ref<Record<string, { isPrimaryKey: boolean; isForeignKey: boolean; refTableId: number | null }>>({})
const selectedCatalogFields = ref<Set<string>>(new Set())
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
    // Catalog is scoped to THIS view's owning table (not always MAIN).
    const table = tables.find(tbl => tbl.id === props.view.mainTableId)
    mainTableName.value = table?.tableDisplayName || table?.tableName || ''
    // Remember FK/PK metadata per field so newly-added columns render as links immediately
    // (before a save round-trip refreshes the backend-derived flags).
    fieldMetaMap.value = {}
    for (const f of table?.fieldDefinitions || []) {
      fieldMetaMap.value[f.fieldName] = {
        isPrimaryKey: !!f.isPrimaryKey,
        isForeignKey: !!f.isForeignKey,
        refTableId: f.refTableId ?? null,
      }
    }
    const business: MainTableFieldCatalogItem[] = (table?.fieldDefinitions || []).map(f => ({
      fieldName: f.fieldName,
      displayName: f.displayName || f.fieldName,
      dataType: f.dataType,
      systemField: false,
    }))
    // System fields (process_status / start_time / …) only exist for the MAIN table runtime.
    catalogFields.value = table?.tableType === 'MAIN'
      ? [...business, ...SYSTEM_VIEW_FIELDS]
      : business
  } catch {
    catalogFields.value = []
    fieldMetaMap.value = {}
  }
}



// Load the catalog for the current view's table, and reload when switching to a different table.
watch(() => props.view?.mainTableId, () => { loadCatalog() }, { immediate: true })

// Designer-internal FK navigation: clicking a FK column opens the referenced table's default view.
// Flags come from the view field (backend-derived) or fall back to the table's catalog metadata
// (so columns just added in this session render as links before a save round-trip).
function isFkField(fieldName: string): boolean {
  const f = viewFields.value.find(v => v.fieldName === fieldName)
  return !!(f?.isForeignKey ?? fieldMetaMap.value[fieldName]?.isForeignKey)
}
function isPkField(fieldName: string): boolean {
  const f = viewFields.value.find(v => v.fieldName === fieldName)
  return !!(f?.isPrimaryKey ?? fieldMetaMap.value[fieldName]?.isPrimaryKey)
}
function onFkColumnClick(fieldName: string) {
  const f = viewFields.value.find(v => v.fieldName === fieldName)
  const refTableId = f?.refTableId ?? fieldMetaMap.value[fieldName]?.refTableId
  if ((f?.isForeignKey ?? fieldMetaMap.value[fieldName]?.isForeignKey) && refTableId) {
    emit('navigate-to-table-view', refTableId)
  }
}



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
    eq: t('mainTableView.opEq').toLowerCase(),
    ne: t('mainTableView.opNe').toLowerCase(),
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
  const meta = fieldMetaMap.value[field.fieldName]
  viewFields.value.push({
    fieldName: field.fieldName,
    displayLabel: field.displayName || field.fieldName,
    columnWidth: 150,
    sortOrder: viewFields.value.length,
    visible: true,
    systemField: field.systemField ?? false,
    isPrimaryKey: meta?.isPrimaryKey ?? false,
    isForeignKey: meta?.isForeignKey ?? false,
    refTableId: meta?.refTableId ?? null,
  })
  addColumnPopoverVisible.value = false
}

// Multi-select: toggle a catalog field's selection, then add all selected at once.
function toggleCatalogSelect(fieldName: string) {
  const next = new Set(selectedCatalogFields.value)
  if (next.has(fieldName)) next.delete(fieldName)
  else next.add(fieldName)
  selectedCatalogFields.value = next
}

function addSelectedFields() {
  for (const field of filteredCatalog.value) {
    if (selectedCatalogFields.value.has(field.fieldName)) {
      addField(field)
    }
  }
  selectedCatalogFields.value = new Set()
}

// Remove every column from the view at once.
function clearAllFields() {
  viewFields.value = []
  sortConfig.value = []
  filterEditorRoot.value = parseFilterConfigToEditorRoot(null)
  filterConfig.value = serializeFilterEditorRoot(filterEditorRoot.value)
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
  } catch (e: unknown) {
    ElMessage.error((e instanceof Error ? e.message : undefined) || t('common.saveFailed'))
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
  return {
    t, Search, Close, Menu, DArrowRight, DArrowLeft, Plus, EditPen, Calendar, Document, Coin, SwitchIcon, Filter, CaretTop, CaretBottom, Connection, Key,
    columnsPanelOpen, propsPanelOpen, fieldSearchKeyword, saving, viewName, viewFields, sortConfig, filterConfig, filterEditorRoot,
    enableExport, enableImport, catalogFields, mainTableName, filterDialogVisible, addColumnPopoverVisible, thenSortField,
    dragColIndex, dragOverIndex, isDraggingFromPanel, dragSourceField, visibleColumns, displayFilterConditions,
    sortFieldOptions, filteredCatalog, previewRowCount, fieldLabel, getFieldIcon, getMockValue, sortIndicator,
    formatFilterTag, addField, removeField, toggleSortDirection, sortDirectionTooltip, onFilterEditorSave,
    removeDisplayFilterTag, addSortField, removeSort, handleSave, onFieldDragStart, onFieldDragEnd, onGridDrop,
    onColDragStart, onColDragOver, onColDragLeave, onColDrop, onColDragEnd, getFieldDataType,
    isFkField, isPkField, onFkColumnClick,
    selectedCatalogFields, toggleCatalogSelect, addSelectedFields, clearAllFields,
  }
}