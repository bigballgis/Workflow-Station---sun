<template>
  <div class="process-diagram" ref="containerRef">
    <div ref="canvasRef" class="bpmn-canvas"></div>
    <div class="diagram-bottom-bar">
      <div class="diagram-legend" v-if="showLegend">
        <div class="legend-item">
          <span class="legend-dot completed"></span>
          <span>{{ t('diagram.completed') }}</span>
        </div>
        <div v-if="showCurrentStep" class="legend-item">
          <span class="legend-dot current"></span>
          <span>{{ t('diagram.currentStep') }}</span>
        </div>
        <div class="legend-item">
          <span class="legend-dot rejected"></span>
          <span>{{ t('diagram.rejected') }}</span>
        </div>
        <div class="legend-item">
          <span class="legend-dot pending"></span>
          <span>{{ t('diagram.pending') }}</span>
        </div>
      </div>
      <div class="diagram-toolbar" v-if="showToolbar">
        <el-button-group>
          <el-button :icon="ZoomIn" @click="zoomIn" :title="t('diagram.zoomIn')" />
          <el-button :icon="ZoomOut" @click="zoomOut" :title="t('diagram.zoomOut')" />
          <el-button :icon="RefreshRight" @click="resetZoom" :title="t('diagram.reset')" />
          <el-button :icon="FullScreen" @click="fitViewport" :title="t('diagram.fitViewport')" />
        </el-button-group>
        <span class="zoom-level">{{ Math.round(zoomLevel * 100) }}%</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ZoomIn, ZoomOut, RefreshRight, FullScreen } from '@element-plus/icons-vue'
// @ts-ignore
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'

const { t } = useI18n()

export interface ProcessNode {
  id: string
  name: string
  type: 'start' | 'end' | 'task' | 'gateway' | 'subprocess'
  status?: 'completed' | 'current' | 'pending' | 'rejected'
  x?: number
  y?: number
  width?: number
  height?: number
  assignee?: string
  completedTime?: string
}

export interface ProcessFlow {
  id: string
  sourceRef: string
  targetRef: string
  name?: string
  conditionExpression?: string
  waypoints?: Array<{ x: number; y: number }>
}

interface Props {
  nodes?: ProcessNode[]
  flows?: ProcessFlow[]
  bpmnXml?: string
  currentNodeId?: string
  completedNodeIds?: string[]
  selectedNodeId?: string
  showToolbar?: boolean
  showLegend?: boolean
  showCurrentStep?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  nodes: () => [],
  flows: () => [],
  bpmnXml: '',
  currentNodeId: '',
  completedNodeIds: () => [],
  selectedNodeId: '',
  showToolbar: true,
  showLegend: true,
  showCurrentStep: true
})

const emit = defineEmits<{
  (e: 'node-click', node: ProcessNode): void
  (e: 'loaded'): void
}>()

const containerRef = ref<HTMLElement>()
const canvasRef = ref<HTMLElement>()
const zoomLevel = ref(1)

let viewer: any = null
const showCurrentStep = computed(() => props.showCurrentStep)

const destroyViewer = () => {
  if (viewer) {
    try { viewer.destroy() } catch (_) { /* ignore */ }
    viewer = null
  }
}

// Apply status colors to rendered bpmn-js elements
const applyStatusColors = () => {
  if (!viewer) return
  const elementRegistry = viewer.get('elementRegistry')

  props.nodes.forEach(node => {
    const element = elementRegistry.get(node.id)
    if (!element) return

    const gfx: SVGElement | null = elementRegistry.getGraphics(element)
    if (!gfx) return

    let fill = '#ffffff'
    let stroke = '#909399'

    if (node.status === 'rejected') {
      fill = '#ffebee'
      stroke = '#f44336'
    } else if (showCurrentStep.value && (node.id === props.currentNodeId || node.status === 'current')) {
      fill = '#fff3e0'
      stroke = '#FF6600'
    } else if (props.completedNodeIds.includes(node.id) || node.status === 'completed') {
      fill = '#e8f5e9'
      stroke = '#00A651'
    }

    const visual = gfx.querySelector('.djs-visual')
    if (!visual) return

    // Apply to shape primitives, skip label backgrounds
    const shapes = visual.querySelectorAll('rect, circle, polygon, polyline, ellipse')
    shapes.forEach(shape => {
      const el = shape as SVGElement
      el.style.fill = fill
      el.style.stroke = stroke
      el.style.strokeWidth = '2px'
    })
    // Also handle path shapes (e.g. end event double-circle border)
    const paths = visual.querySelectorAll('path')
    paths.forEach(path => {
      const el = path as SVGElement
      if (!el.style.fill || el.style.fill === 'none') return
      el.style.fill = fill
      el.style.stroke = stroke
    })

    // Apply selected-node highlight (blue border, overrides status colors)
    if (props.selectedNodeId && props.selectedNodeId === node.id) {
      shapes.forEach(shape => {
        const el = shape as SVGElement
        el.style.stroke = '#409EFF'
        el.style.strokeWidth = '3px'
      })
      paths.forEach(path => {
        const el = path as SVGElement
        el.style.stroke = '#409EFF'
        el.style.strokeWidth = '3px'
      })
    }
  })
}

// 从 BPMN XML 解析图形边界（在创建 viewer 之前调用，避免先后顺序问题）
const parseBpmnBounds = (xml: string): { width: number; height: number } | null => {
  try {
    const doc = new DOMParser().parseFromString(xml, 'application/xml')
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity

    doc.querySelectorAll('BPMNShape, bpmndi\\:BPMNShape').forEach(shape => {
      const b = shape.querySelector('Bounds, dc\\:Bounds')
      if (!b) return
      const x = parseFloat(b.getAttribute('x') || '0')
      const y = parseFloat(b.getAttribute('y') || '0')
      const w = parseFloat(b.getAttribute('width') || '0')
      const h = parseFloat(b.getAttribute('height') || '0')
      minX = Math.min(minX, x);      minY = Math.min(minY, y)
      maxX = Math.max(maxX, x + w);  maxY = Math.max(maxY, y + h)
    })
    doc.querySelectorAll('BPMNEdge waypoint, bpmndi\\:BPMNEdge waypoint, BPMNEdge di\\:waypoint').forEach(wp => {
      const x = parseFloat(wp.getAttribute('x') || '0')
      const y = parseFloat(wp.getAttribute('y') || '0')
      minX = Math.min(minX, x);  minY = Math.min(minY, y)
      maxX = Math.max(maxX, x);  maxY = Math.max(maxY, y)
    })
    if (!isFinite(maxX) || !isFinite(maxY)) return null
    return { width: maxX - minX, height: maxY - minY }
  } catch {
    return null
  }
}

const fitToContainer = () => {
  if (!viewer) return
  const canvas = viewer.get('canvas')
  canvas.zoom('fit-viewport')
  zoomLevel.value = canvas.zoom() as number
}

const renderBpmn = async () => {
  if (!canvasRef.value || !props.bpmnXml) return

  destroyViewer()

  const el = canvasRef.value
  await nextTick()

  // 第一步：用临时高度创建 viewer，先 fit-viewport 得到真实的图形像素尺寸
  el.style.height = '400px'
  viewer = new NavigatedViewer({ container: el })

  try {
    await viewer.importXML(props.bpmnXml)
    await nextTick()

    const canvas = viewer.get('canvas')
    canvas.zoom('fit-viewport')

    const vb   = canvas.viewbox()
    const inner = vb.inner as { x: number; y: number; width: number; height: number } | undefined
    const scale = canvas.zoom() as number

    if (inner && inner.width > 0 && inner.height > 0) {
      // 第二步：根据真实像素高度精确设置画布高度，再重新 fit-viewport 完成居中
      const padding = 48
      const exactHeight = Math.max(Math.ceil(inner.height * scale) + padding * 2, 200)
      el.style.height = `${exactHeight}px`

      // 等浏览器 reflow 完成，再通知 bpmn-js 容器尺寸已变化，最后重新居中
      await new Promise<void>(r => setTimeout(r, 0))
      canvas.resized()
      canvas.zoom('fit-viewport')
    }

    zoomLevel.value = canvas.zoom() as number
    applyStatusColors()

    const eventBus = viewer.get('eventBus')
    eventBus.on('canvas.viewbox.changed', () => {
      const z = viewer?.get('canvas')?.zoom()
      if (z !== undefined) zoomLevel.value = z as number
    })

    // Wire up node-click event on the diagram
    eventBus.on('element.click', (event: any) => {
      const element = event.element
      if (!element) return
      // Only emit for shape elements (nodes), not edges/connections
      if (element.type === 'bpmn:SequenceFlow' ||
          element.type?.includes('Edge') ||
          element.type === 'label') {
        return
      }
      const node = props.nodes.find(n => n.id === element.id)
      if (node) {
        emit('node-click', node)
      }
    })

    emit('loaded')
  } catch (err) {
    console.error('Failed to render BPMN:', err)
  }
}

// ── Toolbar actions ───────────────────────────────────────────────────────────

const zoomIn = () => {
  if (viewer) {
    const canvas = viewer.get('canvas')
    canvas.zoom(canvas.zoom() + 0.1)
    zoomLevel.value = canvas.zoom()
  }
}

const zoomOut = () => {
  if (viewer) {
    const canvas = viewer.get('canvas')
    canvas.zoom(Math.max(canvas.zoom() - 0.1, 0.2))
    zoomLevel.value = canvas.zoom()
  }
}

const resetZoom = () => {
  if (viewer) {
    const canvas = viewer.get('canvas')
    canvas.zoom(1)
    zoomLevel.value = 1
  }
}

const fitViewport = () => {
  if (viewer) {
    fitToContainer()
  }
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────

watch(() => props.bpmnXml, async (xml) => {
  if (xml) {
    await nextTick()
    await renderBpmn()
  }
}, { immediate: false })

watch([() => props.nodes, () => props.completedNodeIds, () => props.currentNodeId, () => props.selectedNodeId], () => {
  applyStatusColors()
}, { deep: true })

onMounted(async () => {
  if (props.bpmnXml) {
    await renderBpmn()
  }
})

onUnmounted(() => {
  destroyViewer()
})

defineExpose({ zoomIn, zoomOut, resetZoom, fitViewport })
</script>

<style scoped lang="scss">
.process-diagram {
  width: 100%;
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  display: flex;
  flex-direction: column;

  .bpmn-canvas {
    width: 100%;
    /* 高度由 JS 根据图内容自适应设定 */
    min-height: 200px;
    position: relative;
  }

  .diagram-bottom-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 12px;
    border-top: 1px solid #e4e7ed;
    background: #fff;
    flex-shrink: 0;
  }

  .diagram-toolbar {
    display: flex;
    align-items: center;
    gap: 10px;
    .zoom-level {
      font-size: 12px;
      color: #909399;
      min-width: 40px;
    }
  }

  .diagram-legend {
    display: flex;
    gap: 15px;
    font-size: 12px;
    .legend-item {
      display: flex;
      align-items: center;
      gap: 5px;
      .legend-dot {
        width: 12px;
        height: 12px;
        border-radius: 2px;
        &.completed { background: #e8f5e9; border: 2px solid #00A651; }
        &.current   { background: #fff3e0; border: 2px solid #FF6600; }
        &.rejected  { background: #ffebee; border: 2px solid #f44336; }
        &.pending   { background: #ffffff; border: 2px solid #909399; }
      }
    }
  }
}
</style>

<!-- bpmn-js requires global (non-scoped) CSS -->
<style>
@import 'bpmn-js/dist/assets/diagram-js.css';
@import 'bpmn-js/dist/assets/bpmn-js.css';
@import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';

/* Hide the bpmn-js logo/watermark */
.bjs-powered-by { display: none !important; }

/* Remove editing cursor hint */
.bpmn-canvas .djs-container { cursor: default; }

/* Allow bpmn-js SVG to render fully without clipping */
.bpmn-canvas svg { overflow: visible !important; }

/* Pointer cursor on clickable nodes */
.djs-shape { cursor: pointer !important; }
</style>
