<template>
  <div class="service-task-properties">
    <el-collapse v-model="activeGroups">
      <!-- 基本信息 -->
      <el-collapse-item title="基本信息" name="basic">
        <el-form label-position="top" size="small">
          <el-form-item label="任务ID">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item label="任务名称">
            <el-input v-model="taskName" @change="updateBasicProp('name', taskName)" placeholder="任务名称" />
          </el-form-item>
          <el-form-item label="任务描述">
            <el-input v-model="taskDescription" type="textarea" :rows="2" @change="updateExtProp('description', taskDescription)" placeholder="任务描述" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- 服务类型 -->
      <el-collapse-item title="服务配置" name="service">
        <el-form label-position="top" size="small">
          <el-form-item label="服务类型">
            <el-select v-model="serviceType" @change="updateExtProp('serviceType', serviceType)">
              <el-option label="HTTP 调用" value="http" />
              <el-option label="脚本执行" value="script" />
              <el-option label="消息发送" value="message" />
            </el-select>
          </el-form-item>
          
          <!-- HTTP 配置 -->
          <template v-if="serviceType === 'http'">
            <el-form-item label="请求URL">
              <el-input v-model="httpUrl" @change="updateExtProp('httpUrl', httpUrl)" placeholder="https://api.example.com/endpoint" />
            </el-form-item>
            
            <el-form-item label="请求方法">
              <el-select v-model="httpMethod" @change="updateExtProp('httpMethod', httpMethod)">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
                <el-option label="DELETE" value="DELETE" />
              </el-select>
            </el-form-item>
            
            <el-form-item label="请求头">
              <el-input v-model="httpHeadersStr" type="textarea" :rows="3" @change="updateHttpHeaders" placeholder='{"Content-Type": "application/json"}' />
              <div class="form-tip">JSON 格式的请求头</div>
            </el-form-item>
            
            <el-form-item label="请求体">
              <el-input v-model="httpBody" type="textarea" :rows="4" @change="updateExtProp('httpBody', httpBody)" placeholder='{"key": "${variable}"}' />
              <div class="form-tip">支持 ${variable} 引用流程变量</div>
            </el-form-item>
            
            <el-form-item label="响应存储变量">
              <el-input v-model="httpResponseVar" @change="updateExtProp('httpResponseVar', httpResponseVar)" placeholder="responseData" />
              <div class="form-tip">将响应结果存储到此变量</div>
            </el-form-item>
          </template>
          
          <!-- 脚本配置 -->
          <template v-if="serviceType === 'script'">
            <el-form-item label="脚本语言">
              <el-select v-model="scriptLanguage" @change="updateExtProp('scriptLanguage', scriptLanguage)">
                <el-option label="JavaScript" value="javascript" />
                <el-option label="Groovy" value="groovy" />
              </el-select>
            </el-form-item>
            
            <el-form-item label="脚本内容">
              <el-input v-model="scriptContent" type="textarea" :rows="8" @change="updateExtProp('scriptContent', scriptContent)" placeholder="// 在此编写脚本" />
              <div class="form-tip">可通过 execution 对象访问流程变量</div>
            </el-form-item>
          </template>
          
          <!-- 消息配置 -->
          <template v-if="serviceType === 'message'">
            <el-form-item label="消息主题">
              <el-input v-model="messageTopic" @change="updateExtProp('messageTopic', messageTopic)" placeholder="order.created" />
            </el-form-item>
            
            <el-form-item label="消息内容">
              <el-input v-model="messagePayload" type="textarea" :rows="4" @change="updateExtProp('messagePayload', messagePayload)" placeholder='{"orderId": "${orderId}"}' />
            </el-form-item>
          </template>
        </el-form>
      </el-collapse-item>
      
      <!-- 重试配置 -->
      <el-collapse-item title="重试配置" name="retry">
        <el-form label-position="top" size="small">
          <el-form-item label="启用重试">
            <el-switch v-model="retryEnabled" @change="updateExtProp('retryEnabled', retryEnabled)" />
          </el-form-item>
          
          <template v-if="retryEnabled">
            <el-form-item label="重试次数">
              <el-input-number v-model="retryCount" :min="1" :max="10" @change="updateExtProp('retryCount', retryCount)" />
            </el-form-item>
            
            <el-form-item label="重试间隔">
              <el-input v-model="retryInterval" @change="updateExtProp('retryInterval', retryInterval)" placeholder="PT5M (5分钟)" />
              <div class="form-tip">ISO 8601 格式</div>
            </el-form-item>
          </template>
        </el-form>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import {
  getBasicProperties,
  setBasicProperties,
  getExtensionProperties,
  setExtensionProperty
} from '@/utils/bpmnExtensions'

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
}>()

const activeGroups = ref(['basic', 'service'])

// 基本属性
const taskName = ref('')
const taskDescription = ref('')

// 服务配置
const serviceType = ref<'http' | 'script' | 'message'>('http')

// HTTP 配置
const httpUrl = ref('')
const httpMethod = ref<'GET' | 'POST' | 'PUT' | 'DELETE'>('POST')
const httpHeadersStr = ref('')
const httpBody = ref('')
const httpResponseVar = ref('')

// 脚本配置
const scriptLanguage = ref<'javascript' | 'groovy'>('javascript')
const scriptContent = ref('')

// 消息配置
const messageTopic = ref('')
const messagePayload = ref('')

// 重试配置
const retryEnabled = ref(false)
const retryCount = ref(3)
const retryInterval = ref('PT5M')

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
    // 忽略 JSON 解析错误
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
