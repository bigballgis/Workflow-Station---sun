<template>
  <div class="versioned-process-viewer">
    <!-- What is being shown, and that it cannot be edited. Without this a published
         version and the live draft look identical, and someone would try to edit it. -->
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="version-banner"
    >
      <template #title>
        <span class="banner-title">
          {{ t('versionView.title', { version }) }}
        </span>
        <el-tag
          size="small"
          type="info"
          effect="plain"
          class="banner-tag"
        >
          {{ t('versionView.readOnly') }}
        </el-tag>
      </template>
      <div class="banner-body">
        <span v-if="process?.publishedAt">
          {{ t('versionView.publishedAt', { time: formatTime(process.publishedAt) }) }}
        </span>
        <span v-if="isBehind">
          {{ t('versionView.currentIs', { version: process?.currentVersion }) }}
        </span>
        <el-button
          link
          type="primary"
          size="small"
          @click="emit('open-current')"
        >
          {{ t('versionView.openCurrent') }}
        </el-button>
      </div>
    </el-alert>

    <div
      v-if="errorMessage"
      class="viewer-error"
    >
      <el-empty :description="errorMessage" />
    </div>

    <div
      v-show="!errorMessage"
      ref="canvasRef"
      v-loading="loading"
      class="viewer-canvas"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'
import { functionUnitApi, type VersionedProcess } from '@/api/functionUnit'
import { callActivityRendererModule } from '@/utils/callActivityRenderer'
import { keepNodesFittedToText } from '@platform-shared/bpmnNodeTextFit'
import {
  workflowPlatformModdleDescriptor,
  bpmnIoCustomModdleDescriptor,
  flowableModdleDescriptor,
} from '@/utils/customModdle'

const { t } = useI18n()

const props = defineProps<{
  functionUnitId: number
  version: string
}>()

const emit = defineEmits<{
  (e: 'open-current'): void
}>()

const canvasRef = ref<HTMLElement>()
const process = ref<VersionedProcess | null>(null)
const loading = ref(false)
const errorMessage = ref('')

/** True when the version shown has since been superseded. */
const isBehind = computed(() =>
  Boolean(process.value?.currentVersion && process.value.currentVersion !== props.version)
)

// NavigatedViewer, not the Modeler: it can pan and zoom but has no palette, context
// pad or modeling commands, so the version cannot be edited even by keyboard shortcut.
let viewer: any = null

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await functionUnitApi.getVersionedProcess(props.functionUnitId, props.version)
    process.value = (res as { data?: VersionedProcess })?.data ?? null
    if (!process.value?.bpmnXml) {
      errorMessage.value = t('versionView.noDiagram', { version: props.version })
      return
    }
    await render(process.value.bpmnXml)
  } catch (error) {
    console.warn('Could not load the versioned process', error)
    errorMessage.value = t('versionView.notFound', { version: props.version })
  } finally {
    loading.value = false
  }
}

async function render(xml: string) {
  if (!canvasRef.value) return
  if (!viewer) {
    viewer = new NavigatedViewer({
      container: canvasRef.value,
      moddleExtensions: {
        custom: workflowPlatformModdleDescriptor,
        custom_1: bpmnIoCustomModdleDescriptor,
        flowable: flowableModdleDescriptor,
      },
      // Same call-step rendering as the designer, so a pinned version reads the same way.
      additionalModules: [callActivityRendererModule],
    })
    keepNodesFittedToText(viewer)
  }
  await viewer.importXML(xml)
  viewer.get('canvas').zoom('fit-viewport', 'auto')
}

function formatTime(value: string) {
  try {
    return new Date(value).toLocaleString()
  } catch {
    return value
  }
}

watch(() => [props.functionUnitId, props.version], load)
onMounted(load)
onBeforeUnmount(() => {
  viewer?.destroy()
  viewer = null
})
</script>

<style scoped lang="scss">
.versioned-process-viewer {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.version-banner {
  :deep(.el-alert__content) {
    width: 100%;
  }
}

.banner-title {
  font-weight: 600;
}

.banner-tag {
  margin-left: 8px;
}

.banner-body {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 4px;
  font-size: 12px;
}

.viewer-canvas {
  height: 620px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: #fff;
}

.viewer-error {
  padding: 40px 0;
}
</style>
