<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">RBAC Mapping</span>
      <div class="header-actions">
        <el-button type="success" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>Create Mapping
        </el-button>
        <el-button type="primary" :loading="syncing" @click="handleSync">
          <el-icon><Refresh /></el-icon>Sync Superset Roles
        </el-button>
      </div>
    </div>

    <el-card class="search-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="Role Name">
          <el-input v-model="query.roleName" placeholder="Search role name" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="Role Type">
          <el-select v-model="query.roleType" placeholder="Select role type" clearable style="width: 160px">
            <el-option label="Admin" value="ADMIN" />
            <el-option label="Developer" value="DEVELOPER" />
            <el-option label="BU Bounded" value="BU_BOUNDED" />
            <el-option label="BU Unbounded" value="BU_UNBOUNDED" />
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
      <el-table :data="mappings" v-loading="loading" stripe border table-layout="auto" style="width: 100%">
        <el-table-column prop="sysRoleName" label="System Role" min-width="150" show-overflow-tooltip />
        <el-table-column prop="sysRoleCode" label="Role Code" min-width="140" show-overflow-tooltip />
        <el-table-column label="Role Type" width="140" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ roleTypeText(row.sysRoleType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Mapped Superset Roles" min-width="220">
          <template #default="{ row }">
            <template v-if="row.supersetRoles && row.supersetRoles.length > 0">
              <el-tag
                v-for="sr in row.supersetRoles"
                :key="sr.id"
                size="small"
                class="role-tag"
              >{{ sr.name }}</el-tag>
            </template>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastUpdatedAt" label="Last Updated" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.lastUpdatedAt">{{ row.lastUpdatedAt }}</span>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showEditDialog(row)">Edit Mapping</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Create Mapping Dialog -->
    <el-dialog v-model="createDialogVisible" title="Create Mapping" width="560px" destroy-on-close>
      <el-form :model="createForm" :rules="createFormRules" ref="createFormRef" label-width="140px">
        <el-form-item label="System Role" prop="sysRoleId">
          <el-select
            v-model="createForm.sysRoleId"
            filterable
            placeholder="Select system role"
            :loading="unmappedRolesLoading"
            style="width: 100%"
          >
            <el-option
              v-for="role in unmappedRoles"
              :key="role.id"
              :label="`${role.name} (${role.code})`"
              :value="role.id"
            />
          </el-select>
          <el-empty v-if="!unmappedRolesLoading && unmappedRoles.length === 0" description="No unmapped roles available" :image-size="60" />
        </el-form-item>
        <el-form-item label="Superset Roles" prop="supersetRoleIds">
          <el-select
            v-model="createForm.supersetRoleIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="Select Superset Roles"
            :loading="createSupersetRolesLoading"
            style="width: 100%"
          >
            <el-option
              v-for="role in createActiveSupersetRoles"
              :key="role.supersetRoleId"
              :label="role.name"
              :value="role.supersetRoleId"
            />
          </el-select>
          <el-empty v-if="!createSupersetRolesLoading && createActiveSupersetRoles.length === 0" description="No available Superset roles" :image-size="60" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreateSubmit">OK</el-button>
      </template>
    </el-dialog>

    <!-- Edit Mapping Dialog -->
    <el-dialog v-model="editDialogVisible" title="Edit RBAC Mapping" width="560px" destroy-on-close>
      <el-form label-width="140px">
        <el-form-item label="System Role">
          <span>{{ editForm.sysRoleName }}</span>
        </el-form-item>
        <el-form-item label="Superset Roles">
          <el-select
            v-model="editForm.selectedRoleIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="Select Superset Roles"
            :loading="supersetRolesLoading"
            style="width: 100%"
          >
            <el-option
              v-for="role in activeSupersetRoles"
              :key="role.supersetRoleId"
              :label="role.name"
              :value="role.supersetRoleId"
            />
          </el-select>
          <el-empty v-if="!supersetRolesLoading && activeSupersetRoles.length === 0" description="No available Superset roles" :image-size="60" />
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
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Search, Refresh as RefreshIcon, Plus } from '@element-plus/icons-vue'
import {
  biManagementApi,
  type RbacMappingResponse,
  type SupersetRoleResponse,
  type RbacMappingListParams,
  type RoleOptionResponse
} from '@/api/biManagement'

const { t } = useI18n()

// State
const loading = ref(false)
const syncing = ref(false)
const editLoading = ref(false)
const supersetRolesLoading = ref(false)
const mappings = ref<RbacMappingResponse[]>([])
const allSupersetRoles = ref<SupersetRoleResponse[]>([])

// Query
const query = reactive<RbacMappingListParams>({
  roleName: '',
  roleType: undefined
})

// Edit dialog
const editDialogVisible = ref(false)
const editForm = reactive({
  sysRoleId: '',
  sysRoleName: '',
  selectedRoleIds: [] as number[]
})

// Only show ACTIVE superset roles in the checkbox group
const activeSupersetRoles = computed(() =>
  allSupersetRoles.value.filter(r => r.status === 'ACTIVE')
)

// Create dialog
const createDialogVisible = ref(false)
const createLoading = ref(false)
const unmappedRolesLoading = ref(false)
const createSupersetRolesLoading = ref(false)
const unmappedRoles = ref<RoleOptionResponse[]>([])
const createAllSupersetRoles = ref<SupersetRoleResponse[]>([])
const createFormRef = ref<FormInstance>()

const createForm = reactive({
  sysRoleId: '',
  supersetRoleIds: [] as number[]
})

const createFormRules: FormRules = {
  sysRoleId: [{ required: true, message: 'Please select a system role', trigger: 'change' }],
  supersetRoleIds: [{ required: true, type: 'array', min: 1, message: 'Please select at least one Superset role', trigger: 'change' }]
}

const createActiveSupersetRoles = computed(() =>
  createAllSupersetRoles.value.filter(r => r.status === 'ACTIVE')
)

// Role type display mapping
const roleTypeText = (type: string): string => {
  const map: Record<string, string> = {
    ADMIN: 'Admin',
    DEVELOPER: 'Developer',
    BU_BOUNDED: 'BU Bounded',
    BU_UNBOUNDED: 'BU Unbounded'
  }
  return map[type] || type
}

// Fetch mapping list
const handleSearch = async () => {
  loading.value = true
  try {
    const params: RbacMappingListParams = {
      roleName: query.roleName || undefined,
      roleType: query.roleType || undefined
    }
    mappings.value = await biManagementApi.rbac.listMappings(params)
  } catch (error: any) {
    ElMessage.error(error.message || t('bi.rbac.queryFailed'))
  } finally {
    loading.value = false
  }
}

// Reset query
const handleReset = () => {
  query.roleName = ''
  query.roleType = undefined
  handleSearch()
}

// Sync Superset roles
const handleSync = async () => {
  syncing.value = true
  try {
    const result = await biManagementApi.rbac.syncSupersetRoles()
    ElMessage.success(
      `Sync completed: ${result.created} created, ${result.updated} updated, ${result.autoInactivated} inactivated`
    )
    handleSearch()
  } catch (error: any) {
    ElMessage.error(error.message || t('bi.rbac.syncFailed'))
  } finally {
    syncing.value = false
  }
}

// Load all superset roles for the edit dialog
const loadSupersetRoles = async () => {
  supersetRolesLoading.value = true
  try {
    allSupersetRoles.value = await biManagementApi.rbac.listSupersetRoles()
  } catch (error: any) {
    ElMessage.error(error.message || t('bi.rbac.loadSupersetRolesFailed'))
  } finally {
    supersetRolesLoading.value = false
  }
}

// Show edit mapping dialog
const showEditDialog = (row: RbacMappingResponse) => {
  editForm.sysRoleId = row.sysRoleId
  editForm.sysRoleName = row.sysRoleName
  editForm.selectedRoleIds = row.supersetRoles
    ? row.supersetRoles.map(sr => sr.supersetRoleId)
    : []
  loadSupersetRoles()
  editDialogVisible.value = true
}

// Submit mapping update
const handleEditSubmit = async () => {
  editLoading.value = true
  try {
    await biManagementApi.rbac.updateMapping(editForm.sysRoleId, {
      supersetRoleIds: editForm.selectedRoleIds
    })
    ElMessage.success(t('bi.rbac.updateSuccess'))
    editDialogVisible.value = false
    handleSearch()
  } catch (error: any) {
    ElMessage.error(error.message || t('bi.rbac.updateFailed'))
  } finally {
    editLoading.value = false
  }
}

// Show create mapping dialog
const showCreateDialog = () => {
  createForm.sysRoleId = ''
  createForm.supersetRoleIds = []
  loadUnmappedRoles()
  loadCreateSupersetRoles()
  createDialogVisible.value = true
}

// Load unmapped roles for create dialog dropdown
const loadUnmappedRoles = async () => {
  unmappedRolesLoading.value = true
  try {
    unmappedRoles.value = await biManagementApi.rbac.listUnmappedRoles()
  } catch (error: any) {
    ElMessage.error(error.message || t('bi.rbac.loadUnmappedRolesFailed'))
  } finally {
    unmappedRolesLoading.value = false
  }
}

// Load superset roles for create dialog
const loadCreateSupersetRoles = async () => {
  createSupersetRolesLoading.value = true
  try {
    createAllSupersetRoles.value = await biManagementApi.rbac.listSupersetRoles()
  } catch (error: any) {
    ElMessage.error(error.message || t('bi.rbac.loadSupersetRolesFailed'))
  } finally {
    createSupersetRolesLoading.value = false
  }
}

// Submit create mapping
const handleCreateSubmit = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate()

  createLoading.value = true
  try {
    await biManagementApi.rbac.createMapping({
      sysRoleId: createForm.sysRoleId,
      supersetRoleIds: createForm.supersetRoleIds
    })
    ElMessage.success(t('bi.rbac.createSuccess'))
    createDialogVisible.value = false
    handleSearch()
  } catch (error: any) {
    ElMessage.error(error.message || t('bi.rbac.createFailed'))
  } finally {
    createLoading.value = false
  }
}

// Delete mapping
const handleDelete = async (row: RbacMappingResponse) => {
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to delete all RBAC mappings for role "${row.sysRoleName}"?`,
      'Confirm Delete',
      {
        confirmButtonText: 'Delete',
        cancelButtonText: 'Cancel',
        type: 'warning'
      }
    )
    await biManagementApi.rbac.deleteMapping(row.sysRoleId)
    ElMessage.success(t('bi.rbac.deleteSuccess'))
    handleSearch()
  } catch (error: any) {
    if (error === 'cancel' || error?.toString?.() === 'cancel') return
    ElMessage.error(error.message || t('bi.rbac.deleteFailed'))
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
  // No pagination needed for this page
}

.role-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}
</style>
