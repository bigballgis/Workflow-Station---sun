<template>
  <div class="common-table-page">
    <el-tabs v-model="activeTab">
      <!-- ===================== Tab 1: Common Table List ===================== -->
      <el-tab-pane :label="t('commonTable.list')" name="list">
        <div class="tab-toolbar">
          <el-input
            v-model="searchText"
            placeholder="Search by name or code"
            clearable
            style="width:240px;"
            @clear="loadTables"
            @keyup.enter="loadTables"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button @click="loadTables"><el-icon><Refresh /></el-icon> Refresh</el-button>
        </div>

        <el-table :data="filteredTables" v-loading="loadingTables" stripe style="width:100%;" size="small">
          <el-table-column prop="code" :label="t('commonTable.name')" min-width="140" show-overflow-tooltip />
          <el-table-column prop="name" :label="t('commonTable.displayName')" min-width="140" show-overflow-tooltip />
          <el-table-column prop="version" :label="t('commonTable.version')" width="100" align="center" />
          <el-table-column :label="t('commonTable.status')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('commonTable.enable')" width="90" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled"
                @change="(val: boolean) => handleToggleEnabled(row, val)"
              />
            </template>
          </el-table-column>
          <el-table-column :label="t('commonTable.updatedAt')" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDate(row.updatedAt || row.deployedAt) }}</template>
          </el-table-column>
          <el-table-column prop="deployedBy" :label="t('commonTable.deployedBy')" min-width="120" show-overflow-tooltip />
          <el-table-column :label="t('commonTable.actions')" width="320" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openAccessDialog(row)">{{ t('commonTable.access') }}</el-button>
              <el-button link type="primary" size="small" @click="openVersionsDialog(row)">{{ t('commonTable.versions') }}</el-button>
              <el-button link type="warning" size="small" @click="openRollbackDialog(row)">{{ t('commonTable.rollback') }}</el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">{{ t('commonTable.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="filteredTables.length === 0 && !loadingTables" description="No deployed common tables found" />
      </el-tab-pane>

      <!-- ===================== Tab 2: Deployment Records ===================== -->
      <el-tab-pane :label="t('commonTable.deploymentRecords')" name="deployments">
        <div class="tab-toolbar">
          <el-button @click="loadDeployments"><el-icon><Refresh /></el-icon> Refresh</el-button>
        </div>
        <el-table :data="deployments" v-loading="loadingDeployments" stripe style="width:100%;" size="small">
          <el-table-column :label="t('commonTable.tableName')" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ tableNameById(row.commonTableId) }}</template>
          </el-table-column>
          <el-table-column prop="version" :label="t('commonTable.version')" width="100" align="center" />
          <el-table-column :label="t('commonTable.status')" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="deployStatusTagType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('commonTable.deployedAt')" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDate(row.deployedAt) }}</template>
          </el-table-column>
          <el-table-column prop="deployedBy" :label="t('commonTable.deployedBy')" min-width="120" show-overflow-tooltip />
          <el-table-column prop="notes" :label="t('commonTable.notes')" min-width="160" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="deployments.length === 0 && !loadingDeployments" description="No deployment records" />
      </el-tab-pane>
    </el-tabs>

    <!-- ===================== Access Config Dialog ===================== -->
    <el-dialog v-model="showAccessDialog" title="Access Config" width="700px" :close-on-click-modal="false">
      <div class="access-config-header">
        <span>Common Table: <strong>{{ selectedTable?.name || selectedTable?.code }}</strong></span>
        <el-button type="primary" size="small" @click="openAddRoleDialog">
          <el-icon><Plus /></el-icon> + Add Business Role
        </el-button>
      </div>
      <el-alert type="info" :closable="false" style="margin-bottom:16px;">
        Configure which business roles can access this common table
      </el-alert>
      <el-table :data="accessList" v-loading="accessLoading" stripe :empty-text="'No access config'">
        <el-table-column label="Business Role" min-width="200">
          <template #default="{ row }">{{ row.roleName || row.targetId }}</template>
        </el-table-column>
        <el-table-column label="Created At" width="180">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="Actions" width="90" align="center">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="handleDeleteAccess(row)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showAccessDialog = false">Close</el-button>
      </template>
    </el-dialog>

    <!-- ===================== Add Business Role Dialog ===================== -->
    <el-dialog v-model="showAddRoleDialog" title="Select Business Role" width="500px" :close-on-click-modal="false">
      <el-form label-width="120px" label-position="left">
        <el-form-item label="Business Role" required>
          <el-select
            v-model="selectedRoleId"
            filterable
            placeholder="Select a business role"
            style="width:100%;"
            @change="handleRoleSelectChange"
          >
            <el-option v-for="role in businessRoles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddRoleDialog = false">Cancel</el-button>
        <el-button type="primary" @click="handleAddAccess" :loading="savingAccess">Confirm</el-button>
      </template>
    </el-dialog>

    <!-- ===================== Versions Dialog ===================== -->
    <el-dialog v-model="showVersionsDialog" :title="`Versions — ${selectedTable?.code}`" width="700px" :close-on-click-modal="false">
      <el-table :data="versionHistory" size="small" border>
        <el-table-column prop="version" label="Version" width="100" align="center" />
        <el-table-column prop="status" label="Status" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="deployStatusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Deployed At" min-width="160">
          <template #default="{ row }">{{ formatDate(row.deployedAt) }}</template>
        </el-table-column>
        <el-table-column prop="deployedBy" label="Deployed By" min-width="120" />
        <el-table-column prop="notes" label="Notes" min-width="140" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="showVersionsDialog = false">Close</el-button>
      </template>
    </el-dialog>

    <!-- ===================== Rollback Dialog ===================== -->
    <el-dialog v-model="showRollbackDialog" title="Rollback Deployment" width="500px" :close-on-click-modal="false">
      <p style="margin-bottom:12px;color:#606266;">{{ t('commonTable.confirmRollback') }}</p>
      <el-table :data="rollbackHistory" size="small" border style="margin-bottom:12px;">
        <el-table-column prop="version" label="Version" width="90" align="center" />
        <el-table-column prop="status" label="Status" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="deployStatusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Deployed At" min-width="150">
          <template #default="{ row }">{{ formatDate(row.deployedAt) }}</template>
        </el-table-column>
        <el-table-column label="Select" width="80" align="center">
          <template #default="{ row }">
            <el-radio
              :model-value="selectedDeploymentId"
              :label="row.id"
              @change="selectedDeploymentId = row.id"
            />
          </template>
        </el-table-column>
      </el-table>
      <el-input v-model="rollbackNotes" placeholder="Rollback reason (optional)" type="textarea" :rows="2" />
      <template #footer>
        <el-button @click="showRollbackDialog = false">Cancel</el-button>
        <el-button type="warning" @click="handleRollback" :loading="rollingBack" :disabled="!selectedDeploymentId">Confirm Rollback</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  adminCommonTableApi,
  type AdminCommonTable,
  type AdminCommonTableDeployment,
  type AdminCommonTableAccess
} from '@/api/adminCommonTable'
import { roleApi, type Role } from '@/api/role'

const { t } = useI18n()

const activeTab = ref('list')

// ==================== Tables List ====================
const tables = ref<AdminCommonTable[]>([])
const loadingTables = ref(false)
const searchText = ref('')

const filteredTables = computed(() => {
  if (!searchText.value) return tables.value
  const q = searchText.value.toLowerCase()
  return tables.value.filter(t =>
    t.code.toLowerCase().includes(q) || t.name.toLowerCase().includes(q)
  )
})

async function loadTables() {
  loadingTables.value = true
  try {
    const res = await adminCommonTableApi.list()
    tables.value = (res as any).data || res || []
  } catch {
    ElMessage.error('Failed to load common tables')
  } finally {
    loadingTables.value = false
  }
}

async function handleToggleEnabled(row: AdminCommonTable, val: boolean) {
  try {
    await adminCommonTableApi.setEnabled(row.id, val)
    row.enabled = val
    ElMessage.success(t('commonTable.enableSuccess'))
  } catch {
    ElMessage.error('Failed to update status')
  }
}

async function handleDelete(row: AdminCommonTable) {
  try {
    await ElMessageBox.confirm(t('commonTable.confirmDelete'), 'Confirm', { type: 'warning' })
    await adminCommonTableApi.delete(row.id)
    ElMessage.success(t('commonTable.deleteSuccess'))
    loadTables()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('Delete failed')
  }
}

// ==================== Deployments ====================
const deployments = ref<AdminCommonTableDeployment[]>([])
const loadingDeployments = ref(false)

async function loadDeployments() {
  loadingDeployments.value = true
  try {
    const res = await adminCommonTableApi.listAllDeployments()
    deployments.value = (res as any).data || res || []
  } catch {
    ElMessage.error('Failed to load deployments')
  } finally {
    loadingDeployments.value = false
  }
}

function tableNameById(id: number): string {
  const t = tables.value.find(t => t.id === id)
  return t ? t.code : String(id)
}

// ==================== Access Dialog ====================
const showAccessDialog = ref(false)
const showAddRoleDialog = ref(false)
const selectedTable = ref<AdminCommonTable | null>(null)
const accessList = ref<AdminCommonTableAccess[]>([])
const accessLoading = ref(false)
const businessRoles = ref<Role[]>([])
const selectedRoleId = ref('')
const savingAccess = ref(false)

async function openAccessDialog(row: AdminCommonTable) {
  selectedTable.value = row
  showAccessDialog.value = true
  await loadAccessList()
}

async function loadAccessList() {
  if (!selectedTable.value) return
  accessLoading.value = true
  try {
    const res = await adminCommonTableApi.getAccess(selectedTable.value.id)
    accessList.value = (res as any).data || res || []
  } catch {
    ElMessage.error('Failed to load access list')
  } finally {
    accessLoading.value = false
  }
}

async function openAddRoleDialog() {
  selectedRoleId.value = ''
  showAddRoleDialog.value = true
  try {
    businessRoles.value = await roleApi.getBusinessRoles()
  } catch {
    ElMessage.error('Failed to load business roles')
  }
}

function handleRoleSelectChange(roleId: string) {
  selectedRoleId.value = roleId
}

async function handleAddAccess() {
  if (!selectedRoleId.value) {
    ElMessage.warning('Please select a business role')
    return
  }
  if (!selectedTable.value) return
  savingAccess.value = true
  try {
    const res = await adminCommonTableApi.addAccess(selectedTable.value.id, selectedRoleId.value)
    const added = (res as any).data || res
    accessList.value.push(added)
    showAddRoleDialog.value = false
    ElMessage.success('Access added successfully')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || 'Failed to add access')
  } finally {
    savingAccess.value = false
  }
}

async function handleDeleteAccess(row: AdminCommonTableAccess) {
  if (!selectedTable.value) return
  try {
    await ElMessageBox.confirm(
      `Remove access for role "${row.roleName || row.targetId}"?`,
      'Confirm',
      { type: 'warning' }
    )
    await adminCommonTableApi.deleteAccess(selectedTable.value.id, row.id)
    accessList.value = accessList.value.filter(a => a.id !== row.id)
    ElMessage.success('Access removed')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('Failed to remove access')
  }
}

// ==================== Versions Dialog ====================
const showVersionsDialog = ref(false)
const versionHistory = ref<AdminCommonTableDeployment[]>([])

async function openVersionsDialog(row: AdminCommonTable) {
  selectedTable.value = row
  showVersionsDialog.value = true
  try {
    const res = await adminCommonTableApi.listDeployments(row.id)
    versionHistory.value = (res as any).data || res || []
  } catch {
    ElMessage.error('Failed to load version history')
  }
}

// ==================== Rollback Dialog ====================
const showRollbackDialog = ref(false)
const rollbackHistory = ref<AdminCommonTableDeployment[]>([])
const selectedDeploymentId = ref<number | null>(null)
const rollbackNotes = ref('')
const rollingBack = ref(false)

async function openRollbackDialog(row: AdminCommonTable) {
  selectedTable.value = row
  selectedDeploymentId.value = null
  rollbackNotes.value = ''
  showRollbackDialog.value = true
  try {
    const res = await adminCommonTableApi.listDeployments(row.id)
    rollbackHistory.value = (res as any).data || res || []
  } catch {
    ElMessage.error('Failed to load deployment history')
  }
}

async function handleRollback() {
  if (!selectedDeploymentId.value) return
  rollingBack.value = true
  try {
    await adminCommonTableApi.rollback(selectedDeploymentId.value, rollbackNotes.value || undefined)
    ElMessage.success(t('commonTable.rollbackSuccess'))
    showRollbackDialog.value = false
    loadTables()
    loadDeployments()
  } catch {
    ElMessage.error('Rollback failed')
  } finally {
    rollingBack.value = false
  }
}

// ==================== Helpers ====================
function statusTagType(status: string): '' | 'success' | 'info' | 'warning' | 'danger' {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
}

function statusLabel(status: string): string {
  if (status === 'PUBLISHED') return t('commonTable.published')
  if (status === 'ARCHIVED') return t('commonTable.archived')
  return t('commonTable.draft')
}

function deployStatusTagType(status: string): '' | 'success' | 'info' | 'warning' | 'danger' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'ROLLED_BACK') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function formatDate(val?: string): string {
  if (!val) return '-'
  const d = new Date(val)
  if (isNaN(d.getTime())) return val
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

onMounted(() => {
  loadTables()
  loadDeployments()
})
</script>

<style lang="scss" scoped>
.common-table-page {
  padding: 20px;
  width: 100%;
}
.access-config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.tab-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: nowrap;
}
:deep(.el-table) {
  width: 100% !important;
}
:deep(.el-table .el-table__cell) {
  white-space: nowrap;
}
:deep(.el-table .cell) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
