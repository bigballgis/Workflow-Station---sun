<!--
  Automation tab of the Function Unit editor.

  Mounts the vendored Activepieces builder for the automation flow referenced by a
  BPMN service task of type "ap". The builder is embedded via lib-mode + Shadow DOM
  (ServiceTaskBuilderCanvas), NOT an iframe — see decision X-6.

  Contract (FR-C01/C02): a service task references its flow by ONE business key
  (`ap:flowKey` = the flow's metadata.hermesFlowKey; legacy BPMN may still carry a
  raw `ap:flowId`, and the engine resolves ids too). Flows are designed on the
  standalone Automation page as well — this tab is the per-Function-Unit view of the
  same flows, plus the shortcut that creates a missing flow for a key that nothing
  matches yet. The key already lives in the BPMN, so creation writes nothing back.

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
              :type="taskStateType(task)"
              size="small"
              effect="light"
              disable-transitions
            >
              {{ taskStateLabel(task) }}
            </el-tag>
          </div>
        </el-option>
      </el-select>
      <el-tag
        v-if="selectedTask"
        :type="taskStateType(selectedTask)"
        size="small"
        effect="plain"
        disable-transitions
        class="service-task-designer__status"
      >
        {{ taskStateLabel(selectedTask) }}
      </el-tag>
    </div>

    <!-- One flow reference per service task; the list above switches between them. -->
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

    <!-- Declared as automation but no flow key configured: the key is set in the
         Service Task panel of Process Design, not here. -->
    <el-empty
      v-else-if="!loading && selectedTask && !selectedFlowRef"
      :description="t('functionUnit.serviceTaskNoKey')"
    />

    <!-- A key is configured but no flow matches it yet: create it here -->
    <el-empty
      v-else-if="!loading && selectedTask && !selectedFlowId"
      :description="t('functionUnit.serviceTaskNoFlow', { key: selectedFlowRef })"
    >
      <el-button
        type="primary"
        :loading="creating"
        @click="createFlow"
      >
        {{ t('functionUnit.serviceTaskCreateFlow') }}
      </el-button>
    </el-empty>

    <ServiceTaskBuilderCanvas
      v-else-if="session && selectedFlowId"
      :key="`${selectedFlowId}-${sessionEpoch}`"
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
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { functionUnitApi } from '@/api/functionUnit'
import {
  createAutomationFlow,
  fetchServiceTaskSession,
  listAutomationFlows,
  type ServiceTaskSession,
} from '@/api/automation'
import ServiceTaskBuilderCanvas from '@/components/serviceTask/ServiceTaskBuilderCanvas.vue'

interface ApServiceTask {
  id: string
  name: string
  /** ap:flowKey, or the legacy ap:flowId when no key is present (engine resolves both). */
  flowRef: string
}

const props = defineProps<{ functionUnitId: number }>()

const { t } = useI18n()

const loading = ref(false)
const creating = ref(false)
const errorMessage = ref('')
const session = ref<ServiceTaskSession | null>(null)
const tasks = ref<ApServiceTask[]>([])
const selectedTaskId = ref('')
/** Bump to remount the canvas after a session re-issue (401 recovery). */
const sessionEpoch = ref(0)

/** hermesFlowKey → flow id, over ALL of the project's flows (cursor walk). */
const flowIdByKey = ref<Map<string, string>>(new Map())
/** All known flow ids — a flowRef may hold a raw id instead of a business key. */
const flowIds = ref<Set<string>>(new Set())

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
const selectedFlowRef = computed(() => selectedTask.value?.flowRef || '')
const selectedFlowId = computed(() => resolveFlowId(selectedFlowRef.value))

/** Business key (or legacy id) → environment-local flow id; '' when nothing matches. */
function resolveFlowId(flowRef: string): string {
  if (!flowRef) {
    return ''
  }
  return (
    flowIdByKey.value.get(flowRef) || (flowIds.value.has(flowRef) ? flowRef : '')
  )
}

function taskStateType(task: ApServiceTask): 'success' | 'warning' | 'info' {
  if (!task.flowRef) {
    return 'info'
  }
  return resolveFlowId(task.flowRef) ? 'success' : 'warning'
}

function taskStateLabel(task: ApServiceTask): string {
  if (!task.flowRef) {
    return t('functionUnit.serviceTaskNoKeyTag')
  }
  return resolveFlowId(task.flowRef)
    ? t('functionUnit.serviceTaskBound')
    : t('functionUnit.serviceTaskUnbound')
}

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
 * serviceType=ap — INCLUDING ones without a flow key yet (their empty state tells
 * the user where the key is configured). The reference, when present, is in
 * `ap:flowKey`, falling back to the legacy `ap:flowId`.
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
    result.push({
      id,
      name: taskAttrs.name || '',
      flowRef: props['ap:flowKey'] || props['ap:flowId'] || '',
    })
  }
  return result
}

/** Walk the whole flow list (cursor-paged) into the key→id map used for resolution. */
async function loadFlowIndex(sess: ServiceTaskSession) {
  const byKey = new Map<string, string>()
  const ids = new Set<string>()
  let cursor: string | undefined
  // Bounded walk: 20 pages × 100 flows is far beyond any real project.
  for (let page = 0; page < 20; page++) {
    const result = await listAutomationFlows({
      token: sess.token,
      projectId: sess.projectId,
      cursor,
      limit: 100,
    })
    for (const flow of result.data || []) {
      ids.add(flow.id)
      const key = flow.metadata?.hermesFlowKey
      if (key) {
        byKey.set(key, flow.id)
      }
    }
    if (!result.next) {
      break
    }
    cursor = result.next
  }
  flowIdByKey.value = byKey
  flowIds.value = ids
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
    await loadFlowIndex(session.value)
    sessionEpoch.value += 1
  } catch (error) {
    errorMessage.value = t('functionUnit.serviceTaskLoadFailed')
    console.error('[ServiceTaskDesigner] load failed', error)
  } finally {
    loading.value = false
  }
}

/**
 * Create the flow for the selected task's key and mount it. The key is already in
 * the BPMN, so nothing is written back — the new flow is stamped with
 * metadata.hermesFlowKey = the key and resolution picks it up.
 */
async function createFlow() {
  const task = selectedTask.value
  if (!task || !task.flowRef || creating.value) {
    return
  }
  creating.value = true
  errorMessage.value = ''
  try {
    if (!session.value) {
      session.value = await fetchServiceTaskSession()
    }
    const flow = await createAutomationFlow({
      token: session.value.token,
      projectId: session.value.projectId,
      displayName: task.name || 'Automation flow',
      flowKey: task.flowRef,
    })
    const byKey = new Map(flowIdByKey.value)
    byKey.set(task.flowRef, flow.id)
    flowIdByKey.value = byKey
    const ids = new Set(flowIds.value)
    ids.add(flow.id)
    flowIds.value = ids
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
