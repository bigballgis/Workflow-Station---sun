<!--
  ServiceTask tab of the Function Unit editor.

  Mounts the vendored Activepieces builder for the automation flow bound to this
  Function Unit's BPMN service task(s). The builder is embedded via lib-mode +
  Shadow DOM (ServiceTaskBuilderCanvas), NOT an iframe — see decision X-6.

  Chain: the admin-center bridge issues the per-user AP session (L7), the builder
  talks to AP through the Kong /api/ap prefix (L2), and the bundle itself is served
  from DW's own origin at /dev/service-task-builder (build-time copy, AG-02.8).
-->
<template>
  <div
    v-loading="loading"
    class="service-task-designer"
  >
    <!-- 单任务也显示，让用户知道正在编辑哪条 flow -->
    <div
      v-if="tasks.length > 0"
      class="service-task-designer__toolbar"
    >
      <span class="service-task-designer__label">{{ t('functionUnit.serviceTaskFlow') }}</span>
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
        />
      </el-select>
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

    <el-empty
      v-else-if="!loading && tasks.length === 0"
      :description="t('functionUnit.serviceTaskEmpty')"
    />

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
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { functionUnitApi } from '@/api/functionUnit'
import { fetchServiceTaskSession, type ServiceTaskSession } from '@/api/serviceTask'
import ServiceTaskBuilderCanvas from '@/components/serviceTask/ServiceTaskBuilderCanvas.vue'

interface ApServiceTask {
  id: string
  name: string
  flowId: string
}

const props = defineProps<{ functionUnitId: number }>()

const { t } = useI18n()

const loading = ref(false)
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

const selectedFlowId = computed(
  () => tasks.value.find((task) => task.id === selectedTaskId.value)?.flowId || '',
)

/**
 * Pull the AP-backed service tasks out of the BPMN. They are marked by the
 * `serviceType=ap` extension property and carry their flow in `ap:flowId`
 * (see utils/serviceTaskConfigSerializer).
 */
/** 解析单个 XML 标签的属性表（与属性书写顺序无关） */
function parseTagAttributes(tag: string): Record<string, string> {
  const attrs: Record<string, string> = {}
  const attrPattern = /([\w:.-]+)="([^"]*)"/g
  let match: RegExpExecArray | null
  while ((match = attrPattern.exec(tag)) !== null) {
    attrs[match[1]] = match[2]
  }
  return attrs
}

function parseApServiceTasks(bpmnXml: string): ApServiceTask[] {
  const result: ApServiceTask[] = []
  const taskPattern = /<(?:\w+:)?serviceTask\b([^>]*)>([\s\S]*?)<\/(?:\w+:)?serviceTask>/g
  let match: RegExpExecArray | null
  while ((match = taskPattern.exec(bpmnXml)) !== null) {
    const [, attrs, body] = match
    // 收集扩展 property 标签为键值表，不依赖 name/value 的先后顺序
    const props: Record<string, string> = {}
    const propPattern = /<[\w:.-]*[Pp]roperty\b[^>]*\/?>/g
    let propMatch: RegExpExecArray | null
    while ((propMatch = propPattern.exec(body)) !== null) {
      const propAttrs = parseTagAttributes(propMatch[0])
      if (propAttrs.name !== undefined) {
        props[propAttrs.name] = propAttrs.value ?? ''
      }
    }
    if (props['serviceType'] !== 'ap') {
      continue
    }
    const flowId = props['ap:flowId'] || ''
    if (!flowId) {
      continue
    }
    const taskAttrs = parseTagAttributes(attrs)
    result.push({
      id: taskAttrs.id || flowId,
      name: taskAttrs.name || '',
      flowId,
    })
  }
  return result
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
  } catch (error) {
    errorMessage.value = t('functionUnit.serviceTaskLoadFailed')
    console.error('[ServiceTaskDesigner] load failed', error)
  } finally {
    loading.value = false
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

.service-task-designer__canvas {
  flex: 1;
  min-height: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  overflow: hidden;
}
</style>
