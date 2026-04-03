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
          <el-avatar :size="100" :src="userInfo?.avatar || defaultAvatar">
            {{ (userInfo?.displayName || userInfo?.username || 'U').charAt(0).toUpperCase() }}
          </el-avatar>
          <h2>{{ userInfo?.displayName || userInfo?.username || t('user.username') }}</h2>
          <p class="subtitle">{{ t('profile.sectionAccess') }}</p>
        </div>

        <el-divider />

        <h4 class="subsection-title">{{ t('profile.sectionAccount') }}</h4>
        <el-descriptions :column="2" border class="subsection-block">
          <el-descriptions-item :label="t('user.username')">
            {{ userInfo?.username || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('user.email')">
            {{ userInfo?.email || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('profile.accountId')">
            {{ userInfo?.userId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('profile.interfaceLanguage')">
            {{ languageLabel }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <h4 class="subsection-title">{{ t('profile.sectionAccess') }}</h4>
        <el-alert type="info" :closable="false" show-icon class="hint-alert">
          {{ t('profile.permissionCodesHint') }}
        </el-alert>

        <div class="role-block">
          <div class="block-label">{{ t('profile.loginRoles') }}</div>
          <div v-if="(userInfo?.roles?.length || 0) > 0" class="tag-row">
            <el-tag v-for="r in userInfo?.roles" :key="r" size="small" type="primary" class="item-tag">
              {{ r }}
            </el-tag>
          </div>
          <span v-else class="empty-text">{{ t('profile.noRoles') }}</span>
        </div>

        <div class="perm-block">
          <div class="block-label">{{ t('profile.permissionCodes') }}</div>
          <template v-if="(userInfo?.permissions?.length || 0) > 0">
            <el-collapse>
              <el-collapse-item :title="`${t('profile.permissionCodes')} (${userInfo?.permissions?.length})`" name="perms">
                <div class="perm-scroll">
                  <el-tag v-for="p in userInfo?.permissions" :key="p" size="small" type="info" class="perm-tag">
                    {{ p }}
                  </el-tag>
                </div>
              </el-collapse-item>
            </el-collapse>
          </template>
          <span v-else class="empty-text">{{ t('profile.noPermissionsListed') }}</span>
        </div>
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
            @blur="passwordFormRef?.validateField('newPassword')"
          />
        </el-form-item>
        <el-form-item :label="t('profile.newPassword')" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
            :placeholder="t('profile.newPasswordPlaceholder')"
            @input="passwordFormRef?.validateField('confirmPassword')"
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
import { changePassword, clearAuth, getCurrentUser, getUser, saveUser } from '@/api/auth'
import { getChangePasswordFailureMessage } from '@/utils/changePasswordError'
import { useRouter } from 'vue-router'

const { t, locale } = useI18n()
const router = useRouter()

const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

function languageLabelFor(code: string | undefined, loc: string): string {
  const c = (code || 'zh-CN').replace('_', '-')
  const en = loc.startsWith('en')
  const tw = loc === 'zh-TW'
  if (en) {
    const m: Record<string, string> = {
      'zh-CN': 'Simplified Chinese',
      'zh-TW': 'Traditional Chinese',
      en: 'English'
    }
    return m[c] || c
  }
  if (tw) {
    const m: Record<string, string> = {
      'zh-CN': '簡體中文',
      'zh-TW': '繁體中文',
      en: 'English'
    }
    return m[c] || c
  }
  const m: Record<string, string> = {
    'zh-CN': '简体中文',
    'zh-TW': '繁體中文',
    en: 'English'
  }
  return m[c] || c
}

interface UserInfo {
  userId?: string
  username?: string
  displayName?: string
  email?: string
  roles?: string[]
  permissions?: string[]
  language?: string
  avatar?: string
}

const loading = ref(false)
const userInfo = ref<UserInfo | null>(null)
const passwordFormRef = ref<FormInstance>()
const changingPassword = ref(false)

const languageLabel = computed(() => languageLabelFor(userInfo.value?.language, String(locale.value)))

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (_rule: unknown, value: string, callback: (e?: Error) => void) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error(t('profile.passwordMismatch')))
  } else {
    callback()
  }
}

const validateNewPasswordDiffers = (_rule: unknown, value: string, callback: (e?: Error) => void) => {
  if (value && value === passwordForm.oldPassword) {
    callback(new Error(t('profile.newPasswordSameAsOld')))
  } else {
    callback()
  }
}

const passwordRules = computed<FormRules>(() => ({
  oldPassword: [{ required: true, message: t('profile.currentPasswordPlaceholder'), trigger: 'blur' }],
  newPassword: [
    { required: true, message: t('profile.newPasswordPlaceholder'), trigger: 'blur' },
    { min: 6, message: t('profile.passwordMinLength'), trigger: 'blur' },
    { validator: validateNewPasswordDiffers, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('profile.confirmPasswordPlaceholder'), trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}))

const loadUserInfo = async () => {
  loading.value = true
  try {
    try {
      const fresh = await getCurrentUser()
      saveUser(fresh)
      userInfo.value = fresh
    } catch (error) {
      console.error('Failed to load user info:', error)
      const storedUser = localStorage.getItem('user')
      if (storedUser) {
        try {
          userInfo.value = JSON.parse(storedUser)
        } catch {
          userInfo.value = getUser()
        }
      } else {
        userInfo.value = getUser()
      }
      if (!userInfo.value) {
        const legacy = localStorage.getItem('userInfo')
        if (legacy) {
          try {
            userInfo.value = JSON.parse(legacy)
          } catch {
            userInfo.value = { username: localStorage.getItem('username') || 'User' }
          }
        } else {
          userInfo.value = { username: localStorage.getItem('username') || 'User' }
        }
      }
    }
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
      await changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      ElMessage.success(t('profile.passwordChanged'))
      passwordFormRef.value?.resetFields()
      clearAuth()
      await router.replace('/login')
    } catch (error: unknown) {
      ElMessage.error(getChangePasswordFailureMessage(error, t))
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

.subtitle {
  color: #909399;
  font-size: 13px;
  margin: 0;
}

.subsection-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.subsection-block {
  margin-bottom: 8px;
}

.hint-alert {
  margin-bottom: 12px;
}

.role-block,
.perm-block {
  margin-bottom: 16px;
}

.block-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.item-tag {
  margin: 0;
}

.perm-scroll {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 220px;
  overflow-y: auto;
}

.perm-tag {
  margin: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}

.empty-text {
  color: #909399;
  font-size: 12px;
}
</style>
