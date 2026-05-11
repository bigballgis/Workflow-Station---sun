import { ref } from 'vue'
import type { Ref } from 'vue'
import type { FormDefinition } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import { ElMessage, ElMessageBox } from 'element-plus'

export interface FormActionsContext {
  functionUnitId: number
  store: {
    updateForm: (unitId: number, formId: number, data: any) => Promise<void>
    deleteForm: (unitId: number, formId: number) => Promise<void>
  }
  renameTargetForm: Ref<FormDefinition | null>
  renameFormName: Ref<string>
  showRenameDialog: Ref<boolean>
  selectedForm: Ref<FormDefinition | null>
  loadForms: () => Promise<void>
  t: (key: string, options?: Record<string, any>) => string
}

export function useFormActions(ctx: FormActionsContext) {
  const { functionUnitId, store, renameTargetForm, renameFormName, showRenameDialog, selectedForm, loadForms, t } = ctx

  const renaming = ref(false)

  async function handleDeleteForm(row: FormDefinition) {
    await ElMessageBox.confirm(t('form.deleteConfirm'), t('form.deleteTitle'), { type: 'warning' })
    try {
      await store.deleteForm(functionUnitId, row.id)
      ElMessage.success(t('form.deleteSuccess'))
      loadForms()
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('form.deleteFailed'))
    }
  }

  async function handleConfirmRename() {
    const target = renameTargetForm.value
    const nextName = renameFormName.value.trim()
    if (!target) return
    if (!nextName) {
      ElMessage.warning(t('form.formNameRequired'))
      return
    }
    if (nextName === target.formName) {
      showRenameDialog.value = false
      return
    }
    renaming.value = true
    try {
      await store.updateForm(functionUnitId, target.id, {
        formName: nextName,
        formType: target.formType,
        description: target.description,
        configJson: target.configJson || {}
      })
      await loadForms()
      if (selectedForm.value?.id === target.id) {
        selectedForm.value = { ...selectedForm.value, formName: nextName }
      }
      ElMessage.success(t('form.renameFormSuccess'))
      showRenameDialog.value = false
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('form.renameFormFailed'))
    } finally {
      renaming.value = false
    }
  }

  async function handleCopyForm(form: FormDefinition) {
    try {
      const res = await functionUnitApi.copyTaskForm(functionUnitId, form.id)
      ElMessage.success(t('form.copyFormSuccess'))
      await loadForms()
      if (res?.data) {
        selectedForm.value = res.data
      }
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('form.copyFormFailed'))
    }
  }

  async function handleCopyProcessToTaskForm(form: FormDefinition) {
    try {
      const res = await functionUnitApi.copyProcessToTaskForm(functionUnitId, form.id)
      ElMessage.success(t('form.copyProcessToTaskFormSuccess'))
      await loadForms()
      if (res?.data) {
        selectedForm.value = res.data
      }
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || t('form.copyProcessToTaskFormFailed'))
    }
  }

  return {
    renaming,
    handleDeleteForm,
    handleConfirmRename,
    handleCopyForm,
    handleCopyProcessToTaskForm,
  }
}
