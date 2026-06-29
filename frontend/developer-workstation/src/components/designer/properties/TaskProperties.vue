<template>
  <div class="task-properties">
    <!-- Send task email: always visible (not inside collapse — avoids To missing from DOM / scroll) -->
    <div
      v-if="isSendEmailTask"
      class="send-task-email-section"
    >
      <div class="send-task-email-section__title">
        {{ t('properties.emailConfig') }}
      </div>

      <div
        id="send-task-email-to"
        class="email-field-block email-to-field-wrap"
      >
        <label class="email-field-label">
          {{ t('properties.emailTo') }}
          <span class="email-required-mark">*</span>
        </label>
        <el-input
          v-model="emailTo"
          :placeholder="t('properties.emailToPlaceholder')"
          @input="onEmailConfigChange('emailTo', emailTo)"
        />
        <div class="form-tip">{{ t('properties.emailToHint') }}</div>
      </div>

      <div
        id="send-task-email-connection"
        class="email-field-block"
      >
        <label class="email-field-label">
          {{ t('properties.emailConnection') }}
          <span class="email-required-mark">*</span>
        </label>
        <el-select
          v-model="connectionId"
          :placeholder="t('properties.emailConnectionPlaceholder')"
          style="width: 100%"
          clearable
          @change="onEmailConfigChange('connectionId', connectionId)"
        >
          <el-option
            v-for="conn in emailConnections"
            :key="conn.connectionUid"
            :label="conn.name"
            :value="conn.connectionUid"
          />
        </el-select>
        <div class="form-tip">{{ t('properties.emailConnectionHint') }}</div>
      </div>

      <div
        id="send-task-email-from"
        class="email-field-block"
      >
        <label class="email-field-label">
          {{ t('properties.emailFrom') }}
          <span class="email-required-mark">*</span>
        </label>
        <el-input
          v-model="emailFrom"
          :placeholder="t('properties.emailFromPlaceholder')"
          @input="onEmailConfigChange('emailFrom', emailFrom)"
        />
        <div class="form-tip">{{ t('properties.emailFromHint') }}</div>
      </div>

      <div class="email-field-block">
        <label class="email-field-label">
          {{ t('properties.emailTemplate') }}
        </label>
        <el-select
          v-model="emailTemplateId"
          :placeholder="t('properties.emailTemplatePlaceholder')"
          style="width: 100%"
          clearable
          @change="applyEmailTemplate"
        >
          <el-option
            v-for="tpl in emailTemplates"
            :key="tpl.id"
            :label="tpl.name"
            :value="String(tpl.id)"
          />
        </el-select>
        <div class="form-tip">{{ t('properties.emailTemplateHint') }}</div>
      </div>

      <div class="email-field-block">
        <label class="email-field-label">
          {{ t('properties.emailSubject') }}
          <span class="email-required-mark">*</span>
        </label>
        <el-input
          v-model="emailSubject"
          :placeholder="t('properties.emailSubjectPlaceholder')"
          @input="onEmailConfigChange('emailSubject', emailSubject)"
        />
      </div>

      <div class="email-field-block">
        <label class="email-field-label">
          {{ t('properties.emailBody') }}
          <span class="email-required-mark">*</span>
        </label>
        <EmailRichBodyEditor
          v-model="emailBody"
          :function-unit-id="props.functionUnitId"
          @update:modelValue="onEmailConfigChange('emailBody', emailBody)"
        />
      </div>

      <div class="email-advanced-toggle">
        <el-button
          link
          type="primary"
          @click="emailAdvancedOpen = !emailAdvancedOpen"
        >
          {{ emailAdvancedOpen ? t('properties.emailHideAdvanced') : t('properties.emailShowAdvanced') }}
          <el-icon class="email-advanced-chevron" :class="{ open: emailAdvancedOpen }">
            <ArrowDown />
          </el-icon>
        </el-button>
      </div>

      <div
        v-show="emailAdvancedOpen"
        class="email-advanced-panel"
      >
        <div class="email-field-block">
          <label class="email-field-label">{{ t('properties.emailCc') }}</label>
          <el-input
            v-model="emailCc"
            :placeholder="t('properties.emailCcPlaceholder')"
            @input="onEmailConfigChange('emailCc', emailCc)"
          />
        </div>

        <div class="email-field-block">
          <label class="email-field-label">{{ t('properties.emailBcc') }}</label>
          <el-input
            v-model="emailBcc"
            :placeholder="t('properties.emailBccPlaceholder')"
            @input="onEmailConfigChange('emailBcc', emailBcc)"
          />
        </div>

        <div class="email-attachments-block">
          <div class="email-attachments-label">{{ t('properties.emailAttachments') }}</div>
          <div
            v-for="(att, index) in emailAttachments"
            :key="index"
            class="email-attachment-item"
          >
            <div class="email-field-block">
              <label class="email-field-label">{{ t('properties.emailAttachmentName') }}</label>
              <el-input
                v-model="att.name"
                :placeholder="t('properties.emailAttachmentNamePlaceholder')"
                @input="onAttachmentChange"
              />
            </div>
            <div class="email-field-block">
              <label class="email-field-label">{{ t('properties.emailAttachmentContent') }}</label>
              <el-input
                v-model="att.content"
                type="textarea"
                :rows="2"
                :placeholder="t('properties.emailAttachmentContentPlaceholder')"
                @input="onAttachmentChange"
              />
            </div>
            <el-button
              link
              type="danger"
              @click="removeAttachment(index)"
            >
              {{ t('common.delete') }}
            </el-button>
          </div>
          <el-button
            size="small"
            @click="addAttachment"
          >
            {{ t('properties.emailAddAttachment') }}
          </el-button>
        </div>

        <div class="email-field-block">
          <label class="email-field-label">{{ t('properties.emailSensitivity') }}</label>
          <el-select
            v-model="emailSensitivity"
            style="width: 100%"
            @change="onEmailConfigChange('emailSensitivity', emailSensitivity)"
          >
            <el-option
              v-for="opt in emailSensitivityOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </div>

        <div class="email-field-block">
          <label class="email-field-label">{{ t('properties.emailReplyTo') }}</label>
          <el-input
            v-model="emailReplyTo"
            :placeholder="t('properties.emailReplyToPlaceholder')"
            @input="onEmailConfigChange('emailReplyTo', emailReplyTo)"
          />
        </div>

        <div class="email-field-block">
          <label class="email-field-label">{{ t('properties.emailImportance') }}</label>
          <el-select
            v-model="emailImportance"
            style="width: 100%"
            @change="onEmailConfigChange('emailImportance', emailImportance)"
          >
            <el-option
              v-for="opt in emailImportanceOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </div>
      </div>
    </div>

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
import { ref, reactive, watch, onMounted, computed, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import { getExtensionProperties } from '@/utils/bpmnExtensions'
import { emailTemplateApi } from '@/api/emailTemplate'
import { useTaskPropertiesState } from '@/composables/taskProperties/useTaskPropertiesState'
import { useTaskPropertiesForms } from '@/composables/taskProperties/useTaskPropertiesForms'
import { useSendTaskEmailAttachments } from '@/composables/taskProperties/useSendTaskEmailAttachments'

const EmailRichBodyEditor = defineAsyncComponent(
  () => import('@/components/designer/email/EmailRichBodyEditor.vue')
)

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
  connectionId,
  emailTemplateId,
  emailFrom,
  emailTo,
  emailCc,
  emailBcc,
  emailReplyTo,
  emailImportance,
  emailSensitivity,
  emailSubject,
  emailBody,
  ruleEngine,
  decisionRef,
  ruleResultVariable,
  basicProps,
  taskTypeLabel,
  isSendEmailTask,
  loadProperties,
  updateBasicProp,
  updateExtProp,
  onEmailConfigChange
} = useTaskPropertiesState(propsAccessor, t)

const emailAdvancedOpen = ref(false)

const emailImportanceOptions = computed(() => [
  { value: 'low', label: t('properties.emailImportanceLow') },
  { value: 'normal', label: t('properties.emailImportanceNormal') },
  { value: 'high', label: t('properties.emailImportanceHigh') }
])

const emailSensitivityOptions = computed(() => [
  { value: 'normal', label: t('properties.emailSensitivityNormal') },
  { value: 'personal', label: t('properties.emailSensitivityPersonal') },
  { value: 'private', label: t('properties.emailSensitivityPrivate') },
  { value: 'confidential', label: t('properties.emailSensitivityConfidential') }
])

const {
  emailAttachments,
  loadFromExtension: loadEmailAttachments,
  addAttachment,
  removeAttachment,
  onAttachmentChange
} = useSendTaskEmailAttachments(updateExtProp)

function loadEmailProperties() {
  loadProperties()
  if (props.element) {
    loadEmailAttachments(getExtensionProperties(props.element).emailAttachments)
  }
}

// 表单绑定逻辑（依赖 formId/updateExtProp，通过 wrapper 闭包破环）
const {
  forms,
  emailConnections,
  emailTemplates,
  handleFormChange,
  loadForms,
  loadEmailConnections,
  loadEmailTemplates
} = useTaskPropertiesForms(propsAccessor, {
  formId,
  updateExtProp
})

async function applyEmailTemplate(templateId: string) {
  updateExtProp('emailTemplateId', templateId || '')
  if (!templateId) return
  try {
    const res = await emailTemplateApi.get(props.functionUnitId, Number(templateId))
    const tpl = res.data
    emailSubject.value = tpl.subject || ''
    emailBody.value = tpl.bodyHtml || ''
    onEmailConfigChange('emailSubject', emailSubject.value)
    onEmailConfigChange('emailBody', emailBody.value)
  } catch {
    ElMessage.error(t('properties.emailTemplateLoadFailed'))
  }
}

watch(() => props.element, loadEmailProperties, { immediate: true })

onMounted(() => {
  loadEmailProperties()
  loadForms()
  loadEmailConnections()
  loadEmailTemplates()
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
  
  .send-task-email-section {
    margin-bottom: 12px;
    padding: 8px 4px 4px;
    border: 1px solid #e4e7ed;
    border-radius: 6px;
    background: #fff;

    &__title {
      font-size: 13px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 8px;
      padding: 0 8px;
    }
  }

  .email-field-block {
    margin-bottom: 12px;
    padding: 0 4px;

    .email-field-label {
      display: block;
      font-size: 12px;
      font-weight: 600;
      color: #606266;
      padding-bottom: 4px;
      line-height: 1.4;
    }
  }

  .email-to-field-wrap {
    .email-field-label {
      font-weight: 700;
      color: #303133;
    }
  }

  .email-required-mark {
    color: #f56c6c;
    margin-left: 2px;
  }

  .form-tip {
    font-size: 11px;
    color: #909399;
    margin-top: 4px;
    line-height: 1.4;
  }

  .email-advanced-toggle {
    margin: 4px 0 8px;
  }

  .email-advanced-chevron {
    margin-left: 4px;
    transition: transform 0.2s ease;
    &.open {
      transform: rotate(180deg);
    }
  }

  .email-advanced-panel {
    padding-top: 4px;
    border-top: 1px dashed #e4e7ed;
  }

  .email-attachments-block {
    margin-bottom: 12px;
  }

  .email-attachments-label {
    font-size: 12px;
    font-weight: 600;
    color: #606266;
    margin-bottom: 8px;
  }

  .email-attachment-item {
    padding: 8px;
    margin-bottom: 8px;
    background: #fafafa;
    border-radius: 4px;
  }
}
</style>
