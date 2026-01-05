<template>
  <div class="applications-page">
    <div class="page-header">
      <h1>{{ t('application.title') }}</h1>
    </div>

    <div class="portal-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="进行中" name="running" />
        <el-tab-pane label="已完成" name="completed" />
        <el-tab-pane label="已撤回" name="withdrawn" />
        <el-tab-pane label="已拒绝" name="rejected" />
      </el-tabs>

      <el-table :data="applicationList" stripe>
        <el-table-column prop="title" :label="t('application.processTitle')" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" @click="viewDetail(row)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="processName" label="流程类型" width="120" />
        <el-table-column prop="currentNode" :label="t('application.currentNode')" width="120" />
        <el-table-column prop="startTime" :label="t('application.startTime')" width="160" />
        <el-table-column prop="status" :label="t('application.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ t(`application.${row.status}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'running'" type="warning" size="small" @click="handleUrge(row)">
              {{ t('application.urge') }}
            </el-button>
            <el-button v-if="row.status === 'running'" type="danger" size="small" @click="handleWithdraw(row)">
              {{ t('application.withdraw') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end;"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'

const { t } = useI18n()
const router = useRouter()

const activeTab = ref('all')
const pagination = reactive({ page: 1, size: 20, total: 5 })

const applicationList = ref([
  { id: '1', title: '请假申请-2026年1月', processName: '请假流程', currentNode: '部门经理审批', startTime: '2026-01-05 10:00', status: 'running' },
  { id: '2', title: '报销申请-差旅费', processName: '报销流程', currentNode: '财务审核', startTime: '2026-01-04 14:30', status: 'running' },
  { id: '3', title: '采购申请-办公用品', processName: '采购流程', currentNode: '-', startTime: '2026-01-03 09:00', status: 'completed' },
  { id: '4', title: '出差申请-北京', processName: '出差流程', currentNode: '-', startTime: '2026-01-02 16:00', status: 'rejected' },
  { id: '5', title: '加班申请-项目上线', processName: '加班流程', currentNode: '-', startTime: '2026-01-01 20:00', status: 'withdrawn' }
])

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    running: 'warning',
    completed: 'success',
    withdrawn: 'info',
    rejected: 'danger'
  }
  return map[status] || 'info'
}

const viewDetail = (row: any) => {
  router.push(`/applications/${row.id}`)
}

const handleUrge = (row: any) => {
  ElMessage.success('催办成功')
}

const handleWithdraw = async (row: any) => {
  await ElMessageBox.confirm('确定要撤回该流程吗？', '提示', { type: 'warning' })
  ElMessage.success('撤回成功')
  row.status = 'withdrawn'
}

watch(activeTab, () => {
  // 根据tab筛选数据
})
</script>

<style lang="scss" scoped>
.applications-page {
  .page-header {
    margin-bottom: 20px;
    
    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }
  }
}
</style>
