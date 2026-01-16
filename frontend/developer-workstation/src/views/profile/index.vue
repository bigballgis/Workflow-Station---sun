<template>
  <div class="profile-container">
    <el-card class="profile-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('profile.title') }}</span>
        </div>
      </template>
      
      <div class="profile-content" v-loading="loading">
        <div class="avatar-section">
          <el-avatar :size="100">
            {{ (userInfo?.displayName || userInfo?.username || 'U').charAt(0).toUpperCase() }}
          </el-avatar>
          <h2>{{ userInfo?.displayName || userInfo?.username || t('user.username') }}</h2>
          <p class="user-role">{{ roleNames }}</p>
        </div>
        
        <el-divider />
        
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('user.username')">
            {{ userInfo?.username || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.email')">
            {{ userInfo?.email || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="User ID">
            {{ userInfo?.userId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="Language">
            {{ userInfo?.language || 'zh-CN' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('profile.businessUnits')" :span="2">
            <el-tag v-for="bu in businessUnits" :key="bu.id" size="small" type="info" class="item-tag">
              {{ bu.name }}
            </el-tag>
            <span v-if="businessUnits.length === 0" class="empty-text">{{ t('profile.noBusinessUnits') }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('profile.virtualGroups')" :span="2">
            <el-tag v-for="vg in virtualGroups" :key="vg.groupId" size="small" type="success" class="item-tag">
              {{ vg.groupName }}
            </el-tag>
            <span v-if="virtualGroups.length === 0" class="empty-text">{{ t('profile.noVirtualGroups') }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('profile.roles')" :span="2">
            <el-tag v-for="role in roles" :key="role.id" size="small" :type="getRoleTagType(role.type)" class="item-tag">
              {{ role.name }}
            </el-tag>
            <span v-if="roles.length === 0" class="empty-text">{{ t('profile.noRoles') }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>
    
    <el-card class="password-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('profile.changePassword') }}</span>
        </div>
      </template>
      
      <el-form 
        ref="passwordFormRef" 
        :model="passwordForm" 
        :rules="passwordRules" 
        label-width="100px"
      >
        <el-form-item :label="t('profile.currentPassword')" prop="oldPassword">
          <el-input 
            v-model="passwordForm.oldPassword" 
            type="password" 
            show-password
            :placeholder="t('profile.currentPasswordPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('profile.newPassword')" prop="newPassword">
          <el-input 
            v-model="passwordForm.newPassword" 
            type="password" 
            show-password
            :placeholder="t('profile.newPasswordPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('profile.confirmPassword')" prop="confirmPassword">
          <el-input 
            v-model="passwordForm.confirmPassword" 
            type="password" 
            show-password
            :placeholder="t('profile.confirmPasswordPlaceholder')"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleChangePassword" :loading="changingPassword">
            {{ t('profile.changePassword') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { getUser } from '@/api/auth'
import { userApi } from '@/api/user'

const { t } = useI18n()

interface UserInfo {
  userId?: string
  username?: string
  displayName?: string
  email?: string
  language?: string
}

const loading = ref(false)
const userInfo = ref<UserInfo | null>(null)
const businessUnits = ref<{ id: string; name: string }[]>([])
const virtualGroups = ref<{ groupId: string; groupName: string }[]>([])
const roles = ref<{ id: string; name: string; type?: string }[]>([])
const passwordFormRef = ref<FormInstance>()
const changingPassword = ref(false)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const roleNames = computed(() => {
  return roles.value.map(r => r.name).join(', ') || '-'
})

const getRoleTagType = (type?: string) => {
  if (type === 'BU_BOUNDED') return 'warning'
  if (type === 'BU_UNBOUNDED') return 'success'
  if (type === 'ADMIN') return 'danger'
  return 'primary'
}

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error(t('profile.passwordMismatch')))
  } else {
    callback()
  }
}

const passwordRules = computed<FormRules>(() => ({
  oldPassword: [
    { required: true, message: t('profile.currentPasswordPlaceholder'), trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: t('profile.newPasswordPlaceholder'), trigger: 'blur' },
    { min: 6, message: t('profile.passwordMinLength'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('profile.confirmPasswordPlaceholder'), trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}))

const loadUserInfo = async () => {
  loading.value = true
  try {
    const user = getUser()
    if (user) {
      userInfo.value = user
      
      if (user.userId) {
        const [busResult, vgResult, rolesResult] = await Promise.all([
          userApi.getBusinessUnits(user.userId),
          userApi.getVirtualGroups(user.userId),
          userApi.getRoles(user.userId)
        ])
        businessUnits.value = busResult || []
        virtualGroups.value = vgResult || []
        roles.value = (rolesResult || []).map((r: any) => ({
          id: r.id,
          name: r.name,
          type: r.type
        }))
      }
    }
  } catch (error) {
    console.error('Failed to load user info:', error)
  } finally {
    loading.value = false
  }
}

const handleChangePassword = async () => {
  if (!passwordFormRef.value) return
  
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    
    changingPassword.value = true
    try {
      await userApi.changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      ElMessage.success(t('profile.passwordChanged'))
      passwordFormRef.value?.resetFields()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || t('common.failed'))
    } finally {
      changingPassword.value = false
    }
  })
}

onMounted(() => {
  loadUserInfo()
})
</script>

<style scoped>
.profile-container {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.profile-card {
  margin-bottom: 20px;
}

.password-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.profile-content {
  min-height: 200px;
}

.avatar-section {
  text-align: center;
  padding: 20px 0;
}

.avatar-section h2 {
  margin: 15px 0 5px;
  font-size: 20px;
}

.user-role {
  color: #909399;
  font-size: 14px;
}

.item-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.empty-text {
  color: #909399;
  font-size: 12px;
}
</style>
