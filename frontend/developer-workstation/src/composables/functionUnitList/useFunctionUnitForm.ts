import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance } from 'element-plus'
import { functionUnitApi, type FunctionUnitResponse, type DevGroupOption } from '@/api/functionUnit'
import { adminCenterApi, type VirtualGroupInfo } from '@/api/adminCenter'
import type { useFunctionUnitStore } from '@/stores/functionUnit'
import { normalizeTags } from '@/utils/tagStorage'
import { permissions } from '@/utils/permission'
import { ALL_GROUPS, getActiveGroupRaw } from '@/utils/devGroupContext'

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
  const myGroups = ref<DevGroupOption[]>([])
  const canSeeAll = ref(false)
  const publicGroupId = ref<string>('')

  // Whether the team field is an editable selector (SYS_ADMIN / TECH_LEAD) vs read-only
  // (regular creators: team = their currently selected team; enforced by backend).
  const teamEditable = computed(() => permissions.canReassignTeam())

  // Always show the team field so the user sees which team owns the FU.
  const showTeamSelector = computed(() => true)

  // Display name of the user's currently active team (for the read-only field on create).
  const activeGroupName = computed(() => {
    const raw = getActiveGroupRaw()
    if (!raw || raw === ALL_GROUPS) return raw === ALL_GROUPS ? t('devGroup.allGroups') : ''
    if (raw === publicGroupId.value) return 'Public'
    return myGroups.value.find(g => g.id === raw)?.name ?? ''
  })

  // Read-only display of a function unit's current team names (settings, non-editable users).
  const currentTeamNames = ref<string>('')

  const formDialogTitle = computed(() =>
    formDialogMode.value === 'create' ? t('functionUnit.create') : t('functionUnit.settings')
  )
  const formRules = computed(() => ({
    name: [{ required: true, message: t('functionUnit.enterName'), trigger: 'blur' }],
    // Only editable creators must pick a team; regular creators inherit the active team.
    teamGroupIds: (formDialogMode.value === 'create' && teamEditable.value)
      ? [{ required: true, message: t('functionUnit.selectTeamRequired'), trigger: 'change' }]
      : []
  }))

  function nameById(id: string): string {
    if (id === publicGroupId.value) return 'Public'
    return teamOptions.value.find(g => g.id === id)?.name
      ?? myGroups.value.find(g => g.id === id)?.name
      ?? id
  }

  async function loadTeamOptions() {
    teamsLoading.value = true
    try {
      // Dev-group context: names for read-only display + team scoping for the editable selector.
      const my = await functionUnitApi.getMyDevGroups()
      myGroups.value = my?.data?.groups ?? []
      canSeeAll.value = my?.data?.canSeeAllGroups === true
      publicGroupId.value = my?.data?.publicGroupId ?? ''
    } catch {
      myGroups.value = []
      canSeeAll.value = false
    }
    if (teamEditable.value && teamOptions.value.length === 0) {
      try {
        // ADMIN may target any team (incl. Public); TECH_LEAD is scoped to own teams + Public.
        if (canSeeAll.value) {
          teamOptions.value = await adminCenterApi.getVirtualGroups('CUSTOM', 'ACTIVE')
        } else {
          const opts: VirtualGroupInfo[] = myGroups.value.map(g => ({ id: g.id, name: g.name }))
          if (publicGroupId.value) opts.push({ id: publicGroupId.value, name: 'Public' })
          teamOptions.value = opts
        }
      } catch {
        teamOptions.value = []
      }
    }
    teamsLoading.value = false
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
    currentTeamNames.value = ''
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
    currentTeamNames.value = ''
    showFormDialog.value = true
    // Load names/options, then the FU's current team assignment for display / editing.
    void loadTeamOptions().then(() => {
      functionUnitApi.getDevGroups(item.id)
        .then(res => {
          const ids = res.data ?? []
          basicForm.teamGroupIds = ids
          currentTeamNames.value = ids.map(nameById).join(', ')
        })
        .catch(() => {
          basicForm.teamGroupIds = []
          currentTeamNames.value = ''
        })
    })
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
        // Editable creators (admin/tech-lead) choose the team; regular creators inherit the
        // currently selected team server-side (backend resolves from the X-Dev-Group-Id header).
        const virtualGroupIds = teamEditable.value ? basicForm.teamGroupIds : undefined
        await store.create({ ...payload, virtualGroupIds })
        ElMessage.success(t('functionUnit.createSuccess'))
      } else if (settingsItemId.value != null) {
        await store.update(settingsItemId.value, payload)
        // Only editable users may reassign the team.
        if (teamEditable.value) {
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
    teamEditable,
    activeGroupName,
    currentTeamNames,
    openCreateDialog,
    handleFormDialogClosed,
    handleSettings,
    handleFormSubmit,
  }
}
