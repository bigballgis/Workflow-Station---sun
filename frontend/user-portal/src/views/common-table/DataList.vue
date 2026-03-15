<template>
  <div class="common-table-page">
    <!-- Table Selector -->
    <div class="page-header">
      <h2>Common Table</h2>
      <el-select
        v-model="selectedTableCode"
        placeholder="Select Table"
        style="width: 220px;"
        @change="handleTableChange"
      >
        <el-option
          v-for="tbl in tables"
          :key="tbl.code"
          :label="tbl.name"
          :value="tbl.code"
        />
      </el-select>
    </div>

    <div v-if="selectedTableCode" class="card" v-loading="loading">
      <!-- Toolbar -->
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="searchKeyword"
            placeholder="Search..."
            clearable
            style="width: 220px;"
            @keyup.enter="loadData"
            @clear="loadData"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button @click="loadData">Search</el-button>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon> Add
          </el-button>
          <el-button @click="handleExport">
            <el-icon><Download /></el-icon> Export CSV
          </el-button>
        </div>
      </div>

      <!-- Data table: no wrap, horizontal scroll inside table, Actions fixed right -->
      <el-table :data="rows" stripe border class="data-table" style="width: 100%;">
          <el-table-column type="index" label="#" width="60" />
          <el-table-column
            v-for="field in fields"
            :key="field.fieldName"
            :label="field.displayName || field.fieldName"
            :prop="`dataJson.${field.fieldName}`"
            :min-width="Math.max(180, ((field.displayName || field.fieldName).length + 2) * 10)"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.dataJson?.[field.fieldName] ?? '' }}
            </template>
          </el-table-column>
          <el-table-column label="Created At" width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ row.createdAt || '' }}</template>
          </el-table-column>
          <el-table-column label="Created By" width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.createdBy || '' }}</template>
          </el-table-column>
          <el-table-column label="Updated At" width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ row.updatedAt || '' }}</template>
          </el-table-column>
          <el-table-column label="Updated By" width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.updatedBy || '' }}</template>
          </el-table-column>
          <el-table-column label="Actions" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditDialog(row)">Edit</el-button>
              <el-button link type="danger" @click="handleDelete(row)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </div>

    <el-empty v-else description="Please select a table" style="margin-top: 80px;" />

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="showDialog"
      :title="editingRow ? 'Edit Record' : 'Add Record'"
      width="660px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form
        :model="formData"
        :rules="formRules"
        ref="dataFormRef"
        label-position="left"
        label-width="180px"
        class="data-record-form"
      >
        <el-form-item
          v-for="field in fields"
          :key="field.fieldName"
          :label="field.displayName || field.fieldName"
          :prop="field.fieldName"
          :required="isRequired(field)"
          class="data-form-item"
        >
          <!-- VARCHAR / TEXT -->
          <el-input
            v-if="field.dataType === 'VARCHAR'"
            v-model="formData[field.fieldName]"
            :placeholder="`Enter ${field.displayName || field.fieldName}`"
            clearable
          />
          <el-input
            v-else-if="field.dataType === 'TEXT'"
            v-model="formData[field.fieldName]"
            type="textarea"
            :rows="3"
            :placeholder="`Enter ${field.displayName || field.fieldName}`"
          />
          <!-- Numeric -->
          <el-input-number
            v-else-if="['INTEGER', 'BIGINT', 'DECIMAL'].includes(field.dataType)"
            v-model="formData[field.fieldName]"
            :placeholder="`Enter ${field.displayName || field.fieldName}`"
            style="width: 100%;"
            controls-position="right"
          />
          <!-- Boolean -->
          <el-switch
            v-else-if="field.dataType === 'BOOLEAN'"
            v-model="formData[field.fieldName]"
          />
          <!-- Date -->
          <el-date-picker
            v-else-if="field.dataType === 'DATE'"
            v-model="formData[field.fieldName]"
            type="date"
            style="width: 100%;"
            value-format="YYYY-MM-DD"
            :placeholder="`Select date`"
          />
          <!-- Timestamp -->
          <el-date-picker
            v-else-if="field.dataType === 'TIMESTAMP'"
            v-model="formData[field.fieldName]"
            type="datetime"
            style="width: 100%;"
            value-format="YYYY-MM-DD HH:mm:ss"
            :placeholder="`Select date and time`"
          />
          <!-- Fallback -->
          <el-input
            v-else
            v-model="formData[field.fieldName]"
            :placeholder="`Enter ${field.displayName || field.fieldName}`"
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showDialog = false">Cancel</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">Save</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Download } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { commonTableApi, type CommonTableDef, type CommonFieldDef, type CommonTableDataRow } from '@/api/commonTable'

const { t } = useI18n()

const tables = ref<CommonTableDef[]>([])
const selectedTableCode = ref('')
const fields = ref<CommonFieldDef[]>([])
const rows = ref<CommonTableDataRow[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const searchKeyword = ref('')

const showDialog = ref(false)
const submitting = ref(false)
const editingRow = ref<CommonTableDataRow | null>(null)
const formData = reactive<Record<string, any>>({})
const dataFormRef = ref()

function isRequired(field: CommonFieldDef): boolean {
  return field.isPrimaryKey === true || field.nullable === false
}

const formRules = computed(() => {
  const rules: Record<string, any[]> = {}
  fields.value.forEach(field => {
    if (isRequired(field)) {
      rules[field.fieldName] = [
        {
          required: true,
          message: `${field.displayName || field.fieldName} is required`,
          trigger: ['blur', 'change']
        }
      ]
    }
  })
  return rules
})

async function loadTables() {
  try {
    const res = await commonTableApi.listTables()
    tables.value = (res as any).data || res || []
    if (tables.value.length > 0 && !selectedTableCode.value) {
      selectedTableCode.value = tables.value[0].code
      await loadData()
    }
  } catch (e) {
    ElMessage.error('Failed to load tables')
  }
}

async function handleTableChange(code: string) {
  selectedTableCode.value = code
  currentPage.value = 1
  await loadData()
}

async function loadData() {
  if (!selectedTableCode.value) return
  loading.value = true
  try {
    const res = await commonTableApi.listData(selectedTableCode.value, currentPage.value - 1, pageSize.value)
    const data = (res as any).data || res
    rows.value = data.content || []
    total.value = data.totalElements || 0
    fields.value = data.fields || []
  } catch (e) {
    ElMessage.error('Failed to load data')
  } finally {
    loading.value = false
  }
}

async function openCreateDialog() {
  editingRow.value = null
  // 若尚未加载字段，先获取表定义
  if (fields.value.length === 0 && selectedTableCode.value) {
    try {
      const res = await commonTableApi.getTable(selectedTableCode.value)
      const tbl = (res as any).data || res
      fields.value = tbl.fieldDefinitions || []
    } catch (e) {
      ElMessage.error('Failed to load field definitions')
      return
    }
  }
  Object.keys(formData).forEach(k => delete formData[k])
  fields.value.forEach(f => { formData[f.fieldName] = '' })
  showDialog.value = true
}

function openEditDialog(row: CommonTableDataRow) {
  editingRow.value = row
  Object.keys(formData).forEach(k => delete formData[k])
  Object.assign(formData, { ...row.dataJson })
  showDialog.value = true
}

async function handleSubmit() {
  try {
    await dataFormRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload = { ...formData }
    if (editingRow.value) {
      await commonTableApi.update(selectedTableCode.value, editingRow.value.id, payload)
      ElMessage.success('Updated successfully')
    } else {
      await commonTableApi.create(selectedTableCode.value, payload)
      ElMessage.success('Created successfully')
    }
    showDialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error('Operation failed')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: CommonTableDataRow) {
  try {
    await ElMessageBox.confirm('Are you sure to delete this record?', 'Confirm', { type: 'warning' })
    await commonTableApi.delete(selectedTableCode.value, row.id)
    ElMessage.success('Deleted successfully')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('Delete failed')
  }
}

function handleExport() {
  if (!selectedTableCode.value) return
  const token = localStorage.getItem('token')
  const url = commonTableApi.getExportUrl(selectedTableCode.value)
  const a = document.createElement('a')
  a.href = url + (token ? `?token=${token}` : '')
  // Trigger download via fetch to pass token in headers
  fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
    .then(r => r.blob())
    .then(blob => {
      const blobUrl = URL.createObjectURL(blob)
      a.href = blobUrl
      a.download = `${selectedTableCode.value}_export.csv`
      a.click()
      URL.revokeObjectURL(blobUrl)
    })
    .catch(() => ElMessage.error('Export failed'))
}

function formatDate(dateStr?: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

onMounted(loadTables)
</script>

<style lang="scss" scoped>
.common-table-page {
  padding: 20px;
  width: 100%;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: nowrap;

  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    white-space: nowrap;
  }
}
.card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  width: 100%;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: nowrap;
}
.toolbar-left, .toolbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: nowrap;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.data-table {
  width: 100% !important;
}
:deep(.data-table .el-table__header th .cell),
:deep(.data-table .el-table__body td .cell) {
  white-space: nowrap !important;
  overflow: hidden;
  text-overflow: ellipsis;
}
:deep(.el-form-item__label) {
  white-space: nowrap;
}

/* Add / Edit Record Form — vertical one-field-per-row layout */
.data-record-form {
  padding: 4px 8px;
}
.data-form-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 20px;
  flex-wrap: nowrap;

  :deep(.el-form-item__label) {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    flex-shrink: 0;
    font-weight: 500;
    color: #303133;
    line-height: 32px;
  }

  :deep(.el-form-item__content) {
    flex: 1;
    min-width: 0;

    .el-input,
    .el-input-number,
    .el-date-editor,
    .el-textarea {
      width: 100% !important;
    }
  }

  :deep(.el-form-item__error) {
    white-space: nowrap;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
