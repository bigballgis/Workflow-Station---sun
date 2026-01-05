<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.permissionConfig') }}</span>
    </div>
    
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card>
          <template #header>角色列表</template>
          <el-input v-model="roleFilter" placeholder="搜索角色" clearable style="margin-bottom: 15px" />
          <el-menu :default-active="selectedRoleId" @select="handleRoleSelect">
            <el-menu-item v-for="role in filteredRoles" :key="role.id" :index="role.id">
              <span>{{ role.name }}</span>
              <el-tag size="small" style="margin-left: 10px">{{ role.memberCount }}</el-tag>
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>
      
      <el-col :span="16">
        <el-card v-if="selectedRole">
          <template #header>
            <div class="permission-header">
              <span>{{ selectedRole.name }} - 权限配置</span>
              <el-button type="primary" size="small" @click="handleSave">保存配置</el-button>
            </div>
          </template>
          
          <el-table :data="permissionMatrix" border>
            <el-table-column prop="name" label="资源" width="200" />
            <el-table-column v-for="action in actions" :key="action" :label="actionText(action)" width="100" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.permissions[action]" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-empty v-else description="请选择角色配置权限" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useRoleStore } from '@/stores/role'
import { Role } from '@/api/role'

const { t } = useI18n()
const roleStore = useRoleStore()

const roleFilter = ref('')
const selectedRoleId = ref('')
const selectedRole = ref<Role | null>(null)
const actions = ['CREATE', 'READ', 'UPDATE', 'DELETE', 'EXECUTE']

const filteredRoles = computed(() => roleStore.roles.filter(r => !roleFilter.value || r.name.includes(roleFilter.value)))

const permissionMatrix = ref<any[]>([])

const actionText = (action: string) => ({ CREATE: t('permission.create'), READ: t('permission.read'), UPDATE: t('permission.update'), DELETE: t('permission.delete'), EXECUTE: t('permission.execute') }[action] || action)

const handleRoleSelect = (roleId: string) => {
  selectedRoleId.value = roleId
  selectedRole.value = roleStore.roles.find(r => r.id === roleId) || null
  // Mock permission matrix
  permissionMatrix.value = [
    { id: '1', name: '用户管理', permissions: { CREATE: true, READ: true, UPDATE: true, DELETE: false, EXECUTE: false } },
    { id: '2', name: '部门管理', permissions: { CREATE: false, READ: true, UPDATE: false, DELETE: false, EXECUTE: false } },
    { id: '3', name: '角色管理', permissions: { CREATE: false, READ: true, UPDATE: false, DELETE: false, EXECUTE: false } },
    { id: '4', name: '系统配置', permissions: { CREATE: false, READ: true, UPDATE: false, DELETE: false, EXECUTE: false } }
  ]
}

const handleSave = () => {
  ElMessage.success(t('common.success'))
}

onMounted(() => roleStore.fetchRoles())
</script>

<style scoped>
.permission-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
