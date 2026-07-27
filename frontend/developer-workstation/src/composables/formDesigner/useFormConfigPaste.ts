import { ref } from 'vue'
import type { Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormDefinition } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'
import { collectStaleSubTableBindingIds } from '@/utils/staleSubTableBindings'

export type FormConfigPasteRepairResult = {
  configJson: Record<string, unknown>
  bindingIdMapping: Record<string, string>
  relationTableIdMapping: Record<string, string>
  warnings: string[]
  mixedSource: boolean
  applied: boolean
  createdTableNames?: string[]
}

interface UseFormConfigPasteOptions {
  functionUnitId: number
  selectedForm: Ref<FormDefinition | null>
  /** Live main-canvas rule reader (form-create getRule). */
  getMainDesignerRule: () => unknown[]
  /** Current form table binding ids (PRIMARY/SUB/RELATED). */
  getKnownBindingIds: () => number[]
  handleSelectForm: (row: FormDefinition) => Promise<void>
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Cross-FU form JSON paste / left-JSON-editor paste: call backend repair-config
 * to create missing tables/bindings when needed, remap stale ids, then rehydrate.
 */
export function useFormConfigPaste(options: UseFormConfigPasteOptions) {
  const {
    functionUnitId,
    selectedForm,
    getMainDesignerRule,
    getKnownBindingIds,
    handleSelectForm,
    t,
  } = options

  const showPasteConfigDialog = ref(false)
  const pasteConfigText = ref('')
  const pasteRepairing = ref(false)
  let lastAutoRepairSignature = ''
  let autoRepairTimer: ReturnType<typeof setTimeout> | null = null

  function openPasteConfigDialog() {
    if (!selectedForm.value?.id) {
      ElMessage.warning(t('form.selectFormFirst'))
      return
    }
    pasteConfigText.value = ''
    showPasteConfigDialog.value = true
  }

  function parsePastedConfigJson(raw: string): Record<string, unknown> | null {
    const trimmed = raw.trim()
    if (!trimmed) {
      ElMessage.warning(t('form.pasteConfigEmpty'))
      return null
    }
    try {
      const parsed = JSON.parse(trimmed) as unknown
      // Allow pasting a bare rule array (left JSON editor content).
      if (Array.isArray(parsed)) {
        return { rule: parsed }
      }
      if (!parsed || typeof parsed !== 'object') {
        ElMessage.error(t('form.pasteConfigInvalid'))
        return null
      }
      return parsed as Record<string, unknown>
    } catch {
      ElMessage.error(t('form.pasteConfigInvalid'))
      return null
    }
  }

  function summarizeRepair(result: FormConfigPasteRepairResult): string {
    const mapped = Object.keys(result.bindingIdMapping || {}).length
    const tables = Object.keys(result.relationTableIdMapping || {}).length
    const created = (result.createdTableNames || []).length
      || (result.warnings || []).filter((w) => w.startsWith('CREATED_TABLE:')).length
    const unmapped = (result.warnings || []).filter((w) => w.startsWith('UNMAPPED_BINDING:')).length
    if (created > 0) {
      return t('form.pasteConfigCreatedTables', { created, mapped })
    }
    if (unmapped > 0) {
      return t('form.pasteConfigPartial', { mapped, tables, unmapped })
    }
    if (mapped === 0 && tables === 0) {
      return t('form.pasteConfigNoRemap')
    }
    return t('form.pasteConfigSuccess', { mapped, tables })
  }

  async function applyRepairResult(result: FormConfigPasteRepairResult, form: FormDefinition) {
    if (!result?.configJson || typeof result.configJson !== 'object') {
      ElMessage.error(t('form.pasteConfigFailed'))
      return false
    }
    await handleSelectForm({
      ...form,
      configJson: result.configJson as FormDefinition['configJson'],
    })
    if (result.mixedSource) {
      ElMessage.warning(t('form.pasteConfigMixedSource'))
    }
    ElMessage.success(summarizeRepair(result))
    return true
  }

  async function callRepairApi(
    configJson: Record<string, unknown>,
    apply: boolean,
  ): Promise<FormConfigPasteRepairResult | null> {
    const form = selectedForm.value
    if (!form?.id) {
      ElMessage.warning(t('form.selectFormFirst'))
      return null
    }
    const res = await functionUnitApi.repairFormConfig(functionUnitId, form.id, {
      configJson,
      apply,
      // Only persist new tables when apply=true (manual Save). Paste/Repair preview remaps only.
      createMissingTables: apply,
    })
    return res.data as FormConfigPasteRepairResult
  }

  async function handleConfirmPasteConfig() {
    const form = selectedForm.value
    if (!form?.id) {
      ElMessage.warning(t('form.selectFormFirst'))
      return
    }
    const configJson = parsePastedConfigJson(pasteConfigText.value)
    if (!configJson) return

    pasteRepairing.value = true
    try {
      const result = await callRepairApi(configJson, false)
      if (!result) return
      const ok = await applyRepairResult(result, form)
      if (ok) {
        showPasteConfigDialog.value = false
        pasteConfigText.value = ''
        lastAutoRepairSignature = ''
      }
    } catch (e: unknown) {
      ElMessage.error(resolveUserFacingHttpMessage(e, t) || t('form.pasteConfigFailed'))
    } finally {
      pasteRepairing.value = false
    }
  }

  function buildLiveConfigForRepair(): Record<string, unknown> | null {
    const form = selectedForm.value
    if (!form?.id) return null
    const base = (form.configJson && typeof form.configJson === 'object')
      ? { ...(form.configJson as Record<string, unknown>) }
      : {}
    const liveRule = getMainDesignerRule()
    if (Array.isArray(liveRule)) {
      base.rule = liveRule
    }
    return base
  }

  function findStaleBindingIds(): number[] {
    return collectStaleSubTableBindingIds(getMainDesignerRule(), getKnownBindingIds())
  }

  function needsProvisionOrRemap(): boolean {
    const known = getKnownBindingIds()
    if (known.length === 0) return true
    return findStaleBindingIds().length > 0
  }

  async function repairCurrentDesignerBindings(force = false): Promise<boolean> {
    const form = selectedForm.value
    if (!form?.id) {
      ElMessage.warning(t('form.selectFormFirst'))
      return false
    }
    if (!needsProvisionOrRemap()) {
      if (force) ElMessage.info(t('form.pasteConfigNoRemap'))
      return false
    }
    const stale = findStaleBindingIds()
    const signature = `${knownLen()}:${stale.slice().sort((a, b) => a - b).join(',')}`
    if (!force && signature === lastAutoRepairSignature) {
      return false
    }

    const configJson = buildLiveConfigForRepair()
    if (!configJson) return false

    pasteRepairing.value = true
    try {
      const result = await callRepairApi(configJson, false)
      if (!result) return false
      lastAutoRepairSignature = signature
      return await applyRepairResult(result, form)
    } catch (e: unknown) {
      lastAutoRepairSignature = signature
      ElMessage.error(resolveUserFacingHttpMessage(e, t) || t('form.pasteConfigFailed'))
      return false
    } finally {
      pasteRepairing.value = false
    }
  }

  function knownLen(): number {
    return getKnownBindingIds().length
  }

  function scheduleAutoRepairStaleBindings() {
    if (autoRepairTimer) clearTimeout(autoRepairTimer)
    autoRepairTimer = setTimeout(() => {
      autoRepairTimer = null
      if (!needsProvisionOrRemap()) return
      void repairCurrentDesignerBindings(false)
    }, 600)
  }

  /** True when Save will call createMissingTables (long-running). */
  function willProvisionOnSave(nextConfig: Record<string, unknown>): boolean {
    if (!selectedForm.value?.id) return false
    const known = getKnownBindingIds()
    const rule = Array.isArray(nextConfig.rule) ? nextConfig.rule as unknown[] : []
    const stale = collectStaleSubTableBindingIds(rule, known)
    return known.length === 0 || stale.length > 0
  }

  /**
   * Called from Save: provision+remap live config, return repaired configJson (or null if no-op / fail).
   */
  async function provisionAndRepairForSave(
    nextConfig: Record<string, unknown>,
  ): Promise<Record<string, unknown> | null> {
    const form = selectedForm.value
    if (!form?.id) return null
    if (!willProvisionOnSave(nextConfig)) {
      return null
    }
    const result = await callRepairApi(nextConfig, true)
    if (!result?.configJson) return null
    try {
      const bindingsRes = await functionUnitApi.getFormBindings(functionUnitId, form.id)
      if (selectedForm.value?.id === form.id) {
        selectedForm.value = {
          ...selectedForm.value,
          tableBindings: bindingsRes.data || [],
          configJson: result.configJson as FormDefinition['configJson'],
        }
      }
    } catch {
      // FALLBACK(external): bindings refresh is UX; repaired configJson still returned for save
    }
    if ((result.createdTableNames || []).length > 0) {
      ElMessage.success(summarizeRepair(result))
    }
    return result.configJson
  }

  return {
    showPasteConfigDialog,
    pasteConfigText,
    pasteRepairing,
    openPasteConfigDialog,
    handleConfirmPasteConfig,
    repairCurrentDesignerBindings,
    scheduleAutoRepairStaleBindings,
    findStaleBindingIds,
    willProvisionOnSave,
    provisionAndRepairForSave,
  }
}
