<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">Dashboard Registry</span>
      <div class="header-actions">
        <el-button type="primary" :loading="syncing" @click="handleSync">
          <el-icon><Refresh /></el-icon>Sync Dashboards
        </el-button>
      </div>
    </div>

    <el-card class="search-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="Title">
          <el-input v-model="query.title" placeholder="Search dashboard title" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="Tags">
          <el-input v-model="query.tags" placeholder="Search tags" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="Status">
          <el-select v-model="query.status" placeholder="Select status" clearable style="width: 140px">
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Manual Inactive" value="MANUAL_INACTIVE" />
            <el-option label="Auto Inactive" value="AUTO_INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>Search
          </el-button>
          <el-button @click="handleReset">
            <el-icon><RefreshIcon /></el-icon>Reset
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="dashboards" v-loading="loading" stripe border table-layout="auto" style="width: 100%">
        <el-table-column prop="dashboardTitle" label="Dashboard Title" min-width="180" show-overflow-tooltip />
        <el-table-column prop="embedId" label="Embed ID" min-width="160" show-overflow-tooltip />
        <el-table-column prop="supersetDashboardUuid" label="Superset UUID" min-width="160" show-overflow-tooltip />
        <el-table-column prop="tags" label="Tags" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.tags">{{ row.tags }}</span>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="Default Landing" width="150" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefaultLanding" type="success" size="small">Yes</el-tag>
            <el-tag v-else type="info" size="small">No</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Status" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastSyncedAt" label="Last Synced" min-width="170" show-overflow-tooltip />
        <el-table-column label="Actions" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; justify-content: center; flex-wrap: nowrap; white-space: nowrap; gap: 4px;">
              <el-button link type="primary" size="small" @click="showEditDialog(row)">Edit</el-button>
              <el-button
                v-if="row.status === 'ACTIVE'"
                link type="warning" size="small"
                @click="handleToggleStatus(row)"
              >Disable</el-button>
              <el-button
                v-else-if="row.status === 'MANUAL_INACTIVE'"
                link type="success" size="small"
                @click="handleToggleStatus(row)"
              >Enable</el-button>
              <el-button
                v-else
                link type="info" size="small"
                disabled
              >Enable</el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">Delete</el-button>
            </div>
          </template>
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

    <!-- Edit Dialog -->
    <el-dialog v-model="editDialogVisible" title="Edit Dashboard" width="500px" destroy-on-close>
      <el-form :model="editForm" label-width="140px">
        <el-form-item label="Dashboard Title">
          <span>{{ editForm.dashboardTitle }}</span>
        </el-form-item>
        <el-form-item label="Tags">
          <el-input v-model="editForm.tags" placeholder="Separate multiple tags with commas" />
        </el-form-item>
        <el-form-item label="Default Landing">
          <el-switch v-model="editForm.isDefaultLanding" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="editLoading" @click="handleEditSubmit">OK</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Refresh as RefreshIcon } from '@element-plus/icons-vue'
import {
  biManagementApi,
  type DashboardRegistryResponse,
  type DashboardStatus,
  type DashboardListParams
} from '@/api/biManagement'

// State
const loading = ref(false)
const syncing = ref(false)
const editLoading = ref(false)
const dashboards = ref<DashboardRegistryResponse[]>([])
const total = ref(0)

// Query
const query = reactive<DashboardListParams & { page: number; size: number }>({
  title: '',
  tags: '',
  status: undefined,
  page: 1,
  size: 20
})

// Edit dialog
const editDialogVisible = ref(false)
const editForm = reactive({
  id: '',
  dashboardTitle: '',
  tags: '',
  isDefaultLanding: false
})

// Status helpers
const statusTagType = (status: DashboardStatus) => {
  const map: Record<DashboardStatus, 'success' | 'danger' | 'warning' | 'info'> = {
    ACTIVE: 'success',
    MANUAL_INACTIVE: 'danger',
    AUTO_INACTIVE: 'warning'
  }
  return map[status] || ('info' as const)
}

const statusText = (status: DashboardStatus): string => {
  const map: Record<DashboardStatus, string> = {
    ACTIVE: 'Active',
    MANUAL_INACTIVE: 'Manual Inactive',
    AUTO_INACTIVE: 'Auto Inactive'
  }
  return map[status] || status
}

// Fetch dashboard list
const handleSearch = async () => {
  loading.value = true
  try {
    const params: DashboardListParams = {
      title: query.title || undefined,
      tags: query.tags || undefined,
      status: query.status || undefined,
      page: query.page - 1,
      size: query.size
    }
    const result = await biManagementApi.dashboard.list(params)
    dashboards.value = result.content
    total.value = result.totalElements
  } catch (error: any) {
    ElMessage.error(error.message || 'Failed to query dashboard list')
  } finally {
    loading.value = false
  }
}

// Reset query
const handleReset = () => {
  Object.assign(query, { title: '', tags: '', status: undefined, page: 1 })
  handleSearch()
}

// Sync dashboards
const handleSync = async () => {
  syncing.value = true
  try {
    const result = await biManagementApi.dashboard.sync()
    ElMessage.success(
      `Sync completed: ${result.created} created, ${result.updated} updated, ${result.autoInactivated} auto-inactivated`
    )
    handleSearch()
  } catch (error: any) {
    ElMessage.error(error.message || 'Failed to sync dashboards')
  } finally {
    syncing.value = false
  }
}

// Edit dialog
const showEditDialog = (row: DashboardRegistryResponse) => {
  editForm.id = row.id
  editForm.dashboardTitle = row.dashboardTitle
  editForm.tags = row.tags || ''
  editForm.isDefaultLanding = row.isDefaultLanding
  editDialogVisible.value = true
}

const handleEditSubmit = async () => {
  editLoading.value = true
  try {
    await biManagementApi.dashboard.update(editForm.id, {
      tags: editForm.tags || undefined,
      isDefaultLanding: editForm.isDefaultLanding
    })
    ElMessage.success('Updated successfully')
    editDialogVisible.value = false
    handleSearch()
  } catch (error: any) {
    ElMessage.error(error.message || 'Update failed')
  } finally {
    editLoading.value = false
  }
}

// Toggle status (enable/disable)
const handleToggleStatus = async (row: DashboardRegistryResponse) => {
  const isActive = row.status === 'ACTIVE'
  const action = isActive ? 'disable' : 'enable'
  const newStatus: DashboardStatus = isActive ? 'MANUAL_INACTIVE' : 'ACTIVE'

  try {
    await ElMessageBox.confirm(`Are you sure you want to ${action} "${row.dashboardTitle}"?`, 'Confirm', { type: 'warning' })
    await biManagementApi.dashboard.updateStatus(row.id, { status: newStatus })
    ElMessage.success(`${action.charAt(0).toUpperCase() + action.slice(1)}d successfully`)
    handleSearch()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || `Failed to ${action}`)
    }
  }
}

// Delete dashboard
const handleDelete = async (row: DashboardRegistryResponse) => {
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to delete "${row.dashboardTitle}"? Deletion will fail if there are associated assignments.`,
      'Warning',
      { type: 'warning', confirmButtonText: 'Delete', confirmButtonClass: 'el-button--danger' }
    )
    await biManagementApi.dashboard.delete(row.id)
    ElMessage.success('Deleted successfully')
    handleSearch()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || 'Delete failed')
    }
  }
}

onMounted(() => {
  handleSearch()
})
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

  .header-actions {
    display: flex;
    gap: 12px;
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
