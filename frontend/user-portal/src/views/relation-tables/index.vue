<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">Relation Tables</span>
    </div>

    <div class="data-layout">
      <!-- Left: Table list -->
      <div class="table-list-panel">
        <div class="panel-title">Available Tables</div>
        <div style="padding: 6px 8px;">
          <el-input
            v-model="tableSearchKeyword"
            placeholder="Search tables..."
            clearable
            size="small"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
        <el-menu
          :default-active="selectedTableId ? String(selectedTableId) : ''"
          @select="handleSelectTable"
          v-loading="tableListLoading"
        >
          <el-menu-item
            v-for="t in filteredTables"
            :key="t.id"
            :index="String(t.id)"
          >
            <span>{{ t.displayName || t.tableName }}</span>
          </el-menu-item>
        </el-menu>
        <el-empty v-if="!tableListLoading && filteredTables.length === 0" description="No tables available" :image-size="60" />
      </div>

      <!-- Right: Data grid -->
      <div class="data-grid-panel">
        <template v-if="selectedTable">
          <div class="grid-toolbar">
            <el-input
              v-model="searchKeyword"
              placeholder="Search..."
              clearable
              style="width: 240px; margin-right: 12px;"
              @keyup.enter="fetchData"
              @clear="fetchData"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" @click="handleExport" :loading="exporting">
              <el-icon><Download /></el-icon> Export CSV
            </el-button>
          </div>

          <el-table :data="dataRows" stripe v-loading="dataLoading" style="width: 100%;">
            <el-table-column
              v-for="col in columns"
              :key="col"
              :prop="col"
              :label="col"
              :min-width="isTimestampColumn(col) ? 180 : 120"
              sortable
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ isTimestampColumn(col) ? formatHKT(row[col]) : row[col] }}
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Search, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { relationTableApi, type RelationTableDTO } from '@/api/relationTable'

const tableListLoading = ref(false)
const dataLoading = ref(false)
const exporting = ref(false)
const tables = ref<RelationTableDTO[]>([])
const selectedTableId = ref<number | null>(null)

const searchKeyword = ref('')
const tableSearchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const totalElements = ref(0)
const dataRows = ref<Record<string, any>[]>([])
const columns = ref<string[]>([])

const selectedTable = computed(() =>
  tables.value.find(t => t.id === selectedTableId.value) ?? null
)

const filteredTables = computed(() => {
  const kw = tableSearchKeyword.value.trim().toLowerCase()
  if (!kw) return tables.value
  return tables.value.filter(t =>
    (t.displayName || '').toLowerCase().includes(kw) ||
    (t.tableName || '').toLowerCase().includes(kw)
  )
})

const fetchTables = async () => {
  tableListLoading.value = true
  try {
    const res = await relationTableApi.getVisibleTables()
    tables.value = res.data || []
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
    const params: Record<string, any> = {
      page: currentPage.value - 1,
      size: pageSize.value
    }
    if (searchKeyword.value) {
      params.search = searchKeyword.value
    }
    const res: any = await relationTableApi.queryTableData(selectedTableId.value, params)
    // Handle both wrapped (ApiResponse) and unwrapped response formats
    const pageData = res?.data ?? res
    dataRows.value = pageData?.content || []
    totalElements.value = pageData?.totalElements || 0

    if (dataRows.value.length > 0) {
      columns.value = Object.keys(dataRows.value[0])
    } else {
      columns.value = []
    }
  } catch (e) {
    console.error('Failed to load table data:', e)
    dataRows.value = []
  } finally {
    dataLoading.value = false
  }
}

const handleSelectTable = (index: string) => {
  selectedTableId.value = Number(index)
  searchKeyword.value = ''
  currentPage.value = 1
  columns.value = []
  fetchData()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchData()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchData()
}

const TIMESTAMP_COLUMNS = new Set(['created_at', 'updated_at'])

const isTimestampColumn = (col: string): boolean => TIMESTAMP_COLUMNS.has(col)

const formatHKT = (value: any): string => {
  if (value == null || value === '') return ''
  try {
    const date = new Date(value)
    if (isNaN(date.getTime())) return String(value)
    return date.toLocaleString('en-HK', { timeZone: 'Asia/Hong_Kong', hour12: false })
  } catch {
    return String(value)
  }
}

const handleExport = async () => {
  if (!selectedTableId.value) return
  exporting.value = true
  try {
    const blob = await relationTableApi.exportCsv(selectedTableId.value)
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
  } catch {
    ElMessage.error('Export failed')
  } finally {
    exporting.value = false
  }
}

onMounted(fetchTables)
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.page-header {
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
}

.data-layout {
  display: flex;
  gap: 16px;
  height: calc(100vh - 140px);
}

.table-list-panel {
  width: 220px;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  overflow-y: auto;
}

.table-list-panel :deep(.el-menu-item.is-active) {
  background-color: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}

.table-list-panel :deep(.el-menu-item.is-active)::before {
  display: none;
}

.panel-title {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 14px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.data-grid-panel {
  flex: 1;
  min-width: 0;
}

.grid-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
</style>
