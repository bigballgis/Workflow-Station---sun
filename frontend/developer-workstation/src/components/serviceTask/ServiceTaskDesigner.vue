<!--
  Automation tab of the Function Unit editor.

  Mounts the vendored Activepieces builder for the automation flow bound to a BPMN
  service task of type "ap". The builder is embedded via lib-mode + Shadow DOM
  (ServiceTaskBuilderCanvas), NOT an iframe — see decision X-6.

  This tab is also where the flow is *created*: a service task can be declared as an
  automation (serviceType=ap) in Process Design without a flow id yet, and this tab
  turns that empty task into a real flow (POST /api/ap/v1/flows) and binds the new id
  back onto the BPMN — otherwise the flow id in the Service Task panel would have
  nowhere to come from.

  Chain: the admin-center bridge issues the AP session (L7), the builder talks to AP
  through the Kong /api/ap prefix (L2), and the bundle itself is served from DW's own
  origin at /dev/service-task-builder (build-time copy, AG-02.8).
-->
<template>
  <div
    v-loading="loading"
    class="service-task-designer"
  >
    <div
      v-if="tasks.length > 0"
      class="service-task-designer__toolbar"
    >
      <span class="service-task-designer__label">{{ t('functionUnit.serviceTaskSelector') }}</span>
      <el-select
        v-model="selectedTaskId"
        size="default"
        class="service-task-designer__select"
      >
        <el-option
          v-for="task in tasks"
          :key="task.id"
          :label="task.name || task.id"
          :value="task.id"
        >
          <div class="service-task-designer__option">
            <span class="service-task-designer__option-name">{{ task.name || task.id }}</span>
            <el-tag
              :type="task.flowId ? 'success' : 'info'"
              size="small"
              effect="light"
              disable-transitions
            >
              {{ task.flowId ? t('functionUnit.serviceTaskBound') : t('functionUnit.serviceTaskUnbound') }}
            </el-tag>
          </div>
        </el-option>
      </el-select>
      <!-- Three states, not two: a dangling binding still has a flowId, so keying only on that
           painted a green "Flow ready" right above the "flow no longer exists" recovery prompt. -->
      <el-tag
        v-if="selectedTask"
        :type="danglingFlowId ? 'warning' : selectedTask.flowId ? 'success' : 'info'"
        size="small"
        effect="plain"
        disable-transitions
        class="service-task-designer__status"
      >
        {{
          danglingFlowId
            ? t('functionUnit.serviceTaskFlowMissingTag')
            : selectedTask.flowId
              ? t('functionUnit.serviceTaskBound')
              : t('functionUnit.serviceTaskUnbound')
        }}
      </el-tag>
    </div>

    <!-- One flow per service task; the list above switches between them. New automations
         are born from a Service Task in Process Design, not created free-floating here. -->
    <div
      v-if="tasks.length > 0"
      class="service-task-designer__hint"
    >
      <el-icon><InfoFilled /></el-icon>
      <span>{{ t('functionUnit.serviceTaskAddHint') }}</span>
    </div>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button
          size="small"
          @click="load"
        >
          {{ t('common.retry') }}
        </el-button>
      </template>
    </el-alert>

    <!-- No automation service task in the BPMN at all -->
    <el-empty
      v-else-if="!loading && tasks.length === 0"
      :description="t('functionUnit.serviceTaskEmpty')"
    />

    <!-- A service task is declared as automation but has no flow yet: create it here -->
    <el-empty
      v-else-if="!loading && selectedTask && !selectedTask.flowId"
      :description="t('functionUnit.serviceTaskNoFlow')"
    >
      <el-button
        type="primary"
        :loading="creating"
        @click="createFlow"
      >
        {{ t('functionUnit.serviceTaskCreateFlow') }}
      </el-button>
    </el-empty>

    <!-- Bound to a flow that no longer exists. Without this the builder mounts and shows
         AP's own "Flow not found", leaving no way to rebind from here. -->
    <el-empty
      v-else-if="!loading && danglingFlowId"
      :description="t('functionUnit.serviceTaskFlowMissing', { id: danglingFlowId })"
    >
      <el-button
        type="primary"
        :loading="creating"
        @click="createFlow"
      >
        {{ t('functionUnit.serviceTaskRecreateFlow') }}
      </el-button>
    </el-empty>

    <ServiceTaskBuilderCanvas
      v-else-if="session && selectedFlowId"
      :key="selectedFlowId"
      class="service-task-designer__canvas"
      :flow-id="selectedFlowId"
      :project-id="session.projectId"
      :token="session.token"
      :api-url="apiUrl"
      :socket-base-url="socketBaseUrl"
      socket-path="/api/ap/socket.io"
      :bundle-url="bundleUrl"
      :css-url="cssUrl"
      @unauthorized="onUnauthorized"
    />
  </div>
</template>

<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { functionUnitApi } from '@/api/functionUnit'
import {
  createServiceTaskFlow,
  fetchServiceTaskSession,
  serviceTaskFlowExists,
  type ServiceTaskSession,
} from '@/api/serviceTask'
import ServiceTaskBuilderCanvas from '@/components/serviceTask/ServiceTaskBuilderCanvas.vue'

interface ApServiceTask {
  id: string
  name: string
  flowId: string
}

const CUSTOM_NS = 'http://workflow.platform/schema/custom'

const props = defineProps<{ functionUnitId: number }>()

const { t } = useI18n()

const loading = ref(false)
const creating = ref(false)
const errorMessage = ref('')
const session = ref<ServiceTaskSession | null>(null)
const tasks = ref<ApServiceTask[]>([])
const selectedTaskId = ref('')

// The builder bundle is served from DW's own origin, so REST/socket.io go to the
// same origin too and Kong routes them on via the /api/ap prefix.
const origin = window.location.origin
const apiUrl = `${origin}/api/ap`
const socketBaseUrl = origin
const bundleUrl = `${import.meta.env.BASE_URL}service-task-builder/ap-builder.mjs`
const cssUrl = `${import.meta.env.BASE_URL}service-task-builder/web.css`

const selectedTask = computed(() =>
  tasks.value.find((task) => task.id === selectedTaskId.value),
)
/** Set when the bound flow is gone; suppresses the canvas in favour of the rebind prompt. */
const danglingFlowId = ref('')
const selectedFlowId = computed(() =>
  danglingFlowId.value ? '' : selectedTask.value?.flowId || '',
)

/** Parse one XML tag's attributes (order-independent). */
function parseTagAttributes(tag: string): Record<string, string> {
  const attrs: Record<string, string> = {}
  const attrPattern = /([\w:.-]+)="([^"]*)"/g
  let match: RegExpExecArray | null
  while ((match = attrPattern.exec(tag)) !== null) {
    attrs[match[1]] = match[2]
  }
  return attrs
}

/**
 * Pull the automation service tasks out of the BPMN — those marked
 * serviceType=ap — INCLUDING ones without a flow id yet (they are the ones this
 * tab lets you create a flow for). The flow, when present, is in `ap:flowId`.
 */
function parseApServiceTasks(bpmnXml: string): ApServiceTask[] {
  const result: ApServiceTask[] = []
  const taskPattern = /<(?:\w+:)?serviceTask\b([^>]*)>([\s\S]*?)<\/(?:\w+:)?serviceTask>/g
  let match: RegExpExecArray | null
  while ((match = taskPattern.exec(bpmnXml)) !== null) {
    const [, attrs, body] = match
    const props: Record<string, string> = {}
    const propPattern = /<[\w:.-]*[Pp]roperty\b[^>]*\/?>/g
    let propMatch: RegExpExecArray | null
    while ((propMatch = propPattern.exec(body)) !== null) {
      const propAttrs = parseTagAttributes(propMatch[0])
      if (propAttrs.name !== undefined) {
        props[propAttrs.name] = propAttrs.value ?? ''
      }
    }
    // 存量 BPMN 兼容：属性面板曾只在 change 时落盘 serviceType，默认 'ap' 从未写入，
    // 导致任务只有 ap:* 配置而无类型标记。无显式类型但带 ap:* 属性的也按自动化任务收。
    const isApTask =
      props['serviceType'] === 'ap' ||
      (props['serviceType'] === undefined &&
        Object.keys(props).some((key) => key.startsWith('ap:')))
    if (!isApTask) {
      continue
    }
    const taskAttrs = parseTagAttributes(attrs)
    const id = taskAttrs.id || ''
    if (!id) {
      continue
    }
    result.push({ id, name: taskAttrs.name || '', flowId: props['ap:flowId'] || '' })
  }
  return result
}

/**
 * Write `ap:flowId` onto the given service task in the BPMN and return the new XML.
 * Uses DOM parsing (namespace-aware) and bails to the original XML on any structural
 * surprise rather than risking a corrupt document.
 */
function bindFlowIdToBpmn(bpmnXml: string, taskId: string, flowId: string): string {
  const doc = new DOMParser().parseFromString(bpmnXml, 'application/xml')
  if (doc.getElementsByTagName('parsererror').length > 0) {
    return bpmnXml
  }
  const task = Array.from(doc.getElementsByTagName('*')).find(
    (el) => el.localName === 'serviceTask' && el.getAttribute('id') === taskId,
  )
  if (!task) {
    return bpmnXml
  }
  const propsEl = Array.from(task.getElementsByTagName('*')).find(
    (el) => el.localName === 'properties',
  )
  if (!propsEl) {
    return bpmnXml
  }
  const existing = Array.from(propsEl.getElementsByTagName('*')).find(
    (el) => el.localName === 'property' && el.getAttribute('name') === 'ap:flowId',
  )
  if (existing) {
    existing.setAttribute('value', flowId)
  } else {
    const prop = doc.createElementNS(CUSTOM_NS, 'custom:property')
    prop.setAttribute('name', 'ap:flowId')
    prop.setAttribute('value', flowId)
    propsEl.appendChild(prop)
  }
  return new XMLSerializer().serializeToString(doc)
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    // The api client's interceptor unwraps to the HTTP body, so the process sits
    // under the ApiResponse envelope's `data` (see useFormLifecycle for the same shape).
    const processData = await functionUnitApi.getProcess(props.functionUnitId)
    const bpmnXml = processData?.data?.bpmnXml || ''
    tasks.value = parseApServiceTasks(bpmnXml)
    if (tasks.value.length === 0) {
      return
    }
    if (!tasks.value.some((task) => task.id === selectedTaskId.value)) {
      selectedTaskId.value = tasks.value[0].id
    }
    session.value = await fetchServiceTaskSession()
    await refreshDanglingState()
  } catch (error) {
    errorMessage.value = t('functionUnit.serviceTaskLoadFailed')
    console.error('[ServiceTaskDesigner] load failed', error)
  } finally {
    loading.value = false
  }
}

/** Probe the selected task's bound flow so a deleted one shows a rebind prompt, not a dead builder. */
async function refreshDanglingState() {
  danglingFlowId.value = ''
  const flowId = selectedTask.value?.flowId
  if (!flowId || !session.value) {
    return
  }
  if (!(await serviceTaskFlowExists(flowId, session.value.token))) {
    danglingFlowId.value = flowId
  }
}

/** Create an empty flow for the selected task, bind it into the BPMN, then mount. */
async function createFlow() {
  const task = selectedTask.value
  if (!task || creating.value) {
    return
  }
  creating.value = true
  errorMessage.value = ''
  try {
    if (!session.value) {
      session.value = await fetchServiceTaskSession()
    }
    const flowId = await createServiceTaskFlow({
      projectId: session.value.projectId,
      token: session.value.token,
      displayName: task.name || 'Automation flow',
    })
    const processData = await functionUnitApi.getProcess(props.functionUnitId)
    const bpmnXml = processData?.data?.bpmnXml || ''
    const updatedXml = bindFlowIdToBpmn(bpmnXml, task.id, flowId)
    await functionUnitApi.saveProcess(props.functionUnitId, {
      ...processData?.data,
      bpmnXml: updatedXml,
    })
    task.flowId = flowId
    danglingFlowId.value = ''
  } catch (error) {
    errorMessage.value = t('functionUnit.serviceTaskCreateFailed')
    console.error('[ServiceTaskDesigner] create flow failed', error)
  } finally {
    creating.value = false
  }
}

// The host owns the session: on 401 re-issue it instead of letting the builder
// navigate DW away to AP's own sign-in page.
function onUnauthorized() {
  session.value = null
  void load()
}

// Each task has its own flow, so the dangling check is per-selection.
watch(selectedTaskId, () => {
  void refreshDanglingState()
})

onMounted(load)
</script>

<style scoped>
.service-task-designer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 70vh;
  min-height: 480px;
}

.service-task-designer__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.service-task-designer__label {
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.service-task-designer__select {
  width: 280px;
}

.service-task-designer__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.service-task-designer__option-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-task-designer__status {
  flex-shrink: 0;
}

.service-task-designer__hint {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.service-task-designer__canvas {
  flex: 1;
  min-height: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  overflow: hidden;
}
</style>
