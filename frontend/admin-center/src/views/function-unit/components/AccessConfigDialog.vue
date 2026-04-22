<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    :title="'Access Config - ' + (functionUnitName || '')"
    width="720px"
  >
    <div class="access-config-header">
      <el-alert type="info" :closable="false" style="flex: 1; margin-right: 12px;">
        Configure which roles can access this function unit in User Portal.
      </el-alert>
      <el-button type="primary" size="small" @click="openAddDialog">
        <el-icon><Plus /></el-icon>Add Role
      </el-button>
    </div>

    <el-table :data="accessList" stripe v-loading="loading" empty-text="No access configured">
      <el-table-column label="Role" min-width="180">
        <template #default="{ row }">
          {{ resolveRoleName(row.targetId || row.roleId) }}
        </template>
      </el-table-column>
      <el-table-column label="Type" width="140">
        <template #default="{ row }">
          <el-tag :type="resolveRoleTagType(row.targetId || row.roleId)" size="small">
            {{ resolveRoleTypeLabel(row.targetId || row.roleId) }}
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

        <!-- Tab 1: BU cascade + BU-bounded roles -->
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

        <!-- Tab 2: System / non-BU-bounded roles -->
        <el-tab-pane label="System Role" name="system">
          <p class="tab-hint">All available system roles are pre-selected. Uncheck any you do not want to grant.</p>
          <div v-loading="rolesLoading" class="system-role-list">
            <el-empty v-if="!rolesLoading && availableSystemRoles.length === 0" description="All eligible system roles already have access" :image-size="40" />
            <el-checkbox-group v-else v-model="selectedSystemRoleIds">
              <div v-for="role in availableSystemRoles" :key="role.id" class="role-checkbox-item">
                <el-checkbox :value="role.id">
                  <span class="role-checkbox-name">{{ role.name }}</span>
                  <el-tag size="small" type="info" style="margin-left: 6px;">{{ roleTypeDisplayLabel(role.type) }}</el-tag>
                </el-checkbox>
              </div>
            </el-checkbox-group>
          </div>
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
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { functionUnitApi, type FunctionUnitAccess } from '@/api/functionUnit'
import { roleApi, type Role } from '@/api/role'
import { businessUnitApi, type BusinessUnit } from '@/api/businessUnit'

const props = defineProps<{
  modelValue: boolean
  functionUnitId?: string
  functionUnitName?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

// ---- main dialog ----
const loading = ref(false)
const accessList = ref<FunctionUnitAccess[]>([])

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
const addRoleTab = ref<'system' | 'bu'>('bu')

// system tab
const selectedSystemRoleIds = ref<string[]>([])

// BU tab: BU_BOUNDED roles via cascader
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
// Use targetId (new backend response) or roleId (backward compat) as the assigned identifier
const assignedIds = computed(() =>
  new Set(accessList.value.map(a => a.targetId || a.roleId))
)

// System Role tab: show all non-BU-bounded roles (same filter as Table Structure dialog)
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


// ---- display helpers ----
const ROLE_TYPE_LABELS: Record<string, string> = {
  BU_BOUNDED:   'BU Bounded',
  BU_UNBOUNDED: 'BU Unbounded',
  BUSINESS:     'Business',
  ADMIN:        'Admin',
  DEVELOPER:    'Developer',
}

const roleTypeDisplayLabel = (type: string) => ROLE_TYPE_LABELS[type] ?? type

const resolveRoleName = (roleId: string) =>
  allRolesMap.value.get(roleId)?.name ?? roleId

const resolveRoleTypeLabel = (roleId: string) => {
  const type = allRolesMap.value.get(roleId)?.type
  return type ? (ROLE_TYPE_LABELS[type] ?? type) : '—'
}

const resolveRoleTagType = (roleId: string): '' | 'success' | 'warning' | 'danger' | 'info' => {
  const type = allRolesMap.value.get(roleId)?.type
  if (type === 'BU_BOUNDED')   return 'warning'
  if (type === 'BU_UNBOUNDED') return 'success'
  return ''
}

const formatDate = (d: string | undefined) => (d ? new Date(d).toLocaleString('zh-CN') : '')

// ---- fetch ----
const fetchAccessConfig = async () => {
  if (!props.functionUnitId) return
  loading.value = true
  try {
    accessList.value = await functionUnitApi.getAccessConfigs(props.functionUnitId)
  } catch (e) {
    console.error('Failed to load access configs:', e)
  } finally {
    loading.value = false
  }
}

const fetchAllRoles = async () => {
  // Skip if already loaded successfully; retry if previous attempt returned nothing.
  if (allRoles.value.length > 0) return
  rolesLoading.value = true
  try {
    const result = await roleApi.list()
    // Guard: ensure we got a real array (not an unexpected wrapper object)
    allRoles.value = Array.isArray(result) ? result : []
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
const openAddDialog = async () => {
  resetAddForm()
  showAddRole.value = true
  // Wait for roles so we can pre-select all of them once the list is ready.
  // fetchAllRoles is idempotent (cache-guarded), so this is safe to call here.
  await fetchAllRoles()
  selectedSystemRoleIds.value = availableSystemRoles.value.map(r => r.id)
  fetchBuTree()
}

const resetAddForm = () => {
  addRoleTab.value = 'bu'
  selectedSystemRoleIds.value = []  // watchEffect will re-populate all available roles
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
  if (!props.functionUnitId) return

  if (addRoleTab.value === 'system') {
    const roleIds = selectedSystemRoleIds.value.filter(id => !assignedIds.value.has(id))
    if (roleIds.length === 0) {
      ElMessage.warning('Please select at least one role')
      return
    }
    addLoading.value = true
    try {
      await Promise.all(roleIds.map(id => functionUnitApi.addAccessConfig(props.functionUnitId!, { roleId: id })))
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
      await functionUnitApi.addAccessConfig(props.functionUnitId, { roleId })
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

const handleRemove = async (access: FunctionUnitAccess) => {
  if (!props.functionUnitId) return
  try {
    await ElMessageBox.confirm('Remove this role access?', 'Confirm', { type: 'warning' })
    await functionUnitApi.removeAccessConfig(props.functionUnitId, access.id)
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
