<template>
  <div class="sequence-flow-properties">
    <el-collapse v-model="activeGroups">
      <!-- 基本信息 -->
      <el-collapse-item title="基本信息" name="basic">
        <el-form label-position="top" size="small">
          <el-form-item label="连接线ID">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item label="连接线名称">
            <el-input v-model="flowName" @change="updateBasicProp('name', flowName)" placeholder="连接线名称" />
          </el-form-item>
          <el-form-item label="源节点">
            <el-input :model-value="sourceRef" disabled />
          </el-form-item>
          <el-form-item label="目标节点">
            <el-input :model-value="targetRef" disabled />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- 条件配置 -->
      <el-collapse-item v-if="showCondition" title="条件配置" name="condition">
        <el-form label-position="top" size="small">
          <el-form-item label="条件类型">
            <el-select v-model="conditionType" @change="updateExtProp('conditionType', conditionType)">
              <el-option label="JUEL 表达式" value="juel" />
              <el-option label="脚本" value="script" />
            </el-select>
          </el-form-item>
          
          <el-form-item label="条件表达式">
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
            <div class="examples-title">常用表达式示例</div>
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
      
      <!-- 非条件分支说明 -->
      <el-collapse-item v-if="!showCondition" title="说明" name="info">
        <el-alert type="info" :closable="false">
          此连接线的源节点不是网关，无需配置条件表达式。
        </el-alert>
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
    : '// JavaScript 脚本\nreturn variable > 100;'
})

const conditionTip = computed(() => {
  return conditionType.value === 'juel'
    ? '使用 JUEL 表达式，如 ${amount > 1000}'
    : '使用 JavaScript 脚本，通过 return 返回布尔值'
})

const conditionExamples = [
  { expression: '${amount > 1000}', label: '金额大于1000' },
  { expression: '${approved == true}', label: '已审批通过' },
  { expression: '${status == "completed"}', label: '状态为已完成' }
]

function loadProperties() {
  if (!props.element) return
  
  const basic = getBasicProperties(props.element)
  flowName.value = basic.name
  
  const bo = props.element.businessObject
  sourceRef.value = bo.sourceRef?.name || bo.sourceRef?.id || ''
  targetRef.value = bo.targetRef?.name || bo.targetRef?.id || ''
  
  // 读取条件表达式
  const condition = bo.conditionExpression
  if (condition) {
    conditionExpression.value = condition.body || ''
  } else {
    conditionExpression.value = ''
  }
  
  // 读取扩展属性
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
