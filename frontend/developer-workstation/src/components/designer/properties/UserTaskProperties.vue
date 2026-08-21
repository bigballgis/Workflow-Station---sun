<template>
  <div class="user-task-properties">
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

      <!-- Multi-instance sub-task config -->
      <el-collapse-item
        v-if="isFirstMultiInstanceSubTask"
        :title="t('properties.subTaskConfig')"
        name="subTask"
      >
        <UserTaskSubTaskConfigSection />
      </el-collapse-item>
      
      <!-- Assignee config -->
      <el-collapse-item
        v-if="!isFirstMultiInstanceSubTask"
        :title="t('properties.assigneeConfig')"
        name="assignee"
      >
        <UserTaskAssigneeConfigSection />
      </el-collapse-item>
      
      <!-- Form binding -->
      <el-collapse-item
        v-if="!isFirstMultiInstanceSubTask"
        :title="t('properties.form')"
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
              @change="handleFormChange"
            >
              <el-option
                v-for="form in bindableForms"
                :key="form.id"
                :label="form.formName"
                :value="form.id"
              >
                <span>{{ form.formName }}</span>
                <el-tag
                  size="small"
                  type="success"
                  style="margin-left: 8px;"
                >
                  {{ t('form.sceneTask') }}
                </el-tag>
              </el-option>
            </el-select>
            <!--
              This slot IS the To Do design: it writes the TASK-scene BPMN properties
              (formId / formName), and the actions configured below dispatch only on the
              To Do page. My Requests is the sibling field just below.
            -->
            <div class="form-tip">
              {{ t('properties.bindFormTodoOnlyHint') }}
            </div>
          </el-form-item>

          <!--
            The same node can also carry a My Requests design (requestFormId/requestFormName
            ext props). Editable here in addition to Form Design > My Requests > row menu >
            Bound Node — both paths write the same BPMN fields, so either one works.
          -->
          <el-form-item :label="t('properties.bindFormRequest')">
            <el-select
              v-model="requestFormId"
              :placeholder="t('properties.selectForm')"
              clearable
              @change="handleRequestFormChange"
            >
              <el-option
                v-for="form in requestableForms"
                :key="form.id"
                :label="form.formName"
                :value="form.id"
              >
                <span>{{ form.formName }}</span>
                <el-tag
                  size="small"
                  type="info"
                  style="margin-left: 8px;"
                >
                  {{ t('form.sceneRequest') }}
                </el-tag>
              </el-option>
            </el-select>
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Action binding -->
      <el-collapse-item
        :title="t('properties.actions')"
        name="actions"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.availableActions')">
            <el-select
              v-model="actionIds"
              :placeholder="t('properties.selectActions')"
              multiple
              clearable
              @change="handleActionsChange"
            >
              <el-option
                v-for="action in actions"
                :key="action.id"
                :label="action.actionName"
                :value="action.id"
              >
                <span>{{ action.actionName }}</span>
                <el-tag
                  size="small"
                  style="margin-left: 8px;"
                >
                  {{ actionTypeLabel(action.actionType) }}
                </el-tag>
              </el-option>
            </el-select>
            <!-- Actions ride the node's actionIds and only ever render on the To Do page,
                 i.e. against the form bound above. Say so, so the pairing is not guesswork. -->
            <div class="form-tip">
              {{ t('properties.actionsTodoOnlyHint') }}
            </div>
          </el-form-item>
          <div
            v-if="actionIds.length > 0"
            class="selected-actions"
          >
            <div class="form-tip">
              {{ actionIds.length }}
            </div>
          </div>
        </el-form>
      </el-collapse-item>
      
      <!-- Timeout config -->
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
                  :label="t('properties.notify')"
                  value="remind"
                />
                <el-option
                  :label="t('properties.autoComplete')"
                  value="approve"
                />
                <el-option
                  :label="t('properties.autoComplete')"
                  value="reject"
                />
              </el-select>
            </el-form-item>
          </template>
        </el-form>
      </el-collapse-item>
      
      <!-- Multi-instance config -->
      <el-collapse-item
        v-if="!parentIsMultiInstanceSubProcess"
        :title="t('properties.multiInstanceConfig')"
        name="multiInstance"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-alert
            type="info"
            :closable="false"
            show-icon
          >
            <template #title>
              {{ t('properties.userTaskMultiInstanceHint') }}
            </template>
          </el-alert>
          <el-form-item
            :label="t('properties.enableMultiInstance')"
            style="margin-top: 8px;"
          >
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
              <div class="form-tip">
                {{ t('properties.completionConditionTip') }}
              </div>
            </el-form-item>
          </template>
        </el-form>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>


<script setup lang="ts">
/**
 * UserTask 节点属性面板。
 *
 * 本 SFC 为精简编排器：响应式状态与各职责逻辑已抽到
 * `@/composables/userTaskProperties/*`。此处仅做组装、加载编排与生命周期绑定，
 * 模板/样式与拆分前逐字节一致，emit/props/i18n key/行为均零变化。
 */
import { ref, reactive, computed, watch, onMounted, onUnmounted, provide } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import { getExtensionProperties } from '@/utils/bpmnExtensions'
import type { AssigneeTypeEnum } from '@/composables/userTaskProperties/types'
import { useUserTaskState } from '@/composables/userTaskProperties/useUserTaskState'
import { useUserTaskAssignee } from '@/composables/userTaskProperties/useUserTaskAssignee'
import { useUserTaskMultiInstance } from '@/composables/userTaskProperties/useUserTaskMultiInstance'
import { useUserTaskActions } from '@/composables/userTaskProperties/useUserTaskActions'
import UserTaskSubTaskConfigSection from './UserTaskSubTaskConfigSection.vue'
import UserTaskAssigneeConfigSection from './UserTaskAssigneeConfigSection.vue'
import { USER_TASK_PANEL_KEY } from './userTaskPropertiesInject'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
}>()

const activeGroups = ref(['basic', 'subTask', 'assignee', 'form', 'actions'])

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
const ctx = useUserTaskState(propsAccessor, t)
const {
  taskName,
  taskDescription,
  formId,
  forms,
  requestFormId,
  requestFormName,
  actionIds,
  actions,
  timeoutEnabled,
  timeoutDuration,
  timeoutAction,
  multiInstance,
  sequential,
  collection,
  completionCondition,
  basicProps,
  updateBasicProp,
  updateExtProp,
} = ctx

/**
 * Only To Do (TASK-scene) designs may be bound here.
 *
 * <p>This field writes the TASK-scene BPMN properties (`formId` / `formName`) — there is no
 * code path by which it could produce a My Requests binding, which lives under
 * `requestFormId` / `requestFormName` and is set from Form Design > Bind Process Node.
 * Listing REQUEST forms therefore offered a choice that silently mislabels them as the To Do
 * design; the actions configured just below dispatch only on the To Do page, so the pairing
 * shown here has to be unambiguous.
 *
 * <p>`scene` is optional on older rows — absent means TASK (same defaulting as
 * FormListSidebar.sceneOf).
 */
const bindableForms = computed(() =>
  (forms.value ?? []).filter(f => ((f as { scene?: string }).scene ?? 'TASK') !== 'REQUEST'),
)

// Assignee config 逻辑
const assigneeApi = useUserTaskAssignee(ctx)
const {
  needsBuForRole,
  needsRoleId,
  loadRoleIdsFromExt,
  loadRoles,
  loadBusinessUnits,
  loadEligibleRoles,
  sanitizePersistedRoleIds,
  businessUnitCodeToId
} = assigneeApi

// 多实例子任务 / element-variable 逻辑
const multiInstanceApi = useUserTaskMultiInstance(propsAccessor, ctx)
const {
  parentIsMultiInstanceSubProcess,
  isFirstMultiInstanceSubTask,
  loadSubTables,
  loadSubTaskMiProgressFields
} = multiInstanceApi

// 动作 / 表单加载逻辑
const actionsApi = useUserTaskActions(propsAccessor, ctx)
const {
  handleActionsChange,
  handleFormChange,
  requestableForms,
  handleRequestFormChange,
  actionTypeLabel,
  loadForms,
  loadActions
} = actionsApi

provide(USER_TASK_PANEL_KEY, {
  ctx,
  assignee: assigneeApi,
  multiInstance: multiInstanceApi,
  actions: actionsApi,
})

/** 顺序流变化时递增，驱动「单入线」校验刷新 */
const topologyTick = ctx.topologyTick // retained for backward compatibility; anchor UI removed

function loadProperties() {
  if (!props.element) return
  void loadPropertiesAsync()
}

async function loadPropertiesAsync() {
  if (!props.element) return
  
  // Basic properties
  const basic = basicProps.value
  taskName.value = basic.name
  
  // Extension properties
  const ext = getExtensionProperties(props.element)
  taskDescription.value = ext.description || ''
  let rawType = (ext.assigneeType || 'INITIATOR') as string
  if (rawType === 'PROCESS_INITIATOR') {
    rawType = 'INITIATOR'
  }
  ctx.assigneeType.value = (rawType as AssigneeTypeEnum) || 'INITIATOR'
  ctx.lastLoadedAssigneeType.value = ctx.assigneeType.value
  if (!ext.assigneeType) {
    updateExtProp('assigneeType', ctx.assigneeType.value)
    if (ctx.assigneeType.value === 'INITIATOR') {
      updateExtProp('assigneeLabel', t('properties.initiator'))
    }
  }
  // NOTE: role/BU values in BPMN are *codes* (env-stable). They are mapped back to ids
  // for the UI after the catalogs load (see code→id mapping below). Don't bind ids here.
  ctx.assigneeLabel.value = ext.assigneeLabel || ''
  ctx.candidateUsers.value = ext.candidateUsers || ''
  ctx.candidateGroups.value = ext.candidateGroups || ''
  ctx.manualAssignVariable.value = ext.manualAssignVariable || ''
  ctx.manualAssignBuVariable.value = ext.manualAssignBuVariable || ''
  ctx.manualAssignRoleVariable.value = ext.manualAssignRoleVariable || ''
  ctx.assigneeVariableName.value = ext.assigneeVariable || ''
  const rawSubTableId = ext.subTableId
  ctx.elementSubTableId.value =
    typeof rawSubTableId === 'number'
      ? rawSubTableId
      : (rawSubTableId ? Number(rawSubTableId) || '' : '')
  ctx.elementSubTableName.value = ext.subTableName || ''
  ctx.assigneeField.value = ext.assigneeField || ''
  ctx.roleField.value = ext.roleField || ''
  ctx.buField.value = ext.buField || ''
  // 由 assigneeMode（user|role|both）派生两个开关；缺省时按已存字段兼容旧数据。
  const modeRaw = typeof ext.assigneeMode === 'string' ? ext.assigneeMode.toLowerCase() : ''
  if (modeRaw === 'both') {
    ctx.allowUser.value = true
    ctx.allowRole.value = true
  } else if (modeRaw === 'role') {
    ctx.allowUser.value = false
    ctx.allowRole.value = true
  } else if (modeRaw === 'user') {
    ctx.allowUser.value = true
    ctx.allowRole.value = false
  } else {
    // 旧数据无 assigneeMode：有 roleField 视为允许角色，有 assigneeField 视为允许个人。
    ctx.allowRole.value = !!ctx.roleField.value
    ctx.allowUser.value = !!ctx.assigneeField.value || !ctx.roleField.value
  }
  ctx.assigneeMode.value = ctx.allowUser.value && ctx.allowRole.value
    ? 'both' : (ctx.allowRole.value ? 'role' : 'user')
  ctx.rowIdVariable.value = ext.rowIdVariable || ''
  formId.value = ext.formId || null
  // My Requests binding is read-only here: it is owned by Form Design > My Requests > Bound
  // Node, which writes requestFormId / requestFormName. Surfacing it makes the node's two
  // designs visible side by side instead of only the To Do half.
  requestFormId.value = (ext.requestFormId as number | undefined) ?? null
  requestFormName.value = (ext.requestFormName as string | undefined) ?? ''
  actionIds.value = ext.actionIds || []
  timeoutEnabled.value = ext.timeoutEnabled || false
  timeoutDuration.value = ext.timeoutDuration || ''
  timeoutAction.value = ext.timeoutAction || 'remind'
  multiInstance.value = ext.multiInstance || false
  sequential.value = ext.sequential || false
  collection.value = ext.collection || ''
  completionCondition.value = ext.completionCondition || ''

  if (isFirstMultiInstanceSubTask.value) {
    loadSubTaskMiProgressFields()
    // 回写派生的 assigneeMode 到 modeler，确保 modeler 与 DB 一致（防止某些加载/交互时序
    // 让 saveXML 序列化出与 DB 不符的值）。幂等：与 DB 相同则无实质变化。
    updateExtProp('assigneeMode', ctx.assigneeMode.value)
  }

  // Load role/BU catalogs first so persisted *codes* can be mapped back to ids for the UI,
  // and so tags show names rather than raw codes.
  if (needsRoleId.value) {
    await loadRoles()
  }
  if (needsBuForRole.value) {
    await loadBusinessUnits()
    // BPMN stores BU code; map to id for the tree-select, then load eligible roles by id
    const buId = ext.businessUnitId ? businessUnitCodeToId(ext.businessUnitId) : ''
    ctx.businessUnitId.value = buId
    if (buId) {
      await loadEligibleRoles(buId)
    }
  }
  // BPMN stores role codes; map to ids now that catalogs (incl. eligibleRoles) are loaded
  loadRoleIdsFromExt(ext)
  sanitizePersistedRoleIds()
}

watch(() => props.element, loadProperties, { immediate: true })

function bumpTopologyTick() {
  topologyTick.value++
}

onMounted(() => {
  props.modeler.on('commandStack.changed', bumpTopologyTick)
  loadProperties()
  loadForms()
  loadActions()
  loadSubTables()
})

onUnmounted(() => {
  props.modeler.off('commandStack.changed', bumpTopologyTick)
})
</script>

<style lang="scss" scoped>
.user-task-properties {
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
  
  .selected-actions {
    margin-top: -8px;
  }
}
</style>
