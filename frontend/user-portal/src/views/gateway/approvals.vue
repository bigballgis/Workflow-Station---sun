<template>
  <div class="gateway-approvals-page">
    <div class="page-header"><h1>{{ t('gateway.subscriptionApprovals') }}</h1></div>
    <div class="portal-card">
      <el-tabs v-model="activeTab" @tab-change="fetchData">
        <el-tab-pane :label="t('gateway.pending')" name="PENDING" />
        <el-tab-pane :label="t('gateway.approved')" name="APPROVED" />
        <el-tab-pane :label="t('gateway.rejected')" name="REJECTED" />
      </el-tabs>
      <el-empty v-if="requests.length === 0 && !loading" :description="t('gateway.noPendingApprovals')" />
      <el-table v-else :data="requests" v-loading="loading" stripe>
        <el-table-column prop="id" :label="t('gateway.requestId')" width="80" />
        <el-table-column prop="requesterId" :label="t('gateway.requester')" width="120" />
        <el-table-column prop="applicationId" :label="t('gateway.application')" width="100" />
        <el-table-column prop="environmentId" :label="t('gateway.environment')" width="80" />
        <el-table-column :label="t('gateway.apiVersions')" width="150">
          <template #default="{ row }">{{ (row.apiVersionIds || []).join(', ') }}</template>
        </el-table-column>
        <el-table-column prop="justification" :label="t('gateway.justification')" min-width="200" />
        <el-table-column :label="t('gateway.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('gateway.createdAt')" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'PENDING'" :label="t('gateway.actions')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="handleDecide(row, true)">{{ t('gateway.approve') }}</el-button>
            <el-button link type="danger" @click="handleDecide(row, false)">{{ t('gateway.reject') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="total > size" v-model:current-page="page" :page-size="size" :total="total" @current-change="fetchData" layout="prev,pager,next" style="margin-top:16px" />
    </div>

    <el-dialog v-model="commentDialog" :title="decideApprove ? t('gateway.approveSubscription') : t('gateway.rejectSubscription')" width="400px">
      <el-input v-model="comment" :placeholder="t('gateway.commentPlaceholder')" type="textarea" :rows="3" />
      <template #footer>
        <el-button @click="commentDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button :type="decideApprove ? 'success' : 'danger'" @click="confirmDecide">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const { t } = useI18n()
const loading = ref(false), activeTab = ref('PENDING'), requests = ref<any[]>([])
const page = ref(0), size = ref(20), total = ref(0)
const commentDialog = ref(false), comment = ref(''), decideApprove = ref(false), selectedId = ref<number | null>(null)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await request.get('/gateway/subscriptions/approvals', { params: { status: activeTab.value, page: page.value, size: size.value } })
    requests.value = res.content || []
    total.value = res.totalElements || 0
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error'))
  } finally { loading.value = false }
}

const handleDecide = (row: any, approve: boolean) => {
  selectedId.value = row.id
  decideApprove.value = approve
  comment.value = ''
  commentDialog.value = true
}

const confirmDecide = async () => {
  if (!selectedId.value) return
  try {
    await request.post('/gateway/subscriptions/requests/' + selectedId.value + '/decide', { approved: decideApprove.value, comment: comment.value })
    ElMessage.success(t(decideApprove.value ? 'gateway.subscriptionApproved' : 'gateway.subscriptionRejected'))
    commentDialog.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error'))
  }
}

const formatDate = (d: string) => d ? new Date(d).toLocaleString() : '-'

onMounted(fetchData)
</script>
