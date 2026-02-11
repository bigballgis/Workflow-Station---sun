<template>
  <div class="process-diagram" ref="containerRef">
    <div class="diagram-toolbar" v-if="showToolbar">
      <el-button-group>
        <el-button :icon="ZoomIn" @click="zoomIn" :title="t('diagram.zoomIn')" />
        <el-button :icon="ZoomOut" @click="zoomOut" :title="t('diagram.zoomOut')" />
        <el-button :icon="RefreshRight" @click="resetZoom" :title="t('diagram.reset')" />
        <el-button :icon="FullScreen" @click="fitViewport" :title="t('diagram.fitViewport')" />
      </el-button-group>
      <span class="zoom-level">{{ Math.round(zoomLevel * 100) }}%</span>
    </div>
    <div class="diagram-canvas" ref="canvasRef"></div>
    <div class="diagram-legend" v-if="showLegend">
      <div class="legend-item">
        <span class="legend-dot completed"></span>
        <span>{{ t('diagram.completed') }}</span>
      </div>
      <div class="legend-item">
        <span class="legend-dot current"></span>
        <span>{{ t('diagram.currentNode') }}</span>
      </div>
      <div class="legend-item">
        <span class="legend-dot pending"></span>
        <span>{{ t('diagram.pending') }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ZoomIn, ZoomOut, RefreshRight, FullScreen } from '@element-plus/icons-vue'

const { t } = useI18n()

export interface ProcessNode {
  id: string
  name: string
  type: 'start' | 'end' | 'task' | 'gateway' | 'subprocess'
  status?: 'completed' | 'current' | 'pending'
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
  currentNodeId?: string
  completedNodeIds?: string[]
  showToolbar?: boolean
  showLegend?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  nodes: () => [],
  flows: () => [],
  currentNodeId: '',
  completedNodeIds: () => [],
  showToolbar: true,
  showLegend: true
})

const emit = defineEmits<{
  (e: 'node-click', node: ProcessNode): void
  (e: 'loaded'): void
}>()

const containerRef = ref<HTMLElement>()
const canvasRef = ref<HTMLElement>()
const zoomLevel = ref(1)
const diagramWidth = ref(0)
const diagramHeight = ref(0)
const viewBoxMinX = ref(0)
const viewBoxMinY = ref(0)

// 渲染流程图
const renderDiagram = () => {
  if (!canvasRef.value || !containerRef.value) return

  const canvas = canvasRef.value
  canvas.innerHTML = ''

  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  svg.style.display = 'block'
  svg.style.width = '100%'
  svg.style.height = '100%'
  svg.style.minHeight = '300px'
  
  // 添加箭头定义
  const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs')
  const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker')
  marker.setAttribute('id', 'arrowhead')
  marker.setAttribute('markerWidth', '10')
  marker.setAttribute('markerHeight', '7')
  marker.setAttribute('refX', '9')
  marker.setAttribute('refY', '3.5')
  marker.setAttribute('orient', 'auto')
  const polygon = document.createElementNS('http://www.w3.org/2000/svg', 'polygon')
  polygon.setAttribute('points', '0 0, 10 3.5, 0 7')
  polygon.setAttribute('fill', '#909399')
  marker.appendChild(polygon)
  defs.appendChild(marker)
  svg.appendChild(defs)

  const nodePositions = calculateNodePositions()
  
  // 计算边界，包含所有节点和连线标签
  let minX = Infinity, minY = Infinity, maxX = 0, maxY = 0
  nodePositions.forEach(pos => {
    minX = Math.min(minX, pos.x)
    minY = Math.min(minY, pos.y)
    maxX = Math.max(maxX, pos.x + pos.width)
    maxY = Math.max(maxY, pos.y + pos.height)
  })
  
  // 考虑连线标签和节点标签可能超出节点范围，增加额外边距
  const padding = 60
  minX = Math.max(0, minX - padding)
  minY = Math.max(0, minY - padding / 2)
  maxX = maxX + padding
  maxY = maxY + padding
  
  const viewWidth = Math.max(maxX - minX, 600)
  const viewHeight = Math.max(maxY - minY, 300)
  
  // 保存原始尺寸用于缩放计算
  diagramWidth.value = viewWidth
  diagramHeight.value = viewHeight
  viewBoxMinX.value = minX
  viewBoxMinY.value = minY
  
  // 使用 viewBox 让 SVG 自动缩放以适应容器
  svg.setAttribute('viewBox', `${minX} ${minY} ${viewWidth} ${viewHeight}`)
  svg.setAttribute('preserveAspectRatio', 'xMidYMid meet')

  // 收集所有标签，最后绘制以确保在最上层
  const flowLabels: SVGElement[] = []
  
  props.flows.forEach(flow => {
    const source = nodePositions.get(flow.sourceRef)
    const target = nodePositions.get(flow.targetRef)
    if (source && target) {
      const { line, label } = createFlowLine(source, target, flow)
      svg.appendChild(line)
      if (label) flowLabels.push(label)
    }
  })

  props.nodes.forEach(node => {
    const pos = nodePositions.get(node.id)
    if (pos) {
      const nodeEl = createNodeElement(node, pos)
      svg.appendChild(nodeEl)
    }
  })
  
  // 最后绘制标签，确保在节点之上
  flowLabels.forEach(label => svg.appendChild(label))

  canvas.appendChild(svg)
  
  // 初始化缩放级别为100%
  zoomLevel.value = 1
  
  emit('loaded')
}

// 计算节点位置
const calculateNodePositions = (): Map<string, { x: number; y: number; width: number; height: number }> => {
  const positions = new Map<string, { x: number; y: number; width: number; height: number }>()
  const hasPositionData = props.nodes.some(node => node.x !== undefined && node.y !== undefined)
  
  if (hasPositionData) {
    props.nodes.forEach(node => {
      if (node.x !== undefined && node.y !== undefined) {
        let width = node.width || 100
        let height = node.height || 80
        if ((node.type === 'start' || node.type === 'end') && !node.width) {
          width = 36
          height = 36
        } else if (node.type === 'gateway' && !node.width) {
          width = 50
          height = 50
        }
        positions.set(node.id, { x: node.x, y: node.y, width, height })
      }
    })
  } else {
    const horizontalGap = 180
    const verticalGap = 100
    const startX = 80
    const startY = 80
    const maxNodesPerRow = 5
    let currentX = startX
    let currentY = startY
    let nodeIndex = 0

    props.nodes.forEach(node => {
      let width = 100, height = 80
      if (node.type === 'start' || node.type === 'end') {
        width = 36
        height = 36
      } else if (node.type === 'gateway') {
        width = 50
        height = 50
      }
      positions.set(node.id, { x: currentX, y: currentY, width, height })
      nodeIndex++
      if (nodeIndex % maxNodesPerRow === 0) {
        currentX = startX
        currentY += verticalGap
      } else {
        currentX += horizontalGap
      }
    })
  }
  return positions
}

// 创建节点元素
const createNodeElement = (node: ProcessNode, pos: { x: number; y: number; width: number; height: number }) => {
  const g = document.createElementNS('http://www.w3.org/2000/svg', 'g')
  g.setAttribute('transform', `translate(${pos.x}, ${pos.y})`)
  g.style.cursor = 'pointer'

  let fillColor = '#ffffff'
  let strokeColor = '#909399'
  if (props.completedNodeIds.includes(node.id) || node.status === 'completed') {
    fillColor = '#e8f5e9'
    strokeColor = '#00A651'
  } else if (node.id === props.currentNodeId || node.status === 'current') {
    fillColor = '#fff3e0'
    strokeColor = '#FF6600'
  }

  let shape: SVGElement
  const centerX = pos.width / 2
  const centerY = pos.height / 2
  
  switch (node.type) {
    case 'start':
      shape = document.createElementNS('http://www.w3.org/2000/svg', 'circle')
      shape.setAttribute('cx', String(centerX))
      shape.setAttribute('cy', String(centerY))
      shape.setAttribute('r', String(Math.min(pos.width, pos.height) / 2 - 2))
      shape.setAttribute('fill', '#e8f5e9')
      shape.setAttribute('stroke', '#00A651')
      shape.setAttribute('stroke-width', '2')
      break
    case 'end':
      shape = document.createElementNS('http://www.w3.org/2000/svg', 'circle')
      shape.setAttribute('cx', String(centerX))
      shape.setAttribute('cy', String(centerY))
      shape.setAttribute('r', String(Math.min(pos.width, pos.height) / 2 - 2))
      shape.setAttribute('fill', fillColor)
      shape.setAttribute('stroke', strokeColor)
      shape.setAttribute('stroke-width', '2')
      break
    case 'gateway':
      const halfW = pos.width / 2
      const halfH = pos.height / 2
      shape = document.createElementNS('http://www.w3.org/2000/svg', 'polygon')
      shape.setAttribute('points', `${halfW},0 ${pos.width},${halfH} ${halfW},${pos.height} 0,${halfH}`)
      shape.setAttribute('fill', fillColor)
      shape.setAttribute('stroke', strokeColor)
      shape.setAttribute('stroke-width', '2')
      break
    default:
      shape = document.createElementNS('http://www.w3.org/2000/svg', 'rect')
      shape.setAttribute('width', String(pos.width))
      shape.setAttribute('height', String(pos.height))
      shape.setAttribute('rx', '10')
      shape.setAttribute('ry', '10')
      shape.setAttribute('fill', fillColor)
      shape.setAttribute('stroke', strokeColor)
      shape.setAttribute('stroke-width', '2')
  }
  g.appendChild(shape)

  const text = document.createElementNS('http://www.w3.org/2000/svg', 'text')
  text.setAttribute('text-anchor', 'middle')
  text.setAttribute('font-size', '12')
  text.setAttribute('fill', '#303133')
  
  if (node.type === 'start' || node.type === 'end') {
    text.setAttribute('x', String(centerX))
    text.setAttribute('y', String(pos.height + 15))
    text.textContent = node.name
  } else if (node.type === 'gateway') {
    text.setAttribute('x', String(centerX))
    text.setAttribute('y', String(pos.height + 15))
    text.textContent = node.name
  } else {
    const maxCharsPerLine = Math.floor(pos.width / 12)
    const displayName = node.name.length > maxCharsPerLine * 2 
      ? node.name.substring(0, maxCharsPerLine * 2 - 2) + '...' 
      : node.name
    
    if (displayName.length > maxCharsPerLine) {
      const line1 = displayName.substring(0, maxCharsPerLine)
      const line2 = displayName.substring(maxCharsPerLine)
      const tspan1 = document.createElementNS('http://www.w3.org/2000/svg', 'tspan')
      tspan1.setAttribute('x', String(centerX))
      tspan1.setAttribute('dy', String(centerY - 6))
      tspan1.textContent = line1
      text.appendChild(tspan1)
      const tspan2 = document.createElementNS('http://www.w3.org/2000/svg', 'tspan')
      tspan2.setAttribute('x', String(centerX))
      tspan2.setAttribute('dy', '14')
      tspan2.textContent = line2
      text.appendChild(tspan2)
    } else {
      text.setAttribute('x', String(centerX))
      text.setAttribute('y', String(centerY + 4))
      text.textContent = displayName
    }
  }
  g.appendChild(text)

  g.addEventListener('click', () => emit('node-click', node))
  return g
}

// 创建连线
const createFlowLine = (
  source: { x: number; y: number; width: number; height: number },
  target: { x: number; y: number; width: number; height: number },
  flow: ProcessFlow
): { line: SVGElement; label: SVGElement | null } => {
  const g = document.createElementNS('http://www.w3.org/2000/svg', 'g')
  let d: string
  let labelX: number = 0
  let labelY: number = 0
  
  if (flow.waypoints && flow.waypoints.length >= 2) {
    const points = flow.waypoints
    d = `M ${points[0].x} ${points[0].y}`
    for (let i = 1; i < points.length; i++) {
      d += ` L ${points[i].x} ${points[i].y}`
    }
    
    // 计算路径总长度和中点位置
    let totalLength = 0
    const segments: { length: number; startX: number; startY: number; endX: number; endY: number }[] = []
    for (let i = 0; i < points.length - 1; i++) {
      const segLen = Math.sqrt(
        Math.pow(points[i + 1].x - points[i].x, 2) + 
        Math.pow(points[i + 1].y - points[i].y, 2)
      )
      segments.push({
        length: segLen,
        startX: points[i].x,
        startY: points[i].y,
        endX: points[i + 1].x,
        endY: points[i + 1].y
      })
      totalLength += segLen
    }
    
    // 找到中点所在的线段
    const midLength = totalLength / 2
    let accLength = 0
    for (const seg of segments) {
      if (accLength + seg.length >= midLength) {
        const ratio = (midLength - accLength) / seg.length
        labelX = seg.startX + (seg.endX - seg.startX) * ratio
        labelY = seg.startY + (seg.endY - seg.startY) * ratio
        break
      }
      accLength += seg.length
    }
    // 如果没找到，使用最后一个点
    if (labelX === undefined) {
      labelX = points[points.length - 1].x
      labelY = points[points.length - 1].y
    }
  } else {
    const sourceCenterX = source.x + source.width / 2
    const sourceCenterY = source.y + source.height / 2
    const targetCenterX = target.x + target.width / 2
    const targetCenterY = target.y + target.height / 2
    const dx = targetCenterX - sourceCenterX
    const dy = targetCenterY - sourceCenterY
    let startX: number, startY: number, endX: number, endY: number
    
    if (Math.abs(dx) > Math.abs(dy)) {
      if (dx > 0) {
        startX = source.x + source.width
        startY = sourceCenterY
        endX = target.x
        endY = targetCenterY
      } else {
        startX = source.x
        startY = sourceCenterY
        endX = target.x + target.width
        endY = targetCenterY
      }
    } else {
      if (dy > 0) {
        startX = sourceCenterX
        startY = source.y + source.height
        endX = targetCenterX
        endY = target.y
      } else {
        startX = sourceCenterX
        startY = source.y
        endX = targetCenterX
        endY = target.y + target.height
      }
    }
    d = `M ${startX} ${startY} L ${endX} ${endY}`
    labelX = (startX + endX) / 2
    labelY = (startY + endY) / 2
  }
  
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path')
  path.setAttribute('d', d)
  path.setAttribute('fill', 'none')
  path.setAttribute('stroke', '#909399')
  path.setAttribute('stroke-width', '1.5')
  path.setAttribute('marker-end', 'url(#arrowhead)')
  g.appendChild(path)

  let labelGroup: SVGElement | null = null
  if (flow.name) {
    labelGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g')
    
    const text = document.createElementNS('http://www.w3.org/2000/svg', 'text')
    text.setAttribute('x', String(labelX))
    text.setAttribute('y', String(labelY + 4))
    text.setAttribute('text-anchor', 'middle')
    text.setAttribute('font-size', '12')
    text.setAttribute('fill', '#606266')
    text.textContent = flow.name
    labelGroup.appendChild(text)
  }
  
  return { line: g, label: labelGroup }
}

const zoomIn = () => {
  zoomLevel.value = Math.min(zoomLevel.value + 0.1, 2)
  applyZoom()
}

const zoomOut = () => {
  zoomLevel.value = Math.max(zoomLevel.value - 0.1, 0.3)
  applyZoom()
}

const resetZoom = () => {
  zoomLevel.value = 1
  applyZoom()
}

const fitViewport = () => {
  // 重置为100%，SVG 的 viewBox + preserveAspectRatio 会自动适应
  zoomLevel.value = 1
  applyZoom()
}

const applyZoom = () => {
  if (canvasRef.value) {
    const svg = canvasRef.value.querySelector('svg')
    if (svg && diagramWidth.value > 0) {
      // 通过调整 viewBox 来实现缩放
      const scaledWidth = diagramWidth.value / zoomLevel.value
      const scaledHeight = diagramHeight.value / zoomLevel.value
      const offsetX = (diagramWidth.value - scaledWidth) / 2
      const offsetY = (diagramHeight.value - scaledHeight) / 2
      svg.setAttribute('viewBox', `${viewBoxMinX.value + offsetX} ${viewBoxMinY.value + offsetY} ${scaledWidth} ${scaledHeight}`)
    }
  }
}

watch([() => props.nodes, () => props.flows, () => props.currentNodeId], () => {
  nextTick(() => renderDiagram())
}, { deep: true })

onMounted(() => renderDiagram())

defineExpose({ zoomIn, zoomOut, resetZoom, fitViewport })
</script>

<style scoped lang="scss">
.process-diagram {
  position: relative;
  width: 100%;
  min-height: 350px;
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
    min-height: 350px;
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
        &.completed { background: #e8f5e9; border: 2px solid #00A651; }
        &.current { background: #fff3e0; border: 2px solid #FF6600; }
        &.pending { background: #ffffff; border: 2px solid #909399; }
      }
    }
  }
}
</style>
