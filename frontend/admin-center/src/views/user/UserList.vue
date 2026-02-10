<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.userList') }}</span>
      <div class="header-actions" v-if="canWriteUser">
        <el-button @click="showImportDialog">
          <el-icon><Upload /></el-icon>{{ t('user.batchImport') }}
        </el-button>
        <el-button type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>{{ t('user.createUser') }}
        </el-button>
      </div>
    </div>
    
    <el-card class="search-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item :label="t('user.keyword')">
          <el-input v-model="query.keyword" :placeholder="t('user.keywordPlaceholder')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" :placeholder="t('user.selectStatus')" clearable style="width: 120px">
            <el-option :label="t('user.active')" value="ACTIVE" />
            <el-option :label="t('user.disabled')" value="DISABLED" />
            <el-option :label="t('user.locked')" value="LOCKED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>{{ t('common.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>{{ t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <el-card class="table-card">
      <el-table :data="users" v-loading="loading" stripe border table-layout="fixed">
        <el-table-column prop="employeeId" :label="t('user.employeeId')" min-width="100" show-overflow-tooltip />
        <el-table-column prop="username" :label="t('user.username')" min-width="100" show-overflow-tooltip />
        <el-table-column prop="fullName" :label="t('user.fullName')" min-width="80" show-overflow-tooltip />
        <el-table-column prop="email" :label="t('user.email')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="position" :label="t('user.position')" min-width="80" show-overflow-tooltip />
        <el-table-column :label="t('user.entityManager')" min-width="90" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.entityManagerName" class="manager-name">{{ row.entityManagerName }}</span>
            <span v-else class="no-manager">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('user.functionManager')" min-width="90" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.functionManagerName" class="manager-name">{{ row.functionManagerName }}</span>
            <span v-else class="no-manager">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canWriteUser" link type="primary" size="small" @click="showEditDialog(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="primary" size="small" @click="showDetailDialog(row)">{{ t('common.view') }}</el-button>
            <el-dropdown v-if="canWriteUser" @command="(cmd: string) => handleCommand(row, cmd)">
              <el-button link type="primary" size="small">
                {{ t('common.operation') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="row.status !== 'ACTIVE'" command="enable">
                    <el-icon><CircleCheck /></el-icon>{{ t('common.enable') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'ACTIVE'" command="disable">
                    <el-icon><CircleClose /></el-icon>{{ t('common.disable') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'LOCKED'" command="unlock">
                    <el-icon><Unlock /></el-icon>{{ t('user.unlock') }}
                  </el-dropdown-item>
                  <el-dropdown-item command="resetPassword">
                    <el-icon><Key /></el-icon>{{ t('user.resetPassword') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="canDeleteUser" command="delete" divided>
                    <el-icon><Delete /></el-icon>{{ t('common.delete') }}
                  </el-dropdown-item>
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
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </el-card>
    
    <!-- User form dialog -->
    <UserFormDialog 
      v-model="formDialogVisible" 
      :user="currentUser" 
      @success="handleSearch" 
    />
    
    <!-- User detail dialog -->
    <UserDetailDialog
      v-model="detailDialogVisible"
      :user-id="currentUserId"
    />
    
    <!-- Batch import dialog -->
    <UserImportDialog
      v-model="importDialogVisible"
      @success="handleSearch"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Plus, Upload, Search, Refresh, ArrowDown, 
  CircleCheck, CircleClose, Unlock, Key, Delete 
} from '@element-plus/icons-vue'
import { userApi, type User } from '@/api/user'
import { hasPermission, PERMISSIONS } from '@/utils/permission'
import UserFormDialog from './components/UserFormDialog.vue'
import UserDetailDialog from './components/UserDetailDialog.vue'
import UserImportDialog from './components/UserImportDialog.vue'

const { t } = useI18n()

// Permission checks
const canWriteUser = hasPermission(PERMISSIONS.USER_WRITE)
const canDeleteUser = hasPermission(PERMISSIONS.USER_DELETE)

// State
const loading = ref(false)
const users = ref<User[]>([])
const total = ref(0)

// Query parameters
const query = reactive({
  keyword: '',
  status: '',
  page: 1,
  size: 20
})

// Dialog state
const formDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const importDialogVisible = ref(false)
const currentUser = ref<User | null>(null)
const currentUserId = ref('')

// Status mapping
const statusType = (status: string): 'success' | 'info' | 'danger' | 'warning' => {
  const map: Record<string, 'success' | 'info' | 'danger' | 'warning'> = {
    ACTIVE: 'success',
    DISABLED: 'info',
    LOCKED: 'danger',
    PENDING: 'warning'
  }
  return map[status] || 'info'
}

const statusText = (status: string) => {
  const map: Record<string, string> = {
    ACTIVE: t('user.active'),
    DISABLED: t('user.disabled'),
    LOCKED: t('user.locked'),
    PENDING: t('user.pending')
  }
  return map[status] || status
}



// Query user list
const handleSearch = async () => {
  loading.value = true
  try {
    const params = {
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      page: query.page - 1,
      size: query.size
    }
    const result = await userApi.list(params)
    users.value = result.content
    total.value = result.totalElements
  } catch (error: any) {
    ElMessage.error(error.message || t('user.queryFailed'))
  } finally {
    loading.value = false
  }
}

// Reset query
const handleReset = () => {
  Object.assign(query, { keyword: '', status: '', page: 1 })
  handleSearch()
}

// Show create dialog
const showCreateDialog = () => {
  currentUser.value = null
  formDialogVisible.value = true
}

// Show edit dialog
const showEditDialog = (user: User) => {
  currentUser.value = user
  formDialogVisible.value = true
}

// Show detail dialog
const showDetailDialog = (user: User) => {
  currentUserId.value = user.id
  detailDialogVisible.value = true
}

// Show import dialog
const showImportDialog = () => {
  importDialogVisible.value = true
}

// Handle dropdown menu command
const handleCommand = async (user: User, command: string) => {
  switch (command) {
    case 'enable':
      await handleStatusChange(user, 'ACTIVE', t('user.enableUser'))
      break
    case 'disable':
      await handleStatusChange(user, 'DISABLED', t('user.disableUser'))
      break
    case 'unlock':
      await handleStatusChange(user, 'ACTIVE', t('user.unlockUser'))
      break
    case 'resetPassword':
      await handleResetPassword(user)
      break
    case 'delete':
      await handleDelete(user)
      break
  }
}

// Update user status
const handleStatusChange = async (user: User, status: string, action: string) => {
  try {
    await ElMessageBox.confirm(t('user.confirmAction', { action, name: user.fullName }), t('user.hint'), { type: 'warning' })
    await userApi.updateStatus(user.id, { status: status as any })
    ElMessage.success(t('user.actionSuccess', { action }))
    handleSearch()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || t('user.actionFailed', { action }))
    }
  }
}

// Reset password
const handleResetPassword = async (user: User) => {
  try {
    await ElMessageBox.confirm(t('user.confirmResetPassword', { name: user.fullName }), t('user.hint'), { type: 'warning' })
    const newPassword = await userApi.resetPassword(user.id)
    ElMessageBox.alert(t('user.newPasswordLabel', { password: newPassword }), t('user.passwordReset'), {
      confirmButtonText: t('user.copyPassword'),
      callback: () => {
        navigator.clipboard.writeText(newPassword)
        ElMessage.success(t('user.passwordCopied'))
      }
    })
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || t('user.resetPasswordFailed'))
    }
  }
}

// Delete user
const handleDelete = async (user: User) => {
  try {
    await ElMessageBox.confirm(t('user.confirmDeleteUser', { name: user.fullName }), t('user.warning'), {
      type: 'warning',
      confirmButtonText: t('user.confirmDelete'),
      confirmButtonClass: 'el-button--danger'
    })
    await userApi.delete(user.id)
    ElMessage.success(t('user.deleteSuccess'))
    handleSearch()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || t('user.deleteFailed'))
    }
  }
}

onMounted(() => {
  handleSearch()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  
  .page-title {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
  }
  
  .header-actions {
    display: flex;
    gap: 12px;
  }
}

.search-card {
  margin-bottom: 20px;
  
  .search-form {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }
}

.table-card {
  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }
  
  .manager-name {
    color: #409eff;
  }
  
  .no-manager {
    color: #c0c4cc;
  }
}
</style>
