<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.userList') }}</span>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>{{ t('user.createUser') }}
      </el-button>
    </div>
    
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item :label="t('user.username')">
        <el-input v-model="query.username" clearable />
      </el-form-item>
      <el-form-item :label="t('user.realName')">
        <el-input v-model="query.realName" clearable />
      </el-form-item>
      <el-form-item :label="t('user.status')">
        <el-select v-model="query.status" clearable>
          <el-option :label="t('user.enabled')" value="ENABLED" />
          <el-option :label="t('user.disabled')" value="DISABLED" />
          <el-option :label="t('user.locked')" value="LOCKED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>
    
    <el-table :data="userStore.users" v-loading="userStore.loading" stripe>
      <el-table-column prop="username" :label="t('user.username')" />
      <el-table-column prop="realName" :label="t('user.realName')" />
      <el-table-column prop="email" :label="t('user.email')" />
      <el-table-column prop="phone" :label="t('user.phone')" />
      <el-table-column prop="departmentName" :label="t('user.department')" />
      <el-table-column prop="status" :label="t('user.status')">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.operation')" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="showEditDialog(row)">{{ t('common.edit') }}</el-button>
          <el-dropdown @command="(cmd: string) => handleStatusChange(row, cmd)">
            <el-button link type="primary">更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="row.status !== 'ENABLED'" command="enable">启用</el-dropdown-item>
                <el-dropdown-item v-if="row.status === 'ENABLED'" command="disable">禁用</el-dropdown-item>
                <el-dropdown-item v-if="row.status === 'LOCKED'" command="unlock">解锁</el-dropdown-item>
                <el-dropdown-item command="resetPassword">{{ t('user.resetPassword') }}</el-dropdown-item>
                <el-dropdown-item command="delete" divided>{{ t('common.delete') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
    
    <div class="pagination-container">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="userStore.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </div>
    
    <UserFormDialog v-model="dialogVisible" :user="currentUser" @success="handleSearch" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { User } from '@/api/user'
import UserFormDialog from './components/UserFormDialog.vue'

const { t } = useI18n()
const userStore = useUserStore()

const query = reactive({ username: '', realName: '', status: '', page: 1, size: 10 })
const dialogVisible = ref(false)
const currentUser = ref<User | null>(null)

const statusType = (status: string) => ({ ENABLED: 'success', DISABLED: 'info', LOCKED: 'danger' }[status] || 'info')
const statusText = (status: string) => ({ ENABLED: t('user.enabled'), DISABLED: t('user.disabled'), LOCKED: t('user.locked') }[status] || status)

const handleSearch = () => userStore.fetchUsers(query)
const handleReset = () => {
  Object.assign(query, { username: '', realName: '', status: '', page: 1 })
  handleSearch()
}

const showCreateDialog = () => { currentUser.value = null; dialogVisible.value = true }
const showEditDialog = (user: User) => { currentUser.value = user; dialogVisible.value = true }

const handleStatusChange = async (user: User, command: string) => {
  if (command === 'delete') {
    await ElMessageBox.confirm('确定要删除该用户吗？', '提示', { type: 'warning' })
    await userStore.deleteUser(user.id)
    ElMessage.success(t('common.success'))
    handleSearch()
  } else if (command === 'resetPassword') {
    const { value } = await ElMessageBox.prompt('请输入新密码', t('user.resetPassword'), { inputType: 'password' })
    // await userApi.resetPassword(user.id, value)
    ElMessage.success(t('common.success'))
  } else {
    await userStore.changeStatus(user.id, command as any)
    ElMessage.success(t('common.success'))
    handleSearch()
  }
}

onMounted(handleSearch)
</script>
