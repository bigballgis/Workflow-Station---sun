<template>
  <div class="event-properties">
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
          <el-form-item :label="t('properties.eventId')">
            <el-input
              :model-value="basicProps.id"
              disabled
            />
          </el-form-item>
          <el-form-item :label="t('common.type')">
            <el-input
              :model-value="eventTypeLabel"
              disabled
            />
          </el-form-item>
          <el-form-item :label="t('common.type')">
            <el-input
              :model-value="eventDefinitionLabel"
              disabled
            />
          </el-form-item>
          <el-form-item :label="t('properties.eventName')">
            <el-input
              v-model="eventName"
              :placeholder="t('properties.eventName')"
              @change="updateBasicProp('name', eventName)"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Start event config -->
      <el-collapse-item
        v-if="isStart"
        :title="t('properties.startConfig')"
        name="start"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.startForm')">
            <el-select
              v-model="startFormId"
              :placeholder="t('properties.selectStartForm')"
              clearable
              @change="handleStartFormChange"
            >
              <el-option
                v-for="form in forms"
                :key="form.id"
                :label="form.formName"
                :value="form.id"
              />
            </el-select>
            <div class="form-tip">
              {{ t('properties.startFormTip') }}
            </div>
          </el-form-item>
          <el-form-item :label="t('properties.initiatorVariable')">
            <el-input
              v-model="initiator"
              placeholder="initiator"
              @change="updateExtProp('initiator', initiator)"
            />
            <div class="form-tip">
              {{ t('properties.initiatorVariableTip') }}
            </div>
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- End event config -->
      <el-collapse-item
        v-if="isEnd"
        :title="t('properties.endConfig')"
        name="end"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.endAction')">
            <el-select
              v-model="endAction"
              @change="updateExtProp('endAction', endAction)"
            >
              <el-option
                :label="t('properties.noAction')"
                value="none"
              />
              <el-option
                :label="t('properties.notify')"
                value="notify"
              />
              <el-option
                :label="t('properties.callService')"
                value="service"
              />
            </el-select>
          </el-form-item>
          <template v-if="endAction === 'notify'">
            <el-form-item :label="t('properties.notifyType')">
              <el-select
                v-model="notifyType"
                @change="updateNotifyConfig"
              >
                <el-option
                  :label="t('properties.email')"
                  value="email"
                />
                <el-option
                  :label="t('properties.sms')"
                  value="sms"
                />
                <el-option
                  :label="t('properties.inApp')"
                  value="message"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('properties.notifyRecipient')">
              <el-input
                v-model="notifyRecipient"
                placeholder="${initiator}"
                @change="updateNotifyConfig"
              />
            </el-form-item>
            <el-form-item :label="t('properties.notifyContent')">
              <el-input
                v-model="notifyContent"
                type="textarea"
                :rows="3"
                :placeholder="t('properties.notifyContentPlaceholder')"
                @change="updateNotifyConfig"
              />
            </el-form-item>
          </template>
          <template v-if="endAction === 'service'">
            <el-form-item :label="t('properties.serviceUrl')">
              <el-input
                v-model="serviceUrl"
                placeholder="https://api.example.com/callback"
                @change="updateServiceConfig"
              />
            </el-form-item>
            <el-form-item :label="t('properties.requestMethod')">
              <el-select
                v-model="serviceMethod"
                @change="updateServiceConfig"
              >
                <el-option
                  label="POST"
                  value="POST"
                />
                <el-option
                  label="PUT"
                  value="PUT"
                />
              </el-select>
            </el-form-item>
          </template>
        </el-form>
      </el-collapse-item>
      
      <!-- Timer event config -->
      <el-collapse-item
        v-if="eventDefinitionType === 'timer'"
        :title="t('properties.timerConfig')"
        name="timer"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.timerType')">
            <el-select
              v-model="timerType"
              @change="updateTimerDefinition"
            >
              <el-option
                :label="t('properties.timerTypeDate')"
                value="date"
              />
              <el-option
                :label="t('properties.timerTypeDuration')"
                value="duration"
              />
              <el-option
                :label="t('properties.timerTypeCycle')"
                value="cycle"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('properties.timerExpression')">
            <el-input
              v-model="timerValue"
              :placeholder="timerPlaceholder"
              @change="updateTimerDefinition"
            />
            <div class="form-tip">
              {{ timerTip }}
            </div>
          </el-form-item>
          <div class="timer-examples">
            <div class="examples-title">
              {{ t('properties.expressionExamples') }}
            </div>
            <div
              v-if="timerType === 'date'"
              class="example-item"
              @click="setTimerValue('2026-12-31T23:59:59')"
            >
              <code>2026-12-31T23:59:59</code>
              <span>{{ t('properties.specificDateTime') }}</span>
            </div>
            <div
              v-if="timerType === 'duration'"
              class="example-item"
              @click="setTimerValue('PT1H')"
            >
              <code>PT1H</code>
              <span>{{ t('properties.afterOneHour') }}</span>
            </div>
            <div
              v-if="timerType === 'duration'"
              class="example-item"
              @click="setTimerValue('P1D')"
            >
              <code>P1D</code>
              <span>{{ t('properties.afterOneDay') }}</span>
            </div>
            <div
              v-if="timerType === 'cycle'"
              class="example-item"
              @click="setTimerValue('R3/PT10M')"
            >
              <code>R3/PT10M</code>
              <span>{{ t('properties.every10MinTimes3') }}</span>
            </div>
          </div>
        </el-form>
      </el-collapse-item>
      
      <!-- Message event config -->
      <el-collapse-item
        v-if="eventDefinitionType === 'message'"
        :title="t('properties.message')"
        name="message"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.messageName')">
            <el-input
              v-model="messageName"
              placeholder="order.created"
              @change="updateMessageDefinition"
            />
          </el-form-item>
          <el-form-item :label="t('properties.correlationKey')">
            <el-input
              v-model="correlationKey"
              placeholder="${orderId}"
              @change="updateExtProp('correlationKey', correlationKey)"
            />
            <div class="form-tip">
              {{ t('properties.correlationKeyTip') }}
            </div>
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Signal event config -->
      <el-collapse-item
        v-if="eventDefinitionType === 'signal'"
        :title="t('properties.signalConfig')"
        name="signal"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.signalName')">
            <el-input
              v-model="signalName"
              placeholder="approval.completed"
              @change="updateSignalDefinition"
            />
          </el-form-item>
          <el-form-item :label="t('properties.signalScope')">
            <el-select
              v-model="signalScope"
              @change="updateExtProp('signalScope', signalScope)"
            >
              <el-option
                :label="t('properties.signalScopeGlobal')"
                value="global"
              />
              <el-option
                :label="t('properties.signalScopeProcessInstance')"
                value="processInstance"
              />
            </el-select>
            <div class="form-tip">
              {{ t('properties.signalScopeTip') }}
            </div>
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Error event config -->
      <el-collapse-item
        v-if="eventDefinitionType === 'error'"
        :title="t('properties.errorConfig')"
        name="error"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.errorCode')">
            <el-input
              v-model="errorCode"
              placeholder="ERR_001"
              @change="updateErrorDefinition"
            />
          </el-form-item>
          <el-form-item :label="t('properties.errorMessage')">
            <el-input
              v-model="errorMessage"
              :placeholder="t('properties.errorMessagePlaceholder')"
              @change="updateExtProp('errorMessage', errorMessage)"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
/**
 * 事件（Start / End / Intermediate / Boundary）节点属性面板。
 *
 * 本 SFC 为精简编排器：响应式状态与各职责逻辑已抽到
 * `@/composables/eventProperties/*`。此处仅做组装、加载编排与生命周期绑定，
 * 模板/样式与拆分前逐字节一致，props/i18n key/行为均零变化。
 */
import { ref, reactive, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import { useEventState } from '@/composables/eventProperties/useEventState'
import { useEventDefinitions } from '@/composables/eventProperties/useEventDefinitions'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
}>()

const activeGroups = ref(['basic', 'start', 'end', 'timer', 'message', 'signal', 'error'])

// 以 reactive 适配器透传 props，使 composable 读取 props.element/modeler 时保持响应性
const propsAccessor = reactive({
  get modeler() {
    return props.modeler
  },
  get element() {
    return props.element
  },
  get functionUnitId() {
    return props.functionUnitId
  }
})

// 共享状态（全部顶层 ref/computed + updateBasicProp/updateExtProp）
const ctx = useEventState(propsAccessor, t)
const {
  eventName,
  eventDefinitionType,
  startFormId,
  initiator,
  forms,
  endAction,
  notifyType,
  notifyRecipient,
  notifyContent,
  serviceUrl,
  serviceMethod,
  timerType,
  timerValue,
  messageName,
  correlationKey,
  signalName,
  signalScope,
  errorCode,
  errorMessage,
  basicProps,
  isStart,
  isEnd,
  eventTypeLabel,
  eventDefinitionLabel,
  timerPlaceholder,
  timerTip,
  updateBasicProp,
  updateExtProp
} = ctx

// 事件定义读写与配置处理逻辑
const {
  loadProperties,
  handleStartFormChange,
  updateNotifyConfig,
  updateServiceConfig,
  setTimerValue,
  updateTimerDefinition,
  updateMessageDefinition,
  updateSignalDefinition,
  updateErrorDefinition,
  loadForms
} = useEventDefinitions(propsAccessor, ctx)

watch(() => props.element, loadProperties, { immediate: true })

onMounted(() => {
  loadProperties()
  loadForms()
})
</script>

<style lang="scss" scoped>
.event-properties {
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
