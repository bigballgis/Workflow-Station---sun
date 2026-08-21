import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  Search, Close, Menu, DArrowRight, DArrowLeft, Plus, EditPen, Calendar, Document, Coin,
  Switch as SwitchIcon, Filter, CaretTop, CaretBottom, Connection, Key,
} from '@element-plus/icons-vue'
import {
  mainTableViewApi, SYSTEM_VIEW_FIELDS, lookupDisplayFieldName,
  type MainTableViewDefinition, type MainTableViewField, type MainTableFieldCatalogItem,
  type MainTableLookupCatalogGroup, type FilterCondition, type FilterConfig,
} from '@/api/mainTableView'
import {
  flattenFilterConditions, parseFilterConfigToEditorRoot, removeFieldFromFilterTree,
  removeFlattenedConditionAt, serializeFilterEditorRoot,
} from '@/utils/mainTableViewFilter'
import {
  buildLookupCatalogGroups, flattenLookupCatalogItems,
} from '@/utils/mainTableViewLookupCatalog'
import {
  buildFkCatalogGroups, flattenFkCatalogItems,
} from '@/utils/mainTableViewFkCatalog'
import { functionUnitApi, type TableDefinition } from '@/api/functionUnit'
import { resolveFormTableId } from '@/utils/formDesigner'
import { relationTableBindingApi } from '@/api/relationTable'
import { adminCenterApi, type BusinessUnitInfo, type RoleInfo } from '@/api/adminCenter'

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
/** DETAIL form opened when a portal user clicks a row of this view; null = not clickable. */
const detailFormId = ref<number | null>(null)
/** Only DETAIL forms are offered — task and process forms belong to workflow steps. */
const detailFormOptions = ref<Array<{ id: number; formName: string }>>([])
const enableImport = ref(true)
const restrictToInvolvedUsers = ref(false)
const selectedBusinessUnitIds = ref<string[]>([])
const selectedRoleIds = ref<string[]>([])
const businessUnitOptions = ref<BusinessUnitInfo[]>([])
const roleOptions = ref<RoleInfo[]>([])
const accessOptionsLoading = ref(false)
const catalogFields = ref<MainTableFieldCatalogItem[]>([])
const lookupCatalogGroups = ref<MainTableLookupCatalogGroup[]>([])
const lookupCatalogFields = ref<MainTableFieldCatalogItem[]>([])
const fkCatalogGroups = ref<MainTableLookupCatalogGroup[]>([])
const fkCatalogFields = ref<MainTableFieldCatalogItem[]>([])
const fieldMetaMap = ref<Record<string, { isPrimaryKey: boolean; isForeignKey: boolean; refTableId: number | null }>>({})
const selectedCatalogFields = ref<Set<string>>(new Set())
const selectedLookupCatalogFields = ref<Set<string>>(new Set())
const selectedFkCatalogFields = ref<Set<string>>(new Set())
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
    .filter(f => f.visible !== false
      && f.columnType !== 'lookup_display'
      && f.columnType !== 'fk_display')
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
    restrictToInvolvedUsers.value = v.restrictToInvolvedUsers === true
    detailFormId.value = v.detailFormId ?? null
    selectedBusinessUnitIds.value = (v.accessRules || [])
      .filter(r => r.targetType === 'BUSINESS_UNIT')
      .map(r => r.targetId)
    selectedRoleIds.value = (v.accessRules || [])
      .filter(r => r.targetType === 'ROLE')
      .map(r => r.targetId)
    void refreshRoleOptionsForSelectedBus()
  },
  { immediate: true, deep: true },
)

async function refreshRoleOptionsForSelectedBus() {
  const buIds = selectedBusinessUnitIds.value.filter(Boolean)
  if (buIds.length === 0) {
    roleOptions.value = []
    selectedRoleIds.value = []
    return
  }
  try {
    const lists = await Promise.all(buIds.map(id => adminCenterApi.getBusinessUnitEligibleRoles(id)))
    const byId = new Map<string, RoleInfo>()
    for (const list of lists) {
      for (const role of list || []) {
        if (role?.id) byId.set(role.id, role)
      }
    }
    roleOptions.value = Array.from(byId.values())
    const allowed = new Set(byId.keys())
    selectedRoleIds.value = selectedRoleIds.value.filter(id => allowed.has(id))
  } catch {
    roleOptions.value = []
  }
}

watch(
  selectedBusinessUnitIds,
  () => { void refreshRoleOptionsForSelectedBus() },
  { deep: true },
)

async function loadAccessOptions() {
  accessOptionsLoading.value = true
  try {
    businessUnitOptions.value = (await adminCenterApi.getBusinessUnits()) || []
    await refreshRoleOptionsForSelectedBus()
  } catch {
    businessUnitOptions.value = []
    roleOptions.value = []
  } finally {
    accessOptionsLoading.value = false
  }
}

async function loadCatalog() {
  try {
    const [tablesRes, formsRes, rtRes] = await Promise.all([
      functionUnitApi.getTables(props.functionUnitId),
      functionUnitApi.getForms(props.functionUnitId),
      relationTableBindingApi.getAvailableTables().catch(() => ({ data: [] })),
    ])
    const tables: TableDefinition[] = tablesRes.data || []
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

    const relationTables = (rtRes as { data?: unknown })?.data
      || (Array.isArray(rtRes) ? rtRes : [])
    const allForms = formsRes.data || []
    // One bindings fetch per form, shared by the lookup catalog and the detail-form picker below.
    // Fetching twice would double the request count for no new information.
    const formsForTable: typeof allForms = []
    const detailFormsForTable: typeof allForms = []
    await Promise.all(allForms.map(async (form) => {
      if (form.id == null) return
      let servesThisTable: boolean
      try {
        const bindRes = await functionUnitApi.getFormBindings(props.functionUnitId, form.id)
        const binds = bindRes.data || []
        servesThisTable = binds.some(b => b.tableId === props.view.mainTableId)
          // Older forms carry only the legacy single-table column, or a lone unmarked binding.
          || resolveFormTableId({ tableBindings: binds, boundTableId: form.boundTableId })
            === props.view.mainTableId
      } catch {
        // FALLBACK(ux): if bindings fail to load, still scan the form for lookup widgets
        servesThisTable = true
      }
      if (!servesThisTable) return
      formsForTable.push(form)
      if (form.formType === 'DETAIL') detailFormsForTable.push(form)
    }))
    // Only forms bound to THIS view's table can render its rows: the portal detail page maps row
    // values onto form fields by name and never checks the table, so a mismatched pick renders a
    // blank page instead of failing. The currently saved form is always kept so opening the panel
    // cannot silently drop a pre-existing cross-table selection.
    const detailOptions = detailFormsForTable
      .map(f => ({ id: f.id as number, formName: f.formName }))
    const savedDetailFormId = detailFormId.value
    if (savedDetailFormId != null && !detailOptions.some(o => o.id === savedDetailFormId)) {
      const saved = allForms.find(f => f.id === savedDetailFormId)
      if (saved) detailOptions.unshift({ id: saved.id as number, formName: saved.formName })
    }
    detailFormOptions.value = detailOptions
    const groups = buildLookupCatalogGroups(formsForTable, relationTables as never[])
    lookupCatalogGroups.value = groups
    lookupCatalogFields.value = flattenLookupCatalogItems(groups)
    const fkGroups = buildFkCatalogGroups(table, tables)
    fkCatalogGroups.value = fkGroups
    fkCatalogFields.value = flattenFkCatalogItems(fkGroups)
  } catch {
    catalogFields.value = []
    lookupCatalogGroups.value = []
    lookupCatalogFields.value = []
    fkCatalogGroups.value = []
    fkCatalogFields.value = []
    fieldMetaMap.value = {}
  }
}

// Load the catalog for the current view's table, and reload when switching to a different table.
watch(() => props.view?.mainTableId, () => { loadCatalog() }, { immediate: true })

loadAccessOptions()

function validateAccessControlSelection(): boolean {
  const hasBu = selectedBusinessUnitIds.value.length > 0
  const hasRole = selectedRoleIds.value.length > 0
  if (hasBu === hasRole) return true
  ElMessage.warning(t('mainTableView.accessControlBuRoleRequired'))
  return false
}

function buildAccessRulesPayload() {
  const rules: Array<{ targetType: string; targetId: string }> = []
  for (const id of selectedBusinessUnitIds.value) {
    if (id) rules.push({ targetType: 'BUSINESS_UNIT', targetId: id })
  }
  for (const id of selectedRoleIds.value) {
    if (id) rules.push({ targetType: 'ROLE', targetId: id })
  }
  return rules
}

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

const filteredLookupCatalog = computed(() => {
  const inView = new Set(viewFields.value.map(f => f.fieldName))
  const kw = fieldSearchKeyword.value.trim().toLowerCase()
  return lookupCatalogFields.value.filter(f => {
    if (inView.has(f.fieldName)) return false
    if (!kw) return true
    return f.fieldName.toLowerCase().includes(kw)
      || (f.displayName || '').toLowerCase().includes(kw)
      || (f.lookupSourceField || '').toLowerCase().includes(kw)
      || (f.lookupDisplayField || '').toLowerCase().includes(kw)
  })
})

const filteredLookupCatalogGroups = computed(() => {
  const available = new Set(filteredLookupCatalog.value.map(f => f.fieldName))
  return lookupCatalogGroups.value
    .map(g => ({
      ...g,
      fields: flattenLookupCatalogItems([g]).filter(f => available.has(f.fieldName)),
    }))
    .filter(g => g.fields.length > 0)
})

const filteredFkCatalog = computed(() => {
  const inView = new Set(viewFields.value.map(f => f.fieldName))
  const kw = fieldSearchKeyword.value.trim().toLowerCase()
  return fkCatalogFields.value.filter(f => {
    if (inView.has(f.fieldName)) return false
    if (!kw) return true
    return f.fieldName.toLowerCase().includes(kw)
      || (f.displayName || '').toLowerCase().includes(kw)
      || (f.lookupSourceField || '').toLowerCase().includes(kw)
      || (f.lookupDisplayField || '').toLowerCase().includes(kw)
  })
})

const filteredFkCatalogGroups = computed(() => {
  const available = new Set(filteredFkCatalog.value.map(f => f.fieldName))
  return fkCatalogGroups.value
    .map(g => ({
      ...g,
      fields: flattenFkCatalogItems([g]).filter(f => available.has(f.fieldName)),
    }))
    .filter(g => g.fields.length > 0)
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
  if (field.columnType === 'lookup_display' || field.columnType === 'fk_display') {
    const attr = field.lookupDisplayField || 'value'
    if (attr.includes('name') || attr.includes('Name') || attr.includes('number')) {
      return rowIndex === 0 ? (attr.includes('number') ? 'CASE-001' : 'Alice Chen') : (attr.includes('number') ? 'CASE-002' : 'Bob Lee')
    }
    if (attr.includes('email')) return rowIndex === 0 ? 'alice@example.com' : 'bob@example.com'
    if (attr.includes('hold') || attr.includes('Hold')) return rowIndex === 0 ? 'Yes' : 'No'
    return `Sample ${attr} ${rowIndex + 1}`
  }
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
  if ((field.columnType === 'lookup_display' || field.columnType === 'fk_display')
      && field.lookupSourceField && field.lookupDisplayField) {
    const synthetic = lookupDisplayFieldName(field.lookupSourceField, field.lookupDisplayField)
    const sourceMeta = field.columnType === 'fk_display'
      ? fieldMetaMap.value[field.lookupSourceField]
      : undefined
    viewFields.value.push({
      fieldName: synthetic,
      displayLabel: field.displayName || `${field.lookupSourceField}.${field.lookupDisplayField}`,
      columnWidth: 150,
      sortOrder: viewFields.value.length,
      visible: true,
      systemField: false,
      columnType: field.columnType,
      lookupSourceField: field.lookupSourceField,
      lookupDisplayField: field.lookupDisplayField,
      refTableId: sourceMeta?.refTableId ?? field.lookupTableId ?? null,
    })
    addColumnPopoverVisible.value = false
    return
  }
  const meta = fieldMetaMap.value[field.fieldName]
  viewFields.value.push({
    fieldName: field.fieldName,
    displayLabel: field.displayName || field.fieldName,
    columnWidth: 150,
    sortOrder: viewFields.value.length,
    visible: true,
    systemField: field.systemField ?? false,
    columnType: 'field',
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

// Select-all over the currently-filtered catalog (respects the search box).
const allCatalogSelected = computed(() =>
  filteredCatalog.value.length > 0
  && filteredCatalog.value.every(f => selectedCatalogFields.value.has(f.fieldName)),
)
const someCatalogSelected = computed(() =>
  filteredCatalog.value.some(f => selectedCatalogFields.value.has(f.fieldName)),
)
function toggleSelectAllCatalog(checked: boolean) {
  const next = new Set(selectedCatalogFields.value)
  for (const f of filteredCatalog.value) {
    if (checked) next.add(f.fieldName)
    else next.delete(f.fieldName)
  }
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

function toggleLookupCatalogSelect(fieldName: string) {
  const next = new Set(selectedLookupCatalogFields.value)
  if (next.has(fieldName)) next.delete(fieldName)
  else next.add(fieldName)
  selectedLookupCatalogFields.value = next
}

const allLookupCatalogSelected = computed(() =>
  filteredLookupCatalog.value.length > 0
  && filteredLookupCatalog.value.every(f => selectedLookupCatalogFields.value.has(f.fieldName)),
)
const someLookupCatalogSelected = computed(() =>
  filteredLookupCatalog.value.some(f => selectedLookupCatalogFields.value.has(f.fieldName)),
)
function toggleSelectAllLookupCatalog(checked: boolean) {
  const next = new Set(selectedLookupCatalogFields.value)
  for (const f of filteredLookupCatalog.value) {
    if (checked) next.add(f.fieldName)
    else next.delete(f.fieldName)
  }
  selectedLookupCatalogFields.value = next
}

function addSelectedLookupFields() {
  for (const field of filteredLookupCatalog.value) {
    if (selectedLookupCatalogFields.value.has(field.fieldName)) {
      addField(field)
    }
  }
  selectedLookupCatalogFields.value = new Set()
}

function toggleFkCatalogSelect(fieldName: string) {
  const next = new Set(selectedFkCatalogFields.value)
  if (next.has(fieldName)) next.delete(fieldName)
  else next.add(fieldName)
  selectedFkCatalogFields.value = next
}

const allFkCatalogSelected = computed(() =>
  filteredFkCatalog.value.length > 0
  && filteredFkCatalog.value.every(f => selectedFkCatalogFields.value.has(f.fieldName)),
)
const someFkCatalogSelected = computed(() =>
  filteredFkCatalog.value.some(f => selectedFkCatalogFields.value.has(f.fieldName)),
)
function toggleSelectAllFkCatalog(checked: boolean) {
  const next = new Set(selectedFkCatalogFields.value)
  for (const f of filteredFkCatalog.value) {
    if (checked) next.add(f.fieldName)
    else next.delete(f.fieldName)
  }
  selectedFkCatalogFields.value = next
}

function addSelectedFkFields() {
  for (const field of filteredFkCatalog.value) {
    if (selectedFkCatalogFields.value.has(field.fieldName)) {
      addField(field)
    }
  }
  selectedFkCatalogFields.value = new Set()
}

function isLookupDisplayField(field: MainTableViewField): boolean {
  return field.columnType === 'lookup_display'
}

function isFkDisplayField(field: MainTableViewField): boolean {
  return field.columnType === 'fk_display'
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
  if (!validateAccessControlSelection()) return
  saving.value = true
  try {
    const fields = viewFields.value.map((f, i) => ({ ...f, sortOrder: i }))
    const res = await mainTableViewApi.update(props.functionUnitId, props.view.id, {
      viewName: viewName.value.trim() || props.view.viewName,
      restrictToInvolvedUsers: restrictToInvolvedUsers.value,
      detailFormId: detailFormId.value,
      accessRules: buildAccessRulesPayload(),
      sortConfig: sortConfig.value,
      filterConfig: {
        ...filterConfig.value,
        toolbar: {
          enableExport: enableExport.value,
          // Import is no longer offered in views — always disabled.
          enableImport: false,
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
    enableExport, enableImport, restrictToInvolvedUsers, detailFormId, detailFormOptions, selectedBusinessUnitIds, selectedRoleIds,
    businessUnitOptions, roleOptions, accessOptionsLoading,
    catalogFields, lookupCatalogGroups, fkCatalogGroups, mainTableName, filterDialogVisible, addColumnPopoverVisible, thenSortField,
    dragColIndex, dragOverIndex, isDraggingFromPanel, dragSourceField, visibleColumns, displayFilterConditions,
    sortFieldOptions, filteredCatalog, filteredLookupCatalog, filteredLookupCatalogGroups,
    filteredFkCatalog, filteredFkCatalogGroups, previewRowCount,
    fieldLabel, getFieldIcon, getMockValue, sortIndicator,
    formatFilterTag, addField, removeField, toggleSortDirection, sortDirectionTooltip, onFilterEditorSave,
    removeDisplayFilterTag, addSortField, removeSort, handleSave, onFieldDragStart, onFieldDragEnd, onGridDrop,
    onColDragStart, onColDragOver, onColDragLeave, onColDrop, onColDragEnd, getFieldDataType,
    isFkField, isPkField, onFkColumnClick, isLookupDisplayField, isFkDisplayField,
    selectedCatalogFields, toggleCatalogSelect, addSelectedFields, clearAllFields,
    allCatalogSelected, someCatalogSelected, toggleSelectAllCatalog,
    selectedLookupCatalogFields, toggleLookupCatalogSelect, addSelectedLookupFields,
    allLookupCatalogSelected, someLookupCatalogSelected, toggleSelectAllLookupCatalog,
    selectedFkCatalogFields, toggleFkCatalogSelect, addSelectedFkFields,
    allFkCatalogSelected, someFkCatalogSelected, toggleSelectAllFkCatalog,
  }
}