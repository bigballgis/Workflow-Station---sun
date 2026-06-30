<template>
  <div class="task-properties">
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
          <el-form-item :label="t('common.type')">
            <el-input
              :model-value="taskTypeLabel"
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
        </el-form>
      </el-collapse-item>

      <!-- User task config -->
      <template v-if="taskType === 'bpmn:UserTask'">
        <el-collapse-item
          :title="t('properties.assigneeConfig')"
          name="assignee"
        >
          <el-form
            label-position="top"
            size="small"
          >
            <el-form-item :label="t('properties.assigneeType')">
              <el-select
                v-model="assigneeType"
                @change="updateExtProp('assigneeType', assigneeType)"
              >
                <el-option
                  :label="t('properties.user')"
                  value="user"
                />
                <el-option
                  :label="t('properties.role')"
                  value="role"
                />
                <el-option
                  :label="t('properties.expression')"
                  value="expression"
                />
              </el-select>
            </el-form-item>
            <el-form-item
              v-if="assigneeType === 'user'"
              :label="t('properties.assignee')"
            >
              <el-input
                v-model="assigneeValue"
                :placeholder="t('properties.userIdPlaceholder')"
                @change="updateExtProp('assigneeValue', assigneeValue)"
              />
            </el-form-item>
            <el-form-item
              v-if="assigneeType === 'role'"
              :label="t('properties.role')"
            >
              <el-input
                v-model="assigneeValue"
                :placeholder="t('properties.roleIdPlaceholder')"
                @change="updateExtProp('assigneeValue', assigneeValue)"
              />
            </el-form-item>
            <el-form-item
              v-if="assigneeType === 'expression'"
              :label="t('properties.expression')"
            >
              <el-input
                v-model="assigneeValue"
                placeholder="${initiator}"
                @change="updateExtProp('assigneeValue', assigneeValue)"
              />
              <div class="form-tip">
                {{ t('properties.expressionTip') }}
              </div>
            </el-form-item>
            <el-form-item :label="t('properties.candidateUsers')">
              <el-input
                v-model="candidateUsers"
                :placeholder="t('properties.candidateUsersPlaceholder')"
                @change="updateExtProp('candidateUsers', candidateUsers)"
              />
            </el-form-item>
            <el-form-item :label="t('properties.candidateRoles')">
              <el-input
                v-model="candidateGroups"
                :placeholder="t('properties.candidateRolesPlaceholder')"
                @change="updateExtProp('candidateGroups', candidateGroups)"
              />
            </el-form-item>
          </el-form>
        </el-collapse-item>

        <el-collapse-item
          :title="t('properties.formBinding')"
          name="form"
        >
          <el-form
            label-position="top"
            size="small"
          >
            <el-form-item :label="t('properties.bindForm')">
              <el-select
                v-model="formId"
                :placeholder="t('properties.selectForm')"
                clearable
                style="width: 100%"
                @change="handleFormChange"
              >
                <el-option
                  v-for="form in forms"
                  :key="form.id"
                  :label="form.formName"
                  :value="form.id"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </el-collapse-item>

        <el-collapse-item
          :title="t('properties.timeout')"
          name="timeout"
        >
          <el-form
            label-position="top"
            size="small"
          >
            <el-form-item :label="t('properties.enableTimeout')">
              <el-switch
                v-model="timeoutEnabled"
                @change="updateExtProp('timeoutEnabled', timeoutEnabled)"
              />
            </el-form-item>
            <template v-if="timeoutEnabled">
              <el-form-item :label="t('properties.timeoutDuration')">
                <el-input
                  v-model="timeoutDuration"
                  :placeholder="t('properties.timeoutDurationPlaceholder')"
                  @change="updateExtProp('timeoutDuration', timeoutDuration)"
                />
                <div class="form-tip">
                  {{ t('properties.timeoutDurationHint') }}
                </div>
              </el-form-item>
              <el-form-item :label="t('properties.timeoutAction')">
                <el-select
                  v-model="timeoutAction"
                  @change="updateExtProp('timeoutAction', timeoutAction)"
                >
                  <el-option
                    :label="t('properties.sendReminder')"
                    value="remind"
                  />
                  <el-option
                    :label="t('properties.autoApprove')"
                    value="approve"
                  />
                  <el-option
                    :label="t('properties.autoReject')"
                    value="reject"
                  />
                </el-select>
              </el-form-item>
            </template>
          </el-form>
        </el-collapse-item>

        <el-collapse-item
          :title="t('properties.multiInstanceConfig')"
          name="multiInstance"
        >
          <el-form
            label-position="top"
            size="small"
          >
            <el-form-item :label="t('properties.enableMultiInstance')">
              <el-switch
                v-model="multiInstance"
                @change="updateExtProp('multiInstance', multiInstance)"
              />
            </el-form-item>
            <template v-if="multiInstance">
              <el-form-item :label="t('properties.executionMode')">
                <el-radio-group
                  v-model="sequential"
                  @change="updateExtProp('sequential', sequential)"
                >
                  <el-radio :value="false">
                    {{ t('properties.parallelMode') }}
                  </el-radio>
                  <el-radio :value="true">
                    {{ t('properties.sequentialMode') }}
                  </el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item :label="t('properties.collectionVariable')">
                <el-input
                  v-model="collection"
                  :placeholder="t('properties.collectionVariablePlaceholder')"
                  @change="updateExtProp('collection', collection)"
                />
                <div class="form-tip">
                  {{ t('properties.collectionVariableTip') }}
                </div>
              </el-form-item>
              <el-form-item :label="t('properties.completionCondition')">
                <el-input
                  v-model="completionCondition"
                  placeholder="${nrOfCompletedInstances/nrOfInstances >= 0.5}"
                  @change="updateExtProp('completionCondition', completionCondition)"
                />
              </el-form-item>
            </template>
          </el-form>
        </el-collapse-item>
      </template>

      <!-- Service task config -->
      <template v-if="taskType === 'bpmn:ServiceTask'">
        <el-collapse-item
          :title="t('properties.serviceConfig')"
          name="service"
        >
          <el-form
            label-position="top"
            size="small"
          >
            <el-form-item :label="t('properties.implementationType')">
              <el-select
                v-model="serviceType"
                @change="updateExtProp('serviceType', serviceType)"
              >
                <el-option
                  :label="t('properties.httpCall')"
                  value="http"
                />
                <el-option
                  :label="t('properties.javaClass')"
                  value="class"
                />
                <el-option
                  :label="t('properties.expression')"
                  value="expression"
                />
                <el-option
                  :label="t('properties.delegateExpression')"
                  value="delegateExpression"
                />
              </el-select>
            </el-form-item>
            <el-form-item
              v-if="serviceType === 'http'"
              :label="t('properties.requestUrl')"
            >
              <el-input
                v-model="httpUrl"
                placeholder="https://api.example.com/endpoint"
                @change="updateExtProp('httpUrl', httpUrl)"
              />
            </el-form-item>
            <el-form-item
              v-if="serviceType === 'http'"
              :label="t('properties.requestMethod')"
            >
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
            <el-form-item
              v-if="serviceType === 'class'"
              :label="t('properties.javaClassName')"
            >
              <el-input
                v-model="javaClass"
                placeholder="com.example.MyDelegate"
                @change="updateExtProp('javaClass', javaClass)"
              />
            </el-form-item>
            <el-form-item
              v-if="serviceType === 'expression'"
              :label="t('properties.expression')"
            >
              <el-input
                v-model="serviceExpression"
                placeholder="${myBean.execute()}"
                @change="updateExtProp('serviceExpression', serviceExpression)"
              />
            </el-form-item>
            <el-form-item
              v-if="serviceType === 'delegateExpression'"
              :label="t('properties.delegateExpression')"
            >
              <el-input
                v-model="delegateExpression"
                placeholder="${myDelegate}"
                @change="updateExtProp('delegateExpression', delegateExpression)"
              />
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </template>

      <!-- Script task config -->
      <template v-if="taskType === 'bpmn:ScriptTask'">
        <el-collapse-item
          :title="t('properties.script')"
          name="script"
        >
          <el-form
            label-position="top"
            size="small"
          >
            <el-form-item :label="t('properties.scriptLanguage')">
              <el-select
                v-model="scriptFormat"
                @change="updateExtProp('scriptFormat', scriptFormat)"
              >
                <el-option
                  label="JavaScript"
                  value="javascript"
                />
                <el-option
                  label="Groovy"
                  value="groovy"
                />
                <el-option
                  label="Python"
                  value="python"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('properties.scriptContent')">
              <el-input
                v-model="scriptBody"
                type="textarea"
                :rows="6"
                :placeholder="t('properties.scriptBodyPlaceholder')"
                @change="updateExtProp('scriptBody', scriptBody)"
              />
            </el-form-item>
            <el-form-item :label="t('properties.resultVariable')">
              <el-input
                v-model="resultVariable"
                :placeholder="t('properties.resultVariablePlaceholder')"
                @change="updateExtProp('resultVariable', resultVariable)"
              />
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </template>

      <!-- Receive task config -->
      <template v-if="taskType === 'bpmn:ReceiveTask'">
        <el-collapse-item
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
                :placeholder="t('properties.messageNamePlaceholder')"
                @change="updateExtProp('messageName', messageName)"
              />
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </template>

      <!-- Business rule task config -->
      <template v-if="taskType === 'bpmn:BusinessRuleTask'">
        <el-collapse-item
          :title="t('properties.rule')"
          name="rule"
        >
          <el-form
            label-position="top"
            size="small"
          >
            <el-form-item :label="t('properties.ruleEngine')">
              <el-select
                v-model="ruleEngine"
                @change="updateExtProp('ruleEngine', ruleEngine)"
              >
                <el-option
                  label="DMN"
                  value="dmn"
                />
                <el-option
                  label="Drools"
                  value="drools"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('properties.decisionRef')">
              <el-input
                v-model="decisionRef"
                :placeholder="t('properties.decisionRefPlaceholder')"
                @change="updateExtProp('decisionRef', decisionRef)"
              />
            </el-form-item>
            <el-form-item :label="t('properties.ruleResultVariable')">
              <el-input
                v-model="ruleResultVariable"
                :placeholder="t('properties.ruleResultVariablePlaceholder')"
                @change="updateExtProp('ruleResultVariable', ruleResultVariable)"
              />
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </template>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
/**
 * 通用 Task 节点属性面板。
 *
 * 本 SFC 为精简编排器：响应式状态、加载逻辑与表单绑定逻辑已抽到
 * `@/composables/taskProperties/*`。此处仅做组装、props 透传与生命周期绑定，
 * 模板/样式与拆分前逐字节一致，emit/props/i18n key/行为均零变化。
 */
import { ref, reactive, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import { useTaskPropertiesState } from '@/composables/taskProperties/useTaskPropertiesState'
import { useTaskPropertiesForms } from '@/composables/taskProperties/useTaskPropertiesForms'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
}>()

const activeGroups = ref(['basic', 'assignee', 'service', 'script'])

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

// 共享状态（全部顶层 ref/computed + loadProperties + updateBasicProp/updateExtProp）
const {
  taskType,
  taskName,
  assigneeType,
  assigneeValue,
  candidateUsers,
  candidateGroups,
  formId,
  timeoutEnabled,
  timeoutDuration,
  timeoutAction,
  multiInstance,
  sequential,
  collection,
  completionCondition,
  serviceType,
  httpUrl,
  httpMethod,
  javaClass,
  serviceExpression,
  delegateExpression,
  scriptFormat,
  scriptBody,
  resultVariable,
  messageName,
  messagePayload,
  ruleEngine,
  decisionRef,
  ruleResultVariable,
  basicProps,
  taskTypeLabel,
  loadProperties,
  updateBasicProp,
  updateExtProp
} = useTaskPropertiesState(propsAccessor, t)

const {
  forms,
  handleFormChange,
  loadForms
} = useTaskPropertiesForms(propsAccessor, {
  formId,
  updateExtProp
})

watch(() => props.element, loadProperties, { immediate: true })

onMounted(() => {
  loadProperties()
  loadForms()
})
</script>

<style lang="scss" scoped>
.task-properties {
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
}
</style>
