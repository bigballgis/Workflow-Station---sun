<template>
  <el-dialog 
    :model-value="modelValue" 
    @update:model-value="$emit('update:modelValue', $event)" 
    :title="t('common.view')" 
    width="700px"
    destroy-on-close
  >
    <div v-loading="loading" class="user-detail">
      <template v-if="user">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('user.username')">{{ user.username }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.fullName')">{{ user.fullName }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.email')">{{ user.email }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.employeeId')">{{ user.employeeId || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.department')">{{ user.departmentName || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.position')">{{ user.position || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.status')">
            <el-tag :type="statusType(user.status)" size="small">{{ statusText(user.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.entityManager')">{{ user.entityManagerName || t('user.notSet') }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.functionManager')">{{ user.functionManagerName || t('user.notSet') }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.createTime')">{{ formatDate(user.createdAt) }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.lastLogin')">{{ user.lastLoginAt ? formatDate(user.lastLoginAt) : '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('user.lastLoginIp')" :span="2">{{ user.lastLoginIp || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">{{ t('user.roleInfo') }}</div>
        <el-table :data="user.roles" border size="small" v-if="user.roles?.length">
          <el-table-column prop="roleName" :label="t('user.roleName')" />
          <el-table-column prop="roleCode" :label="t('user.roleCode')" />
          <el-table-column prop="description" :label="t('common.description')" />
        </el-table>
        <el-empty v-else :description="t('user.noRoles')" :image-size="60" />

        <div class="section-title">{{ t('user.loginHistory') }}</div>
        <el-table :data="user.loginHistory" border size="small" max-height="200" v-if="user.loginHistory?.length">
          <el-table-column prop="loginTime" :label="t('user.loginTime')" width="170">
            <template #default="{ row }">{{ formatDate(row.loginTime) }}</template>
          </el-table-column>
          <el-table-column prop="ipAddress" :label="t('user.ipAddress')" width="140" />
          <el-table-column prop="success" :label="t('user.loginStatus')" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? t('common.success') : t('common.failed') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="failureReason" :label="t('user.failureReason')" />
        </el-table>
        <el-empty v-else :description="t('user.noLoginHistory')" :image-size="60" />
      </template>
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">{{ t('common.close') }}</el-button>
      <el-button type="warning" @click="handleResetPassword">{{ t('user.resetPassword') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi, type UserDetail } from '@/api/user'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean; userId: string }>()
const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const user = ref<UserDetail | null>(null)

const statusType = (status: string) => {
  const map: Record<string, string> = { ACTIVE: 'success', DISABLED: 'info', LOCKED: 'danger', PENDING: 'warning' }
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
      ElMessage.error(error.message || t('common.failed'))
    } finally {
      loading.value = false
    }
  }
})

const handleResetPassword = async () => {
  if (!user.value) return
  try {
    await ElMessageBox.confirm(t('user.resetPassword') + ` - ${user.value.fullName}?`, t('common.confirm'), { type: 'warning' })
    const newPassword = await userApi.resetPassword(user.value.id)
    ElMessageBox.alert(`${t('user.initialPassword')}: ${newPassword}`, t('common.success'), {
      confirmButtonText: t('common.confirm'),
      callback: () => {
        navigator.clipboard.writeText(newPassword)
        ElMessage.success(t('common.success'))
      }
    })
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error(error.message || t('common.failed'))
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
