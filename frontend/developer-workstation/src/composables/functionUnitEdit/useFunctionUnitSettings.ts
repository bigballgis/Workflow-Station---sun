import { computed, reactive, ref } from 'vue'
import type { ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { useFunctionUnitStore } from '@/stores/functionUnit'
import { normalizeTags, collectAvailableTags } from '@/utils/tagStorage'

type FunctionUnitStore = ReturnType<typeof useFunctionUnitStore>

interface UseFunctionUnitSettingsOptions {
  functionUnitId: ComputedRef<number>
  store: FunctionUnitStore
}

/** Edit/settings dialog: name/description/icon/tags form and persistence. */
export function useFunctionUnitSettings(options: UseFunctionUnitSettingsOptions) {
  const { functionUnitId, store } = options
  const { t } = useI18n()

  const saving = ref(false)
  const showEditDialog = ref(false)

  const editForm = reactive({
    name: '',
    description: '',
    iconId: undefined as number | null | undefined,
    tags: [] as string[]
  })

  const availableTags = computed(() => {
    const fromCurrent = store.current ? [store.current] : []
    const fromList = store.list.filter(item => item.id !== functionUnitId.value)
    const fromServer = (store.allTags ?? []).map((t: string) => ({ tags: [t] }))
    return collectAvailableTags([...fromServer, ...fromList, ...fromCurrent])
  })

  function openEditDialog() {
    editForm.name = store.current?.name || ''
    editForm.description = store.current?.description || ''
    editForm.iconId = store.current?.icon?.id ?? undefined
    editForm.tags = [...normalizeTags(store.current?.tags)]
    showEditDialog.value = true
  }

  async function handleSaveEdit() {
    if (!editForm.name.trim()) {
      ElMessage.warning(t('functionUnit.enterName'))
      return
    }
    saving.value = true
    try {
      await store.update(functionUnitId.value, {
        name: editForm.name.trim(),
        description: editForm.description?.trim() || undefined,
        iconId: editForm.iconId ?? undefined,
        tags: normalizeTags(editForm.tags),
      })
      ElMessage.success(t('functionUnit.saveSuccess'))
      showEditDialog.value = false
      await store.fetchById(functionUnitId.value)
      store.fetchAllTags()
    } catch (e: unknown) {
      const message = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      ElMessage.error(message || t('functionUnit.saveFailed'))
    } finally {
      saving.value = false
    }
  }

  return { saving, showEditDialog, editForm, availableTags, openEditDialog, handleSaveEdit }
}
