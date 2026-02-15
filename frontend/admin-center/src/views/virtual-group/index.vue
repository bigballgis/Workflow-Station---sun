<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.virtualGroup') }}</span>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>{{ t('virtualGroup.create') }}
      </el-button>
    </div>
    
    <el-table :data="groups" v-loading="loading" stripe>
      <el-table-column prop="name" :label="t('virtualGroup.name')" min-width="160" />
      <el-table-column prop="code" :label="t('virtualGroup.code')" min-width="120" />
      <el-table-column prop="type" :label="t('virtualGroup.type')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.type === 'SYSTEM' ? 'warning' : 'info'">{{ typeText(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="boundRoleName" :label="t('virtualGroup.boundRole')" min-width="150">
        <template #default="{ row }">
          <template v-if="row.boundRoleName">
            <span>{{ row.boundRoleName }}</span>
            <el-tag size="small" :type="row.boundRoleType === 'BU_BOUNDED' ? 'warning' : 'success'" style="margin-left: 6px">
              {{ roleTypeText(row.boundRoleType) }}
            </el-tag>
          </template>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="adGroup" :label="t('virtualGroup.adGroup')" min-width="150">
        <template #default="{ row }">
          <span v-if="row.adGroup">{{ row.adGroup }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="memberCount" :label="t('virtualGroup.memberCount')" width="100" align="center" :show-overflow-tooltip="false" class-name="no-wrap-header" />
      <el-table-column prop="status" :label="t('virtualGroup.status')" width="100" :show-overflow-tooltip="false" class-name="no-wrap-header">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? t('virtualGroup.active') : t('virtualGroup.inactive') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.operation')" width="340" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showEditDialog(row)">{{ t('virtualGroup.edit') }}</el-button>
          <el-button link type="primary" @click="showMembersDialog(row)">{{ t('virtualGroup.members') }}</el-button>
          <el-button link type="primary" @click="showRolesDialog(row)">{{ t('virtualGroup.bindRoles') }}</el-button>
          <el-button link type="primary" @click="showApproversDialog(row)">{{ t('virtualGroup.approvers') }}</el-button>
          <el-button v-if="row.type !== 'SYSTEM'" link type="danger" @click="handleDelete(row)">{{ t('virtualGroup.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <VirtualGroupFormDialog v-model="formDialogVisible" :group="currentGroup" @success="fetchGroups" />
    <VirtualGroupMembersDialog v-model="membersDialogVisible" :group="currentGroup" />
    <VirtualGroupRolesDialog v-model="rolesDialogVisible" :group="currentGroup" @success="fetchGroups" />
    <VirtualGroupApproversDialog v-model="approversDialogVisible" :group="currentGroup" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import VirtualGroupFormDialog from './components/VirtualGroupFormDialog.vue'
import VirtualGroupMembersDialog from './components/VirtualGroupMembersDialog.vue'
import VirtualGroupRolesDialog from './components/VirtualGroupRolesDialog.vue'
import VirtualGroupApproversDialog from './components/VirtualGroupApproversDialog.vue'
import { virtualGroupApi, type VirtualGroup } from '@/api/virtualGroup'

const { t } = useI18n()

const loading = ref(false)
const groups = ref<VirtualGroup[]>([])
const formDialogVisible = ref(false)
const membersDialogVisible = ref(false)
const rolesDialogVisible = ref(false)
const approversDialogVisible = ref(false)
const currentGroup = ref<VirtualGroup | null>(null)

const typeText = (type: string) => ({ SYSTEM: t('virtualGroup.typeSystem'), CUSTOM: t('virtualGroup.typeCustom') }[type] || type)
const roleTypeText = (type: string) => ({ 
  BU_BOUNDED: t('role.buBounded'), 
  BU_UNBOUNDED: t('role.buUnbounded'),
  ADMIN: t('role.adminRole'),
  DEVELOPER: t('role.developerRole')
}[type] || type)

const fetchGroups = async () => {
  loading.value = true
  try {
    groups.value = await virtualGroupApi.list()
  } catch (e) {
    console.error('Failed to load virtual groups:', e)
    ElMessage.error(t('common.failed'))
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => { currentGroup.value = null; formDialogVisible.value = true }
const showEditDialog = (group: VirtualGroup) => { currentGroup.value = group; formDialogVisible.value = true }
const showMembersDialog = (group: VirtualGroup) => { currentGroup.value = group; membersDialogVisible.value = true }
const showRolesDialog = (group: VirtualGroup) => { currentGroup.value = group; rolesDialogVisible.value = true }
const showApproversDialog = (group: VirtualGroup) => { currentGroup.value = group; approversDialogVisible.value = true }

const handleDelete = async (group: VirtualGroup) => {
  await ElMessageBox.confirm(t('common.confirm'), t('common.confirm'), { type: 'warning' })
  try {
    await virtualGroupApi.delete(group.id)
    ElMessage.success(t('common.success'))
    fetchGroups()
  } catch (e) {
    console.error('Failed to delete group:', e)
    ElMessage.error(t('common.failed'))
  }
}

onMounted(fetchGroups)
</script>

<style scoped>
.text-muted {
  color: #909399;
}

.page-container :deep(.no-wrap-header .cell) {
  white-space: nowrap !important;
  overflow: visible !important;
}
</style>
