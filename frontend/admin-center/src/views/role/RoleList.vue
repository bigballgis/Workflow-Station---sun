<template>
  <div class="page-container">
    <PageHeader :title="t('menu.roleList')">
      <template #actions>
        <el-button v-if="canWriteRole" type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>{{ t('role.createRole') }}
        </el-button>
      </template>
    </PageHeader>
    
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item :label="t('role.roleType')">
        <el-select v-model="query.type" clearable style="width: 150px">
          <el-option :label="t('role.buBounded')" value="BU_BOUNDED" />
          <el-option :label="t('role.buUnbounded')" value="BU_UNBOUNDED" />
          <el-option :label="t('role.adminRole')" value="ADMIN" />
          <el-option :label="t('role.developerRole')" value="DEVELOPER" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>
    
    <el-table :data="sortedRoles" v-loading="roleStore.loading" stripe table-layout="auto" style="width: 100%">
      <el-table-column prop="name" :label="t('role.roleName')" min-width="160">
        <template #default="{ row }">
          <el-tooltip :content="row.description || '-'" placement="top-start" :disabled="!row.description" popper-class="role-desc-tooltip">
            <span style="cursor: default">{{ row.name }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="code" :label="t('role.roleCode')" min-width="140" />
      <el-table-column prop="type" :label="t('role.roleType')" width="130" align="center">
        <template #default="{ row }">
          <el-tag :type="roleTypeTagType(row.type) as any" size="small">{{ t(roleTypeKey(row.type)) }}</el-tag>
        </template>
      </el-table-column>
      <!-- <el-table-column prop="description" :label="t('role.description')" min-width="150" show-overflow-tooltip /> -->
      <!-- <el-table-column prop="memberCount" :label="t('role.memberCount')" width="100" align="center" :show-overflow-tooltip="false" class-name="no-wrap-header" /> -->
      <el-table-column prop="status" :label="t('common.status')" width="100" align="center" :show-overflow-tooltip="false" class-name="no-wrap-header">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? t('common.enabled') : t('common.disabled') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('role.systemRole')" width="110" align="center" :show-overflow-tooltip="false" class-name="no-wrap-header">
        <template #default="{ row }">
          <el-icon v-if="row.isSystem" color="#E6A23C"><Lock /></el-icon>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.operation')" width="220" fixed="right" align="center">
        <template #default="{ row }">
          <div style="display: flex; align-items: center; justify-content: center; flex-wrap: nowrap; white-space: nowrap; gap: 4px;">
            <el-button v-if="!row.isSystem && canWriteRole" link type="primary" @click="showEditDialog(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="primary" @click="showMembersDialog(row)">{{ t('role.members') }}</el-button>
            <el-button v-if="!row.isSystem && canWriteRole && canDeleteRole" link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    
    <RoleFormDialog v-model="formDialogVisible" :role="currentRole" @success="handleSearch" />
    <RoleMembersDialog v-model="membersDialogVisible" :role="currentRole" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { useRoleStore } from '@/stores/role'
import { Role } from '@/api/role'
import { hasPermission, PERMISSIONS } from '@/utils/permission'
import { roleTypeTagType, roleTypeKey } from '@/utils/format'
import RoleFormDialog from './components/RoleFormDialog.vue'
import RoleMembersDialog from './components/RoleMembersDialog.vue'
import { useTabRefresh } from '@/composables/useTabRefresh'

const { t } = useI18n()
const roleStore = useRoleStore()

// Permission checks
const canWriteRole = hasPermission(PERMISSIONS.ROLE_WRITE)
const canDeleteRole = hasPermission(PERMISSIONS.ROLE_DELETE)

const query = reactive({ type: '' })
const formDialogVisible = ref(false)
const membersDialogVisible = ref(false)
const currentRole = ref<Role | null>(null)

// 排序：非系统角色在前，系统角色在后
const sortedRoles = computed(() => {
  return [...roleStore.roles].sort((a, b) => {
    if (a.isSystem === b.isSystem) return 0
    return a.isSystem ? 1 : -1
  })
})

const handleSearch = () => roleStore.fetchRoles(query.type ? { type: query.type } : undefined)
const handleReset = () => { query.type = ''; handleSearch() }

const showCreateDialog = () => { currentRole.value = null; formDialogVisible.value = true }
const showEditDialog = (role: Role) => { currentRole.value = role; formDialogVisible.value = true }
const showMembersDialog = (role: Role) => { currentRole.value = role; membersDialogVisible.value = true }

const handleDelete = async (role: Role) => {
  await ElMessageBox.confirm(t('role.confirmDeleteRole'), t('user.hint'), { type: 'warning' })
  await roleStore.deleteRole(role.id)
  ElMessage.success(t('common.success'))
}

useTabRefresh(handleSearch)

onMounted(() => {
  handleSearch()
})
</script>

<style scoped>
.page-container :deep(.no-wrap-header .cell) {
  white-space: nowrap !important;
  overflow: visible !important;
}
</style>

<style>
.role-desc-tooltip.el-popper {
  background-color: #737373 !important;
  color: #ffffff !important;
  border: 1px solid #808080 !important;
}
.role-desc-tooltip.el-popper .el-popper__arrow::before {
  background-color: #737373 !important;
  border-color: #808080 !important;
}
</style>
