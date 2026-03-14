<template>
  <div class="n8n-task-properties">
    <!-- N8N Connection Config -->
    <el-form-item :label="t('properties.n8nConfigId')" :error="validationErrors.configId">
      <el-select
        v-model="n8nConfig.configId"
        :placeholder="t('properties.n8nConfigPlaceholder')"
        filterable
        @change="onConfigChange"
      >
        <el-option
          v-for="config in configList"
          :key="config.id"
          :label="config.name"
          :value="config.id"
        />
      </el-select>
    </el-form-item>

    <!-- N8N Workflow Selection -->
    <el-form-item :label="t('properties.n8nWorkflowId')">
      <el-select
        v-model="n8nConfig.workflowId"
        :placeholder="t('properties.n8nWorkflowPlaceholder')"
        filterable
        :disabled="!n8nConfig.configId"
        @change="onWorkflowChange"
      >
        <el-option
          v-for="wf in workflowList"
          :key="wf.id"
          :label="wf.name"
          :value="wf.id"
        />
      </el-select>
    </el-form-item>

    <!-- Webhook URL -->
    <el-form-item :label="t('properties.n8nWebhookUrl')" :error="validationErrors.webhookUrl">
      <el-input
        v-model="n8nConfig.webhookUrl"
        :placeholder="t('properties.n8nWebhookUrlPlaceholder')"
        @change="saveConfig"
      />
    </el-form-item>

    <!-- Timeout -->
    <el-form-item :label="t('properties.n8nTimeout')">
      <el-input-number
        v-model="n8nConfig.timeoutSeconds"
        :min="1"
        :max="3600"
        @change="saveConfig"
      />
      <span class="form-tip">{{ t('properties.n8nTimeoutUnit') }}</span>
    </el-form-item>

    <!-- Retry Count -->
    <el-form-item :label="t('properties.n8nRetryCount')">
      <el-input-number
        v-model="n8nConfig.retryCount"
        :min="0"
        :max="10"
        @change="saveConfig"
      />
    </el-form-item>

    <!-- Input Mapping -->
    <div class="mapping-section">
      <div class="mapping-header">
        <span>{{ t('properties.n8nInputMapping') }}</span>
        <el-button type="primary" link size="small" @click="addInputMapping">
          + {{ t('common.add') }}
        </el-button>
      </div>
      <el-table :data="n8nConfig.inputMapping" size="small" border v-if="n8nConfig.inputMapping.length > 0">
        <el-table-column :label="t('properties.n8nMappingSource')" min-width="120">
          <template #default="{ row, $index }">
            <el-input
              v-model="row.source"
              size="small"
              :placeholder="t('properties.n8nInputSourcePlaceholder')"
              @change="saveConfig"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('properties.n8nMappingTarget')" min-width="120">
          <template #default="{ row, $index }">
            <el-input
              v-model="row.target"
              size="small"
              :placeholder="t('properties.n8nInputTargetPlaceholder')"
              @change="saveConfig"
            />
          </template>
        </el-table-column>
        <el-table-column width="50" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="removeInputMapping($index)">
              ✕
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Output Mapping -->
    <div class="mapping-section">
      <div class="mapping-header">
        <span>{{ t('properties.n8nOutputMapping') }}</span>
        <el-button type="primary" link size="small" @click="addOutputMapping">
          + {{ t('common.add') }}
        </el-button>
      </div>
      <el-table :data="n8nConfig.outputMapping" size="small" border v-if="n8nConfig.outputMapping.length > 0">
        <el-table-column :label="t('properties.n8nMappingSource')" min-width="120">
          <template #default="{ row, $index }">
            <el-input
              v-model="row.source"
              size="small"
              :placeholder="t('properties.n8nOutputSourcePlaceholder')"
              @change="saveConfig"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('properties.n8nMappingTarget')" min-width="120">
          <template #default="{ row, $index }">
            <el-input
              v-model="row.target"
              size="small"
              :placeholder="t('properties.n8nOutputTargetPlaceholder')"
              @change="saveConfig"
            />
          </template>
        </el-table-column>
        <el-table-column width="50" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="removeOutputMapping($index)">
              ✕
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import { getExtensionProperties, setExtensionProperty } from '@/utils/bpmnExtensions'
import { n8nApi, type N8nConfig, type N8nWorkflow, type VariableMapping, type N8nTaskConfig } from '@/api/n8n'
import {
  serializeN8nConfig,
  deserializeN8nConfig,
  validateN8nConfig,
  createDefaultN8nConfig,
  type N8nValidationErrors
} from '@/utils/n8nConfigSerializer'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
}>()

const configList = ref<N8nConfig[]>([])
const workflowList = ref<N8nWorkflow[]>([])
const validationErrors = reactive<N8nValidationErrors>({ configId: '', webhookUrl: '' })

const n8nConfig = reactive<N8nTaskConfig>(createDefaultN8nConfig())

/** Load N8N connection configs from admin-center */
async function loadConfigs() {
  try {
    configList.value = await n8nApi.getConfigs()
  } catch {
    configList.value = []
  }
}

/** Load N8N workflows for selected config */
async function loadWorkflows(configId: string) {
  if (!configId) {
    workflowList.value = []
    return
  }
  try {
    workflowList.value = await n8nApi.getWorkflows(configId)
  } catch {
    workflowList.value = []
  }
}

/** Handle config selection change */
function onConfigChange(configId: string) {
  n8nConfig.workflowId = ''
  n8nConfig.webhookUrl = ''
  workflowList.value = []
  loadWorkflows(configId)
  saveConfig()
}

/** Handle workflow selection change - auto-fill webhook URL */
function onWorkflowChange(workflowId: string) {
  const selected = workflowList.value.find(wf => wf.id === workflowId)
  if (selected?.webhookUrl) {
    n8nConfig.webhookUrl = selected.webhookUrl
  }
  saveConfig()
}

/** Add input mapping row */
function addInputMapping() {
  n8nConfig.inputMapping.push({ source: '', target: '' })
  saveConfig()
}

/** Remove input mapping row */
function removeInputMapping(index: number) {
  n8nConfig.inputMapping.splice(index, 1)
  saveConfig()
}

/** Add output mapping row */
function addOutputMapping() {
  n8nConfig.outputMapping.push({ source: '', target: '' })
  saveConfig()
}

/** Remove output mapping row */
function removeOutputMapping(index: number) {
  n8nConfig.outputMapping.splice(index, 1)
  saveConfig()
}

/** Save all N8N config to BPMN extension properties */
function saveConfig() {
  if (!props.element || !props.modeler) return
  clearValidationErrors()
  const serialized = serializeN8nConfig(n8nConfig)
  for (const [key, value] of Object.entries(serialized)) {
    setExtensionProperty(props.modeler, props.element, key, value)
  }
}

/** Load N8N config from BPMN extension properties */
function loadConfig() {
  if (!props.element) return
  const ext = getExtensionProperties(props.element)
  const loaded = deserializeN8nConfig(ext)
  Object.assign(n8nConfig, loaded)

  // Load workflows if configId is set
  if (n8nConfig.configId) {
    loadWorkflows(n8nConfig.configId)
  }
}

/** Validate required fields - returns true if valid */
function validate(): boolean {
  const errors = validateN8nConfig(n8nConfig)
  validationErrors.configId = errors.configId
  validationErrors.webhookUrl = errors.webhookUrl
  return !errors.configId && !errors.webhookUrl
}

function clearValidationErrors() {
  validationErrors.configId = ''
  validationErrors.webhookUrl = ''
}

/** Expose validate method for parent component */
defineExpose({ validate })

watch(() => props.element, loadConfig, { immediate: true })

onMounted(() => {
  loadConfigs()
  loadConfig()
})
</script>

<style lang="scss" scoped>
.n8n-task-properties {
  .mapping-section {
    margin-bottom: 16px;

    .mapping-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
      font-size: 12px;
      font-weight: 600;
      color: #606266;
    }
  }

  .form-tip {
    font-size: 11px;
    color: #909399;
    margin-left: 8px;
  }
}
</style>
