<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    :title="'Access Config - ' + (tableName || '')"
    width="720px"
  >
    <div class="access-config-header">
      <el-alert type="info" :closable="false" style="flex: 1; margin-right: 12px;">
        Configure which roles can access this table in User Portal.
      </el-alert>
      <el-button type="primary" size="small" @click="openAddDialog">
        <el-icon><Plus /></el-icon>Add Role
      </el-button>
    </div>

    <el-table :data="accessList" stripe v-loading="loading" empty-text="No access configured">
      <el-table-column label="Role" min-width="180">
        <template #default="{ row }">
          {{ resolveRoleName(row.targetId) }}
        </template>
      </el-table-column>
      <el-table-column label="Type" width="130">
        <template #default="{ row }">
          <el-tag :type="resolveRoleTagType(row.targetId)" size="small">
            {{ resolveRoleTypeLabel(row.targetId) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="Created At" width="170">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="createdBy" label="Created By" width="120" />
      <el-table-column label="Actions" width="80" align="center">
        <template #default="{ row }">
          <el-button link type="danger" size="small" @click="handleRemove(row)">Delete</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">Close</el-button>
    </template>

    <!-- Add Role Sub-Dialog -->
    <el-dialog
      v-model="showAddRole"
      title="Add Role Access"
      width="560px"
      append-to-body
      @closed="resetAddForm"
    >
      <el-tabs v-model="addRoleTab" class="add-role-tabs">

        <!-- Tab 1: System / Unbounded roles -->
        <el-tab-pane label="System Role" name="system">
          <p class="tab-hint">
            Roles marked with <el-tag size="small" type="danger" style="margin: 0 2px;">Default</el-tag>
            are pre-selected and always granted access when Portal Visibility is enabled.
          </p>
          <div v-loading="rolesLoading" class="system-role-list">
            <el-empty v-if="!rolesLoading && availableSystemRoles.length === 0" description="All system roles already have access" :image-size="40" />
            <el-checkbox-group v-else v-model="selectedSystemRoleIds">
              <div v-for="role in availableSystemRoles" :key="role.id" class="role-checkbox-item">
                <el-checkbox
                  :value="role.id"
                  :disabled="isDefaultSystemRole(role.name)"
                >
                  <span class="role-checkbox-name">{{ role.name }}</span>
                  <el-tag v-if="isDefaultSystemRole(role.name)" size="small" type="danger" style="margin-left: 6px;">Default</el-tag>
                  <el-tag v-else size="small" type="info" style="margin-left: 6px;">{{ roleTypeDisplayLabel(role.type) }}</el-tag>
                </el-checkbox>
              </div>
            </el-checkbox-group>
          </div>
        </el-tab-pane>

        <!-- Tab 2: BU cascade + BU roles -->
        <el-tab-pane label="BU Role" name="bu">
          <p class="tab-hint">Select a Business Unit, then choose one of its bound roles.</p>
          <el-form label-width="110px" label-position="left" style="margin-top: 8px;">
            <el-form-item label="Business Unit" required>
              <el-cascader
                v-model="selectedBuId"
                :options="buCascaderOptions"
                :props="buCascaderProps"
                filterable
                clearable
                placeholder="Select BU"
                style="width: 100%;"
                @change="handleBuChange"
              />
            </el-form-item>
            <el-form-item label="BU Role" required>
              <el-select
                v-model="selectedBuRoleId"
                filterable
                placeholder="Select a role"
                style="width: 100%;"
                :loading="buRolesLoading"
                :disabled="!selectedBuId"
              >
                <el-option
                  v-for="item in availableBuRoles"
                  :key="item.id"
                  :label="item.name || item.code"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>

      </el-tabs>

      <template #footer>
        <el-button @click="showAddRole = false">Cancel</el-button>
        <el-button type="primary" :loading="addLoading" @click="handleAddRole">Confirm</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, watchEffect } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { relationTableStructureApi, type RelationTableAccess } from '@/api/relationTable'
import { roleApi, type Role, type RoleType } from '@/api/role'
import { businessUnitApi, type BusinessUnit } from '@/api/businessUnit'

const props = defineProps<{
  modelValue: boolean
  tableId?: number
  tableName?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

// ---- main dialog ----
const loading = ref(false)
const accessList = ref<RelationTableAccess[]>([])

// ---- all roles cache (for display in table) ----
const allRoles = ref<Role[]>([])
const rolesLoading = ref(false)
const allRolesMap = computed(() => {
  const m = new Map<string, Role>()
  allRoles.value.forEach(r => m.set(r.id, r))
  return m
})

// ---- add role sub-dialog ----
const showAddRole = ref(false)
const addLoading = ref(false)
const addRoleTab = ref<'system' | 'bu'>('system')

// system tab
const DEFAULT_SYSTEM_ROLE_NAMES = ['System Administrator', 'Auditor', 'Technical Lead', 'Team Lead']

const isDefaultSystemRole = (name: string) => DEFAULT_SYSTEM_ROLE_NAMES.includes(name)

const selectedSystemRoleIds = ref<string[]>([])

// BU tab
const selectedBuId = ref<string | null>(null)
const selectedBuRoleId = ref('')
const buCascaderOptions = ref<BusinessUnit[]>([])
const buRoles = ref<Role[]>([])
const buRolesLoading = ref(false)

const buCascaderProps = {
  value: 'id',
  label: 'name',
  children: 'children',
  checkStrictly: true,
  emitPath: false,
}

// ---- computed ----
const assignedIds = computed(() => new Set(accessList.value.map(a => a.targetId)))

const availableSystemRoles = computed(() =>
  allRoles.value.filter(r =>
    r.status === 'ACTIVE' &&
    r.type !== 'BU_BOUNDED' &&
    !assignedIds.value.has(r.id)
  )
)

const availableBuRoles = computed(() =>
  buRoles.value.filter(r => !assignedIds.value.has(r.id))
)

// Keep default roles pre-selected whenever dialog is open or role list changes
watchEffect(() => {
  if (!showAddRole.value) return
  const defaultIds = allRoles.value
    .filter(r => isDefaultSystemRole(r.name) && !assignedIds.value.has(r.id))
    .map(r => r.id)
  // Merge: keep any manually checked extras + ensure defaults are always in the list
  const current = new Set(selectedSystemRoleIds.value)
  defaultIds.forEach(id => current.add(id))
  selectedSystemRoleIds.value = [...current]
})

// ---- display helpers ----
const ROLE_TYPE_LABELS: Record<string, string> = {
  BU_BOUNDED: 'BU Bounded',
  BU_UNBOUNDED: 'BU Unbounded',
  BUSINESS: 'Business',
  ADMIN: 'Admin',
  DEVELOPER: 'Developer',
}

const roleTypeDisplayLabel = (type: RoleType) => ROLE_TYPE_LABELS[type] ?? type

const resolveRoleName = (roleId: string) =>
  allRolesMap.value.get(roleId)?.name ?? roleId

const resolveRoleTypeLabel = (roleId: string) => {
  const type = allRolesMap.value.get(roleId)?.type
  return type ? (ROLE_TYPE_LABELS[type] ?? type) : '—'
}

const resolveRoleTagType = (roleId: string): '' | 'success' | 'warning' | 'danger' | 'info' => {
  const type = allRolesMap.value.get(roleId)?.type
  if (type === 'BU_BOUNDED') return 'warning'
  if (type === 'BU_UNBOUNDED' || type === 'BUSINESS') return 'success'
  if (type === 'ADMIN') return 'danger'
  if (type === 'DEVELOPER') return 'info'
  return ''
}

const formatDate = (d: string) => (d ? new Date(d).toLocaleString('zh-CN') : '')

// ---- fetch ----
const fetchAccessConfig = async () => {
  if (!props.tableId) return
  loading.value = true
  try {
    accessList.value = await relationTableStructureApi.getAccessConfig(props.tableId)
  } catch (e) {
    console.error('Failed to load access config:', e)
  } finally {
    loading.value = false
  }
}

const fetchAllRoles = async () => {
  if (allRoles.value.length > 0) return
  rolesLoading.value = true
  try {
    allRoles.value = await roleApi.list()
  } catch (e) {
    console.error('Failed to load roles:', e)
  } finally {
    rolesLoading.value = false
  }
}

const fetchBuTree = async () => {
  if (buCascaderOptions.value.length > 0) return
  try {
    buCascaderOptions.value = await businessUnitApi.getTree()
  } catch (e) {
    console.error('Failed to load BU tree:', e)
  }
}

// ---- add role ----
const openAddDialog = () => {
  resetAddForm()
  showAddRole.value = true
  fetchBuTree()
}

const resetAddForm = () => {
  addRoleTab.value = 'system'
  selectedSystemRoleIds.value = []  // watchEffect will re-populate defaults
  selectedBuId.value = null
  selectedBuRoleId.value = ''
  buRoles.value = []
}

const handleBuChange = async (buId: string | null) => {
  selectedBuRoleId.value = ''
  buRoles.value = []
  if (!buId) return
  buRolesLoading.value = true
  try {
    buRoles.value = await businessUnitApi.getBoundRoles(buId)
  } catch (e) {
    console.error('Failed to load BU roles:', e)
  } finally {
    buRolesLoading.value = false
  }
}

const handleAddRole = async () => {
  if (!props.tableId) return

  if (addRoleTab.value === 'system') {
    const roleIds = selectedSystemRoleIds.value.filter(id => !assignedIds.value.has(id))
    if (roleIds.length === 0) {
      ElMessage.warning('Please select at least one role')
      return
    }
    addLoading.value = true
    try {
      await Promise.all(roleIds.map(id => relationTableStructureApi.addAccess(props.tableId!, id)))
      ElMessage.success(`Added ${roleIds.length} role(s)`)
      showAddRole.value = false
      await fetchAccessConfig()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.error?.message || e?.response?.data?.message || 'Failed to add access')
    } finally {
      addLoading.value = false
    }
  } else {
    const roleId = selectedBuRoleId.value
    if (!roleId) {
      ElMessage.warning('Please select a role')
      return
    }
    addLoading.value = true
    try {
      await relationTableStructureApi.addAccess(props.tableId, roleId)
      ElMessage.success('Access added')
      showAddRole.value = false
      await fetchAccessConfig()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.error?.message || e?.response?.data?.message || 'Failed to add access')
    } finally {
      addLoading.value = false
    }
  }
}

const handleRemove = async (access: RelationTableAccess) => {
  if (!props.tableId) return
  try {
    await ElMessageBox.confirm('Remove this role access?', 'Confirm', { type: 'warning' })
    await relationTableStructureApi.removeAccess(props.tableId, access.id)
    ElMessage.success('Access removed')
    await fetchAccessConfig()
  } catch (e: any) {
    if (e !== 'cancel') console.error('Failed to remove access:', e)
  }
}

watch(() => props.modelValue, val => {
  if (val) {
    fetchAccessConfig()
    fetchAllRoles()
  }
})
</script>

<style scoped>
.access-config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.add-role-tabs {
  min-height: 160px;
}
.tab-hint {
  font-size: 13px;
  color: #909399;
  margin: 0 0 8px;
  line-height: 1.5;
}
.system-role-list {
  max-height: 220px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  padding: 8px 12px;
}
.role-checkbox-item {
  padding: 5px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.role-checkbox-item:last-child {
  border-bottom: none;
}
.role-checkbox-name {
  font-size: 13px;
}
</style>
