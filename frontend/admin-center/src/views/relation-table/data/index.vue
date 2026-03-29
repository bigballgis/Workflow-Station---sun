<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">Table Data Management</span>
    </div>

    <div class="data-layout">
      <!-- Left: Table list -->
      <div class="table-list-panel">
        <div class="panel-title">Deployed Tables</div>
        <div style="padding: 6px 8px;">
          <el-input v-model="tableSearchKeyword" placeholder="Search tables..." clearable size="small">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
        <el-menu :default-active="selectedTableId ? String(selectedTableId) : ''" @select="handleSelectTable" v-loading="tableListLoading">
          <el-menu-item v-for="t in filteredTables" :key="t.id" :index="String(t.id)">
            <span>{{ t.displayName || t.tableName }}</span>
          </el-menu-item>
        </el-menu>
        <el-empty v-if="!tableListLoading && filteredTables.length === 0" description="No tables available" :image-size="60" />
      </div>

      <!-- Right: Data grid -->
      <div class="data-grid-panel">
        <template v-if="selectedTable">
          <div class="grid-toolbar">
            <el-input v-model="searchKeyword" placeholder="Search..." clearable style="width: 240px; margin-right: 12px;" @keyup.enter="fetchData" @clear="fetchData">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" @click="openAddDialog"><el-icon><Plus /></el-icon> Add</el-button>
            <el-button @click="handleExport" :loading="exporting"><el-icon><Download /></el-icon> Export CSV</el-button>
          </div>

          <el-table :data="dataRows" stripe v-loading="dataLoading" style="width: 100%;" border>
            <!-- Field columns from table structure -->
            <el-table-column v-for="field in fieldColumns" :key="field.fieldName" :prop="'data.' + field.fieldName" :label="field.comment || field.fieldName" :min-width="120" sortable show-overflow-tooltip>
              <template #default="{ row }">{{ row.data?.[field.fieldName] ?? '' }}</template>
            </el-table-column>
            <!-- System columns -->
            <el-table-column prop="data.created_at" label="Created At" width="170" show-overflow-tooltip>
              <template #default="{ row }">{{ formatHKT(row.data?.created_at) }}</template>
            </el-table-column>
            <el-table-column prop="data.created_by" label="Created By" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.data?.created_by ?? '' }}</template>
            </el-table-column>
            <el-table-column prop="data.updated_at" label="Updated At" width="170" show-overflow-tooltip>
              <template #default="{ row }">{{ formatHKT(row.data?.updated_at) }}</template>
            </el-table-column>
            <el-table-column prop="data.updated_by" label="Updated By" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.data?.updated_by ?? '' }}</template>
            </el-table-column>
            <el-table-column label="Status" width="100">
              <template #default="{ row }">
                <el-tag :type="isRowDisabled(row) ? 'danger' : 'success'" size="small">
                  {{ isRowDisabled(row) ? 'Inactive' : 'Active' }}
                </el-tag>
              </template>
            </el-table-column>
            <!-- Action column -->
            <el-table-column label="Actions" width="240" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditDialog(row)">Edit</el-button>
                <el-button v-if="isRowDisabled(row)" link type="success" size="small" @click="handleEnable(row)">Active</el-button>
                <el-button v-else link type="warning" size="small" @click="handleDisable(row)">Inactive</el-button>
                <el-button link type="danger" size="small" @click="handleDelete(row)">Delete</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-if="totalElements > 0"
            style="margin-top: 16px; justify-content: flex-end;"
            background
            layout="total, sizes, prev, pager, next"
            :total="totalElements"
            :page-size="pageSize"
            :current-page="currentPage"
            :page-sizes="[10, 20, 50, 100]"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </template>
        <el-empty v-else description="Select a table from the left panel" />
      </div>
    </div>

    <!-- Add/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'add' ? 'Add Record' : 'Edit Record'" width="600px" destroy-on-close>
      <el-form :model="formData" label-width="140px" label-position="left">
        <el-form-item v-for="field in fieldColumns" :key="field.fieldName" :label="field.comment || field.fieldName" :required="!field.nullable">
          <el-switch v-if="field.dataType === 'BOOLEAN'" v-model="formData[field.fieldName]" :disabled="dialogMode === 'edit' && field.isPrimaryKey" />
          <el-input-number v-else-if="isNumericType(field.dataType)" v-model="formData[field.fieldName]" :precision="field.dataType === 'DECIMAL' ? (field.scale || 2) : 0" style="width: 100%;" :disabled="dialogMode === 'edit' && field.isPrimaryKey" />
          <el-date-picker v-else-if="field.dataType === 'DATE'" v-model="formData[field.fieldName]" type="date" value-format="YYYY-MM-DD" style="width: 100%;" :disabled="dialogMode === 'edit' && field.isPrimaryKey" />
          <el-date-picker v-else-if="field.dataType === 'TIMESTAMP'" v-model="formData[field.fieldName]" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%;" :disabled="dialogMode === 'edit' && field.isPrimaryKey" />
          <el-input v-else-if="field.dataType === 'TEXT'" v-model="formData[field.fieldName]" type="textarea" :rows="3" :disabled="dialogMode === 'edit' && field.isPrimaryKey" />
          <el-input v-else v-model="formData[field.fieldName]" :maxlength="field.length || undefined" :disabled="dialogMode === 'edit' && field.isPrimaryKey" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="handleSaveRecord" :loading="saving">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { Search, Download, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  relationTableDataApi,
  type RelationTableResponse,
  type RelationTableDataRow,
  type FieldDefinitionResponse
} from '@/api/relationTable'

const tableListLoading = ref(false)
const dataLoading = ref(false)
const exporting = ref(false)
const saving = ref(false)
const tables = ref<RelationTableResponse[]>([])
const selectedTableId = ref<number | null>(null)

const searchKeyword = ref('')
const tableSearchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const totalElements = ref(0)
const dataRows = ref<RelationTableDataRow[]>([])

// Dialog state
const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const editingRowId = ref<string | null>(null)
const formData = ref<Record<string, any>>({})

const selectedTable = computed(() =>
  tables.value.find(t => t.id === selectedTableId.value) ?? null
)

const SYSTEM_COLUMNS = new Set(['created_at', 'created_by', 'updated_at', 'updated_by', 'status'])

/** Field columns from table structure, excluding system columns */
const fieldColumns = computed<FieldDefinitionResponse[]>(() => {
  if (!selectedTable.value?.fieldDefinitions) return []
  return selectedTable.value.fieldDefinitions
    .filter(f => !SYSTEM_COLUMNS.has(f.fieldName))
    .sort((a, b) => a.sortOrder - b.sortOrder)
})

const filteredTables = computed(() => {
  const kw = tableSearchKeyword.value.trim().toLowerCase()
  if (!kw) return tables.value
  return tables.value.filter(t =>
    (t.displayName || '').toLowerCase().includes(kw) ||
    (t.tableName || '').toLowerCase().includes(kw)
  )
})

const isNumericType = (dt: string) => ['INTEGER', 'BIGINT', 'DECIMAL'].includes(dt)

// Local status overrides for rows (when table has no status column)
const localStatusMap = ref<Record<string, string>>({})

const isRowDisabled = (row: RelationTableDataRow): boolean => {
  // Check local override first
  const local = localStatusMap.value[row.rowId]
  if (local) return local === 'INACTIVE'
  // Then check data
  const status = row.data?.status ?? (row as any).status ?? ''
  return String(status).toUpperCase() === 'DISABLED' || String(status).toUpperCase() === 'INACTIVE'
}

const fetchTables = async () => {
  tableListLoading.value = true
  try {
    const res: any = await relationTableDataApi.getDeployedTables()
    tables.value = res?.data ?? res ?? []
    if (!selectedTableId.value && tables.value.length > 0) {
      selectedTableId.value = tables.value[0].id
      fetchData()
    }
  } catch {
    tables.value = []
  } finally {
    tableListLoading.value = false
  }
}

const fetchData = async () => {
  if (!selectedTableId.value) return
  dataLoading.value = true
  try {
    const params: Record<string, any> = { page: currentPage.value - 1, size: pageSize.value }
    if (searchKeyword.value) params.search = searchKeyword.value
    const res: any = await relationTableDataApi.queryData(selectedTableId.value, params)
    const pageData = res?.data ?? res
    dataRows.value = pageData?.content || []
    totalElements.value = pageData?.totalElements || 0
  } catch {
    dataRows.value = []
  } finally {
    dataLoading.value = false
  }
}

const handleSelectTable = (index: string) => {
  selectedTableId.value = Number(index)
  searchKeyword.value = ''
  currentPage.value = 1
  localStatusMap.value = {}
  fetchData()
}

const handlePageChange = (page: number) => { currentPage.value = page; fetchData() }
const handleSizeChange = (size: number) => { pageSize.value = size; currentPage.value = 1; fetchData() }

// --- CRUD ---
const openAddDialog = () => {
  dialogMode.value = 'add'
  editingRowId.value = null
  formData.value = {}
  fieldColumns.value.forEach(f => { formData.value[f.fieldName] = f.defaultValue ?? (f.dataType === 'BOOLEAN' ? false : null) })
  dialogVisible.value = true
}

const openEditDialog = (row: RelationTableDataRow) => {
  dialogMode.value = 'edit'
  editingRowId.value = row.rowId
  formData.value = {}
  fieldColumns.value.forEach(f => { formData.value[f.fieldName] = row.data?.[f.fieldName] ?? null })
  dialogVisible.value = true
}

const handleSaveRecord = async () => {
  if (!selectedTableId.value) return
  saving.value = true
  try {
    // Filter out null/undefined values to avoid sending empty fields
    const cleanData: Record<string, any> = {}
    for (const [key, val] of Object.entries(formData.value)) {
      if (val !== null && val !== undefined && val !== '') {
        cleanData[key] = val
      }
    }
    if (dialogMode.value === 'add') {
      await relationTableDataApi.addData(selectedTableId.value, cleanData)
      ElMessage.success('Record added')
    } else if (editingRowId.value) {
      await relationTableDataApi.updateData(selectedTableId.value, editingRowId.value, cleanData)
      ElMessage.success('Record updated')
    }
    dialogVisible.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e?.message || 'Save failed')
  } finally {
    saving.value = false
  }
}

const handleDisable = async (row: RelationTableDataRow) => {
  if (!selectedTableId.value) return
  try {
    await relationTableDataApi.changeStatus(selectedTableId.value, row.rowId, 'INACTIVE')
    localStatusMap.value = { ...localStatusMap.value, [row.rowId]: 'INACTIVE' }
    ElMessage.success('Record set to inactive')
    fetchData()
  } catch (e: any) { ElMessage.error(e?.message || 'Failed') }
}

const handleEnable = async (row: RelationTableDataRow) => {
  if (!selectedTableId.value) return
  try {
    await relationTableDataApi.changeStatus(selectedTableId.value, row.rowId, 'ACTIVE')
    localStatusMap.value = { ...localStatusMap.value, [row.rowId]: 'ACTIVE' }
    ElMessage.success('Record set to active')
    fetchData()
  } catch (e: any) { ElMessage.error(e?.message || 'Failed') }
}

const handleDelete = async (row: RelationTableDataRow) => {
  if (!selectedTableId.value) return
  await ElMessageBox.confirm('Are you sure to delete this record?', 'Confirm', { type: 'warning' })
  try {
    await relationTableDataApi.deleteData(selectedTableId.value, row.rowId)
    ElMessage.success('Record deleted')
    fetchData()
  } catch (e: any) { ElMessage.error(e?.message || 'Delete failed') }
}

const formatHKT = (value: any): string => {
  if (value == null || value === '') return ''
  try {
    const date = new Date(value)
    if (isNaN(date.getTime())) return String(value)
    return date.toLocaleString('en-HK', { timeZone: 'Asia/Hong_Kong', hour12: false })
  } catch { return String(value) }
}

const handleExport = async () => {
  if (!selectedTableId.value) return
  exporting.value = true
  try {
    const blob = await relationTableDataApi.exportCsv(selectedTableId.value)
    const url = window.URL.createObjectURL(new Blob([blob as any]))
    const link = document.createElement('a')
    link.href = url
    const name = selectedTable.value?.displayName || selectedTable.value?.tableName || 'data'
    link.setAttribute('download', `${name}.csv`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    ElMessage.success('Export completed')
  } catch { ElMessage.error('Export failed') }
  finally { exporting.value = false }
}

onMounted(fetchTables)
// Reload when navigating back to this page (e.g. after deploying in Table Structure)
onActivated(() => {
  fetchTables()
  if (selectedTableId.value) {
    fetchData()
  }
})
</script>

<style scoped>
.page-container { padding: 20px; }
.page-header { margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 600; color: #303133; }
.data-layout { display: flex; gap: 16px; height: calc(100vh - 140px); }
.table-list-panel {
  width: 220px; flex-shrink: 0;
  border: 1px solid var(--el-border-color-light); border-radius: 4px; overflow-y: auto;
}
.table-list-panel :deep(.el-menu-item.is-active) {
  background-color: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}
.table-list-panel :deep(.el-menu-item.is-active)::before { display: none; }
.panel-title { padding: 12px 16px; font-weight: 600; font-size: 14px; border-bottom: 1px solid var(--el-border-color-light); }
.data-grid-panel { flex: 1; min-width: 0; overflow: auto; }
.grid-toolbar { display: flex; align-items: center; margin-bottom: 12px; gap: 8px; }
</style>
