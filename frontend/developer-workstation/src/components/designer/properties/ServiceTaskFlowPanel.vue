<!--
  Service Task (type=ap) panel — FR-C01/C02: the task references its automation
  flow by ONE business key (`ap:flowKey` = the flow's metadata.hermesFlowKey).
  Flows themselves are designed on the standalone Automation page; webhook URL,
  timeout/retry and variable mappings are no longer BPMN concerns.

  The picker lazy-loads the project's flows via the bridge session on first open;
  a load failure only shows a hint — manual key entry is never blocked.
-->
<template>
  <div class="ap-task-properties">
    <el-form-item
      :label="t('properties.apFlowKey')"
      :error="validationErrors.flowKey"
    >
      <el-select
        v-model="apConfig.flowKey"
        class="flow-key-select"
        filterable
        clearable
        allow-create
        default-first-option
        :loading="loadingFlows"
        :placeholder="t('properties.apFlowKeyPlaceholder')"
        @visible-change="onDropdownVisible"
        @change="saveConfig"
      >
        <el-option
          v-for="flow in flowOptions"
          :key="flow.key"
          :label="`${flow.name} (${flow.key})`"
          :value="flow.key"
        />
      </el-select>
      <div class="form-tip">
        {{ t('properties.apFlowKeyTip') }}
      </div>
      <div
        v-if="flowLoadFailed"
        class="form-tip form-tip--warning"
      >
        {{ t('properties.apFlowKeyLoadFailed') }}
      </div>
    </el-form-item>

    <el-alert
      v-if="legacyFlowId"
      :title="t('properties.apFlowKeyLegacy', { id: legacyFlowId })"
      type="warning"
      show-icon
      :closable="false"
      class="legacy-alert"
    />

    <!-- Envelope contract: how the flow receives and returns process variables -->
    <div class="contract-tip">
      <div class="contract-tip__title">
        {{ t('properties.apContractTitle') }}
      </div>
      <div class="contract-tip__line">
        {{ t('properties.apContractInput') }}
        <code>{{ INPUT_EXPRESSION }}</code>
      </div>
      <div class="contract-tip__line">
        {{ t('properties.apContractOutput') }}
        <code>{{ OUTPUT_ENVELOPE }}</code>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import { getExtensionProperties, setExtensionProperty, removeExtensionProperty } from '@/utils/bpmnExtensions'
import { fetchServiceTaskSession, listAutomationFlows } from '@/api/automation'
import {
  LEGACY_AP_KEYS,
  serializeApConfig,
  deserializeApConfig,
  validateApConfig,
  createDefaultApConfig,
  type ServiceTaskConfig,
  type ApValidationErrors
} from '@/utils/serviceTaskConfigSerializer'

const { t } = useI18n()

// 信封契约样例。字符串拼接是必需的：模板里直接写 `{{…}}` 字面量会被 Vue 编译器
// 当成插值结束符，SFC 解析直接报错。
const INPUT_EXPRESSION = `{${'{'}trigger.body.variables.<name>${'}'}}`
const OUTPUT_ENVELOPE = '{ "variables": { ... } }'

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
}>()

const validationErrors = reactive<ApValidationErrors>({ flowKey: '' })
const apConfig = reactive<ServiceTaskConfig>(createDefaultApConfig())
/** Legacy ap:flowId found on load (no ap:flowKey yet); cleared once re-saved as a key. */
const legacyFlowId = ref('')

/* ---- optional flow picker (lazy, non-blocking) ---- */
interface FlowOption { key: string; name: string }
const flowOptions = ref<FlowOption[]>([])
const loadingFlows = ref(false)
const flowLoadFailed = ref(false)
let flowsLoaded = false

async function onDropdownVisible(visible: boolean) {
  if (!visible || flowsLoaded || loadingFlows.value) return
  loadingFlows.value = true
  flowLoadFailed.value = false
  try {
    const session = await fetchServiceTaskSession()
    const page = await listAutomationFlows({
      token: session.token,
      projectId: session.projectId,
      limit: 100,
    })
    flowOptions.value = (page.data || [])
      .map((flow) => ({
        key: flow.metadata?.hermesFlowKey || '',
        name: flow.version?.displayName || flow.id,
      }))
      .filter((option) => option.key !== '')
    flowsLoaded = true
  } catch (error) {
    // Manual entry stays available — only surface a hint.
    flowLoadFailed.value = true
    console.error('[ServiceTaskFlowPanel] flow list load failed', error)
  } finally {
    loadingFlows.value = false
  }
}

/** Save: write ap:flowKey and clear every legacy ap:* key (FR-C02). */
function saveConfig() {
  if (!props.element || !props.modeler) return
  validate()
  const serialized = serializeApConfig(apConfig)
  for (const [key, value] of Object.entries(serialized)) {
    setExtensionProperty(props.modeler, props.element, key, value)
  }
  for (const key of LEGACY_AP_KEYS) {
    removeExtensionProperty(props.modeler, props.element, key)
  }
  if (apConfig.flowKey) {
    legacyFlowId.value = ''
  }
}

/** Load AP config from BPMN extension properties (legacy ap:flowId prefills the input). */
function loadConfig() {
  if (!props.element) return
  const ext = getExtensionProperties(props.element)
  const loaded = deserializeApConfig(ext)
  apConfig.flowKey = loaded.flowKey || loaded.legacyFlowId
  legacyFlowId.value = loaded.legacyFlowId
  validate()
}

/** Validate required fields - returns true if valid */
function validate(): boolean {
  const errors = validateApConfig(apConfig)
  validationErrors.flowKey = errors.flowKey ? t('properties.apFlowKeyRequired') : ''
  return !errors.flowKey
}

/** Expose validate method for parent component */
defineExpose({ validate })

watch(() => props.element, loadConfig, { immediate: true })

onMounted(loadConfig)
</script>

<style lang="scss" scoped>
.ap-task-properties {
  .flow-key-select {
    width: 100%;
  }

  .form-tip {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    margin-top: 4px;
    line-height: 1.4;
  }

  .form-tip--warning {
    color: var(--el-color-warning);
  }

  .legacy-alert {
    margin-bottom: 12px;

    :deep(.el-alert__title) {
      font-size: 12px;
      line-height: 1.5;
    }
  }

  .contract-tip {
    margin-top: 4px;
    padding: 8px 10px;
    border-radius: 4px;
    background: var(--el-fill-color-lighter);
    font-size: 11px;
    line-height: 1.6;
    color: var(--el-text-color-secondary);

    .contract-tip__title {
      font-weight: 600;
      color: var(--el-text-color-regular);
      margin-bottom: 2px;
    }

    code {
      font-size: 11px;
      color: var(--el-text-color-regular);
      background: var(--el-fill-color);
      border-radius: 3px;
      padding: 0 4px;
      word-break: break-all;
    }
  }
}
</style>
