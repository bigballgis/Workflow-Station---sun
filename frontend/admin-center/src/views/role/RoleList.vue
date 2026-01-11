<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.roleList') }}</span>
      <el-button v-if="canWriteRole" type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>{{ t('role.createRole') }}
      </el-button>
    </div>
    
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item :label="t('role.roleType')">
        <el-select v-model="query.type" clearable style="width: 150px">
          <el-option label="业务角色" value="BUSINESS" />
          <el-option label="管理角色" value="ADMIN" />
          <el-option label="开发角色" value="DEVELOPER" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>
    
    <el-table :data="sortedRoles" v-loading="roleStore.loading" stripe>
      <el-table-column prop="name" :label="t('role.roleName')" />
      <el-table-column prop="code" :label="t('role.roleCode')" />
      <el-table-column prop="type" :label="t('role.roleType')" width="120">
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.type) as any">{{ typeText(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="t('role.description')" show-overflow-tooltip />
      <el-table-column prop="memberCount" label="成员数" width="80" />
      <el-table-column prop="status" :label="t('common.status')" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="系统角色" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isSystem" type="warning" size="small">系统</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.operation')" width="180" min-width="180">
        <template #default="{ row }">
          <div style="white-space: nowrap;">
            <template v-if="!row.isSystem && canWriteRole">
              <el-button link type="primary" @click="showEditDialog(row)">{{ t('common.edit') }}</el-button>
              <el-button link type="primary" @click="showMembersDialog(row)">成员</el-button>
              <el-button v-if="canDeleteRole" link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
            </template>
            <template v-else-if="!row.isSystem">
              <el-button link type="primary" @click="showMembersDialog(row)">成员</el-button>
            </template>
            <template v-else>
              <el-button link type="primary" @click="showMembersDialog(row)">成员</el-button>
            </template>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoleStore } from '@/stores/role'
import { Role } from '@/api/role'
import { hasPermission, PERMISSIONS } from '@/utils/permission'
import RoleFormDialog from './components/RoleFormDialog.vue'
import RoleMembersDialog from './components/RoleMembersDialog.vue'

const { t } = useI18n()
const roleStore = useRoleStore()

// Permission checks
const canWriteRole = hasPermission(PERMISSIONS.ROLE_WRITE)
const canDeleteRole = hasPermission(PERMISSIONS.ROLE_DELETE)

const query = reactive({ type: '' })
const formDialogVisible = ref(false)
const membersDialogVisible = ref(false)
const currentRole = ref<Role | null>(null)

const typeText = (type: string) => ({ BUSINESS: '业务角色', ADMIN: '管理角色', DEVELOPER: '开发角色' }[type] || type)
const typeTagType = (type: string) => ({ BUSINESS: 'success', ADMIN: 'danger', DEVELOPER: 'primary' }[type] || 'info')

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
  await ElMessageBox.confirm('确定要删除该角色吗？', '提示', { type: 'warning' })
  await roleStore.deleteRole(role.id)
  ElMessage.success(t('common.success'))
}

onMounted(handleSearch)
</script>
