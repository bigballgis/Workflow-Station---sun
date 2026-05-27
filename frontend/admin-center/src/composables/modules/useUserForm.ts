/**
 * User Form 业务逻辑 composable
 */
import { ref, reactive, computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifySuccess, notifyError } from '@/utils/notify'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { userApi, type User } from '@/api/user'
import type { FormRules } from 'element-plus'

interface UserData { id: string; username: string; fullName: string; email: string; employeeId?: string; position?: string; entityManagerId?: string; functionManagerId?: string }

export function useUserForm(options: { user: Ref<UserData | null>; onSuccess: () => void }) {
  const { user, onSuccess } = options
  const { t } = useI18n()
  const terr = (code: string) => t(errorTranslator(code))
  const isEdit = computed(() => !!user.value)

  const loading = ref(false)
  const userSearchLoading = ref(false)
  const userOptions = ref<{ id: string; fullName: string; username: string }[]>([])

  const form = reactive({
    username: '', fullName: '', email: '', employeeId: '', position: '',
    entityManagerId: '', functionManagerId: '', initialPassword: '',
  })

  const rules = computed<FormRules>(() => ({
    username: [{ required: true, message: t('user.usernamePlaceholder'), trigger: 'blur' }, { min: 3, max: 50, message: t('user.usernamePlaceholder'), trigger: 'blur' }],
    fullName: [{ required: true, message: t('user.fullNamePlaceholder'), trigger: 'blur' }],
    email: [{ required: true, message: t('user.emailPlaceholder'), trigger: 'blur' }, { type: 'email', message: t('user.emailPlaceholder'), trigger: 'blur' }],
    initialPassword: [{ required: true, message: t('user.initialPasswordPlaceholder'), trigger: 'blur' }, { min: 8, message: t('user.initialPasswordPlaceholder'), trigger: 'blur' }],
  }))

  const initForm = async () => {
    if (user.value) {
      const u = user.value
      Object.assign(form, { username: u.username, fullName: u.fullName, email: u.email, employeeId: u.employeeId || '', position: u.position || '', entityManagerId: u.entityManagerId || '', functionManagerId: u.functionManagerId || '', initialPassword: '' })
      await loadSelectedManagers()
    } else {
      Object.assign(form, { username: '', fullName: '', email: '', employeeId: '', position: '', entityManagerId: '', functionManagerId: '', initialPassword: '' })
    }
    await loadDefaultUsers()
  }

  const loadSelectedManagers = async () => {
    const ids = [form.entityManagerId, form.functionManagerId].filter(Boolean)
    if (!ids.length) return
    try {
      const managers: { id: string; fullName: string; username: string }[] = []
      for (const id of ids) { const u = await userApi.getById(id); if (u) managers.push({ id: u.id, fullName: u.fullName, username: u.username }) }
      const existing = new Set(userOptions.value.map(o => o.id))
      for (const m of managers) { if (!existing.has(m.id)) userOptions.value.push(m) }
    } catch { /* silent */ }
  }

  const loadDefaultUsers = async () => {
    userSearchLoading.value = true
    try {
      const res = await userApi.list({ page: 0, size: 3 })
      userOptions.value = (res.content || []).map((u: User) => ({ id: u.id, fullName: u.fullName, username: u.username }))
    } catch { userOptions.value = [] } finally { userSearchLoading.value = false }
  }

  const searchUsers = async (query: string) => {
    if (!query) { await loadDefaultUsers(); return }
    userSearchLoading.value = true
    try {
      const res = await userApi.list({ keyword: query, page: 0, size: 20 })
      userOptions.value = (res.content || []).map((u: User) => ({ id: u.id, fullName: u.fullName, username: u.username }))
    } catch { userOptions.value = [] } finally { userSearchLoading.value = false }
  }

  const submit = async () => {
    loading.value = true
    try {
      if (isEdit.value) {
        await userApi.update(user.value!.id, { fullName: form.fullName, email: form.email, employeeId: form.employeeId || undefined, position: form.position || undefined, entityManagerId: form.entityManagerId || undefined, functionManagerId: form.functionManagerId || undefined })
      } else {
        await userApi.create({ username: form.username, fullName: form.fullName, email: form.email, employeeId: form.employeeId || undefined, position: form.position || undefined, entityManagerId: form.entityManagerId || undefined, functionManagerId: form.functionManagerId || undefined, initialPassword: form.initialPassword })
      }
      notifySuccess(t('common.success'))
      onSuccess()
    } catch (e: unknown) { const msg = e instanceof Error ? e.message : undefined; notifyError(msg || terr(AppErrorCode.USER_ACTION_FAILED)) }
    finally { loading.value = false }
  }

  return { form, rules, loading, userSearchLoading, userOptions, isEdit, initForm, searchUsers, submit }
}
