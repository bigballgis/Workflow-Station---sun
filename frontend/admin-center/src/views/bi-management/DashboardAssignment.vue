<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">Dashboard Assignment</span>
      <div class="header-actions">
        <el-button type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>New Assignment
        </el-button>
      </div>
    </div>

    <el-card class="search-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="Target Type">
          <el-select v-model="query.targetType" placeholder="Select target type" clearable style="width: 160px">
            <el-option label="User" value="USER" />
            <el-option label="Role" value="ROLE" />
            <el-option label="Business Unit" value="BUSINESS_UNIT" />
          </el-select>
        </el-form-item>
        <el-form-item label="Dashboard Title">
          <el-input v-model="query.dashboardTitle" placeholder="Search dashboard title" clearable style="width: 200px" />
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
      <el-table :data="assignments" v-loading="loading" stripe border table-layout="auto" style="width: 100%">
        <el-table-column prop="dashboardTitle" label="Dashboard Title" min-width="180" show-overflow-tooltip />
        <el-table-column label="Target Type" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="targetTypeTagType(row.targetType)" size="small">{{ targetTypeText(row.targetType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetName" label="Target Name" min-width="150" show-overflow-tooltip />
        <el-table-column label="Layout Mode" width="110" align="center">
          <template #default="{ row }">
            {{ layoutModeText(row.layoutMode) }}
          </template>
        </el-table-column>
        <el-table-column prop="displayOrder" label="Display Order" width="120" align="center" />
        <el-table-column label="Default" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="success" size="small">Yes</el-tag>
            <el-tag v-else type="info" size="small">No</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; justify-content: center; gap: 4px;">
              <el-button link type="primary" size="small" @click="showEditDialog(row)">Edit</el-button>
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

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? 'Edit Assignment' : 'New Assignment'" width="520px" destroy-on-close>
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="120px">
        <el-form-item label="Dashboard" prop="dashboardId">
          <el-select
            v-model="form.dashboardId"
            placeholder="Select dashboard"
            filterable
            :disabled="isEdit"
            style="width: 100%"
            :loading="dashboardsLoading"
          >
            <el-option
              v-for="d in activeDashboards"
              :key="d.id"
              :label="d.dashboardTitle"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Target Type" prop="targetType">
          <el-select
            v-model="form.targetType"
            placeholder="Select target type"
            :disabled="isEdit"
            style="width: 100%"
            @change="handleTargetTypeChange"
          >
            <el-option label="User" value="USER" />
            <el-option label="Role" value="ROLE" />
            <el-option label="Business Unit" value="BUSINESS_UNIT" />
          </el-select>
        </el-form-item>
        <el-form-item label="Target" prop="targetId">
          <el-select
            v-model="form.targetId"
            placeholder="Select target"
            filterable
            :disabled="isEdit"
            style="width: 100%"
            :loading="targetsLoading"
          >
            <el-option
              v-for="t in targetOptions"
              :key="t.id"
              :label="t.name"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Layout Mode" prop="layoutMode">
          <el-select v-model="form.layoutMode" placeholder="Select layout mode" style="width: 100%">
            <el-option label="Single" value="SINGLE" />
            <el-option label="Multi-tab" value="MULTI" />
            <el-option label="Widget" value="WIDGET" />
          </el-select>
        </el-form-item>
        <el-form-item label="Display Order" prop="displayOrder">
          <el-input-number v-model="form.displayOrder" :min="0" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Default">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">OK</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Refresh as RefreshIcon } from '@element-plus/icons-vue'
import {
  biManagementApi,
  type DashboardAssignmentResponse,
  type DashboardAssignmentCreateRequest,
  type DashboardRegistryResponse,
  type AssignmentTargetType,
  type LayoutMode,
  type AssignmentListParams
} from '@/api/biManagement'
import { userApi } from '@/api/user'
import { roleApi } from '@/api/role'
import { businessUnitApi } from '@/api/businessUnit'

// State
const loading = ref(false)
const submitLoading = ref(false)
const dashboardsLoading = ref(false)
const targetsLoading = ref(false)
const assignments = ref<DashboardAssignmentResponse[]>([])
const total = ref(0)

// Query
const query = reactive<AssignmentListParams & { page: number; size: number }>({
  targetType: undefined,
  dashboardTitle: '',
  page: 1,
  size: 20
})

// Dialog
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref<FormInstance>()

const form = reactive<{
  dashboardId: string
  targetType: AssignmentTargetType | ''
  targetId: string
  layoutMode: LayoutMode
  displayOrder: number
  isDefault: boolean
}>({
  dashboardId: '',
  targetType: '',
  targetId: '',
  layoutMode: 'SINGLE',
  displayOrder: 0,
  isDefault: false
})

const formRules: FormRules = {
  dashboardId: [{ required: true, message: 'Please select a dashboard', trigger: 'change' }],
  targetType: [{ required: true, message: 'Please select a target type', trigger: 'change' }],
  targetId: [{ required: true, message: 'Please select a target', trigger: 'change' }],
  layoutMode: [{ required: true, message: 'Please select a layout mode', trigger: 'change' }]
}

// Dropdown data
const activeDashboards = ref<DashboardRegistryResponse[]>([])
const targetOptions = ref<{ id: string; name: string }[]>([])

// Display helpers
const targetTypeText = (type: AssignmentTargetType): string => {
  const map: Record<AssignmentTargetType, string> = {
    USER: 'User',
    ROLE: 'Role',
    BUSINESS_UNIT: 'Business Unit'
  }
  return map[type] || type
}

const targetTypeTagType = (type: AssignmentTargetType) => {
  const map: Record<AssignmentTargetType, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    USER: 'primary',
    ROLE: 'success',
    BUSINESS_UNIT: 'warning'
  }
  return map[type] || ('info' as const)
}

const layoutModeText = (mode: LayoutMode): string => {
  const map: Record<LayoutMode, string> = {
    SINGLE: 'Single',
    MULTI: 'Multi-tab',
    WIDGET: 'Widget'
  }
  return map[mode] || mode
}

// Fetch assignment list
const handleSearch = async () => {
  loading.value = true
  try {
    const params: AssignmentListParams = {
      targetType: query.targetType || undefined,
      dashboardTitle: query.dashboardTitle || undefined,
      page: query.page - 1,
      size: query.size
    }
    const result = await biManagementApi.assignment.list(params)
    assignments.value = result.content
    total.value = result.totalElements
  } catch (error: any) {
    ElMessage.error(error.message || 'Failed to query assignment list')
  } finally {
    loading.value = false
  }
}

// Reset query
const handleReset = () => {
  Object.assign(query, { targetType: undefined, dashboardTitle: '', page: 1 })
  handleSearch()
}

// Load active dashboards for dropdown
const loadActiveDashboards = async () => {
  dashboardsLoading.value = true
  try {
    const result = await biManagementApi.dashboard.list({ status: 'ACTIVE', size: 1000 })
    activeDashboards.value = result.content
  } catch (error: any) {
    ElMessage.error(error.message || 'Failed to load dashboard list')
  } finally {
    dashboardsLoading.value = false
  }
}

// Load targets based on target type
const loadTargets = async (targetType: string) => {
  if (!targetType) {
    targetOptions.value = []
    return
  }
  targetsLoading.value = true
  try {
    if (targetType === 'USER') {
      const result = await userApi.list({ size: 1000 })
      targetOptions.value = result.content.map((u: any) => ({
        id: u.id,
        name: u.fullName || u.username
      }))
    } else if (targetType === 'ROLE') {
      const roles = await roleApi.list()
      targetOptions.value = roles.map((r: any) => ({
        id: r.id,
        name: r.name
      }))
    } else if (targetType === 'BUSINESS_UNIT') {
      const units = await businessUnitApi.list()
      targetOptions.value = units.map((bu: any) => ({
        id: bu.id,
        name: bu.name
      }))
    }
  } catch (error: any) {
    ElMessage.error(error.message || 'Failed to load target list')
  } finally {
    targetsLoading.value = false
  }
}

// Handle target type change in form
const handleTargetTypeChange = (val: string) => {
  form.targetId = ''
  targetOptions.value = []
  if (val) {
    loadTargets(val)
  }
}

// Reset form
const resetForm = () => {
  form.dashboardId = ''
  form.targetType = ''
  form.targetId = ''
  form.layoutMode = 'SINGLE'
  form.displayOrder = 0
  form.isDefault = false
  editingId.value = ''
  targetOptions.value = []
}

// Show create dialog
const showCreateDialog = () => {
  isEdit.value = false
  resetForm()
  loadActiveDashboards()
  dialogVisible.value = true
}

// Show edit dialog
const showEditDialog = (row: DashboardAssignmentResponse) => {
  isEdit.value = true
  editingId.value = row.id
  form.dashboardId = row.dashboardId
  form.targetType = row.targetType
  form.targetId = row.targetId
  form.layoutMode = row.layoutMode
  form.displayOrder = row.displayOrder
  form.isDefault = row.isDefault
  loadActiveDashboards()
  loadTargets(row.targetType)
  dialogVisible.value = true
}

// Submit form
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  submitLoading.value = true
  try {
    const data: DashboardAssignmentCreateRequest = {
      dashboardId: form.dashboardId,
      targetType: form.targetType as AssignmentTargetType,
      targetId: form.targetId,
      layoutMode: form.layoutMode,
      displayOrder: form.displayOrder,
      isDefault: form.isDefault
    }

    if (isEdit.value) {
      await biManagementApi.assignment.update(editingId.value, data)
      ElMessage.success('Updated successfully')
    } else {
      await biManagementApi.assignment.create(data)
      ElMessage.success('Created successfully')
    }
    dialogVisible.value = false
    handleSearch()
  } catch (error: any) {
    ElMessage.error(error.message || (isEdit.value ? 'Update failed' : 'Create failed'))
  } finally {
    submitLoading.value = false
  }
}

// Delete assignment
const handleDelete = async (row: DashboardAssignmentResponse) => {
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to delete the assignment of "${row.dashboardTitle}" to "${row.targetName}"?`,
      'Warning',
      { type: 'warning', confirmButtonText: 'Delete', confirmButtonClass: 'el-button--danger' }
    )
    await biManagementApi.assignment.delete(row.id)
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
