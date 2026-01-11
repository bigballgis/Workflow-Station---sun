<template>
  <el-dialog 
    :model-value="modelValue" 
    @update:model-value="$emit('update:modelValue', $event)" 
    title="用户详情" 
    width="700px"
    destroy-on-close
  >
    <div v-loading="loading" class="user-detail">
      <template v-if="user">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户名">{{ user.username }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ user.fullName }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ user.email }}</el-descriptions-item>
          <el-descriptions-item label="工号">{{ user.employeeId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ user.departmentName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="职位">{{ user.position || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(user.status)" size="small">{{ statusText(user.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="实体管理者">{{ user.entityManagerName || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="职能管理者">{{ user.functionManagerName || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(user.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="最后登录">{{ user.lastLoginAt ? formatDate(user.lastLoginAt) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="最后登录IP" :span="2">{{ user.lastLoginIp || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">角色信息</div>
        <el-table :data="user.roles" border size="small" v-if="user.roles?.length">
          <el-table-column prop="roleName" label="角色名称" />
          <el-table-column prop="roleCode" label="角色编码" />
          <el-table-column prop="description" label="描述" />
        </el-table>
        <el-empty v-else description="暂无角色" :image-size="60" />

        <div class="section-title">登录历史</div>
        <el-table :data="user.loginHistory" border size="small" max-height="200" v-if="user.loginHistory?.length">
          <el-table-column prop="loginTime" label="登录时间" width="170">
            <template #default="{ row }">{{ formatDate(row.loginTime) }}</template>
          </el-table-column>
          <el-table-column prop="ipAddress" label="IP地址" width="140" />
          <el-table-column prop="success" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="failureReason" label="失败原因" />
        </el-table>
        <el-empty v-else description="暂无登录记录" :image-size="60" />
      </template>
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
      <el-button type="warning" @click="handleResetPassword">重置密码</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi, type UserDetail } from '@/api/user'

const props = defineProps<{ modelValue: boolean; userId: string }>()
const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const user = ref<UserDetail | null>(null)

const statusType = (status: string) => {
  const map: Record<string, string> = { ACTIVE: 'success', DISABLED: 'info', LOCKED: 'danger', PENDING: 'warning' }
  return map[status] || 'info'
}

const statusText = (status: string) => {
  const map: Record<string, string> = { ACTIVE: '活跃', DISABLED: '停用', LOCKED: '锁定', PENDING: '待激活' }
  return map[status] || status
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

watch(() => props.modelValue, async (val) => {
  if (val && props.userId) {
    loading.value = true
    try {
      user.value = await userApi.getById(props.userId)
    } catch (error: any) {
      ElMessage.error(error.message || '加载用户详情失败')
    } finally {
      loading.value = false
    }
  }
})

const handleResetPassword = async () => {
  if (!user.value) return
  try {
    await ElMessageBox.confirm(`确定要重置「${user.value.fullName}」的密码吗？`, '提示', { type: 'warning' })
    const newPassword = await userApi.resetPassword(user.value.id)
    ElMessageBox.alert(`新密码：${newPassword}`, '密码已重置', {
      confirmButtonText: '复制密码',
      callback: () => {
        navigator.clipboard.writeText(newPassword)
        ElMessage.success('密码已复制到剪贴板')
      }
    })
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error(error.message || '重置密码失败')
  }
}
</script>

<style scoped lang="scss">
.user-detail {
  min-height: 200px;
  .section-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    margin: 20px 0 12px;
    padding-left: 8px;
    border-left: 3px solid #DB0011;
  }
}
</style>
