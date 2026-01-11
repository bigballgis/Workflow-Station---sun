<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.audit') }}</span>
      <el-button @click="handleExport">
        <el-icon><Download /></el-icon>导出日志
      </el-button>
    </div>
    
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="操作类型">
        <el-select v-model="query.action" clearable placeholder="请选择">
          <el-option label="登录" value="LOGIN" />
          <el-option label="登出" value="LOGOUT" />
          <el-option label="创建" value="CREATE" />
          <el-option label="更新" value="UPDATE" />
          <el-option label="删除" value="DELETE" />
          <el-option label="权限变更" value="PERMISSION_CHANGE" />
        </el-select>
      </el-form-item>
      <el-form-item label="操作人">
        <el-input v-model="query.username" clearable placeholder="用户名" />
      </el-form-item>
      <el-form-item label="结果">
        <el-select v-model="query.result" clearable placeholder="请选择">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker v-model="dateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>
    
    <el-table :data="logs" v-loading="loading" stripe>
      <el-table-column prop="action" label="操作类型" width="120">
        <template #default="{ row }">
          <el-tag :type="actionType(row.action)" size="small">{{ actionText(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="resourceType" label="资源类型" width="100" />
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column prop="username" label="操作人" width="120" />
      <el-table-column prop="ipAddress" label="IP地址" width="130" />
      <el-table-column prop="result" label="结果" width="80">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="duration" label="耗时" width="80">
        <template #default="{ row }">{{ row.duration }}ms</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="160" />
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">详情</el-button>
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
    
    <el-dialog v-model="detailDialogVisible" title="日志详情" width="600px">
      <el-descriptions :column="2" border v-if="currentLog">
        <el-descriptions-item label="操作类型">
          <el-tag :type="actionType(currentLog.action)">{{ actionText(currentLog.action) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentLog.username }}</el-descriptions-item>
        <el-descriptions-item label="资源类型">{{ currentLog.resourceType }}</el-descriptions-item>
        <el-descriptions-item label="资源ID">{{ currentLog.resourceId }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ currentLog.ipAddress }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag :type="currentLog.result === 'SUCCESS' ? 'success' : 'danger'">
            {{ currentLog.result === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ currentLog.requestMethod }}</el-descriptions-item>
        <el-descriptions-item label="请求路径">{{ currentLog.requestPath }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentLog.duration }}ms</el-descriptions-item>
        <el-descriptions-item label="时间">{{ currentLog.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ currentLog.description }}</el-descriptions-item>
        <el-descriptions-item v-if="currentLog.errorMessage" label="错误信息" :span="2">
          <span style="color: #F56C6C">{{ currentLog.errorMessage }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
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
  LOGIN: '登录', LOGOUT: '登出', CREATE: '创建',
  UPDATE: '更新', DELETE: '删除', PERMISSION_CHANGE: '权限变更'
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
    ElMessage.success('导出成功')
  } catch (e) {
    console.error('Failed to export:', e)
    ElMessage.error('导出失败')
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
