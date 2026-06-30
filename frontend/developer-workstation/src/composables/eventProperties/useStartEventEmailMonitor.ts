/**
 * Start Event ↔ EmailMonitorRule bidirectional binding for the process designer.
 */
import { ref, reactive, watch, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import {
  emailMonitorApi,
  type EmailMonitorRule,
  type EmailMonitorRuleRequest,
  type ExtractionRules
} from '@/api/emailMonitor'
import { connectionApi, type EmailConnection } from '@/api/connection'
import { getExtensionProperties, resolveProcessDefinitionKey, setExtensionProperties } from '@/utils/bpmnExtensions'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'

type MonitorProps = {
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
}

function isNotFoundError(err: unknown): boolean {
  const status = (err as { response?: { status?: number } })?.response?.status
  return status === 404
}

function isConflictError(err: unknown): boolean {
  const status = (err as { response?: { status?: number } })?.response?.status
  const code = (err as { response?: { data?: { error?: { code?: string } } } })?.response?.data?.error?.code
  return status === 409 || code === 'CONFLICT_RULE_NAME' || code === 'CONFLICT_START_EVENT'
}

function rulesEqual(a?: ExtractionRules, b?: ExtractionRules): boolean {
  return JSON.stringify(a ?? {}) === JSON.stringify(b ?? {})
}

function unwrapMonitorRule(res: unknown): EmailMonitorRule {
  const body = res as { data?: EmailMonitorRule }
  if (body?.data?.id != null) {
    return body.data
  }
  throw new Error('Invalid email monitor response')
}

function defaultForm(): EmailMonitorRuleRequest {
  return {
    name: '',
    enabled: true,
    connectionUid: '',
    actionType: 'START_PROCESS',
    folderLabel: 'INBOX',
    pollIntervalSeconds: 60,
    reviewOnMissing: true,
    extractionRules: {}
  }
}

export function useStartEventEmailMonitor(
  props: MonitorProps,
  updateExtProp: (name: string, value: unknown) => void,
  t: (key: string, params?: Record<string, unknown>) => string
) {
  const enabled = ref(false)
  const saving = ref(false)
  const loading = ref(false)
  const ruleId = ref<number | null>(null)
  const showWizard = ref(false)
  const inboundConnections = ref<EmailConnection[]>([])
  const processDefinitionKey = ref('')
  const startEventId = ref('')
  const form = reactive<EmailMonitorRuleRequest>(defaultForm())
  const extractionRules = ref<ExtractionRules>({})

  const inboundOnly = (list: EmailConnection[]) =>
    list.filter(c => c.direction === 'INBOUND' || c.direction === 'BOTH')

  async function loadConnections() {
    try {
      const res = await connectionApi.list(props.functionUnitId)
      inboundConnections.value = inboundOnly(res.data || [])
    } catch {
      inboundConnections.value = []
    }
  }

  function syncBpmnBinding(id: number | null, isEnabled: boolean) {
    if (!props.element || !props.modeler) {
      return
    }
    setExtensionProperties(props.modeler, props.element, {
      emailMonitorRuleId: id != null ? String(id) : '',
      emailMonitorEnabled: isEnabled ? 'true' : 'false'
    })
  }

  function applyRule(rule: EmailMonitorRule, options?: { syncBpmn?: boolean }) {
    ruleId.value = rule.id
    enabled.value = rule.enabled
    form.name = rule.name
    form.enabled = rule.enabled
    form.connectionUid = rule.connectionUid
    form.filterFrom = rule.filterFrom || ''
    form.filterSubject = rule.filterSubject || ''
    form.systemInitiatorUserId = rule.systemInitiatorUserId || ''
    form.folderLabel = rule.folderLabel || 'INBOX'
    form.pollIntervalSeconds = rule.pollIntervalSeconds ?? 60
    form.reviewOnMissing = rule.reviewOnMissing ?? true
    form.actionType = rule.actionType || 'START_PROCESS'
    const nextRules = rule.extractionRules ? { ...rule.extractionRules } : {}
    form.extractionRules = nextRules
    if (!rulesEqual(extractionRules.value, nextRules)) {
      extractionRules.value = nextRules
    }
    if (options?.syncBpmn !== false) {
      nextTick(() => syncBpmnBinding(rule.id, rule.enabled))
    }
  }

  function resetForNewEvent(eventLabel: string) {
    ruleId.value = null
    const defaults = defaultForm()
    Object.assign(form, defaults)
    form.name = eventLabel
      ? t('emailMonitor.startEvent.defaultName', { name: eventLabel })
      : t('emailMonitor.startEvent.defaultNameFallback')
    extractionRules.value = {}
    syncBpmnBinding(null, enabled.value)
  }

  async function fetchRuleByStartEvent(eventId: string): Promise<EmailMonitorRule | null> {
    try {
      const res = await emailMonitorApi.getByStartEventId(props.functionUnitId, eventId)
      return res.data
    } catch (err) {
      if (isNotFoundError(err)) {
        return null
      }
      throw err
    }
  }

  async function fetchRuleById(id: number): Promise<EmailMonitorRule | null> {
    try {
      const res = await emailMonitorApi.get(props.functionUnitId, id)
      return res.data
    } catch (err) {
      if (isNotFoundError(err)) {
        return null
      }
      throw err
    }
  }

  async function loadMonitor() {
    if (!props.element?.businessObject || saving.value) {
      return
    }
    loading.value = true
    try {
      startEventId.value = props.element.businessObject.id || ''
      processDefinitionKey.value = resolveProcessDefinitionKey(props.element)
      const ext = getExtensionProperties(props.element)
      const extEnabled = ext.emailMonitorEnabled === 'true' || ext.emailMonitorEnabled === true
      enabled.value = extEnabled

      let rule: EmailMonitorRule | null = null
      const extRuleId = ext.emailMonitorRuleId ? Number(ext.emailMonitorRuleId) : NaN
      if (!Number.isNaN(extRuleId) && extRuleId > 0) {
        rule = await fetchRuleById(extRuleId)
      }
      if (!rule && startEventId.value) {
        rule = await fetchRuleByStartEvent(startEventId.value)
      }

      if (rule) {
        applyRule(rule)
        enabled.value = rule.enabled || extEnabled
      } else {
        resetForNewEvent(props.element.businessObject.name || startEventId.value)
        enabled.value = extEnabled
      }
    } catch {
      ElMessage.error(t('emailMonitor.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  function buildPayload(): EmailMonitorRuleRequest {
    form.extractionRules = extractionRules.value
    return {
      ...form,
      enabled: enabled.value,
      startEventId: startEventId.value,
      processDefinitionKey: processDefinitionKey.value || undefined,
      name: form.name.trim(),
      connectionUid: form.connectionUid,
      filterFrom: form.filterFrom?.trim() || undefined,
      filterSubject: form.filterSubject?.trim() || undefined,
      systemInitiatorUserId: form.systemInitiatorUserId?.trim() || undefined
    }
  }

  async function saveMonitor(retryAfterConflict = false): Promise<boolean> {
    if (saving.value) {
      return false
    }
    if (!enabled.value) {
      return true
    }
    if (!form.name.trim()) {
      ElMessage.warning(t('emailMonitor.nameRequired'))
      return false
    }
    if (!form.connectionUid) {
      ElMessage.warning(t('emailMonitor.connectionRequired'))
      return false
    }
    if (!startEventId.value) {
      ElMessage.warning(t('emailMonitor.startEvent.missingEventId'))
      return false
    }
    if (!processDefinitionKey.value) {
      ElMessage.warning(t('emailMonitor.startEvent.missingProcessKey'))
      return false
    }

    saving.value = true
    try {
      const payload = buildPayload()
      const res = ruleId.value
        ? await emailMonitorApi.update(props.functionUnitId, ruleId.value, payload)
        : await emailMonitorApi.create(props.functionUnitId, payload)
      applyRule(unwrapMonitorRule(res))
      ElMessage.success(t('emailMonitor.startEvent.saved'))
      return true
    } catch (err) {
      if (!retryAfterConflict && !ruleId.value && isConflictError(err) && startEventId.value) {
        const existing = await fetchRuleByStartEvent(startEventId.value)
        if (existing) {
          ruleId.value = existing.id
          saving.value = false
          return saveMonitor(true)
        }
      }
      ElMessage.error(resolveUserFacingHttpMessage(err, t) || t('emailMonitor.startEvent.saveFailed'))
      return false
    } finally {
      saving.value = false
    }
  }

  async function onEnabledChange(value: boolean) {
    enabled.value = value
    updateExtProp('emailMonitorEnabled', value ? 'true' : 'false')
    if (!value && ruleId.value) {
      form.enabled = false
      await saveMonitor()
    }
  }

  async function confirmWizard() {
    form.extractionRules = extractionRules.value
    const canPersist = Boolean(
      form.connectionUid
      && form.name.trim()
      && startEventId.value
      && processDefinitionKey.value
    )
    if (!canPersist) {
      ElMessage.info(t('emailMonitor.startEvent.extractionDraftSaved'))
      showWizard.value = false
      return
    }
    const ok = await saveMonitor()
    if (ok) {
      showWizard.value = false
    }
  }

  async function openWizard() {
    try {
      if (ruleId.value) {
        const rule = await fetchRuleById(ruleId.value)
        if (rule) {
          applyRule(rule, { syncBpmn: false })
        }
      } else if (startEventId.value) {
        const rule = await fetchRuleByStartEvent(startEventId.value)
        if (rule) {
          applyRule(rule, { syncBpmn: false })
        }
      }
    } catch {
      ElMessage.error(t('emailMonitor.loadFailed'))
      return
    }
    showWizard.value = true
  }

  watch(() => props.element?.id, loadMonitor, { immediate: true })

  onMounted(loadConnections)

  return {
    enabled,
    saving,
    loading,
    ruleId,
    showWizard,
    inboundConnections,
    processDefinitionKey,
    startEventId,
    form,
    extractionRules,
    loadMonitor,
    saveMonitor,
    onEnabledChange,
    confirmWizard,
    openWizard
  }
}
