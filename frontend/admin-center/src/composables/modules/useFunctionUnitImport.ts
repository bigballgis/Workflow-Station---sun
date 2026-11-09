/**
 * Admin 手动导入功能单元：读包 → 预览包里的团队 → 确认团队 → 导入（平台访问模型 §8.1）。
 *
 * 能不能导入、预填哪个团队由后端预览给的状态决定；后端导入时按同一规则再校验一次。
 */

import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifyError, notifySuccess } from '@/utils/notify'
import { notifyUnreportedError } from '@/utils/notifyUnreportedError'
import { functionUnitApi, type ImportPreview } from '@/api/functionUnit'

/** An i18n message: why the package cannot be imported, or where the chosen team came from. */
export interface ImportMessage {
  key: string
  params: Record<string, string>
}

export interface InitialImportTeam {
  teamCode: string
  /** Set when the package cannot be imported here at all (D-23: its team is missing or disabled). */
  block: ImportMessage | null
}

/**
 * The team the dialog starts with: the package's team, else the team the unit already has here; only teams the user
 * may choose are prefilled.
 */
export function initialImportTeam(preview: ImportPreview): InitialImportTeam {
  const selectable = new Set(preview.selectableTeams.map(team => team.code))
  const packaged = preview.packageTeam
  if (packaged) {
    if (packaged.state !== 'ACTIVE') {
      const key = packaged.state === 'MISSING' ? 'functionUnit.importTeamMissing' : 'functionUnit.importTeamInactive'
      return { teamCode: '', block: { key, params: { name: packaged.name, code: packaged.code } } }
    }
    return { teamCode: selectable.has(packaged.code) ? packaged.code : '', block: null }
  }
  const current = preview.currentTeam
  return { teamCode: current && selectable.has(current.code) ? current.code : '', block: null }
}

/** The hint under the team select. */
export function importTeamHint(preview: ImportPreview, teamCode: string): ImportMessage {
  const packaged = preview.packageTeam
  if (packaged) {
    if (!preview.selectableTeams.some(team => team.code === packaged.code)) {
      return { key: 'functionUnit.packageTeamNotYours', params: { name: packaged.name, code: packaged.code } }
    }
    return { key: 'functionUnit.teamFromPackage', params: {} }
  }
  const current = preview.currentTeam
  if (current && current.code === teamCode) {
    return { key: 'functionUnit.teamFromCatalog', params: {} }
  }
  return { key: 'functionUnit.teamRequired', params: {} }
}

export function useFunctionUnitImport(onImported: () => void) {
  const { t } = useI18n()

  const showImportDialog = ref(false)
  const importLoading = ref(false)
  const previewLoading = ref(false)
  const importFile = ref<File | null>(null)
  const importContent = ref('')
  const importPreview = ref<ImportPreview | null>(null)
  const importTeamCode = ref('')
  const importBlock = ref<ImportMessage | null>(null)

  const canStartImport = computed(() =>
    !!importPreview.value && !importBlock.value && !!importTeamCode.value)

  const resetImportDialog = () => {
    importFile.value = null
    importContent.value = ''
    importPreview.value = null
    importTeamCode.value = ''
    importBlock.value = null
  }

  const openImportDialog = () => {
    resetImportDialog()
    showImportDialog.value = true
  }

  watch(showImportDialog, (open) => {
    if (!open) resetImportDialog()
  })

  const handleImportFileChange = async (file: { raw?: File } | null) => {
    resetImportDialog()
    const raw = file?.raw ?? null
    if (!raw) return
    importFile.value = raw
    previewLoading.value = true
    try {
      const content = await readBase64(raw)
      const preview = await functionUnitApi.previewImport({ fileName: raw.name, fileContent: content })
      if (importFile.value !== raw) return
      const initial = initialImportTeam(preview)
      importContent.value = content
      importPreview.value = preview
      importTeamCode.value = initial.teamCode
      importBlock.value = initial.block
    } catch (e: unknown) {
      if (importFile.value !== raw) return
      notifyUnreportedError(e, t('functionUnit.importFailed'))
    } finally {
      previewLoading.value = false
    }
  }

  const handleStartImport = async () => {
    if (!canStartImport.value || !importFile.value) return
    importLoading.value = true
    try {
      const result = await functionUnitApi.import({
        fileName: importFile.value.name,
        fileContent: importContent.value,
        teamCode: importTeamCode.value,
      })
      if (result.success) {
        notifySuccess(result.versioned ? t('functionUnit.importVersioned') : t('functionUnit.importSuccess'))
        showImportDialog.value = false
        onImported()
      } else {
        notifyError(result.errorMessage || t('functionUnit.importFailed'))
      }
    } catch (e: unknown) {
      notifyUnreportedError(e, t('functionUnit.importFailed'))
    } finally {
      importLoading.value = false
    }
  }

  return {
    showImportDialog,
    importLoading,
    previewLoading,
    importFile,
    importPreview,
    importTeamCode,
    importBlock,
    canStartImport,
    openImportDialog,
    handleImportFileChange,
    handleStartImport,
  }
}

function readBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result).split(',')[1] ?? '')
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}
