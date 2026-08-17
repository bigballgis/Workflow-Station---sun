/**
 * Start Event ↔ Email Monitor template binding (filters configured here only).
 */
import { ref, watch, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import {
  emailMonitorApi,
  type EmailMonitorRule,
  type EmailMonitorStartEventBindRequest
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

function unwrapMonitorRule(res: unknown): EmailMonitorRule {
  const body = res as { data?: EmailMonitorRule }
  if (body?.data?.id != null) {
    return body.data
  }
  throw new Error('Invalid email monitor response')
}

export function useStartEventEmailMonitor(
  props: MonitorProps,
  updateExtProp: (name: string, value: unknown) => void,
  t: (key: string, params?: Record<string, unknown>) => string
) {
  const enabled = ref(false)
  const saving = ref(false)
  const loading = ref(false)
  const bindingId = ref<number | null>(null)
  const templateRuleId = ref<number | null>(null)
  const templates = ref<EmailMonitorRule[]>([])
  const connections = ref<EmailConnection[]>([])
  const processDefinitionKey = ref('')
  const startEventId = ref('')
  const filterFrom = ref('')
  const filterSubject = ref('')

  const selectedTemplate = computed(() =>
    templates.value.find(r => r.id === templateRuleId.value) ?? null
  )

  const connectionLabel = computed(() => {
    const uid = selectedTemplate.value?.connectionUid
    if (!uid) {
      return ''
    }
    const conn = connections.value.find(c => c.connectionUid === uid)
    return conn ? `${conn.name} (${conn.connectionType})` : uid
  })

  const hasExtraction = computed(() => {
    const rules = selectedTemplate.value?.extractionRules
    const fields = rules?.fields?.length ?? 0
    const subTables = rules?.subTables?.length ?? 0
    return fields + subTables > 0
  })

  async function loadTemplates() {
    try {
      const [templatesRes, connRes] = await Promise.all([
        emailMonitorApi.listTemplates(props.functionUnitId),
        connectionApi.list(props.functionUnitId)
      ])
      templates.value = templatesRes.data || []
      connections.value = connRes.data || []
    } catch {
      templates.value = []
      connections.value = []
    }
  }

  function syncBpmnBinding(ruleId: number | null, isEnabled: boolean) {
    if (!props.element || !props.modeler) {
      return
    }
    setExtensionProperties(props.modeler, props.element, {
      emailMonitorRuleId: ruleId != null ? String(ruleId) : '',
      emailMonitorEnabled: isEnabled ? 'true' : 'false'
    })
  }

  function applyBinding(rule: EmailMonitorRule) {
    bindingId.value = rule.id
    templateRuleId.value = rule.sourceRuleId ?? null
    filterFrom.value = rule.filterFrom || ''
    filterSubject.value = rule.filterSubject || ''
    enabled.value = rule.enabled
    nextTick(() => syncBpmnBinding(rule.id, rule.enabled))
  }

  function resetForNewEvent() {
    bindingId.value = null
    templateRuleId.value = null
    filterFrom.value = ''
    filterSubject.value = ''
    syncBpmnBinding(null, enabled.value)
  }

  async function fetchBindingByStartEvent(eventId: string): Promise<EmailMonitorRule | null> {
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

  async function loadMonitor() {
    if (!props.element?.businessObject || saving.value) {
      return
    }
    loading.value = true
    try {
      startEventId.value = props.element.businessObject.id || ''
      processDefinitionKey.value = resolveProcessDefinitionKey(props.element)
      const ext = getExtensionProperties(props.element)
      enabled.value = ext.emailMonitorEnabled === 'true' || ext.emailMonitorEnabled === true

      const binding = startEventId.value
        ? await fetchBindingByStartEvent(startEventId.value)
        : null

      if (binding) {
        applyBinding(binding)
        enabled.value = binding.enabled || enabled.value
      } else {
        resetForNewEvent()
        enabled.value = ext.emailMonitorEnabled === 'true' || ext.emailMonitorEnabled === true
      }
    } catch {
      ElMessage.error(t('emailMonitor.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  async function saveBinding(): Promise<boolean> {
    if (saving.value) {
      return false
    }
    if (!enabled.value) {
      return true
    }
    if (!templateRuleId.value) {
      ElMessage.warning(t('emailMonitor.startEvent.templateRequired'))
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
      const payload: EmailMonitorStartEventBindRequest = {
        templateRuleId: templateRuleId.value,
        startEventId: startEventId.value,
        processDefinitionKey: processDefinitionKey.value,
        filterFrom: filterFrom.value.trim() || undefined,
        filterSubject: filterSubject.value.trim() || undefined,
        enabled: enabled.value
      }
      const res = await emailMonitorApi.bindStartEvent(props.functionUnitId, payload)
      applyBinding(unwrapMonitorRule(res))
      ElMessage.success(t('emailMonitor.startEvent.saved'))
      return true
    } catch (err) {
      ElMessage.error(resolveUserFacingHttpMessage(err, t) || t('emailMonitor.startEvent.saveFailed'))
      return false
    } finally {
      saving.value = false
    }
  }

  async function onEnabledChange(value: boolean) {
    enabled.value = value
    updateExtProp('emailMonitorEnabled', value ? 'true' : 'false')
    if (!value && startEventId.value) {
      saving.value = true
      try {
        await emailMonitorApi.unbindStartEvent(props.functionUnitId, startEventId.value)
        resetForNewEvent()
      } catch (err) {
        ElMessage.error(resolveUserFacingHttpMessage(err, t) || t('emailMonitor.startEvent.saveFailed'))
      } finally {
        saving.value = false
      }
    }
  }

  watch(() => props.element?.id, loadMonitor, { immediate: true })
  onMounted(loadTemplates)

  return {
    enabled,
    saving,
    loading,
    templates,
    templateRuleId,
    selectedTemplate,
    connectionLabel,
    hasExtraction,
    processDefinitionKey,
    startEventId,
    filterFrom,
    filterSubject,
    loadMonitor,
    saveBinding,
    onEnabledChange
  }
}
