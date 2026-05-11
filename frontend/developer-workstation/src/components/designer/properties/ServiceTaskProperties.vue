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
              @change="updateExtProp('serviceType', serviceType)"
            >
              <el-option
                :label="t('properties.serviceTypeHttp')"
                value="http"
              />
              <el-option
                :label="t('properties.serviceTypeScript')"
                value="script"
              />
              <el-option
                :label="t('properties.serviceTypeMessage')"
                value="message"
              />
              <el-option
                :label="t('properties.serviceTypeN8n')"
                value="n8n"
              />
              <el-option
                :label="t('properties.serviceTypeDmn')"
                value="dmn"
              />
            </el-select>
          </el-form-item>
          
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

          <!-- N8N config -->
          <template v-if="serviceType === 'n8n'">
            <N8nTaskPropertiesPanel
              ref="n8nPanelRef"
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
      
      <!-- Retry config -->
      <el-collapse-item
        :title="t('properties.retryConfig')"
        name="retry"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.enableRetry')">
            <el-switch
              v-model="retryEnabled"
              @change="updateExtProp('retryEnabled', retryEnabled)"
            />
          </el-form-item>
          
          <template v-if="retryEnabled">
            <el-form-item :label="t('properties.retryCount')">
              <el-input-number
                v-model="retryCount"
                :min="1"
                :max="10"
                @change="updateExtProp('retryCount', retryCount)"
              />
            </el-form-item>
            
            <el-form-item :label="t('properties.retryInterval')">
              <el-input
                v-model="retryInterval"
                :placeholder="t('properties.retryIntervalPlaceholder')"
                @change="updateExtProp('retryInterval', retryInterval)"
              />
              <div class="form-tip">
                ISO 8601
              </div>
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
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import {
  getBasicProperties,
  setBasicProperties,
  getExtensionProperties,
  setExtensionProperty
} from '@/utils/bpmnExtensions'
import N8nTaskPropertiesPanel from './N8nTaskPropertiesPanel.vue'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
}>()

const activeGroups = ref(['basic', 'service'])

const taskName = ref('')
const taskDescription = ref('')

const serviceType = ref<'http' | 'script' | 'message' | 'n8n' | 'dmn'>('http')

const n8nPanelRef = ref<InstanceType<typeof N8nTaskPropertiesPanel> | null>(null)

const httpUrl = ref('')
const httpMethod = ref<'GET' | 'POST' | 'PUT' | 'DELETE'>('POST')
const httpHeadersStr = ref('')
const httpBody = ref('')
const httpResponseVar = ref('')

const scriptLanguage = ref<'javascript' | 'groovy'>('javascript')
const scriptContent = ref('')

const messageTopic = ref('')
const messagePayload = ref('')

const retryEnabled = ref(false)
const retryCount = ref(3)
const retryInterval = ref('PT5M')

const dmnDecisionKey = ref('')
const dmnFallbackToDefaultTenant = ref(false)

const basicProps = computed(() => getBasicProperties(props.element))

function loadProperties() {
  if (!props.element) return
  
  const basic = getBasicProperties(props.element)
  taskName.value = basic.name
  
  const ext = getExtensionProperties(props.element)
  taskDescription.value = ext.description || ''
  serviceType.value = ext.serviceType || 'http'
  httpUrl.value = ext.httpUrl || ''
  httpMethod.value = ext.httpMethod || 'POST'
  httpHeadersStr.value = ext.httpHeaders ? JSON.stringify(ext.httpHeaders, null, 2) : ''
  httpBody.value = ext.httpBody || ''
  httpResponseVar.value = ext.httpResponseVar || ''
  scriptLanguage.value = ext.scriptLanguage || 'javascript'
  scriptContent.value = ext.scriptContent || ''
  messageTopic.value = ext.messageTopic || ''
  messagePayload.value = ext.messagePayload || ''
  retryEnabled.value = ext.retryEnabled || false
  retryCount.value = ext.retryCount || 3
  retryInterval.value = ext.retryInterval || 'PT5M'
  dmnDecisionKey.value = ext.decisionTableReferenceKey || ''
  dmnFallbackToDefaultTenant.value = ext.fallbackToDefaultTenant || false
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
    // Ignore JSON parse errors
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
      color: #303133;
      background: #fafafa;
      padding: 0 12px;
      height: 36px;
      line-height: 36px;
      border-radius: 4px;
      margin-bottom: 8px;
      
      &:hover {
        background: #f0f0f0;
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
      color: #606266;
      padding-bottom: 4px;
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
