/**
 * 用户 Profile 业务逻辑 composable
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { changePassword, clearAuth, getCurrentUser, getUser, saveUser, USER_KEY, USERNAME_KEY } from '@/api/auth'
import { getChangePasswordFailureMessage } from '@/utils/changePasswordError'
import { redirectToUnifiedLogin } from '@/utils/sso'

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

export function useProfile() {
  const { t, locale } = useI18n()

  const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

  const loading = ref(false)
  const userInfo = ref<UserInfo | null>(null)
  const passwordFormRef = ref<FormInstance>()
  const changingPassword = ref(false)

  function languageLabelFor(code: string | undefined, loc: string): string {
    const c = (code || 'zh-CN').replace('_', '-')
    const en = loc.startsWith('en')
    const tw = loc === 'zh-TW'
    if (en) {
      const m: Record<string, string> = { 'zh-CN': 'Simplified Chinese', 'zh-TW': 'Traditional Chinese', en: 'English' }
      return m[c] || c
    }
    if (tw) {
      const m: Record<string, string> = { 'zh-CN': '簡體中文', 'zh-TW': '繁體中文', en: 'English' }
      return m[c] || c
    }
    const m: Record<string, string> = { 'zh-CN': '简体中文', 'zh-TW': '繁體中文', en: 'English' }
    return m[c] || c
  }

  const languageLabel = computed(() => languageLabelFor(userInfo.value?.language, String(locale.value)))

  const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

  const validateConfirmPassword = (_rule: unknown, value: string, callback: (e?: Error) => void) => {
    if (value !== passwordForm.newPassword) callback(new Error(t('profile.passwordMismatch')))
    else callback()
  }

  const validateNewPasswordDiffers = (_rule: unknown, value: string, callback: (e?: Error) => void) => {
    if (value && value === passwordForm.oldPassword) callback(new Error(t('profile.newPasswordSameAsOld')))
    else callback()
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
        const storedUser = localStorage.getItem(USER_KEY)
        if (storedUser) {
          try { userInfo.value = JSON.parse(storedUser) }
          catch { userInfo.value = getUser() }
        } else {
          userInfo.value = getUser()
        }
        if (!userInfo.value) {
          const legacy = localStorage.getItem('userInfo')
          if (legacy) {
            try { userInfo.value = JSON.parse(legacy) }
            catch { userInfo.value = { username: localStorage.getItem(USERNAME_KEY) || 'User' } }
          } else {
            userInfo.value = { username: localStorage.getItem(USERNAME_KEY) || 'User' }
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
        await changePassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword })
        ElMessage.success(t('profile.passwordChanged'))
        passwordFormRef.value?.resetFields()
        clearAuth()
        redirectToUnifiedLogin('admin')
      } catch (error: unknown) {
        ElMessage.error(getChangePasswordFailureMessage(error, t))
      } finally {
        changingPassword.value = false
      }
    })
  }

  return {
    defaultAvatar, loading, userInfo, passwordFormRef, changingPassword,
    languageLabel, passwordForm, passwordRules,
    loadUserInfo, handleChangePassword,
  }
}
