<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('permissionRequest.title') }}</span>
    </div>
    
    <el-card class="search-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item :label="t('permissionRequest.status')">
          <el-select v-model="query.status" :placeholder="t('permissionRequest.filterByStatus')" clearable style="width: 140px">
            <el-option :label="t('permissionRequest.pending')" value="PENDING" />
            <el-option :label="t('permissionRequest.approved')" value="APPROVED" />
            <el-option :label="t('permissionRequest.rejected')" value="REJECTED" />
            <el-option :label="t('permissionRequest.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('permissionRequest.requestType')">
          <el-select v-model="query.requestType" :placeholder="t('permissionRequest.filterByType')" clearable style="width: 160px">
            <el-option :label="t('permissionRequest.virtualGroup')" value="VIRTUAL_GROUP" />
            <el-option :label="t('permissionRequest.businessUnitRole')" value="BUSINESS_UNIT_ROLE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :start-placeholder="t('common.startDate')"
            :end-placeholder="t('common.endDate')"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>{{ t('common.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>{{ t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <el-card class="table-card">
      <el-table :data="requests" v-loading="loading" stripe border>
        <el-table-column prop="applicantName" :label="t('permissionRequest.applicant')" width="120">
          <template #default="{ row }">
            {{ row.applicantName || row.applicantUsername }}
          </template>
        </el-table-column>
        <el-table-column prop="requestType" :label="t('permissionRequest.requestType')" width="140">
          <template #default="{ row }">
            <el-tag :type="row.requestType === 'VIRTUAL_GROUP' ? 'success' : 'primary'" size="small">
              {{ row.requestType === 'VIRTUAL_GROUP' ? t('permissionRequest.virtualGroup') : t('permissionRequest.businessUnitRole') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetName" :label="t('permissionRequest.target')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="roleNames" :label="t('permissionRequest.roles')" min-width="150">
          <template #default="{ row }">
            <template v-if="row.roleNames?.length">
              <el-tag v-for="role in row.roleNames" :key="role" size="small" style="margin-right: 4px">{{ role }}</el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" :label="t('permissionRequest.reason')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('permissionRequest.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approverName" :label="t('permissionRequest.approver')" width="100" />
        <el-table-column prop="approverComment" :label="t('permissionRequest.approverComment')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdAt" :label="t('permissionRequest.createdAt')" width="160">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="approvedAt" :label="t('permissionRequest.approvedAt')" width="160">
          <template #default="{ row }">{{ row.approvedAt ? formatDate(row.approvedAt) : '-' }}</template>
        </el-table-column>
      </el-table>
      
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { permissionRequestApi, type PermissionRequest, type PermissionRequestStatus } from '@/api/permissionRequest'

const { t } = useI18n()

const loading = ref(false)
const requests = ref<PermissionRequest[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const query = reactive({
  status: '' as PermissionRequestStatus | '',
  requestType: '' as 'VIRTUAL_GROUP' | 'BUSINESS_UNIT_ROLE' | '',
  startDate: '',
  endDate: '',
  page: 1,
  size: 20
})

watch(dateRange, (val) => {
  if (val) {
    query.startDate = val[0]
    query.endDate = val[1]
  } else {
    query.startDate = ''
    query.endDate = ''
  }
})

const statusType = (status: PermissionRequestStatus): 'warning' | 'success' | 'danger' | 'info' => {
  const map: Record<PermissionRequestStatus, 'warning' | 'success' | 'danger' | 'info'> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'info'
  }
  return map[status] || 'info'
}

const statusText = (status: PermissionRequestStatus) => {
  const map: Record<PermissionRequestStatus, string> = {
    PENDING: t('permissionRequest.pending'),
    APPROVED: t('permissionRequest.approved'),
    REJECTED: t('permissionRequest.rejected'),
    CANCELLED: t('permissionRequest.cancelled')
  }
  return map[status] || status
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

const handleSearch = async () => {
  loading.value = true
  try {
    const params = {
      status: query.status || undefined,
      requestType: query.requestType || undefined,
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined,
      page: query.page - 1,
      size: query.size
    }
    const result = await permissionRequestApi.list(params)
    requests.value = result.content
    total.value = result.totalElements
  } catch (error: any) {
    ElMessage.error(error.message || t('common.failed'))
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  Object.assign(query, { status: '', requestType: '', startDate: '', endDate: '', page: 1 })
  dateRange.value = null
  handleSearch()
}

onMounted(handleSearch)
</script>

<style scoped lang="scss">
.page-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  
  .page-title {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
  }
}

.search-card {
  margin-bottom: 20px;
  
  .search-form {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }
}

.table-card {
  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }
}
</style>
