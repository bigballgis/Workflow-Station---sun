import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance } from 'element-plus'
import {
  functionUnitApi,
  type FunctionUnitResponse,
  type FunctionUnitStartupMode
} from '@/api/functionUnit'
import type { useFunctionUnitStore } from '@/stores/functionUnit'
import { normalizeTags } from '@/utils/tagStorage'
import { canCreateInWorkspace, canManageFunctionUnit } from '@/utils/permission'
import { ALL_GROUPS, getActiveGroupRaw, setMyDevGroups } from '@/utils/devGroupContext'

type FunctionUnitStore = ReturnType<typeof useFunctionUnitStore>

interface UseFunctionUnitFormOptions {
  store: FunctionUnitStore
  reload: () => void
}

interface WorkspaceOption {
  id: string
  name: string
}

/**
 * Create / settings dialog state and submission for the function unit list. A function unit belongs to exactly one
 * workspace (platform access model §6.3): it is chosen when creating, and changing it in the settings moves the unit,
 * which only the workspace's Technical Lead or a System Administrator may do (§6.2).
 */
export function useFunctionUnitForm(options: UseFunctionUnitFormOptions) {
  const { store, reload } = options
  const { t } = useI18n()

  const showFormDialog = ref(false)
  const formDialogMode = ref<'create' | 'settings'>('create')
  const settingsItem = ref<FunctionUnitResponse | null>(null)
  const formSubmitting = ref(false)
  const formRef = ref<FormInstance>()
  const basicForm = reactive({
    name: '',
    description: '',
    iconId: null as number | null,
    tags: [] as string[],
    teamGroupId: '',
    /**
     * Only meaningful when editing: a new unit is created STANDALONE, and whether it should be
     * callable is typically decided later, once its process exists.
     */
    startupMode: 'STANDALONE' as FunctionUnitStartupMode
  })

  const teamOptions = ref<WorkspaceOption[]>([])
  const teamsLoading = ref(false)

  // Creating always picks the workspace; in the settings only someone who may move the unit picks another one.
  const teamEditable = computed(() =>
    formDialogMode.value === 'create' || canManageFunctionUnit(settingsItem.value))

  // Always show the team field so the user sees which team owns the FU.
  const showTeamSelector = computed(() => true)

  /** The unit's workspace as the settings show it read-only, with its state when the team is gone or disabled. */
  const currentTeamName = computed(() => {
    const item = settingsItem.value
    if (!item) return ''
    if (item.workspaceStatus === 'MISSING') return t('devGroup.workspaceMissing')
    if (item.workspaceStatus === 'INACTIVE') return `${item.workspaceName ?? ''} (${t('devGroup.inactive')})`
    return item.workspaceStatus === 'PUBLIC' ? t('devGroup.publicGroup') : item.workspaceName ?? ''
  })

  const formDialogTitle = computed(() =>
    formDialogMode.value === 'create' ? t('functionUnit.create') : t('functionUnit.settings')
  )
  const formRules = computed(() => ({
    name: [{ required: true, message: t('functionUnit.enterName'), trigger: 'blur' }],
    teamGroupId: teamEditable.value
      ? [{ required: true, message: t('functionUnit.selectTeamRequired'), trigger: 'change' }]
      : []
  }))

  /** Workspaces a unit may land in for the user: teams they may create in, and Public for a System Administrator. */
  async function loadTeamOptions() {
    teamsLoading.value = true
    try {
      const my = await functionUnitApi.getMyDevGroups()
      const groups = my?.data?.groups ?? []
      const publicGroupId = my?.data?.publicGroupId ?? ''
      setMyDevGroups(groups, my?.data?.canWritePublic === true)
      const options: WorkspaceOption[] = groups
        .filter(g => canCreateInWorkspace(g.id))
        .map(g => ({ id: g.id, name: g.name }))
      if (publicGroupId && canCreateInWorkspace(publicGroupId)) {
        options.push({ id: publicGroupId, name: t('devGroup.publicGroup') })
      }
      // Moving: the current workspace stays listed so the field shows where the unit is now.
      const item = settingsItem.value
      if (item?.workspaceId && !options.some(o => o.id === item.workspaceId)) {
        options.unshift({ id: item.workspaceId, name: currentTeamName.value })
      }
      teamOptions.value = options
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
    basicForm.teamGroupId = ''
    basicForm.startupMode = 'STANDALONE'
  }

  function openCreateDialog() {
    formDialogMode.value = 'create'
    settingsItem.value = null
    resetBasicForm()
    void loadTeamOptions().then(() => {
      // Default to the workspace the user is working in, when they may create there.
      const raw = getActiveGroupRaw()
      if (raw && raw !== ALL_GROUPS && teamOptions.value.some(o => o.id === raw)) {
        basicForm.teamGroupId = raw
      }
    })
    showFormDialog.value = true
  }

  function handleFormDialogClosed() {
    formRef.value?.resetFields()
    settingsItem.value = null
  }

  function handleSettings(item: FunctionUnitResponse) {
    formDialogMode.value = 'settings'
    settingsItem.value = item
    basicForm.name = item.name
    basicForm.description = item.description ?? ''
    basicForm.iconId = item.iconId ?? null
    basicForm.tags = [...normalizeTags(item.tags)]
    basicForm.teamGroupId = item.workspaceId ?? ''
    basicForm.startupMode = item.startupMode ?? 'STANDALONE'
    showFormDialog.value = true
    if (teamEditable.value) void loadTeamOptions()
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
      const item = settingsItem.value
      if (formDialogMode.value === 'create') {
        await store.create({ ...payload, startupMode: basicForm.startupMode, virtualGroupIds: [basicForm.teamGroupId] })
        ElMessage.success(t('functionUnit.createSuccess'))
      } else if (item) {
        await store.update(item.id, {
          ...payload,
          startupMode: basicForm.startupMode
        })
        if (teamEditable.value && basicForm.teamGroupId && basicForm.teamGroupId !== item.workspaceId) {
          await functionUnitApi.moveToWorkspace(item.id, basicForm.teamGroupId)
        }
        ElMessage.success(t('functionUnit.saveSuccess'))
      }
      showFormDialog.value = false
      resetBasicForm()
      reload()
      store.fetchAllTags()
    } catch (e: unknown) {
      const data = (e as { response?: { data?: { message?: string; error?: { message?: string } } } })?.response?.data
      ElMessage.error(data?.error?.message || data?.message || t('functionUnit.saveFailed'))
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
    teamEditable,
    currentTeamName,
    openCreateDialog,
    handleFormDialogClosed,
    handleSettings,
    handleFormSubmit,
  }
}
