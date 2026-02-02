<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.permissionConfig') }}</span>
    </div>
    
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card>
          <template #header>{{ t('role.roleList') }}</template>
          <el-input v-model="roleFilter" :placeholder="t('role.searchRole')" clearable style="margin-bottom: 15px" />
          <el-menu :default-active="selectedRoleId" @select="handleRoleSelect">
            <el-menu-item v-for="role in filteredRoles" :key="role.id" :index="role.id">
              <span>{{ role.name }}</span>
              <el-tag size="small" style="margin-left: 10px">{{ role.memberCount }}</el-tag>
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>
      
      <el-col :span="16">
        <el-card v-if="selectedRole" v-loading="loading">
          <template #header>
            <div class="permission-header">
              <span>{{ selectedRole.name }} - 权限配置</span>
              <el-button type="primary" size="small" @click="handleSave" :disabled="loading">保存配置</el-button>
            </div>
          </template>
          
          <el-table :data="permissionMatrix" border>
            <el-table-column prop="name" label="资源" width="200" />
            <el-table-column v-for="action in actions" :key="action" :label="actionText(action)" width="100" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.permissions[action]" :disabled="loading" />
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
import { Role, roleApi, permissionApi } from '@/api/role'

const { t } = useI18n()
const roleStore = useRoleStore()

const roleFilter = ref('')
const selectedRoleId = ref('')
const selectedRole = ref<Role | null>(null)
const actions = ['CREATE', 'READ', 'UPDATE', 'DELETE', 'EXECUTE']
const loading = ref(false)

const filteredRoles = computed(() => roleStore.roles.filter(r => !roleFilter.value || r.name.includes(roleFilter.value)))

const permissionMatrix = ref<any[]>([])
const allPermissions = ref<any[]>([])

const actionText = (action: string) => ({ CREATE: t('permission.create'), READ: t('permission.read'), UPDATE: t('permission.update'), DELETE: t('permission.delete'), EXECUTE: t('permission.execute') }[action] || action)

const handleRoleSelect = async (roleId: string) => {
  selectedRoleId.value = roleId
  selectedRole.value = roleStore.roles.find(r => r.id === roleId) || null
  
  if (!selectedRole.value) return
  
  loading.value = true
  try {
    // 获取角色的权限
    const rolePermissions = await roleApi.getPermissions(roleId)
    
    // 构建权限矩阵
    permissionMatrix.value = allPermissions.value.map(permission => {
      const rolePermission = rolePermissions.find((rp: any) => rp.permissionId === permission.id)
      const permissionActions = rolePermission?.actions || []
      
      return {
        id: permission.id,
        name: permission.name,
        permissions: {
          CREATE: permissionActions.includes('CREATE'),
          READ: permissionActions.includes('READ'),
          UPDATE: permissionActions.includes('UPDATE'),
          DELETE: permissionActions.includes('DELETE'),
          EXECUTE: permissionActions.includes('EXECUTE')
        }
      }
    })
  } catch (error) {
    console.error('Failed to load role permissions:', error)
    ElMessage.error('加载角色权限失败')
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  if (!selectedRole.value) return
  
  loading.value = true
  try {
    // 构建权限配置数据
    const permissions = permissionMatrix.value
      .filter(item => Object.values(item.permissions).some(v => v))
      .map(item => ({
        roleId: selectedRole.value!.id,
        permissionId: item.id,
        actions: Object.entries(item.permissions)
          .filter(([_, enabled]) => enabled)
          .map(([action]) => action)
      }))
    
    await roleApi.updatePermissions(selectedRole.value.id, permissions)
    ElMessage.success(t('common.success'))
  } catch (error) {
    console.error('Failed to save permissions:', error)
    ElMessage.error('保存权限配置失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await roleStore.fetchRoles()
  
  // 加载所有权限
  try {
    allPermissions.value = await permissionApi.getTree()
  } catch (error) {
    console.error('Failed to load permissions:', error)
    ElMessage.error('加载权限列表失败')
  }
})
</script>

<style scoped>
.permission-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
