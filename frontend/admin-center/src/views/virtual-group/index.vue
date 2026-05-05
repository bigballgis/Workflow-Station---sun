<template>
  <div class="page-container">
    <PageHeader :title="t('menu.virtualGroup')">
      <template #actions>
        <el-button type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>{{ t('virtualGroup.create') }}
        </el-button>
      </template>
    </PageHeader>
    
    <el-table :data="groups" v-loading="loading" stripe table-layout="auto" style="width: 100%">
      <el-table-column prop="name" :label="t('virtualGroup.name')" min-width="160" show-overflow-tooltip />
      <el-table-column prop="code" :label="t('virtualGroup.code')" min-width="160" show-overflow-tooltip />
      <el-table-column prop="type" :label="t('virtualGroup.type')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.type === 'SYSTEM' ? 'warning' : 'info'">{{ t(virtualGroupTypeKey(row.type)) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="boundRoleName" :label="t('virtualGroup.boundRole')" min-width="220">
        <template #default="{ row }">
          <template v-if="row.boundRoleName">
            <span>{{ row.boundRoleName }}</span>
            <el-tag size="small" :type="row.boundRoleType === 'BU_BOUNDED' ? 'warning' : 'success'" style="margin-left: 6px">
              {{ t(roleTypeKey(row.boundRoleType)) }}
            </el-tag>
          </template>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="adGroup" :label="t('virtualGroup.adGroup')" min-width="140">
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
      <el-table-column :label="t('common.operation')" width="400" fixed="right">
        <template #default="{ row }">
          <div style="display: flex; align-items: center; flex-wrap: nowrap; white-space: nowrap;">
            <el-button link type="primary" @click="showEditDialog(row)">{{ t('virtualGroup.edit') }}</el-button>
            <el-button link type="primary" @click="showMembersDialog(row)">{{ t('virtualGroup.members') }}</el-button>
            <el-button link type="primary" @click="showRolesDialog(row)">{{ t('virtualGroup.bindRoles') }}</el-button>
            <el-button link type="primary" @click="showApproversDialog(row)">{{ t('virtualGroup.approvers') }}</el-button>
            <el-button v-if="row.type !== 'SYSTEM'" link type="danger" @click="handleDelete(row.id)">{{ t('virtualGroup.delete') }}</el-button>
          </div>
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
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import VirtualGroupFormDialog from './components/VirtualGroupFormDialog.vue'
import VirtualGroupMembersDialog from './components/VirtualGroupMembersDialog.vue'
import VirtualGroupRolesDialog from './components/VirtualGroupRolesDialog.vue'
import VirtualGroupApproversDialog from './components/VirtualGroupApproversDialog.vue'
import { useVirtualGroup } from '@/composables/modules/useVirtualGroup'
import { virtualGroupTypeKey, roleTypeKey } from '@/utils/format'

const { t } = useI18n()

const {
  loading,
  groups,
  formDialogVisible,
  membersDialogVisible,
  rolesDialogVisible,
  approversDialogVisible,
  currentGroup,
  fetchGroups,
  showCreateDialog,
  showEditDialog,
  showMembersDialog,
  showRolesDialog,
  showApproversDialog,
  handleDelete,
} = useVirtualGroup()

onMounted(fetchGroups)
</script>

<style scoped>

.page-container :deep(.no-wrap-header .cell) {
  white-space: nowrap !important;
  overflow: visible !important;
}
</style>
