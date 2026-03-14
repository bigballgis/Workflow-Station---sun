<template>
  <div class="common-table-page">
    <!-- Table Selector -->
    <div class="page-header">
      <h2>{{ t('menu.commonTable') }}</h2>
      <el-select
        v-model="selectedTableCode"
        :placeholder="'选择数据表'"
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
            placeholder="搜索..."
            clearable
            style="width: 220px;"
            @keyup.enter="loadData"
            @clear="loadData"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button @click="loadData">搜索</el-button>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
          <el-button @click="handleExport">
            <el-icon><Download /></el-icon> 导出 CSV
          </el-button>
        </div>
      </div>

      <!-- Data table with dynamic columns -->
      <el-table :data="rows" stripe border style="width: 100%;">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column
          v-for="field in fields"
          :key="field.fieldName"
          :label="field.displayName || field.fieldName"
          :prop="`dataJson.${field.fieldName}`"
          min-width="120"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.dataJson?.[field.fieldName] ?? '' }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <el-empty v-else description="请选择一个数据表" style="margin-top: 80px;" />

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="showDialog"
      :title="editingRow ? '编辑记录' : '新增记录'"
      width="600px"
      destroy-on-close
    >
      <el-form :model="formData" label-width="120px" label-position="left">
        <el-form-item
          v-for="field in fields"
          :key="field.fieldName"
          :label="field.displayName || field.fieldName"
        >
          <el-input
            v-if="['VARCHAR', 'TEXT'].includes(field.dataType)"
            v-model="formData[field.fieldName]"
            :type="field.dataType === 'TEXT' ? 'textarea' : 'text'"
            :rows="3"
          />
          <el-input-number
            v-else-if="['INTEGER', 'BIGINT', 'DECIMAL'].includes(field.dataType)"
            v-model="formData[field.fieldName]"
            style="width: 100%;"
          />
          <el-switch
            v-else-if="field.dataType === 'BOOLEAN'"
            v-model="formData[field.fieldName]"
          />
          <el-date-picker
            v-else-if="field.dataType === 'DATE'"
            v-model="formData[field.fieldName]"
            type="date"
            style="width: 100%;"
            value-format="YYYY-MM-DD"
          />
          <el-date-picker
            v-else-if="field.dataType === 'TIMESTAMP'"
            v-model="formData[field.fieldName]"
            type="datetime"
            style="width: 100%;"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
          <el-input v-else v-model="formData[field.fieldName]" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
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

async function loadTables() {
  try {
    const res = await commonTableApi.listTables()
    tables.value = (res as any).data || res || []
  } catch (e) {
    ElMessage.error('加载公共表失败')
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
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingRow.value = null
  // Reset formData
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
  submitting.value = true
  try {
    const payload = { ...formData }
    if (editingRow.value) {
      await commonTableApi.update(selectedTableCode.value, editingRow.value.id, payload)
      ElMessage.success('更新成功')
    } else {
      await commonTableApi.create(selectedTableCode.value, payload)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: CommonTableDataRow) {
  try {
    await ElMessageBox.confirm('确定要删除这条记录吗？', '提示', { type: 'warning' })
    await commonTableApi.delete(selectedTableCode.value, row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败')
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
    .catch(() => ElMessage.error('导出失败'))
}

function formatDate(dateStr?: string) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(loadTables)
</script>

<style lang="scss" scoped>
.common-table-page {
  padding: 20px;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;

  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
  }
}
.card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.toolbar-left, .toolbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
