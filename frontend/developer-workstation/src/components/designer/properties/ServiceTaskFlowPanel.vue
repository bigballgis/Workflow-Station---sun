<template>
  <div class="ap-task-properties">
    <!-- AP Flow ID -->
    <el-form-item
      :label="t('properties.apFlowId')"
      :error="validationErrors.flowId"
    >
      <el-input
        v-model="apConfig.flowId"
        :placeholder="t('properties.apFlowIdPlaceholder')"
        @change="saveConfig"
      />
      <div class="form-tip">
        {{ t('properties.apFlowIdTip') }}
      </div>
    </el-form-item>

    <!-- Webhook URL override (optional) -->
    <el-form-item :label="t('properties.apWebhookUrl')">
      <el-input
        v-model="apConfig.webhookUrl"
        :placeholder="t('properties.apWebhookUrlPlaceholder')"
        @change="saveConfig"
      />
      <div class="form-tip">
        {{ t('properties.apWebhookUrlTip') }}
      </div>
    </el-form-item>

    <!-- Timeout -->
    <el-form-item :label="t('properties.apTimeout')">
      <el-input-number
        v-model="apConfig.timeoutSeconds"
        :min="1"
        :max="3600"
        @change="saveConfig"
      />
      <span class="form-tip">{{ t('properties.apTimeoutUnit') }}</span>
    </el-form-item>

    <!-- Retry Count -->
    <el-form-item :label="t('properties.apRetryCount')">
      <el-input-number
        v-model="apConfig.retryCount"
        :min="0"
        :max="10"
        @change="saveConfig"
      />
    </el-form-item>

    <!-- Input Mapping -->
    <div class="mapping-section">
      <div class="mapping-header">
        <span>{{ t('properties.apInputMapping') }}</span>
        <el-button
          type="primary"
          link
          size="small"
          @click="addInputMapping"
        >
          + {{ t('common.add') }}
        </el-button>
      </div>
      <div class="table-scroll-wrap">
        <el-table
          v-if="apConfig.inputMapping.length > 0"
          :data="apConfig.inputMapping"
          size="small"
          border
        >
          <el-table-column
            :label="t('properties.apMappingSource')"
            min-width="120"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.source"
                size="small"
                :placeholder="t('properties.apInputSourcePlaceholder')"
                @change="saveConfig"
              />
            </template>
          </el-table-column>
          <el-table-column
            :label="t('properties.apMappingTarget')"
            min-width="120"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.target"
                size="small"
                :placeholder="t('properties.apInputTargetPlaceholder')"
                @change="saveConfig"
              />
            </template>
          </el-table-column>
          <el-table-column
            width="50"
            align="center"
          >
            <template #default="{ $index }">
              <el-button
                type="danger"
                link
                size="small"
                @click="removeInputMapping($index)"
              >
                ✕
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- Output Mapping -->
    <div class="mapping-section">
      <div class="mapping-header">
        <span>{{ t('properties.apOutputMapping') }}</span>
        <el-button
          type="primary"
          link
          size="small"
          @click="addOutputMapping"
        >
          + {{ t('common.add') }}
        </el-button>
      </div>
      <div class="table-scroll-wrap">
        <el-table
          v-if="apConfig.outputMapping.length > 0"
          :data="apConfig.outputMapping"
          size="small"
          border
        >
          <el-table-column
            :label="t('properties.apMappingSource')"
            min-width="120"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.source"
                size="small"
                :placeholder="t('properties.apOutputSourcePlaceholder')"
                @change="saveConfig"
              />
            </template>
          </el-table-column>
          <el-table-column
            :label="t('properties.apMappingTarget')"
            min-width="120"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.target"
                size="small"
                :placeholder="t('properties.apOutputTargetPlaceholder')"
                @change="saveConfig"
              />
            </template>
          </el-table-column>
          <el-table-column
            width="50"
            align="center"
          >
            <template #default="{ $index }">
              <el-button
                type="danger"
                link
                size="small"
                @click="removeOutputMapping($index)"
              >
                ✕
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import { getExtensionProperties, setExtensionProperty } from '@/utils/bpmnExtensions'
import type { ServiceTaskConfig } from '@/api/serviceTask'
import {
  serializeApConfig,
  deserializeApConfig,
  validateApConfig,
  createDefaultApConfig,
  type ApValidationErrors
} from '@/utils/serviceTaskConfigSerializer'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
}>()

const validationErrors = reactive<ApValidationErrors>({ flowId: '' })
const apConfig = reactive<ServiceTaskConfig>(createDefaultApConfig())

/** Add input mapping row */
function addInputMapping() {
  apConfig.inputMapping.push({ source: '', target: '' })
  saveConfig()
}

/** Remove input mapping row */
function removeInputMapping(index: number) {
  apConfig.inputMapping.splice(index, 1)
  saveConfig()
}

/** Add output mapping row */
function addOutputMapping() {
  apConfig.outputMapping.push({ source: '', target: '' })
  saveConfig()
}

/** Remove output mapping row */
function removeOutputMapping(index: number) {
  apConfig.outputMapping.splice(index, 1)
  saveConfig()
}

/** Save all AP config to BPMN extension properties */
function saveConfig() {
  if (!props.element || !props.modeler) return
  clearValidationErrors()
  const serialized = serializeApConfig(apConfig)
  for (const [key, value] of Object.entries(serialized)) {
    setExtensionProperty(props.modeler, props.element, key, value)
  }
}

/** Load AP config from BPMN extension properties */
function loadConfig() {
  if (!props.element) return
  const ext = getExtensionProperties(props.element)
  const loaded = deserializeApConfig(ext)
  Object.assign(apConfig, loaded)
}

/** Validate required fields - returns true if valid */
function validate(): boolean {
  const errors = validateApConfig(apConfig)
  validationErrors.flowId = errors.flowId
  return !errors.flowId
}

function clearValidationErrors() {
  validationErrors.flowId = ''
}

/** Expose validate method for parent component */
defineExpose({ validate })

watch(() => props.element, loadConfig, { immediate: true })

onMounted(loadConfig)
</script>

<style lang="scss" scoped>
.ap-task-properties {
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
    margin-top: 4px;
    line-height: 1.4;
  }
}
</style>
