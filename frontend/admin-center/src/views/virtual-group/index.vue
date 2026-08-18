<template>
  <div class="page-container">
    <PageHeader :title="t('menu.virtualGroup')">
      <template #actions>
        <el-button
          v-if="!readOnly"
          type="primary"
          @click="showCreateDialog"
        >
          <el-icon><Plus /></el-icon>{{ t('virtualGroup.create') }}
        </el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab">
      <el-tab-pane
        :label="t('virtualGroup.tabSystem')"
        name="SYSTEM"
      />
      <el-tab-pane
        :label="t('virtualGroup.tabCustom')"
        name="CUSTOM"
      />
      <el-tab-pane
        :label="t('virtualGroup.tabDeveloper')"
        name="DEVELOPER"
      />
    </el-tabs>

    <div style="margin-bottom: 16px; display: flex; align-items: center; gap: 12px;">
      <el-input
        v-model="searchKeyword"
        :placeholder="t('virtualGroup.searchPlaceholder')"
        clearable
        style="width: 300px;"
      />
    </div>

    <el-empty
      v-if="!loading && listTotal === 0"
      :description="searchKeyword.trim() ? t('virtualGroup.noSearchResults') : t('virtualGroup.noGroupsInTab')"
    />

    <el-table
      v-else
      v-loading="loading"
      :data="pagedGroups"
      stripe
      table-layout="auto"
      style="width: 100%"
    >
      <el-table-column
        prop="name"
        :label="t('virtualGroup.name')"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        prop="code"
        :label="t('virtualGroup.code')"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        prop="type"
        :label="t('virtualGroup.type')"
        width="120"
      >
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.type)">
            {{ t(virtualGroupTypeKey(row.type)) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="boundRoleName"
        :label="t('virtualGroup.boundRole')"
        min-width="220"
      >
        <template #default="{ row }">
          <template v-if="row.boundRoleName">
            <span>{{ row.boundRoleName }}</span>
            <el-tag
              size="small"
              :type="row.boundRoleType === 'BU_BOUNDED' ? 'warning' : 'success'"
              style="margin-left: 6px"
            >
              {{ t(roleTypeKey(row.boundRoleType)) }}
            </el-tag>
          </template>
          <span
            v-else
            class="text-muted"
          >-</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="adGroup"
        :label="t('virtualGroup.adGroup')"
        min-width="140"
      >
        <template #default="{ row }">
          <span v-if="row.adGroup">{{ row.adGroup }}</span>
          <span
            v-else
            class="text-muted"
          >-</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="memberCount"
        :label="t('virtualGroup.memberCount')"
        width="100"
        align="center"
        :show-overflow-tooltip="false"
        class-name="no-wrap-header"
      />
      <el-table-column
        prop="status"
        :label="t('virtualGroup.status')"
        width="100"
        :show-overflow-tooltip="false"
        class-name="no-wrap-header"
      >
        <template #default="{ row }">
          <el-tag
            :type="row.status === 'ACTIVE' ? 'success' : 'info'"
            size="small"
          >
            {{ row.status === 'ACTIVE' ? t('virtualGroup.active') : t('virtualGroup.inactive') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('common.operation')"
        width="400"
        fixed="right"
      >
        <template #default="{ row }">
          <div style="display: flex; align-items: center; flex-wrap: nowrap; white-space: nowrap;">
            <el-button
              v-if="!readOnly"
              link
              type="primary"
              @click="showEditDialog(row)"
            >
              {{ t('virtualGroup.edit') }}
            </el-button>
            <el-button
              link
              type="primary"
              @click="showMembersDialog(row)"
            >
              {{ t('virtualGroup.members') }}
            </el-button>
            <el-button
              link
              type="primary"
              @click="showRolesDialog(row)"
            >
              {{ t('virtualGroup.bindRoles') }}
            </el-button>
            <el-button
              v-if="!readOnly && row.type !== 'SYSTEM'"
              link
              type="primary"
              :loading="statusToggleLoadingId === row.id"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? t('virtualGroup.deactivate') : t('virtualGroup.activate') }}
            </el-button>
            <el-button
              v-if="!readOnly && row.type !== 'SYSTEM'"
              link
              type="danger"
              @click="handleDelete(row.id)"
            >
              {{ t('virtualGroup.delete') }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="listTotal > 0"
      v-model:current-page="listPagination.page"
      v-model:page-size="listPagination.size"
      :disabled="loading"
      :total="listTotal"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      style="margin-top: 16px; justify-content: flex-end;"
      @size-change="handleListSizeChange"
    />

    <VirtualGroupFormDialog
      v-model="formDialogVisible"
      :group="currentGroup"
      @success="handleCreateSuccess"
    />
    <VirtualGroupMembersDialog
      v-model="membersDialogVisible"
      :group="currentGroup"
      :read-only="readOnly"
    />
    <VirtualGroupRolesDialog
      v-model="rolesDialogVisible"
      :group="currentGroup"
      :read-only="readOnly"
      @success="fetchGroups"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import VirtualGroupFormDialog from './components/VirtualGroupFormDialog.vue'
import VirtualGroupMembersDialog from './components/VirtualGroupMembersDialog.vue'
import VirtualGroupRolesDialog from './components/VirtualGroupRolesDialog.vue'
import { useVirtualGroup } from '@/composables/modules/useVirtualGroup'
import { virtualGroupTypeKey, roleTypeKey } from '@/utils/format'
import { hasPermission, PERMISSIONS } from '@/utils/permission'

const { t } = useI18n()
const readOnly = computed(() => !hasPermission(PERMISSIONS.USER_WRITE))

const {
  loading,
  activeTab,
  searchKeyword,
  listPagination,
  listTotal,
  pagedGroups,
  formDialogVisible,
  membersDialogVisible,
  rolesDialogVisible,
  currentGroup,
  statusToggleLoadingId,
  fetchGroups,
  showCreateDialog,
  showEditDialog,
  showMembersDialog,
  showRolesDialog,
  handleDelete,
  handleCreateSuccess,
  handleListSizeChange,
  handleToggleStatus,
} = useVirtualGroup()

function typeTagType(type: string): 'warning' | 'info' | 'success' {
  if (type === 'SYSTEM') return 'warning'
  if (type === 'DEVELOPER') return 'success'
  return 'info'
}

onMounted(fetchGroups)
</script>

<style scoped>

.page-container :deep(.no-wrap-header .cell) {
  white-space: nowrap !important;
  overflow: visible !important;
}
</style>
