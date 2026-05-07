<template>
  <div class="page-container">
    <PageHeader :title="t('bi.rbac.pageTitle')">
      <template #actions>
        <el-button type="success" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>{{ t('bi.rbac.createMapping') }}
        </el-button>
        <el-button type="primary" :loading="syncing" @click="handleSync">
          <el-icon><Refresh /></el-icon>{{ t('bi.rbac.syncRoles') }}
        </el-button>
      </template>
    </PageHeader>

    <el-card class="search-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item :label="t('bi.rbac.searchRoleName')">
          <el-input v-model="query.roleName" :placeholder="t('bi.rbac.searchRoleNamePlaceholder')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="t('bi.rbac.filterRoleType')">
          <el-select v-model="query.roleType" :placeholder="t('bi.rbac.filterRoleTypePlaceholder')" clearable style="width: 160px">
            <el-option :label="t('role.adminRole')" value="ADMIN" />
            <el-option :label="t('role.developerRole')" value="DEVELOPER" />
            <el-option :label="t('role.buBounded')" value="BU_BOUNDED" />
            <el-option :label="t('role.buUnbounded')" value="BU_UNBOUNDED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>{{ t('common.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><RefreshIcon /></el-icon>{{ t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="mappings" v-loading="loading" stripe border table-layout="auto" style="width: 100%">
        <el-table-column prop="sysRoleName" :label="t('bi.rbac.colSystemRole')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="sysRoleCode" :label="t('bi.rbac.colRoleCode')" min-width="140" show-overflow-tooltip />
        <el-table-column :label="t('bi.rbac.colRoleType')" width="140" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ t(roleTypeKey(row.sysRoleType)) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('bi.rbac.colSupersetRoles')" min-width="220">
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
        <el-table-column prop="lastUpdatedAt" :label="t('bi.rbac.colLastUpdated')" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.lastUpdatedAt">{{ row.lastUpdatedAt }}</span>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('bi.rbac.colActions')" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showEditDialog(row)">{{ t('bi.rbac.editMapping') }}</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">{{ t('bi.rbac.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <RbacCreateDialog
      ref="createDialogRef"
      v-model="createDialogVisible"
      :create-form="createForm"
      :create-form-rules="createFormRules"
      :unmapped-roles="unmappedRoles"
      :unmapped-roles-loading="unmappedRolesLoading"
      :create-active-superset-roles="createActiveSupersetRoles"
      :create-superset-roles-loading="createSupersetRolesLoading"
      :create-loading="createLoading"
      @submit="handleCreateSubmit"
    />

    <RbacEditDialog
      v-model="editDialogVisible"
      :edit-form="editForm"
      :active-superset-roles="activeSupersetRoles"
      :superset-roles-loading="supersetRolesLoading"
      :edit-loading="editLoading"
      @submit="handleEditSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onActivated } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Search, Refresh as RefreshIcon } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { useBiRbac } from '@/composables/modules/useBiRbac'
import { roleTypeKey } from '@/utils/format'
import RbacCreateDialog from './components/RbacCreateDialog.vue'
import RbacEditDialog from './components/RbacEditDialog.vue'

const { t } = useI18n()

const {
  loading, syncing, editLoading, supersetRolesLoading, mappings, allSupersetRoles,
  query, editDialogVisible, editForm, activeSupersetRoles,
  createDialogVisible, createLoading, unmappedRolesLoading, createSupersetRolesLoading,
  unmappedRoles, createAllSupersetRoles, createDialogRef, createForm, createFormRules, createActiveSupersetRoles,
  handleSearch, handleReset, handleSync,
  showEditDialog, handleEditSubmit,
  showCreateDialog, handleCreateSubmit, handleDelete,
} = useBiRbac()

onMounted(() => { handleSearch() })
onActivated(() => { handleSearch() })
</script>

<style scoped lang="scss">
.role-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}
</style>
