<template>
  <div class="subprocess-properties">
    <el-collapse v-model="activeGroups">
      <!-- Basic info -->
      <el-collapse-item :title="t('properties.basic')" name="basic">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.taskId')">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item :label="t('properties.subProcessName')">
            <el-input
              v-model="subProcessName"
              @change="updateBasicProp('name', subProcessName)"
              :placeholder="t('properties.subProcessNamePlaceholder')"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>

      <!-- Multi-instance config (the main event for this element) -->
      <el-collapse-item :title="t('properties.multiInstanceConfig')" name="multiInstance">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.enableMultiInstance')">
            <el-switch v-model="multiInstance" @change="handleMultiInstanceToggle" />
            <div class="form-tip">{{ t('properties.multiInstanceSubProcessTip') }}</div>
          </el-form-item>

          <template v-if="multiInstance">
            <el-form-item :label="t('properties.executionMode')">
              <el-radio-group v-model="sequential" @change="handleSequentialChange">
                <el-radio :value="false">{{ t('properties.parallelMode') }}</el-radio>
                <el-radio :value="true">{{ t('properties.sequentialMode') }}</el-radio>
              </el-radio-group>
              <div class="form-tip">{{ t('properties.executionModeTip') }}</div>
            </el-form-item>

            <el-form-item :label="t('properties.collectionVariable')" required>
              <el-input
                v-model="collectionVariable"
                @change="handleCollectionVariableChange"
                :placeholder="collectionVariablePlaceholder"
              >
                <template #append>
                  <el-tooltip :content="t('properties.collectionVariableSyncHint')" placement="top">
                    <el-button
                      :disabled="!suggestedCollectionVariable"
                      @click="applySuggestedCollectionVariable"
                    >
                      {{ t('properties.syncFromInnerTask') }}
                    </el-button>
                  </el-tooltip>
                </template>
              </el-input>
              <div class="form-tip">{{ t('properties.subProcessCollectionVariableTip') }}</div>
              <div v-if="collectionVarInvalid" class="form-error">
                {{ t('properties.collectionVariableInvalid') }}
              </div>
              <div v-if="suggestedCollectionVariable && suggestedCollectionVariable !== collectionVariable" class="form-tip">
                {{ t('properties.collectionVariableSuggestion', { name: suggestedCollectionVariable }) }}
              </div>
            </el-form-item>

            <el-form-item :label="t('properties.elementVariable')">
              <el-input
                v-model="elementVariable"
                @change="handleElementVariableChange"
                placeholder="currentItem"
              />
              <div class="form-tip">{{ t('properties.elementVariableTip') }}</div>
            </el-form-item>

            <el-form-item :label="t('properties.completionCondition')">
              <el-input
                v-model="completionCondition"
                @change="handleCompletionConditionChange"
                placeholder="${nrOfCompletedInstances/nrOfInstances == 1}"
              />
              <div class="form-tip">{{ t('properties.completionConditionTip') }}</div>
            </el-form-item>

            <el-alert
              v-if="!hasInnerUserTask"
              type="warning"
              :closable="false"
              show-icon
              style="margin-top: 8px;"
            >
              <template #title>{{ t('properties.multiInstanceMissingInnerTaskWarn') }}</template>
            </el-alert>
          </template>
        </el-form>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import {
  getBasicProperties,
  setBasicProperties,
  getExtensionProperties,
} from '@/utils/bpmnExtensions'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
}>()

const activeGroups = ref(['basic', 'multiInstance'])

const subProcessName = ref('')
const multiInstance = ref(false)
const sequential = ref(false)
const collectionVariable = ref('')
const elementVariable = ref('currentItem')
const completionCondition = ref('')

// Reactive tick to recompute suggestion when inner elements change (child add/remove, props)
const modelTick = ref(0)

const basicProps = computed(() => getBasicProperties(props.element))

const VARIABLE_NAME_RE = /^[a-zA-Z_][a-zA-Z0-9_]*$/

const collectionVarInvalid = computed(() => {
  return !!collectionVariable.value && !VARIABLE_NAME_RE.test(collectionVariable.value)
})

/** Walk inner flowElements of this SubProcess, pick the first userTask's subTableName (if any). */
function findInnerUserTaskSubTableName(): string {
  modelTick.value
  const bo: any = props.element?.businessObject
  const flowElements: any[] = bo?.flowElements || []
  for (const fe of flowElements) {
    if (fe?.$type !== 'bpmn:UserTask') continue
    const fakeElement: any = { businessObject: fe }
    const ext = getExtensionProperties(fakeElement)
    const raw = ext?.subTableName
    if (typeof raw === 'string' && raw.trim()) return raw.trim()
  }
  return ''
}

const suggestedCollectionVariable = computed(() => {
  const tableName = findInnerUserTaskSubTableName()
  return tableName ? `multiInstance_${tableName}_collection` : ''
})


const hasInnerUserTask = computed(() => {
  modelTick.value
  const bo: any = props.element?.businessObject
  const flowElements: any[] = bo?.flowElements || []
  return flowElements.some(fe => fe?.$type === 'bpmn:UserTask')
})

const collectionVariablePlaceholder = computed(() => {
  return suggestedCollectionVariable.value || 'multiInstance_<tableName>_collection'
})

function updateBasicProp(name: string, value: any) {
  if (!props.element || !props.modeler) return
  setBasicProperties(props.modeler, props.element, { [name]: value })
}

function getLoopCharacteristics(): any {
  return props.element?.businessObject?.loopCharacteristics || null
}

function readLoopChars(): { collection: string; elementVariable: string; completionCondition: string } {
  const lc = getLoopCharacteristics()
  const result = { collection: '', elementVariable: '', completionCondition: '' }
  if (!lc) return result

  const ext = lc.extensionElements
  if (ext?.values?.length) {
    for (const v of ext.values) {
      const type = v.$type || ''
      if (type === 'flowable:collection') {
        result.collection = (v.body || '').trim()
      } else if (type === 'flowable:elementVariable') {
        result.elementVariable = (v.body || '').trim()
      }
    }
  }
  const colAttr = (lc as any).get?.('flowable:collection') || (lc as any)['flowable:collection']
  if (!result.collection && typeof colAttr === 'string' && colAttr) {
    result.collection = colAttr
  }
  const evAttr = (lc as any).get?.('flowable:elementVariable') || (lc as any)['flowable:elementVariable']
  if (!result.elementVariable && typeof evAttr === 'string' && evAttr) {
    result.elementVariable = evAttr
  }

  const cc = lc.completionCondition
  if (cc?.body) {
    result.completionCondition = cc.body
  }

  return result
}

function writeLoopCharacteristics() {
  if (!props.element || !props.modeler) return
  const modeling = props.modeler.get('modeling')
  const moddle = props.modeler.get('moddle')

  if (!multiInstance.value) {
    modeling.updateProperties(props.element, { loopCharacteristics: undefined })
    return
  }

  const loopProps: any = {
    isSequential: !!sequential.value
  }
  let collectionName = collectionVariable.value.trim()
  if (!collectionName) {
    collectionName =
      suggestedCollectionVariable.value || 'multiInstance_subProcess_collection'
    collectionVariable.value = collectionName
  }
  const elementVarName = (elementVariable.value || 'currentItem').trim()
  loopProps['flowable:collection'] = collectionName
  if (elementVarName) {
    loopProps['flowable:elementVariable'] = elementVarName
  }
  if (completionCondition.value.trim()) {
    loopProps.completionCondition = moddle.create('bpmn:FormalExpression', {
      body: completionCondition.value.trim()
    })
  }

  const loopChars = moddle.create('bpmn:MultiInstanceLoopCharacteristics', loopProps)

  if (loopProps.completionCondition) {
    loopProps.completionCondition.$parent = loopChars
  }

  modeling.updateProperties(props.element, { loopCharacteristics: loopChars })
}

function handleMultiInstanceToggle(enabled: boolean) {
  if (enabled) {
    if (!collectionVariable.value && suggestedCollectionVariable.value) {
      collectionVariable.value = suggestedCollectionVariable.value
    }
    if (!elementVariable.value) {
      elementVariable.value = 'currentItem'
    }
  }
  writeLoopCharacteristics()
}

function handleSequentialChange() {
  writeLoopCharacteristics()
}

function applySuggestedCollectionVariable() {
  if (!suggestedCollectionVariable.value) return
  collectionVariable.value = suggestedCollectionVariable.value
  writeLoopCharacteristics()
}

function handleCollectionVariableChange() {
  collectionVariable.value = collectionVariable.value.trim()
  if (collectionVarInvalid.value) return
  writeLoopCharacteristics()
}

function handleElementVariableChange() {
  elementVariable.value = elementVariable.value.trim() || 'currentItem'
  writeLoopCharacteristics()
}

function handleCompletionConditionChange() {
  writeLoopCharacteristics()
}

function loadProperties() {
  if (!props.element) return
  const basic = getBasicProperties(props.element)
  subProcessName.value = basic.name

  const lc = getLoopCharacteristics()
  multiInstance.value = !!lc
  sequential.value = !!lc?.isSequential

  const { collection, elementVariable: ev, completionCondition: cc } = readLoopChars()
  collectionVariable.value = collection
  elementVariable.value = ev || 'currentItem'
  completionCondition.value = cc
}

function bumpModelTick() {
  modelTick.value++
}

watch(() => props.element, loadProperties, { immediate: true })

onMounted(() => {
  props.modeler.on('commandStack.changed', bumpModelTick)
  loadProperties()
})

onUnmounted(() => {
  props.modeler.off('commandStack.changed', bumpModelTick)
})
</script>

<style lang="scss" scoped>
.subprocess-properties {
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

  .form-error {
    font-size: 11px;
    color: #f56c6c;
    margin-top: 4px;
    line-height: 1.4;
  }
}
</style>
