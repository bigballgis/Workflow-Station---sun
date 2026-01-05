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
        <el-select v-model="query.action" clearable>
          <el-option label="登录" value="LOGIN" />
          <el-option label="登出" value="LOGOUT" />
          <el-option label="创建" value="CREATE" />
          <el-option label="更新" value="UPDATE" />
          <el-option label="删除" value="DELETE" />
          <el-option label="权限变更" value="PERMISSION_CHANGE" />
        </el-select>
      </el-form-item>
      <el-form-item label="操作人">
        <el-input v-model="query.operator" clearable />
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker v-model="query.dateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" />
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
      <el-table-column prop="resource" label="资源" />
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column prop="operator" label="操作人" width="120" />
      <el-table-column prop="ip" label="IP地址" width="130" />
      <el-table-column prop="result" label="结果" width="80">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.result === 'SUCCESS' ? '成功' : '失败' }}</el-tag>
        </template>
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
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </div>
    
    <el-dialog v-model="detailDialogVisible" title="日志详情" width="600px">
      <el-descriptions :column="2" border v-if="currentLog">
        <el-descriptions-item label="操作类型">{{ actionText(currentLog.action) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentLog.operator }}</el-descriptions-item>
        <el-descriptions-item label="资源">{{ currentLog.resource }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ currentLog.ip }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ currentLog.result }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ currentLog.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ currentLog.description }}</el-descriptions-item>
        <el-descriptions-item label="详细数据" :span="2">
          <pre>{{ JSON.stringify(currentLog.data, null, 2) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

const loading = ref(false)
const logs = ref<any[]>([])
const total = ref(0)
const detailDialogVisible = ref(false)
const currentLog = ref<any>(null)

const query = reactive({ action: '', operator: '', dateRange: null as any, page: 1, size: 20 })

const actionType = (action: string) => ({ LOGIN: 'success', LOGOUT: 'info', CREATE: 'primary', UPDATE: 'warning', DELETE: 'danger', PERMISSION_CHANGE: 'warning' }[action] || 'info')
const actionText = (action: string) => ({ LOGIN: '登录', LOGOUT: '登出', CREATE: '创建', UPDATE: '更新', DELETE: '删除', PERMISSION_CHANGE: '权限变更' }[action] || action)

const handleSearch = () => {
  loading.value = true
  // Mock data
  logs.value = [
    { id: '1', action: 'LOGIN', resource: '系统', description: '用户登录系统', operator: 'admin', ip: '192.168.1.100', result: 'SUCCESS', createdAt: '2026-01-05 10:30:00', data: { browser: 'Chrome', os: 'Windows' } },
    { id: '2', action: 'CREATE', resource: '用户', description: '创建用户 zhangsan', operator: 'admin', ip: '192.168.1.100', result: 'SUCCESS', createdAt: '2026-01-05 10:35:00', data: { userId: '123', username: 'zhangsan' } },
    { id: '3', action: 'PERMISSION_CHANGE', resource: '角色', description: '修改角色权限', operator: 'admin', ip: '192.168.1.100', result: 'SUCCESS', createdAt: '2026-01-05 11:00:00', data: { roleId: '456', changes: ['ADD_READ', 'REMOVE_DELETE'] } },
    { id: '4', action: 'DELETE', resource: '部门', description: '删除部门失败：存在子部门', operator: 'admin', ip: '192.168.1.100', result: 'FAILED', createdAt: '2026-01-05 11:30:00', data: { deptId: '789', error: 'HAS_CHILDREN' } }
  ]
  total.value = 100
  loading.value = false
}

const handleReset = () => {
  Object.assign(query, { action: '', operator: '', dateRange: null, page: 1 })
  handleSearch()
}

const showDetail = (log: any) => {
  currentLog.value = log
  detailDialogVisible.value = true
}

const handleExport = () => {
  ElMessage.success('导出任务已提交')
}

onMounted(handleSearch)
</script>

<style scoped>
pre {
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  max-height: 200px;
  overflow: auto;
}
</style>
