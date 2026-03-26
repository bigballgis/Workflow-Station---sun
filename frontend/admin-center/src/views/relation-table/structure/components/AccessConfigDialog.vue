<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    :title="'Access Config - ' + (tableName || '')"
    width="700px"
  >
    <div class="access-config-header">
      <el-alert type="info" :closable="false" style="flex: 1; margin-right: 12px;">
        Configure which Business Roles can access this table in User Portal.
      </el-alert>
      <el-button type="primary" size="small" @click="showAddRole = true">
        <el-icon><Plus /></el-icon>Add Role
      </el-button>
    </div>

    <el-table :data="accessList" stripe v-loading="loading" empty-text="No access configured">
      <el-table-column prop="targetId" label="Business Role" min-width="200">
        <template #default="{ row }">
          {{ getRoleName(row.targetId) || row.targetId }}
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="Created At" width="180">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
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
    <el-dialog v-model="showAddRole" title="Select Business Role" width="500px" append-to-body>
      <el-form label-width="120px" label-position="left">
        <el-form-item label="Business Role" required>
          <el-select v-model="selectedRoleId" filterable placeholder="Select a role" style="width: 100%;">
            <el-option
              v-for="role in availableRoles"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
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
import { roleApi, type Role } from '@/api/role'

const props = defineProps<{
  modelValue: boolean
  tableId?: number
  tableName?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const loading = ref(false)
const addLoading = ref(false)
const showAddRole = ref(false)
const selectedRoleId = ref('')
const accessList = ref<RelationTableAccess[]>([])
const businessRoles = ref<Role[]>([])

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

const getRoleName = (roleId: string) => {
  return businessRoles.value.find(r => r.id === roleId)?.name
}

const availableRoles = computed(() => {
  const assignedIds = new Set(accessList.value.map(a => a.targetId))
  return businessRoles.value.filter(r => !assignedIds.has(r.id))
})

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

const fetchBusinessRoles = async () => {
  try {
    businessRoles.value = await roleApi.getBusinessRoles()
  } catch (e) {
    console.error('Failed to load business roles:', e)
  }
}

const handleAddRole = async () => {
  if (!selectedRoleId.value) {
    ElMessage.warning('Please select a role')
    return
  }
  if (!props.tableId) return

  addLoading.value = true
  try {
    await relationTableStructureApi.addAccess(props.tableId, selectedRoleId.value)
    ElMessage.success('Access added')
    showAddRole.value = false
    selectedRoleId.value = ''
    await fetchAccessConfig()
  } catch (e) {
    console.error('Failed to add access:', e)
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
    if (e !== 'cancel') {
      console.error('Failed to remove access:', e)
    }
  }
}

watch(() => props.modelValue, (val) => {
  if (val) {
    fetchAccessConfig()
    fetchBusinessRoles()
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
</style>
