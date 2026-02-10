<template>
  <div class="sequence-flow-properties">
    <el-collapse v-model="activeGroups">
      <!-- Basic info -->
      <el-collapse-item :title="t('properties.basic')" name="basic">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.flowId')">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item :label="t('properties.flowName')">
            <el-input v-model="flowName" @change="updateBasicProp('name', flowName)" :placeholder="t('properties.flowNamePlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('properties.sourceNode')">
            <el-input :model-value="sourceRef" disabled />
          </el-form-item>
          <el-form-item :label="t('properties.targetNode')">
            <el-input :model-value="targetRef" disabled />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Condition config -->
      <el-collapse-item v-if="showCondition" :title="t('properties.conditionConfig')" name="condition">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.conditionType')">
            <el-select v-model="conditionType" @change="updateExtProp('conditionType', conditionType)">
              <el-option :label="t('properties.juelExpression')" value="juel" />
              <el-option :label="t('properties.script')" value="script" />
            </el-select>
          </el-form-item>
          
          <el-form-item :label="t('properties.conditionExpression')">
            <el-input 
              v-model="conditionExpression" 
              type="textarea" 
              :rows="4"
              @change="updateConditionExpression"
              :placeholder="conditionPlaceholder"
            />
            <div class="form-tip">{{ conditionTip }}</div>
          </el-form-item>
          
          <div class="condition-examples">
            <div class="examples-title">{{ t('properties.commonExpressions') }}</div>
            <div 
              v-for="example in conditionExamples" 
              :key="example.expression"
              class="example-item" 
              @click="insertExample(example.expression)"
            >
              <code>{{ example.expression }}</code>
              <span>{{ example.label }}</span>
            </div>
          </div>
        </el-form>
      </el-collapse-item>
      
      <!-- Non-condition branch info -->
      <el-collapse-item v-if="!showCondition" :title="t('properties.info')" name="info">
        <el-alert type="info" :closable="false">
          {{ t('properties.flowNoConditionInfo') }}
        </el-alert>
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

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
}>()

const activeGroups = ref(['basic', 'condition'])

const flowName = ref('')
const sourceRef = ref('')
const targetRef = ref('')
const conditionType = ref<'juel' | 'script'>('juel')
const conditionExpression = ref('')

const basicProps = computed(() => getBasicProperties(props.element))

const showCondition = computed(() => {
  const bo = props.element?.businessObject
  const sourceType = bo?.sourceRef?.$type
  return sourceType?.includes('Gateway')
})

const conditionPlaceholder = computed(() => {
  return conditionType.value === 'juel' 
    ? '${variable > 100}' 
    : t('properties.conditionPlaceholderScript')
})

const conditionTip = computed(() => {
  return conditionType.value === 'juel'
    ? t('properties.conditionTipJuel')
    : t('properties.conditionTipScript')
})

const conditionExamples = computed(() => [
  { expression: '${amount > 1000}', label: t('properties.amountGreaterThan1000') },
  { expression: '${approved == true}', label: t('properties.approved') },
  { expression: '${status == "completed"}', label: t('properties.statusCompleted') }
])

function loadProperties() {
  if (!props.element) return
  
  const basic = getBasicProperties(props.element)
  flowName.value = basic.name
  
  const bo = props.element.businessObject
  sourceRef.value = bo.sourceRef?.name || bo.sourceRef?.id || ''
  targetRef.value = bo.targetRef?.name || bo.targetRef?.id || ''
  
  // Read condition expression
  const condition = bo.conditionExpression
  if (condition) {
    conditionExpression.value = condition.body || ''
  } else {
    conditionExpression.value = ''
  }
  
  // Read extension properties
  const ext = getExtensionProperties(props.element)
  conditionType.value = ext.conditionType || 'juel'
}

function updateBasicProp(name: string, value: any) {
  if (!props.element || !props.modeler) return
  setBasicProperties(props.modeler, props.element, { [name]: value })
}

function updateExtProp(name: string, value: any) {
  if (!props.element || !props.modeler) return
  setExtensionProperty(props.modeler, props.element, name, value)
}

function updateConditionExpression() {
  if (!props.element || !props.modeler) return
  
  const modeling = props.modeler.get('modeling')
  const moddle = props.modeler.get('moddle')
  
  if (conditionExpression.value) {
    const condition = moddle.create('bpmn:FormalExpression', {
      body: conditionExpression.value
    })
    modeling.updateProperties(props.element, { conditionExpression: condition })
  } else {
    modeling.updateProperties(props.element, { conditionExpression: undefined })
  }
}

function insertExample(example: string) {
  conditionExpression.value = example
  updateConditionExpression()
}

watch(() => props.element, loadProperties, { immediate: true })

onMounted(loadProperties)
</script>

<style lang="scss" scoped>
.sequence-flow-properties {
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
  
  .condition-examples {
    margin-top: 16px;
    padding-top: 12px;
    border-top: 1px dashed #e6e6e6;
    
    .examples-title {
      font-size: 12px;
      font-weight: 600;
      color: #606266;
      margin-bottom: 8px;
    }
    
    .example-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 6px 8px;
      background: #f5f7fa;
      border-radius: 4px;
      margin-bottom: 6px;
      cursor: pointer;
      transition: background 0.2s;
      
      &:hover {
        background: #e6e8eb;
      }
      
      code {
        font-size: 11px;
        color: #DB0011;
        background: #fff;
        padding: 2px 6px;
        border-radius: 3px;
      }
      
      span {
        font-size: 11px;
        color: #909399;
      }
    }
  }
}
</style>
