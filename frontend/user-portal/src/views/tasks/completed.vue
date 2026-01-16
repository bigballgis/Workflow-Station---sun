<template>
  <div class="completed-tasks-page">
    <div class="page-header">
      <h1>{{ t('task.completedTasks') }}</h1>
    </div>

    <!-- 筛选条件 -->
    <div class="portal-card filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item :label="t('task.processName')">
          <el-input v-model="filterForm.keyword" :placeholder="t('common.search')" clearable style="width: 200px;">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item :label="t('task.completedTime')">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            :start-placeholder="t('common.startDate')"
            :end-placeholder="t('common.endDate')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 260px;"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 任务列表 -->
    <div class="portal-card">
      <el-table :data="taskList" v-loading="loading" stripe table-layout="fixed">
        <el-table-column prop="taskName" :label="t('task.taskName')" min-width="160">
          <template #default="{ row }">
            <el-link type="primary" @click="viewTask(row)">{{ row.taskName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="processDefinitionName" :label="t('task.processName')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="action" :label="t('task.action')" width="100">
          <template #default="{ row }">
            <el-tag :type="getActionTagType(row.action)" size="small">
              {{ t(`action.${row.action || 'completed'}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('task.createTime')" width="150">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="completedTime" :label="t('task.completedTime')" width="150">
          <template #default="{ row }">
            {{ formatDate(row.completedTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="durationInMillis" :label="t('task.duration')" width="120">
          <template #default="{ row }">
            {{ formatDuration(row.durationInMillis) }}
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        style="margin-top: 16px; justify-content: flex-end;"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Search } from '@element-plus/icons-vue'
import { queryCompletedTasks, TaskInfo } from '@/api/task'
import dayjs from 'dayjs'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const taskList = ref<TaskInfo[]>([])

const filterForm = reactive({
  keyword: '',
  dateRange: null as [string, string] | null
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await queryCompletedTasks({
      keyword: filterForm.keyword || undefined,
      startTime: filterForm.dateRange?.[0] || undefined,
      endTime: filterForm.dateRange?.[1] || undefined,
      page: pagination.page - 1,
      size: pagination.size
    })
    const data = res.data || res
    taskList.value = data.content || []
    pagination.total = data.totalElements || 0
  } catch (error) {
    console.error('Failed to load completed tasks:', error)
    taskList.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadTasks()
}

const handleReset = () => {
  filterForm.keyword = ''
  filterForm.dateRange = null
  handleSearch()
}

const handleSizeChange = () => {
  pagination.page = 1
  loadTasks()
}

const handlePageChange = () => {
  loadTasks()
}

const viewTask = (task: TaskInfo) => {
  // 跳转到流程详情页面
  router.push(`/applications/${task.processInstanceId}`)
}

const formatDate = (date: string) => {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

const formatDuration = (ms: number | undefined) => {
  if (!ms) return '-'
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  
  if (days > 0) {
    return `${days}天${hours % 24}小时`
  } else if (hours > 0) {
    return `${hours}小时${minutes % 60}分钟`
  } else if (minutes > 0) {
    return `${minutes}分钟`
  } else {
    return `${seconds}秒`
  }
}

const getActionTagType = (action: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' => {
  const typeMap: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
    'approved': 'success',
    'rejected': 'danger',
    'transferred': 'warning',
    'delegated': 'info',
    'completed': 'primary'
  }
  return typeMap[action] || 'primary'
}

onMounted(() => {
  loadTasks()
})
</script>

<style lang="scss" scoped>
.completed-tasks-page {
  .page-header {
    margin-bottom: 20px;
    
    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }
  }
  
  .filter-card {
    margin-bottom: 20px;
    
    .el-form {
      margin-bottom: -18px;
    }
  }
}
</style>
