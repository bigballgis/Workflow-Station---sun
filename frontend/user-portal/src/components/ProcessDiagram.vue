<template>
  <div class="process-diagram" ref="containerRef">
    <div class="diagram-toolbar" v-if="showToolbar">
      <el-button-group>
        <el-button :icon="ZoomIn" @click="zoomIn" title="放大" />
        <el-button :icon="ZoomOut" @click="zoomOut" title="缩小" />
        <el-button :icon="RefreshRight" @click="resetZoom" title="重置" />
        <el-button :icon="FullScreen" @click="fitViewport" title="适应" />
      </el-button-group>
      <span class="zoom-level">{{ Math.round(zoomLevel * 100) }}%</span>
    </div>
    <div class="diagram-canvas" ref="canvasRef"></div>
    <div class="diagram-legend" v-if="showLegend">
      <div class="legend-item">
        <span class="legend-dot completed"></span>
        <span>已完成</span>
      </div>
      <div class="legend-item">
        <span class="legend-dot current"></span>
        <span>当前节点</span>
      </div>
      <div class="legend-item">
        <span class="legend-dot pending"></span>
        <span>待处理</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { ZoomIn, ZoomOut, RefreshRight, FullScreen } from '@element-plus/icons-vue'

export interface ProcessNode {
  id: string
  name: string
  type: 'start' | 'end' | 'task' | 'gateway' | 'subprocess'
  status?: 'completed' | 'current' | 'pending'
  x?: number
  y?: number
  assignee?: string
  completedTime?: string
}

export interface ProcessFlow {
  id: string
  sourceRef: string
  targetRef: string
  name?: string
  conditionExpression?: string
}

interface Props {
  xml?: string
  nodes?: ProcessNode[]
  flows?: ProcessFlow[]
  currentNodeId?: string
  completedNodeIds?: string[]
  showToolbar?: boolean
  showLegend?: boolean
  readonly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  xml: '',
  nodes: () => [],
  flows: () => [],
  currentNodeId: '',
  completedNodeIds: () => [],
  showToolbar: true,
  showLegend: true,
  readonly: true
})

const emit = defineEmits<{
  (e: 'node-click', node: ProcessNode): void
  (e: 'loaded'): void
}>()

const containerRef = ref<HTMLElement>()
const canvasRef = ref<HTMLElement>()
const zoomLevel = ref(1)

// 简化的流程图渲染（不依赖BPMN.js）
const renderDiagram = () => {
  if (!canvasRef.value) return

  const canvas = canvasRef.value
  canvas.innerHTML = ''

  // 创建SVG容器
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  svg.setAttribute('width', '100%')
  svg.setAttribute('height', '100%')
  svg.style.minHeight = '300px'

  // 计算节点位置
  const nodePositions = calculateNodePositions()

  // 绘制连线
  props.flows.forEach(flow => {
    const source = nodePositions.get(flow.sourceRef)
    const target = nodePositions.get(flow.targetRef)
    if (source && target) {
      const line = createFlowLine(source, target, flow)
      svg.appendChild(line)
    }
  })

  // 绘制节点
  props.nodes.forEach(node => {
    const pos = nodePositions.get(node.id)
    if (pos) {
      const nodeEl = createNodeElement(node, pos)
      svg.appendChild(nodeEl)
    }
  })

  canvas.appendChild(svg)
  emit('loaded')
}

// 计算节点位置
const calculateNodePositions = (): Map<string, { x: number; y: number }> => {
  const positions = new Map<string, { x: number; y: number }>()
  const nodeWidth = 120
  const nodeHeight = 60
  const horizontalGap = 180
  const verticalGap = 100
  const startX = 80
  const startY = 80

  // 简单的水平布局
  let currentX = startX
  let currentY = startY
  let maxNodesPerRow = 5
  let nodeIndex = 0

  props.nodes.forEach(node => {
    if (node.x !== undefined && node.y !== undefined) {
      positions.set(node.id, { x: node.x, y: node.y })
    } else {
      positions.set(node.id, { x: currentX, y: currentY })
      nodeIndex++
      if (nodeIndex % maxNodesPerRow === 0) {
        currentX = startX
        currentY += verticalGap
      } else {
        currentX += horizontalGap
      }
    }
  })

  return positions
}

// 创建节点元素
const createNodeElement = (node: ProcessNode, pos: { x: number; y: number }) => {
  const g = document.createElementNS('http://www.w3.org/2000/svg', 'g')
  g.setAttribute('transform', `translate(${pos.x}, ${pos.y})`)
  g.style.cursor = 'pointer'

  // 确定节点状态颜色
  let fillColor = '#ffffff'
  let strokeColor = '#909399'
  if (props.completedNodeIds.includes(node.id) || node.status === 'completed') {
    fillColor = '#e8f5e9'
    strokeColor = '#00A651'
  } else if (node.id === props.currentNodeId || node.status === 'current') {
    fillColor = '#fff3e0'
    strokeColor = '#FF6600'
  }

  // 根据节点类型绘制不同形状
  let shape: SVGElement
  switch (node.type) {
    case 'start':
      shape = document.createElementNS('http://www.w3.org/2000/svg', 'circle')
      shape.setAttribute('cx', '20')
      shape.setAttribute('cy', '20')
      shape.setAttribute('r', '18')
      shape.setAttribute('fill', '#e8f5e9')
      shape.setAttribute('stroke', '#00A651')
      shape.setAttribute('stroke-width', '2')
      break
    case 'end':
      shape = document.createElementNS('http://www.w3.org/2000/svg', 'circle')
      shape.setAttribute('cx', '20')
      shape.setAttribute('cy', '20')
      shape.setAttribute('r', '18')
      shape.setAttribute('fill', '#ffebee')
      shape.setAttribute('stroke', '#DB0011')
      shape.setAttribute('stroke-width', '3')
      break
    case 'gateway':
      shape = document.createElementNS('http://www.w3.org/2000/svg', 'polygon')
      shape.setAttribute('points', '20,0 40,20 20,40 0,20')
      shape.setAttribute('fill', fillColor)
      shape.setAttribute('stroke', strokeColor)
      shape.setAttribute('stroke-width', '2')
      break
    default:
      shape = document.createElementNS('http://www.w3.org/2000/svg', 'rect')
      shape.setAttribute('width', '100')
      shape.setAttribute('height', '40')
      shape.setAttribute('rx', '4')
      shape.setAttribute('fill', fillColor)
      shape.setAttribute('stroke', strokeColor)
      shape.setAttribute('stroke-width', '2')
  }
  g.appendChild(shape)

  // 添加节点名称
  if (node.type !== 'start' && node.type !== 'end') {
    const text = document.createElementNS('http://www.w3.org/2000/svg', 'text')
    text.setAttribute('x', node.type === 'gateway' ? '20' : '50')
    text.setAttribute('y', node.type === 'gateway' ? '50' : '25')
    text.setAttribute('text-anchor', 'middle')
    text.setAttribute('font-size', '12')
    text.setAttribute('fill', '#303133')
    text.textContent = node.name.length > 10 ? node.name.substring(0, 10) + '...' : node.name
    g.appendChild(text)
  }

  // 点击事件
  g.addEventListener('click', () => {
    emit('node-click', node)
  })

  return g
}

// 创建连线
const createFlowLine = (
  source: { x: number; y: number },
  target: { x: number; y: number },
  flow: ProcessFlow
) => {
  const g = document.createElementNS('http://www.w3.org/2000/svg', 'g')

  // 计算连线起点和终点
  const startX = source.x + 100
  const startY = source.y + 20
  const endX = target.x
  const endY = target.y + 20

  // 绘制路径
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path')
  const d = `M ${startX} ${startY} L ${endX} ${endY}`
  path.setAttribute('d', d)
  path.setAttribute('fill', 'none')
  path.setAttribute('stroke', '#909399')
  path.setAttribute('stroke-width', '1.5')
  path.setAttribute('marker-end', 'url(#arrowhead)')
  g.appendChild(path)

  // 添加条件标签
  if (flow.name) {
    const midX = (startX + endX) / 2
    const midY = (startY + endY) / 2
    const text = document.createElementNS('http://www.w3.org/2000/svg', 'text')
    text.setAttribute('x', String(midX))
    text.setAttribute('y', String(midY - 5))
    text.setAttribute('text-anchor', 'middle')
    text.setAttribute('font-size', '10')
    text.setAttribute('fill', '#909399')
    text.textContent = flow.name
    g.appendChild(text)
  }

  return g
}

// 缩放控制
const zoomIn = () => {
  zoomLevel.value = Math.min(zoomLevel.value + 0.1, 2)
  applyZoom()
}

const zoomOut = () => {
  zoomLevel.value = Math.max(zoomLevel.value - 0.1, 0.5)
  applyZoom()
}

const resetZoom = () => {
  zoomLevel.value = 1
  applyZoom()
}

const fitViewport = () => {
  zoomLevel.value = 1
  applyZoom()
}

const applyZoom = () => {
  if (canvasRef.value) {
    canvasRef.value.style.transform = `scale(${zoomLevel.value})`
    canvasRef.value.style.transformOrigin = 'top left'
  }
}

// 监听数据变化
watch([() => props.nodes, () => props.flows, () => props.currentNodeId], () => {
  nextTick(() => renderDiagram())
}, { deep: true })

onMounted(() => {
  renderDiagram()
})

defineExpose({
  zoomIn,
  zoomOut,
  resetZoom,
  fitViewport
})
</script>

<style scoped lang="scss">
.process-diagram {
  position: relative;
  width: 100%;
  min-height: 400px;
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;

  .diagram-toolbar {
    position: absolute;
    top: 10px;
    right: 10px;
    z-index: 10;
    display: flex;
    align-items: center;
    gap: 10px;
    background: white;
    padding: 5px 10px;
    border-radius: 4px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);

    .zoom-level {
      font-size: 12px;
      color: #909399;
      min-width: 40px;
    }
  }

  .diagram-canvas {
    width: 100%;
    height: 100%;
    min-height: 400px;
    overflow: auto;
    transition: transform 0.2s ease;
  }

  .diagram-legend {
    position: absolute;
    bottom: 10px;
    left: 10px;
    display: flex;
    gap: 15px;
    background: white;
    padding: 8px 12px;
    border-radius: 4px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    font-size: 12px;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 5px;

      .legend-dot {
        width: 12px;
        height: 12px;
        border-radius: 2px;

        &.completed {
          background: #e8f5e9;
          border: 2px solid #00A651;
        }

        &.current {
          background: #fff3e0;
          border: 2px solid #FF6600;
        }

        &.pending {
          background: #ffffff;
          border: 2px solid #909399;
        }
      }
    }
  }
}
</style>
