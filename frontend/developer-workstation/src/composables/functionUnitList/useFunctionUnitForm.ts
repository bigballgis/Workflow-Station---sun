import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance } from 'element-plus'
import type { FunctionUnitResponse } from '@/api/functionUnit'
import type { useFunctionUnitStore } from '@/stores/functionUnit'
import { getTags, setTags } from '@/utils/tagStorage'

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
    tags: [] as string[]
  })

  const formDialogTitle = computed(() =>
    formDialogMode.value === 'create' ? t('functionUnit.create') : t('functionUnit.settings')
  )
  const formRules = computed(() => ({
    name: [{ required: true, message: t('functionUnit.enterName'), trigger: 'blur' }]
  }))

  function resetBasicForm() {
    basicForm.name = ''
    basicForm.description = ''
    basicForm.iconId = null
    basicForm.tags = []
  }

  function openCreateDialog() {
    formDialogMode.value = 'create'
    settingsItemId.value = null
    resetBasicForm()
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
    basicForm.tags = [...getTags(item.id)]
    showFormDialog.value = true
  }

  async function handleFormSubmit() {
    await formRef.value?.validate()
    formSubmitting.value = true
    try {
      const payload = {
        name: basicForm.name.trim(),
        description: basicForm.description?.trim() || undefined,
        iconId: basicForm.iconId ?? undefined
      }
      if (formDialogMode.value === 'create') {
        const result = await store.create(payload)
        if (result) {
          setTags(result.id, basicForm.tags)
        }
        ElMessage.success(t('functionUnit.createSuccess'))
      } else if (settingsItemId.value != null) {
        await store.update(settingsItemId.value, payload)
        setTags(settingsItemId.value, basicForm.tags)
        ElMessage.success(t('functionUnit.saveSuccess'))
      }
      showFormDialog.value = false
      resetBasicForm()
      reload()
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
    openCreateDialog,
    handleFormDialogClosed,
    handleSettings,
    handleFormSubmit,
  }
}
