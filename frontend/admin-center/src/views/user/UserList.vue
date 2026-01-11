<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.userList') }}</span>
      <div class="header-actions" v-if="canWriteUser">
        <el-button @click="showImportDialog">
          <el-icon><Upload /></el-icon>批量导入
        </el-button>
        <el-button type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>{{ t('user.createUser') }}
        </el-button>
      </div>
    </div>
    
    <el-card class="search-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="用户名/姓名/邮箱" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.departmentId" placeholder="选择部门" clearable style="width: 150px">
            <el-option v-for="dept in departments" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="选择状态" clearable style="width: 120px">
            <el-option label="活跃" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
            <el-option label="锁定" value="LOCKED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <el-card class="table-card">
      <el-table :data="users" v-loading="loading" stripe border table-layout="fixed">
        <el-table-column prop="employeeId" label="员工编号" min-width="100" show-overflow-tooltip />
        <el-table-column prop="username" label="用户名" min-width="100" show-overflow-tooltip />
        <el-table-column prop="fullName" label="姓名" min-width="80" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column prop="departmentName" label="部门" min-width="100" show-overflow-tooltip />
        <el-table-column prop="position" label="职位" min-width="80" show-overflow-tooltip />
        <el-table-column label="实体管理者" min-width="90" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.entityManagerName" class="manager-name">{{ row.entityManagerName }}</span>
            <span v-else class="no-manager">-</span>
          </template>
        </el-table-column>
        <el-table-column label="职能管理者" min-width="90" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.functionManagerName" class="manager-name">{{ row.functionManagerName }}</span>
            <span v-else class="no-manager">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canWriteUser" link type="primary" size="small" @click="showEditDialog(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="showDetailDialog(row)">详情</el-button>
            <el-dropdown v-if="canWriteUser" @command="(cmd: string) => handleCommand(row, cmd)">
              <el-button link type="primary" size="small">
                更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="row.status !== 'ACTIVE'" command="enable">
                    <el-icon><CircleCheck /></el-icon>启用
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'ACTIVE'" command="disable">
                    <el-icon><CircleClose /></el-icon>停用
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'LOCKED'" command="unlock">
                    <el-icon><Unlock /></el-icon>解锁
                  </el-dropdown-item>
                  <el-dropdown-item command="resetPassword">
                    <el-icon><Key /></el-icon>重置密码
                  </el-dropdown-item>
                  <el-dropdown-item v-if="canDeleteUser" command="delete" divided>
                    <el-icon><Delete /></el-icon>删除
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
    
    <!-- 用户表单对话框 -->
    <UserFormDialog 
      v-model="formDialogVisible" 
      :user="currentUser" 
      @success="handleSearch" 
    />
    
    <!-- 用户详情对话框 -->
    <UserDetailDialog
      v-model="detailDialogVisible"
      :user-id="currentUserId"
    />
    
    <!-- 批量导入对话框 -->
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
import { organizationApi, type Department } from '@/api/organization'
import { hasPermission, PERMISSIONS } from '@/utils/permission'
import UserFormDialog from './components/UserFormDialog.vue'
import UserDetailDialog from './components/UserDetailDialog.vue'
import UserImportDialog from './components/UserImportDialog.vue'

const { t } = useI18n()

// Permission checks
const canWriteUser = hasPermission(PERMISSIONS.USER_WRITE)
const canDeleteUser = hasPermission(PERMISSIONS.USER_DELETE)

// 状态
const loading = ref(false)
const users = ref<User[]>([])
const total = ref(0)
const departments = ref<{ id: string; name: string }[]>([])

// 查询参数
const query = reactive({
  keyword: '',
  departmentId: '',
  status: '',
  page: 1,
  size: 20
})

// 对话框状态
const formDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const importDialogVisible = ref(false)
const currentUser = ref<User | null>(null)
const currentUserId = ref('')

// 状态映射
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
    ACTIVE: '活跃',
    DISABLED: '停用',
    LOCKED: '锁定',
    PENDING: '待激活'
  }
  return map[status] || status
}



// 查询用户列表
const handleSearch = async () => {
  loading.value = true
  try {
    const params = {
      keyword: query.keyword || undefined,
      departmentId: query.departmentId || undefined,
      status: query.status || undefined,
      page: query.page - 1,
      size: query.size
    }
    const result = await userApi.list(params)
    users.value = result.content
    total.value = result.totalElements
  } catch (error: any) {
    ElMessage.error(error.message || '查询失败')
  } finally {
    loading.value = false
  }
}

// 重置查询
const handleReset = () => {
  Object.assign(query, { keyword: '', departmentId: '', status: '', page: 1 })
  handleSearch()
}

// 显示创建对话框
const showCreateDialog = () => {
  currentUser.value = null
  formDialogVisible.value = true
}

// 显示编辑对话框
const showEditDialog = (user: User) => {
  currentUser.value = user
  formDialogVisible.value = true
}

// 显示详情对话框
const showDetailDialog = (user: User) => {
  currentUserId.value = user.id
  detailDialogVisible.value = true
}

// 显示导入对话框
const showImportDialog = () => {
  importDialogVisible.value = true
}

// 处理下拉菜单命令
const handleCommand = async (user: User, command: string) => {
  switch (command) {
    case 'enable':
      await handleStatusChange(user, 'ACTIVE', '启用用户')
      break
    case 'disable':
      await handleStatusChange(user, 'DISABLED', '停用用户')
      break
    case 'unlock':
      await handleStatusChange(user, 'ACTIVE', '解锁用户')
      break
    case 'resetPassword':
      await handleResetPassword(user)
      break
    case 'delete':
      await handleDelete(user)
      break
  }
}

// 更新用户状态
const handleStatusChange = async (user: User, status: string, action: string) => {
  try {
    await ElMessageBox.confirm(`确定要${action}「${user.fullName}」吗？`, '提示', { type: 'warning' })
    await userApi.updateStatus(user.id, { status: status as any })
    ElMessage.success(`${action}成功`)
    handleSearch()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || `${action}失败`)
    }
  }
}

// 重置密码
const handleResetPassword = async (user: User) => {
  try {
    await ElMessageBox.confirm(`确定要重置「${user.fullName}」的密码吗？`, '提示', { type: 'warning' })
    const newPassword = await userApi.resetPassword(user.id)
    ElMessageBox.alert(`新密码：${newPassword}`, '密码已重置', {
      confirmButtonText: '复制密码',
      callback: () => {
        navigator.clipboard.writeText(newPassword)
        ElMessage.success('密码已复制到剪贴板')
      }
    })
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '重置密码失败')
    }
  }
}

// 删除用户
const handleDelete = async (user: User) => {
  try {
    await ElMessageBox.confirm(`确定要删除用户「${user.fullName}」吗？此操作不可恢复！`, '警告', {
      type: 'warning',
      confirmButtonText: '确定删除',
      confirmButtonClass: 'el-button--danger'
    })
    await userApi.delete(user.id)
    ElMessage.success('删除成功')
    handleSearch()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// 扁平化部门树
const flattenDepartments = (depts: Department[], result: { id: string; name: string }[] = []): { id: string; name: string }[] => {
  depts.forEach(dept => {
    result.push({ id: dept.id, name: dept.name })
    if (dept.children && dept.children.length > 0) {
      flattenDepartments(dept.children, result)
    }
  })
  return result
}

// 加载部门列表
const loadDepartments = async () => {
  try {
    const tree = await organizationApi.getTree()
    departments.value = flattenDepartments(tree)
  } catch (error) {
    console.error('Failed to load departments:', error)
  }
}

onMounted(() => {
  handleSearch()
  loadDepartments()
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
