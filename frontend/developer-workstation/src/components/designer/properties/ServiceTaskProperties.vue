<template>
  <div class="service-task-properties">
    <el-collapse v-model="activeGroups">
      <!-- Basic info -->
      <el-collapse-item
        :title="t('properties.basic')"
        name="basic"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.taskId')">
            <el-input
              :model-value="basicProps.id"
              disabled
            />
          </el-form-item>
          <el-form-item :label="t('properties.taskName')">
            <el-input
              v-model="taskName"
              :placeholder="t('properties.taskName')"
              @change="updateBasicProp('name', taskName)"
            />
          </el-form-item>
          <el-form-item :label="t('properties.taskDescription')">
            <el-input
              v-model="taskDescription"
              type="textarea"
              :rows="2"
              :placeholder="t('properties.taskDescription')"
              @change="updateExtProp('description', taskDescription)"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Service config -->
      <el-collapse-item
        :title="t('properties.serviceConfig')"
        name="service"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('common.type')">
            <el-select
              v-model="serviceType"
              @change="onServiceTypeChange"
            >
              <!-- 引擎只执行 ap 与 dmn；旧类型仅在存量节点上显示，供迁移 -->
              <el-option
                v-if="isLegacyType"
                :label="legacyTypeLabel"
                :value="serviceType"
                disabled
              />
              <el-option
                :label="t('properties.serviceTypeAp')"
                value="ap"
              />
              <el-option
                :label="t('properties.serviceTypeDmn')"
                value="dmn"
              />
            </el-select>
          </el-form-item>

          <el-alert
            v-if="isLegacyType"
            :title="t('properties.serviceTypeLegacyWarning')"
            type="warning"
            show-icon
            :closable="false"
            class="legacy-type-alert"
          />

          <!-- HTTP config -->
          <template v-if="serviceType === 'http'">
            <el-form-item :label="t('properties.requestUrl')">
              <el-input
                v-model="httpUrl"
                placeholder="https://api.example.com/endpoint"
                @change="updateExtProp('httpUrl', httpUrl)"
              />
            </el-form-item>
            
            <el-form-item :label="t('properties.requestMethod')">
              <el-select
                v-model="httpMethod"
                @change="updateExtProp('httpMethod', httpMethod)"
              >
                <el-option
                  label="GET"
                  value="GET"
                />
                <el-option
                  label="POST"
                  value="POST"
                />
                <el-option
                  label="PUT"
                  value="PUT"
                />
                <el-option
                  label="DELETE"
                  value="DELETE"
                />
              </el-select>
            </el-form-item>
            
            <el-form-item :label="t('properties.requestHeaders')">
              <el-input
                v-model="httpHeadersStr"
                type="textarea"
                :rows="3"
                placeholder="{&quot;Content-Type&quot;: &quot;application/json&quot;}"
                @change="updateHttpHeaders"
              />
              <div class="form-tip">
                {{ t('properties.requestHeadersTip') }}
              </div>
            </el-form-item>
            
            <el-form-item :label="t('properties.requestBody')">
              <el-input
                v-model="httpBody"
                type="textarea"
                :rows="4"
                placeholder="{&quot;key&quot;: &quot;${variable}&quot;}"
                @change="updateExtProp('httpBody', httpBody)"
              />
              <div class="form-tip">
                {{ t('properties.requestBodyTip') }}
              </div>
            </el-form-item>
            
            <el-form-item :label="t('properties.responseVariable')">
              <el-input
                v-model="httpResponseVar"
                placeholder="responseData"
                @change="updateExtProp('httpResponseVar', httpResponseVar)"
              />
              <div class="form-tip">
                {{ t('properties.responseVariableTip') }}
              </div>
            </el-form-item>
          </template>
          
          <!-- Script config -->
          <template v-if="serviceType === 'script'">
            <el-form-item :label="t('properties.scriptLanguage')">
              <el-select
                v-model="scriptLanguage"
                @change="updateExtProp('scriptLanguage', scriptLanguage)"
              >
                <el-option
                  label="JavaScript"
                  value="javascript"
                />
                <el-option
                  label="Groovy"
                  value="groovy"
                />
              </el-select>
            </el-form-item>
            
            <el-form-item :label="t('properties.scriptContent')">
              <el-input
                v-model="scriptContent"
                type="textarea"
                :rows="8"
                :placeholder="t('properties.scriptContentPlaceholder')"
                @change="updateExtProp('scriptContent', scriptContent)"
              />
              <div class="form-tip">
                {{ t('properties.scriptContentTip') }}
              </div>
            </el-form-item>
          </template>
          
          <!-- Message config -->
          <template v-if="serviceType === 'message'">
            <el-form-item :label="t('properties.messageTopic')">
              <el-input
                v-model="messageTopic"
                placeholder="order.created"
                @change="updateExtProp('messageTopic', messageTopic)"
              />
            </el-form-item>
            
            <el-form-item :label="t('properties.messagePayload')">
              <el-input
                v-model="messagePayload"
                type="textarea"
                :rows="4"
                placeholder="{&quot;orderId&quot;: &quot;${orderId}&quot;}"
                @change="updateExtProp('messagePayload', messagePayload)"
              />
            </el-form-item>
          </template>

          <!-- ServiceTask config -->
          <template v-if="serviceType === 'ap'">
            <ServiceTaskFlowPanel
              ref="apPanelRef"
              :modeler="modeler"
              :element="element"
            />
          </template>

          <!-- DMN config -->
          <template v-if="serviceType === 'dmn'">
            <el-form-item :label="t('properties.dmnDecisionKey')">
              <el-input
                v-model="dmnDecisionKey"
                :placeholder="t('properties.dmnDecisionKeyPlaceholder')"
                @change="updateExtProp('decisionTableReferenceKey', dmnDecisionKey)"
              />
            </el-form-item>
            <el-form-item :label="t('properties.dmnFallbackToDefaultTenant')">
              <el-switch
                v-model="dmnFallbackToDefaultTenant"
                @change="updateExtProp('fallbackToDefaultTenant', dmnFallbackToDefaultTenant)"
              />
            </el-form-item>
          </template>
        </el-form>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import {
  getBasicProperties,
  setBasicProperties,
  getExtensionProperties,
  setExtensionProperty,
  removeExtensionProperty
} from '@/utils/bpmnExtensions'
import ServiceTaskFlowPanel from './ServiceTaskFlowPanel.vue'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
}>()

const activeGroups = ref(['basic', 'service'])

const taskName = ref('')
const taskDescription = ref('')

type ServiceTaskType = 'http' | 'script' | 'message' | 'ap' | 'dmn'

/** 引擎不执行的存量类型（ServiceTaskExecutor 只认 ap:flowId，DMN 走 Flowable 原生字段） */
const LEGACY_TYPES: ServiceTaskType[] = ['http', 'script', 'message']

/** 各类型独占的扩展属性；切换类型时清掉其他类型的残留，避免污染导出/版本快照 */
const TYPE_EXT_KEYS: Record<ServiceTaskType, string[]> = {
  http: ['httpUrl', 'httpMethod', 'httpHeaders', 'httpBody', 'httpResponseVar'],
  script: ['scriptLanguage', 'scriptContent'],
  message: ['messageTopic', 'messagePayload'],
  ap: ['ap:flowId', 'ap:webhookUrl', 'ap:timeoutSeconds', 'ap:retryCount', 'ap:inputMapping', 'ap:outputMapping'],
  dmn: ['decisionTableReferenceKey', 'fallbackToDefaultTenant'],
}

const serviceType = ref<ServiceTaskType>('ap')

const isLegacyType = computed(() => LEGACY_TYPES.includes(serviceType.value))
const legacyTypeLabel = computed(() => {
  switch (serviceType.value) {
    case 'http': return t('properties.serviceTypeHttp')
    case 'script': return t('properties.serviceTypeScript')
    case 'message': return t('properties.serviceTypeMessage')
    default: return serviceType.value
  }
})

const apPanelRef = ref<InstanceType<typeof ServiceTaskFlowPanel> | null>(null)

const httpUrl = ref('')
const httpMethod = ref<'GET' | 'POST' | 'PUT' | 'DELETE'>('POST')
const httpHeadersStr = ref('')
const httpBody = ref('')
const httpResponseVar = ref('')

const scriptLanguage = ref<'javascript' | 'groovy'>('javascript')
const scriptContent = ref('')

const messageTopic = ref('')
const messagePayload = ref('')

const dmnDecisionKey = ref('')
const dmnFallbackToDefaultTenant = ref(false)

const basicProps = computed(() => getBasicProperties(props.element))

function loadProperties() {
  if (!props.element) return
  
  const basic = getBasicProperties(props.element)
  taskName.value = basic.name
  
  const ext = getExtensionProperties(props.element)
  taskDescription.value = ext.description || ''
  serviceType.value = ext.serviceType || 'ap'
  // 默认类型也必须落盘：只在 change 时写会让从未切换过下拉的任务缺 serviceType，
  // Automation 页按该标记识别任务，缺失则整个 tab 显示空态。
  if (!ext.serviceType && props.modeler) {
    updateExtProp('serviceType', serviceType.value)
  }
  httpUrl.value = ext.httpUrl || ''
  httpMethod.value = ext.httpMethod || 'POST'
  httpHeadersStr.value = ext.httpHeaders ? JSON.stringify(ext.httpHeaders, null, 2) : ''
  httpBody.value = ext.httpBody || ''
  httpResponseVar.value = ext.httpResponseVar || ''
  scriptLanguage.value = ext.scriptLanguage || 'javascript'
  scriptContent.value = ext.scriptContent || ''
  messageTopic.value = ext.messageTopic || ''
  messagePayload.value = ext.messagePayload || ''
  dmnDecisionKey.value = ext.decisionTableReferenceKey || ''
  dmnFallbackToDefaultTenant.value = ext.fallbackToDefaultTenant || false
}

/** 切换类型：写入新类型并清理其他类型的残留扩展属性（可用 Ctrl+Z 撤销） */
function onServiceTypeChange(newType: ServiceTaskType) {
  if (!props.element || !props.modeler) return
  updateExtProp('serviceType', newType)
  for (const [type, keys] of Object.entries(TYPE_EXT_KEYS)) {
    if (type === newType) continue
    for (const key of keys) {
      removeExtensionProperty(props.modeler, props.element, key)
    }
  }
  loadProperties()
}

function updateBasicProp(name: string, value: any) {
  if (!props.element || !props.modeler) return
  setBasicProperties(props.modeler, props.element, { [name]: value })
}

function updateExtProp(name: string, value: any) {
  if (!props.element || !props.modeler) return
  setExtensionProperty(props.modeler, props.element, name, value)
}

function updateHttpHeaders() {
  try {
    const headers = httpHeadersStr.value ? JSON.parse(httpHeadersStr.value) : {}
    updateExtProp('httpHeaders', headers)
  } catch {
    ElMessage.error(t('properties.requestHeadersInvalid'))
  }
}

watch(() => props.element, loadProperties, { immediate: true })

onMounted(loadProperties)
</script>

<style lang="scss" scoped>
.service-task-properties {
  :deep(.el-collapse) {
    border: none;

    .el-collapse-item__header {
      font-size: 13px;
      font-weight: 600;
      color: var(--el-text-color-primary);
      background: var(--el-fill-color-lighter);
      padding: 0 12px;
      height: 36px;
      line-height: 36px;
      border-radius: 4px;
      margin-bottom: 8px;

      &:hover {
        background: var(--el-fill-color-light);
      }
    }

    .el-collapse-item__wrap {
      border: none;
    }

    .el-collapse-item__content {
      padding: 0 4px 12px;
    }
  }

  :deep(.el-form-item) {
    margin-bottom: 12px;

    .el-form-item__label {
      font-size: 12px;
      color: var(--el-text-color-regular);
      padding-bottom: 4px;
    }
  }

  .legacy-type-alert {
    margin-bottom: 12px;
  }

  .form-tip {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    margin-top: 4px;
    line-height: 1.4;
  }
}
</style>
