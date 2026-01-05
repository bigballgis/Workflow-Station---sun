<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.roleList') }}</span>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>{{ t('role.createRole') }}
      </el-button>
    </div>
    
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item :label="t('role.roleType')">
        <el-select v-model="query.type" clearable>
          <el-option :label="t('role.systemRole')" value="SYSTEM" />
          <el-option :label="t('role.businessRole')" value="BUSINESS" />
          <el-option :label="t('role.functionRole')" value="FUNCTION" />
          <el-option :label="t('role.tempRole')" value="TEMPORARY" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>
    
    <el-table :data="roleStore.roles" v-loading="roleStore.loading" stripe>
      <el-table-column prop="name" :label="t('role.roleName')" />
      <el-table-column prop="code" :label="t('role.roleCode')" />
      <el-table-column prop="type" :label="t('role.roleType')">
        <template #default="{ row }">
          <el-tag>{{ typeText(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="t('role.description')" show-overflow-tooltip />
      <el-table-column prop="memberCount" label="成员数" width="100" />
      <el-table-column prop="status" :label="t('common.status')">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.operation')" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="showEditDialog(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="primary" @click="showPermissionDialog(row)">权限</el-button>
          <el-button link type="primary" @click="showMembersDialog(row)">成员</el-button>
          <el-button link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <RoleFormDialog v-model="formDialogVisible" :role="currentRole" @success="handleSearch" />
    <RolePermissionDialog v-model="permissionDialogVisible" :role="currentRole" />
    <RoleMembersDialog v-model="membersDialogVisible" :role="currentRole" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoleStore } from '@/stores/role'
import { Role } from '@/api/role'
import RoleFormDialog from './components/RoleFormDialog.vue'
import RolePermissionDialog from './components/RolePermissionDialog.vue'
import RoleMembersDialog from './components/RoleMembersDialog.vue'

const { t } = useI18n()
const roleStore = useRoleStore()

const query = reactive({ type: '' })
const formDialogVisible = ref(false)
const permissionDialogVisible = ref(false)
const membersDialogVisible = ref(false)
const currentRole = ref<Role | null>(null)

const typeText = (type: string) => ({ SYSTEM: t('role.systemRole'), BUSINESS: t('role.businessRole'), FUNCTION: t('role.functionRole'), TEMPORARY: t('role.tempRole') }[type] || type)

const handleSearch = () => roleStore.fetchRoles(query.type ? { type: query.type } : undefined)
const handleReset = () => { query.type = ''; handleSearch() }

const showCreateDialog = () => { currentRole.value = null; formDialogVisible.value = true }
const showEditDialog = (role: Role) => { currentRole.value = role; formDialogVisible.value = true }
const showPermissionDialog = (role: Role) => { currentRole.value = role; permissionDialogVisible.value = true }
const showMembersDialog = (role: Role) => { currentRole.value = role; membersDialogVisible.value = true }

const handleDelete = async (role: Role) => {
  await ElMessageBox.confirm('确定要删除该角色吗？', '提示', { type: 'warning' })
  await roleStore.deleteRole(role.id)
  ElMessage.success(t('common.success'))
}

onMounted(handleSearch)
</script>
