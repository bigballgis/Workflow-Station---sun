<template>
  <div class="applications-page">
    <div class="page-header">
      <h1>{{ t('application.title') }}</h1>
    </div>

    <div class="portal-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="进行中" name="RUNNING" />
        <el-tab-pane label="已完成" name="COMPLETED" />
        <el-tab-pane label="已撤回" name="WITHDRAWN" />
        <el-tab-pane label="已拒绝" name="REJECTED" />
        <el-tab-pane name="DRAFT">
          <template #label>
            <span>草稿箱</span>
            <el-badge v-if="draftCount > 0" :value="draftCount" :max="99" class="draft-badge" />
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 草稿列表 -->
      <template v-if="activeTab === 'DRAFT'">
        <el-table :data="draftList" v-loading="loading" stripe>
          <el-table-column prop="processDefinitionName" label="流程类型" min-width="200">
            <template #default="{ row }">
              <el-link type="primary" @click="continueDraft(row)">
                {{ row.processDefinitionName }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="保存时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="continueDraft(row)">
                继续填写
              </el-button>
              <el-button type="danger" size="small" @click="handleDeleteDraft(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && draftList.length === 0" description="暂无草稿" />
      </template>

      <!-- 申请列表 -->
      <template v-else>
        <el-table :data="applicationList" v-loading="loading" stripe>
          <el-table-column prop="businessKey" :label="t('application.processTitle')" min-width="200">
            <template #default="{ row }">
              <el-link type="primary" @click="viewDetail(row)">
                {{ row.businessKey || row.processDefinitionName }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column prop="processDefinitionName" label="流程类型" width="120" />
          <el-table-column prop="currentNode" :label="t('application.currentNode')" width="120">
            <template #default="{ row }">
              {{ row.currentNode || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="currentAssignee" label="当前处理人" width="120">
            <template #default="{ row }">
              {{ row.currentAssignee || '待分配' }}
            </template>
          </el-table-column>
          <el-table-column prop="startTime" :label="t('application.startTime')" width="160">
            <template #default="{ row }">
              {{ formatDate(row.startTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="status" :label="t('application.status')" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small">
                {{ getStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'RUNNING'" type="warning" size="small" @click="handleUrge(row)">
                {{ t('application.urge') }}
              </el-button>
              <el-button v-if="row.status === 'RUNNING'" type="danger" size="small" @click="handleWithdraw(row)">
                {{ t('application.withdraw') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <el-pagination
        v-if="activeTab !== 'DRAFT'"
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end;"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { processApi } from '@/api/process'
import dayjs from 'dayjs'

const { t } = useI18n()
const router = useRouter()

const activeTab = ref('all')
const loading = ref(false)
const pagination = reactive({ page: 1, size: 20, total: 0 })
const applicationList = ref<any[]>([])
const draftList = ref<any[]>([])
const draftCount = ref(0)

const formatDate = (date: string) => {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

const getStatusType = (status: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' => {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
    RUNNING: 'warning',
    COMPLETED: 'success',
    WITHDRAWN: 'info',
    REJECTED: 'danger'
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    RUNNING: '进行中',
    COMPLETED: '已完成',
    WITHDRAWN: '已撤回',
    REJECTED: '已拒绝'
  }
  return map[status] || status
}

const loadApplications = async () => {
  loading.value = true
  try {
    const status = activeTab.value === 'all' ? undefined : activeTab.value
    const response = await processApi.getMyApplications({
      page: pagination.page - 1,
      size: pagination.size,
      status
    })
    // API 返回格式: { success: true, data: { content: [], totalElements: 0 } }
    const data = response.data || response
    applicationList.value = data.content || []
    pagination.total = data.totalElements || 0
  } catch (error) {
    console.error('Failed to load applications:', error)
    ElMessage.error('加载申请列表失败')
    applicationList.value = []
  } finally {
    loading.value = false
  }
}

const loadDrafts = async () => {
  loading.value = true
  try {
    const response = await processApi.getDraftList()
    const data = response.data || response
    draftList.value = Array.isArray(data) ? data : []
    draftCount.value = draftList.value.length
  } catch (error) {
    console.error('Failed to load drafts:', error)
    ElMessage.error('加载草稿列表失败')
    draftList.value = []
  } finally {
    loading.value = false
  }
}

const loadDraftCount = async () => {
  try {
    const response = await processApi.getDraftList()
    const data = response.data || response
    draftCount.value = Array.isArray(data) ? data.length : 0
  } catch (error) {
    console.error('Failed to load draft count:', error)
  }
}

const handleTabChange = () => {
  pagination.page = 1
  if (activeTab.value === 'DRAFT') {
    loadDrafts()
  } else {
    loadApplications()
  }
}

const handlePageChange = () => {
  loadApplications()
}

const viewDetail = (row: any) => {
  router.push(`/applications/${row.id}`)
}

const continueDraft = (row: any) => {
  // 跳转到流程发起页面，带上草稿标记
  router.push(`/processes/start/${row.processDefinitionKey}?draft=true`)
}

const handleDeleteDraft = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定要删除该草稿吗？', '提示', { type: 'warning' })
    await processApi.deleteDraftById(row.id)
    ElMessage.success('删除成功')
    loadDrafts()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleUrge = async (row: any) => {
  try {
    await processApi.urgeProcess(row.id)
    ElMessage.success('催办成功')
  } catch (error) {
    ElMessage.error('催办失败')
  }
}

const handleWithdraw = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定要撤回该流程吗？', '提示', { type: 'warning' })
    await processApi.withdrawProcess(row.id, '用户主动撤回')
    ElMessage.success('撤回成功')
    loadApplications()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('撤回失败')
    }
  }
}

onMounted(() => {
  loadApplications()
  loadDraftCount()
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
  
  .draft-badge {
    margin-left: 6px;
    
    :deep(.el-badge__content) {
      font-size: 10px;
    }
  }
}
</style>
