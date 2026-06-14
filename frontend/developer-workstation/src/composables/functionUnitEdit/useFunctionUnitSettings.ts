import { computed, reactive, ref } from 'vue'
import type { ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { useFunctionUnitStore } from '@/stores/functionUnit'
import { getTags, setTags, getAllAvailableTags } from '@/utils/tagStorage'

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

  const availableTags = computed(() => getAllAvailableTags())

  function openEditDialog() {
    editForm.name = store.current?.name || ''
    editForm.description = store.current?.description || ''
    editForm.iconId = store.current?.icon?.id ?? undefined
    editForm.tags = [...getTags(functionUnitId.value)]
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
        iconId: editForm.iconId ?? undefined
      })
      setTags(functionUnitId.value, editForm.tags)
      ElMessage.success(t('functionUnit.saveSuccess'))
      showEditDialog.value = false
      store.fetchById(functionUnitId.value)
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('functionUnit.saveFailed'))
    } finally {
      saving.value = false
    }
  }

  return { saving, showEditDialog, editForm, availableTags, openEditDialog, handleSaveEdit }
}
