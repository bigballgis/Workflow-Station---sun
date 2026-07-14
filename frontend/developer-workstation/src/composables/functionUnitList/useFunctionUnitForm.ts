import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance } from 'element-plus'
import { functionUnitApi, type FunctionUnitResponse } from '@/api/functionUnit'
import { adminCenterApi, type VirtualGroupInfo } from '@/api/adminCenter'
import type { useFunctionUnitStore } from '@/stores/functionUnit'
import { normalizeTags } from '@/utils/tagStorage'
import { permissions } from '@/utils/permission'

type FunctionUnitStore = ReturnType<typeof useFunctionUnitStore>

interface UseFunctionUnitFormOptions {
  store: FunctionUnitStore
  reload: () => void
}

/** Create / settings dialog state and submission for the function unit list. */
export function useFunctionUnitForm(options: UseFunctionUnitFormOptions) {
  const { store, reload } = options
  const { t } = useI18n()

  const showFormDialog = ref(false)
  const formDialogMode = ref<'create' | 'settings'>('create')
  const settingsItemId = ref<number | null>(null)
  const formSubmitting = ref(false)
  const formRef = ref<FormInstance>()
  const basicForm = reactive({
    name: '',
    description: '',
    iconId: null as number | null,
    tags: [] as string[],
    teamGroupIds: [] as string[]
  })

  const teamOptions = ref<VirtualGroupInfo[]>([])
  const teamsLoading = ref(false)

  // Team ownership controls FU visibility. Show the selector when creating (creators pick a team)
  // or when a user allowed to reassign teams opens settings. Backend enforces per-FU scope.
  const showTeamSelector = computed(() =>
    formDialogMode.value === 'create' || permissions.canAssignDevGroups()
  )

  const formDialogTitle = computed(() =>
    formDialogMode.value === 'create' ? t('functionUnit.create') : t('functionUnit.settings')
  )
  const formRules = computed(() => ({
    name: [{ required: true, message: t('functionUnit.enterName'), trigger: 'blur' }],
    teamGroupIds: formDialogMode.value === 'create'
      ? [{ required: true, message: t('functionUnit.selectTeamRequired'), trigger: 'change' }]
      : []
  }))

  async function loadTeamOptions() {
    if (teamOptions.value.length > 0) {
      return
    }
    teamsLoading.value = true
    try {
      teamOptions.value = await adminCenterApi.getVirtualGroups('CUSTOM', 'ACTIVE')
    } catch {
      teamOptions.value = []
    } finally {
      teamsLoading.value = false
    }
  }

  function resetBasicForm() {
    basicForm.name = ''
    basicForm.description = ''
    basicForm.iconId = null
    basicForm.tags = []
    basicForm.teamGroupIds = []
  }

  function openCreateDialog() {
    formDialogMode.value = 'create'
    settingsItemId.value = null
    resetBasicForm()
    void loadTeamOptions()
    showFormDialog.value = true
  }

  function handleFormDialogClosed() {
    formRef.value?.resetFields()
    settingsItemId.value = null
  }

  function handleSettings(item: FunctionUnitResponse) {
    formDialogMode.value = 'settings'
    settingsItemId.value = item.id
    basicForm.name = item.name
    basicForm.description = item.description ?? ''
    basicForm.iconId = item.iconId ?? null
    basicForm.tags = [...normalizeTags(item.tags)]
    basicForm.teamGroupIds = []
    showFormDialog.value = true
    if (permissions.canAssignDevGroups()) {
      void loadTeamOptions()
      functionUnitApi.getDevGroups(item.id)
        .then(res => { basicForm.teamGroupIds = res.data ?? [] })
        .catch(() => { basicForm.teamGroupIds = [] })
    }
  }

  async function handleFormSubmit() {
    await formRef.value?.validate()
    formSubmitting.value = true
    try {
      const desc = basicForm.description?.trim()
      const payload = {
        name: basicForm.name.trim(),
        description: desc || undefined,
        iconId: basicForm.iconId ?? undefined,
        tags: normalizeTags(basicForm.tags),
      }
      if (formDialogMode.value === 'create') {
        await store.create({ ...payload, virtualGroupIds: basicForm.teamGroupIds })
        ElMessage.success(t('functionUnit.createSuccess'))
      } else if (settingsItemId.value != null) {
        await store.update(settingsItemId.value, payload)
        if (permissions.canAssignDevGroups()) {
          await functionUnitApi.replaceDevGroups(settingsItemId.value, basicForm.teamGroupIds)
        }
        ElMessage.success(t('functionUnit.saveSuccess'))
      }
      showFormDialog.value = false
      resetBasicForm()
      reload()
      store.fetchAllTags()
    } catch (e: unknown) {
      const message = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      ElMessage.error(message || t('functionUnit.saveFailed'))
    } finally {
      formSubmitting.value = false
    }
  }

  return {
    showFormDialog,
    formDialogMode,
    formSubmitting,
    formRef,
    basicForm,
    formDialogTitle,
    formRules,
    teamOptions,
    teamsLoading,
    showTeamSelector,
    openCreateDialog,
    handleFormDialogClosed,
    handleSettings,
    handleFormSubmit,
  }
}
