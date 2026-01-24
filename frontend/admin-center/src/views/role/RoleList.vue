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
    
    <el-table :data="sortedRoles" v-loading="roleStore.loading" stripe>
      <el-table-column prop="name" :label="t('role.roleName')" min-width="140" />
      <el-table-column prop="code" :label="t('role.roleCode')" min-width="120" />
      <el-table-column prop="type" :label="t('role.roleType')" width="110">
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.type) as any" size="small">{{ typeText(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="t('role.description')" min-width="150" show-overflow-tooltip />
      <el-table-column prop="memberCount" :label="t('role.memberCount')" width="70" align="center" />
      <el-table-column prop="status" :label="t('common.status')" width="70" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? t('common.enabled') : t('common.disabled') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('role.systemRole')" width="70" align="center">
        <template #default="{ row }">
          <el-icon v-if="row.isSystem" color="#E6A23C"><Lock /></el-icon>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.operation')" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-if="!row.isSystem && canWriteRole" link type="primary" @click="showEditDialog(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="primary" @click="showMembersDialog(row)">{{ t('role.members') }}</el-button>
          <el-button v-if="!row.isSystem && canWriteRole && canDeleteRole" link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
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
import { Lock } from '@element-plus/icons-vue'
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

const typeText = (type: string) => ({ 
  BU_BOUNDED: t('role.buBounded'), 
  BU_UNBOUNDED: t('role.buUnbounded'), 
  BUSINESS: t('role.businessRole'), 
  ADMIN: t('role.adminRole'), 
  DEVELOPER: t('role.developerRole') 
}[type] || type)
const typeTagType = (type: string) => ({ 
  BU_BOUNDED: 'warning', 
  BU_UNBOUNDED: 'success', 
  BUSINESS: 'success', 
  ADMIN: 'danger', 
  DEVELOPER: 'primary' 
}[type] || 'info')

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
