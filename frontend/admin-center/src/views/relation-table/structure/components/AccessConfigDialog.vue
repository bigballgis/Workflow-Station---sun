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
          <p class="tab-hint">Select a system-level or unbounded business role to grant access.</p>
          <el-select
            v-model="selectedSystemRoleId"
            filterable
            placeholder="Select a role"
            style="width: 100%; margin-top: 8px;"
            :loading="rolesLoading"
          >
            <el-option
              v-for="role in availableSystemRoles"
              :key="role.id"
              :label="`${role.name}  (${roleTypeDisplayLabel(role.type)})`"
              :value="role.id"
            />
          </el-select>
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
                  :key="item.roleId"
                  :label="item.roleName || item.roleCode || item.roleId"
                  :value="item.roleId"
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
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { relationTableStructureApi, type RelationTableAccess } from '@/api/relationTable'
import { roleApi, type Role, type RoleType } from '@/api/role'
import { businessUnitApi, type BusinessUnit, type BusinessUnitRole } from '@/api/businessUnit'

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
const selectedSystemRoleId = ref('')

// BU tab
const selectedBuId = ref<string | null>(null)
const selectedBuRoleId = ref('')
const buCascaderOptions = ref<BusinessUnit[]>([])
const buRoles = ref<BusinessUnitRole[]>([])
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
  allRoles.value.filter(r => r.status === 'ACTIVE' && !assignedIds.value.has(r.id))
)

const availableBuRoles = computed(() =>
  buRoles.value.filter(r => !assignedIds.value.has(r.roleId))
)

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
  selectedSystemRoleId.value = ''
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
  const roleId = addRoleTab.value === 'system' ? selectedSystemRoleId.value : selectedBuRoleId.value
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
    ElMessage.error(e?.response?.data?.message || 'Failed to add access')
  } finally {
    addLoading.value = false
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
  margin: 0 0 4px;
  line-height: 1.5;
}
</style>
