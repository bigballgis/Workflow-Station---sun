<template>
  <div class="my-requests-page">
    <div class="page-header">
      <h1>{{ t('permission.requestHistory') }}</h1>
    </div>

    <div class="portal-card">
      <el-table :data="requests" stripe v-loading="loading">
        <el-table-column prop="requestType" :label="t('permission.requestType')" width="140">
          <template #default="{ row }">
            <el-tag :type="row.requestType === 'VIRTUAL_GROUP' ? 'success' : 'primary'" size="small">
              {{ row.requestType === 'VIRTUAL_GROUP' ? t('permission.virtualGroupJoin') : t('permission.businessUnitRole') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetName" :label="t('permission.requestTarget')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="roleNames" :label="t('permission.role')" min-width="150">
          <template #default="{ row }">
            <template v-if="row.roleNames?.length">
              <el-tag v-for="role in row.roleNames" :key="role" size="small" style="margin-right: 4px">{{ role }}</el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" :label="t('permission.reason')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('permission.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approverComment" :label="t('permission.approverComment')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdAt" :label="t('permission.applyTime')" width="160">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" type="danger" link size="small" @click="handleCancel(row)">
              {{ t('permission.cancelRequest') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadRequests"
          @current-change="loadRequests"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { permissionApi, type PermissionRequestRecord } from '@/api/permission'

const { t } = useI18n()

const loading = ref(false)
const requests = ref<PermissionRequestRecord[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20 })

const statusType = (status: string) => {
  const map: Record<string, 'warning' | 'success' | 'danger' | 'info'> = {
    PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', CANCELLED: 'info'
  }
  return map[status] || 'info'
}

const statusText = (status: string) => {
  const map: Record<string, string> = {
    PENDING: t('permission.pending'), APPROVED: t('permission.approved'),
    REJECTED: t('permission.rejected'), CANCELLED: t('permission.cancelled')
  }
  return map[status] || status
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

const loadRequests = async () => {
  loading.value = true
  try {
    const res = await permissionApi.getMyRequests({ page: query.page - 1, size: query.size })
    const data = res.data?.data || res.data || res
    requests.value = data.content || []
    total.value = data.totalElements || 0
  } catch (e) {
    console.error('Failed to load requests:', e)
  } finally {
    loading.value = false
  }
}

const handleCancel = async (row: PermissionRequestRecord) => {
  try {
    await ElMessageBox.confirm(t('permission.cancelConfirm'), t('common.confirm'))
    await permissionApi.cancelRequest(row.id)
    ElMessage.success(t('permission.cancelSuccess'))
    loadRequests()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || t('permission.cancelFailed'))
    }
  }
}

onMounted(loadRequests)
</script>

<style lang="scss" scoped>
.my-requests-page {
  .page-header {
    margin-bottom: 20px;
    h1 { font-size: 24px; font-weight: 500; margin: 0; }
  }
  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }
}
</style>
