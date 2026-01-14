<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.audit') }}</span>
      <el-button @click="handleExport">
        <el-icon><Download /></el-icon>{{ t('common.export') }}
      </el-button>
    </div>
    
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item :label="t('audit.actionType')">
        <el-select v-model="query.action" clearable :placeholder="t('common.selectPlaceholder')">
          <el-option :label="t('audit.login')" value="LOGIN" />
          <el-option :label="t('audit.logout')" value="LOGOUT" />
          <el-option :label="t('audit.create')" value="CREATE" />
          <el-option :label="t('audit.update')" value="UPDATE" />
          <el-option :label="t('audit.delete')" value="DELETE" />
          <el-option :label="t('audit.permissionChange')" value="PERMISSION_CHANGE" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('audit.operator')">
        <el-input v-model="query.username" clearable :placeholder="t('audit.usernamePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('audit.result')">
        <el-select v-model="query.result" clearable :placeholder="t('common.selectPlaceholder')">
          <el-option :label="t('audit.success')" value="SUCCESS" />
          <el-option :label="t('audit.failed')" value="FAILED" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('audit.dateRange')">
        <el-date-picker v-model="dateRange" type="daterange" :start-placeholder="t('common.startDate')" :end-placeholder="t('common.endDate')" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>
    
    <el-table :data="logs" v-loading="loading" stripe>
      <el-table-column prop="action" :label="t('audit.actionType')" width="120">
        <template #default="{ row }">
          <el-tag :type="actionType(row.action)" size="small">{{ actionText(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="resourceType" :label="t('audit.resourceType')" width="100" />
      <el-table-column prop="description" :label="t('common.description')" show-overflow-tooltip />
      <el-table-column prop="username" :label="t('audit.operator')" width="120" />
      <el-table-column prop="ipAddress" :label="t('audit.ipAddress')" width="130" />
      <el-table-column prop="result" :label="t('audit.result')" width="80">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ row.result === 'SUCCESS' ? t('audit.success') : t('audit.failed') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="duration" :label="t('audit.duration')" width="80">
        <template #default="{ row }">{{ row.duration }}ms</template>
      </el-table-column>
      <el-table-column prop="createdAt" :label="t('audit.time')" width="160" />
      <el-table-column :label="t('common.actions')" width="80">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">{{ t('common.view') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-container">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </div>
    
    <el-dialog v-model="detailDialogVisible" :title="t('audit.logDetail')" width="600px">
      <el-descriptions :column="2" border v-if="currentLog">
        <el-descriptions-item :label="t('audit.actionType')">
          <el-tag :type="actionType(currentLog.action)">{{ actionText(currentLog.action) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('audit.operator')">{{ currentLog.username }}</el-descriptions-item>
        <el-descriptions-item :label="t('audit.resourceType')">{{ currentLog.resourceType }}</el-descriptions-item>
        <el-descriptions-item :label="t('audit.resourceId')">{{ currentLog.resourceId }}</el-descriptions-item>
        <el-descriptions-item :label="t('audit.ipAddress')">{{ currentLog.ipAddress }}</el-descriptions-item>
        <el-descriptions-item :label="t('audit.result')">
          <el-tag :type="currentLog.result === 'SUCCESS' ? 'success' : 'danger'">
            {{ currentLog.result === 'SUCCESS' ? t('audit.success') : t('audit.failed') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('audit.requestMethod')">{{ currentLog.requestMethod }}</el-descriptions-item>
        <el-descriptions-item :label="t('audit.requestPath')">{{ currentLog.requestPath }}</el-descriptions-item>
        <el-descriptions-item :label="t('audit.duration')">{{ currentLog.duration }}ms</el-descriptions-item>
        <el-descriptions-item :label="t('audit.time')">{{ currentLog.createdAt }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.description')" :span="2">{{ currentLog.description }}</el-descriptions-item>
        <el-descriptions-item v-if="currentLog.errorMessage" :label="t('audit.errorMessage')" :span="2">
          <span style="color: #F56C6C">{{ currentLog.errorMessage }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('audit.requestParams')" :span="2">
          <pre class="json-pre">{{ formatJson(currentLog.requestParams) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { queryAuditLogs, exportAuditLogs, type AuditLog, type AuditQueryRequest } from '@/api/audit'

const { t } = useI18n()

const loading = ref(false)
const logs = ref<AuditLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const detailDialogVisible = ref(false)
const currentLog = ref<AuditLog | null>(null)
const dateRange = ref<string[] | null>(null)

const query = reactive<AuditQueryRequest>({
  action: '',
  username: '',
  result: ''
})

const actionType = (action: string) => ({
  LOGIN: 'success', LOGOUT: 'info', CREATE: 'primary',
  UPDATE: 'warning', DELETE: 'danger', PERMISSION_CHANGE: 'warning'
}[action] || 'info')

const actionText = (action: string) => ({
  LOGIN: t('audit.login'), LOGOUT: t('audit.logout'), CREATE: t('audit.create'),
  UPDATE: t('audit.update'), DELETE: t('audit.delete'), PERMISSION_CHANGE: t('audit.permissionChange')
}[action] || action)

const formatJson = (obj: any) => {
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

const handleSearch = async () => {
  loading.value = true
  try {
    const request: AuditQueryRequest = { ...query }
    if (dateRange.value && dateRange.value.length === 2) {
      request.startTime = dateRange.value[0] + 'T00:00:00'
      request.endTime = dateRange.value[1] + 'T23:59:59'
    }
    // Remove empty values
    Object.keys(request).forEach(key => {
      if (!request[key as keyof AuditQueryRequest]) {
        delete request[key as keyof AuditQueryRequest]
      }
    })
    const result = await queryAuditLogs(request, page.value - 1, size.value)
    logs.value = result.content
    total.value = result.totalElements
  } catch (e) {
    console.error('Failed to load audit logs:', e)
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  Object.assign(query, { action: '', username: '', result: '' })
  dateRange.value = null
  page.value = 1
  handleSearch()
}

const showDetail = (log: AuditLog) => {
  currentLog.value = log
  detailDialogVisible.value = true
}

const handleExport = async () => {
  try {
    const request: AuditQueryRequest = { ...query }
    if (dateRange.value && dateRange.value.length === 2) {
      request.startTime = dateRange.value[0] + 'T00:00:00'
      request.endTime = dateRange.value[1] + 'T23:59:59'
    }
    Object.keys(request).forEach(key => {
      if (!request[key as keyof AuditQueryRequest]) {
        delete request[key as keyof AuditQueryRequest]
      }
    })
    await exportAuditLogs(request)
    ElMessage.success(t('common.success'))
  } catch (e) {
    console.error('Failed to export:', e)
    ElMessage.error(t('common.failed'))
  }
}

onMounted(handleSearch)
</script>

<style scoped>
.json-pre {
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  max-height: 200px;
  overflow: auto;
  margin: 0;
  font-size: 12px;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
