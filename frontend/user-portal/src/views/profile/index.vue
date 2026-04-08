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
          <p class="workspace-line">{{ workspaceLine }}</p>
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

        <h4 class="subsection-title">{{ t('profile.sectionWorkspace') }}</h4>
        <el-alert type="info" :closable="false" show-icon class="hint-alert">
          {{ t('profile.workspaceHint') }}
        </el-alert>
        <el-descriptions :column="1" border class="subsection-block">
          <el-descriptions-item :label="t('profile.workspaceCurrent')">
            <template v-if="workspaceContextText">
              {{ workspaceContextText }}
            </template>
            <span v-else class="empty-text">{{ t('profile.noWorkspaceSelected') }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <h4 class="subsection-title">{{ t('profile.sectionMembership') }}</h4>
        <el-alert type="success" :closable="false" show-icon class="hint-alert">
          {{ t('profile.membershipRolesHint') }}
        </el-alert>
        <el-descriptions :column="1" border class="subsection-block">
          <el-descriptions-item :label="t('profile.sectionBuRolePairs')">
            <ul v-if="buBoundedRoles.length" class="ubr-list">
              <li v-for="(row, idx) in buBoundedRoles" :key="`${row.role?.id}-${idx}`">
                {{ formatUbrLine(row) }}
              </li>
            </ul>
            <span v-else class="empty-text">{{ t('profile.noBuRoleAssignments') }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('profile.sectionBuUnboundedRoles')">
            <template v-if="buUnboundedRoles.length">
              <el-tag
                v-for="role in buUnboundedRoles"
                :key="role.id"
                size="small"
                type="success"
                class="item-tag"
              >
                {{ role.name }}
              </el-tag>
            </template>
            <span v-else class="empty-text">{{ t('profile.noBuUnboundedRoles') }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('profile.sectionBuMembership')">
            <el-tag v-for="bu in businessUnits" :key="bu.id" size="small" type="info" class="item-tag">
              {{ bu.name }}
            </el-tag>
            <span v-if="businessUnits.length === 0" class="empty-text">{{ t('profile.noBusinessUnits') }}</span>
            <div class="desc-hint">{{ t('profile.sectionBuMembershipHint') }}</div>
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
import { getCurrentUser, getUser, saveUser, clearAuth, type UserInfo } from '@/api/auth'
import { redirectToUnifiedLogin } from '@/utils/sso'
import { userApi } from '@/api/user'
import { permissionApi } from '@/api/permission'
import { parseMyPermissionViewPayload, type PortalBuBoundedRow } from '@/utils/myPermissionView'
import { getChangePasswordFailureMessage } from '@/utils/changePasswordError'

const { t, locale } = useI18n()

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

const loading = ref(false)
const userInfo = ref<UserInfo | null>(null)
const businessUnits = ref<{ id: string; name: string }[]>([])
const buBoundedRoles = ref<PortalBuBoundedRow[]>([])
const buUnboundedRoles = ref<{ id: string; name: string }[]>([])

const formatUbrLine = (row: PortalBuBoundedRow) => {
  const bu = row.activatedBusinessUnits?.[0]
  const buName = bu?.name || '—'
  const roleName = row.role?.name || '—'
  return `${buName} · ${roleName}`
}
const passwordFormRef = ref<FormInstance>()
const changingPassword = ref(false)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const languageLabel = computed(() => languageLabelFor(userInfo.value?.language, String(locale.value)))

const workspaceContextText = computed(() => {
  const u = userInfo.value
  if (!u?.activeBusinessUnitName && !u?.activeRoleName) return ''
  const bu = u.activeBusinessUnitName || '—'
  const r = u.activeRoleName || '—'
  return `${bu} · ${r}`
})

const workspaceLine = computed(() => workspaceContextText.value || t('profile.noWorkspaceSelected'))

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
    let u: UserInfo | null = null
    try {
      u = await getCurrentUser()
      saveUser(u)
    } catch {
      u = getUser()
    }
    userInfo.value = u

    const response = (await permissionApi.getMyPermissionView()) as { data?: Record<string, unknown> } & Record<string, unknown>
    const data = (response.data || response) as Record<string, unknown>
    const lists = parseMyPermissionViewPayload(data)
    businessUnits.value = lists.businessUnits
    buBoundedRoles.value = lists.buBoundedRoles
    buUnboundedRoles.value = lists.buUnboundedRoles
  } catch (error) {
    console.error('Failed to load user info:', error)
    userInfo.value = getUser()
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
      clearAuth()
      redirectToUnifiedLogin('portal')
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

.workspace-line {
  color: #606266;
  font-size: 14px;
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

.item-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.empty-text {
  color: #909399;
  font-size: 12px;
}

.ubr-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.desc-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  line-height: 1.4;
}
</style>
