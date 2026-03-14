<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">RBAC Mapping</span>
      <div class="header-actions">
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
        <el-table-column label="Actions" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showEditDialog(row)">Edit Mapping</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Edit Mapping Dialog -->
    <el-dialog v-model="editDialogVisible" title="Edit RBAC Mapping" width="560px" destroy-on-close>
      <el-form label-width="140px">
        <el-form-item label="System Role">
          <span>{{ editForm.sysRoleName }}</span>
        </el-form-item>
        <el-form-item label="Superset Roles">
          <el-checkbox-group v-model="editForm.selectedRoleIds" v-loading="supersetRolesLoading">
            <el-checkbox
              v-for="role in activeSupersetRoles"
              :key="role.supersetRoleId"
              :label="role.supersetRoleId"
              :value="role.supersetRoleId"
              style="display: block; margin-bottom: 8px;"
            >{{ role.name }}</el-checkbox>
          </el-checkbox-group>
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
import { ElMessage } from 'element-plus'
import { Refresh, Search, Refresh as RefreshIcon } from '@element-plus/icons-vue'
import {
  biManagementApi,
  type RbacMappingResponse,
  type SupersetRoleResponse,
  type RbacMappingListParams
} from '@/api/biManagement'

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
    ElMessage.error(error.message || 'Failed to query RBAC mapping list')
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
    ElMessage.error(error.message || 'Failed to sync Superset roles')
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
    ElMessage.error(error.message || 'Failed to load Superset role list')
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
    ElMessage.success('Mapping updated successfully')
    editDialogVisible.value = false
    handleSearch()
  } catch (error: any) {
    ElMessage.error(error.message || 'Failed to update mapping')
  } finally {
    editLoading.value = false
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
