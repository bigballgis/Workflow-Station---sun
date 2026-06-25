<template>
  <div class="page-container">
    <PageHeader :title="focusId ? t('erDiagram.titleFocus', { table: focusTableName }) : t('erDiagram.title')">
      <template #actions>
        <el-button @click="router.back()">
          {{ t('erDiagram.back') }}
        </el-button>
        <el-button @click="autoLayout">
          {{ t('erDiagram.relayout') }}
        </el-button>
        <el-button
          v-if="focusId"
          type="primary"
          @click="router.push('/relation-tables/structure/er-diagram')"
        >
          {{ t('erDiagram.viewAll') }}
        </el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="hiddenEdgeCount > 0"
      :title="t('erDiagram.hiddenEdges', { count: hiddenEdgeCount })"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px;"
    />

    <el-card
      v-loading="loading"
      class="er-card"
      body-style="padding: 0;"
    >
      <div class="er-legend">
        <span class="legend-item">🔑 {{ t('erDiagram.legendPk') }}</span>
        <span class="legend-item">🔗 {{ t('erDiagram.legendFk') }}</span>
        <span class="legend-item legend-hint">{{ t('erDiagram.dragHint') }}</span>
      </div>

      <div
        v-show="hasData"
        ref="canvasRef"
        class="er-canvas"
        @scroll="scheduleRecalc"
      >
        <svg
          class="er-svg"
          :width="canvasSize.w"
          :height="canvasSize.h"
        >
          <defs>
            <marker
              id="er-arrow"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="7"
              markerHeight="7"
              orient="auto-start-reverse"
            >
              <path
                d="M 0 0 L 10 5 L 0 10 z"
                fill="#409eff"
              />
            </marker>
          </defs>
          <g
            v-for="c in connectors"
            :key="c.key"
          >
            <path
              :d="c.path"
              class="er-edge"
              marker-end="url(#er-arrow)"
            />
            <text
              :x="c.labelX"
              :y="c.labelY"
              class="er-edge-label"
            >
              {{ c.label }}
            </text>
          </g>
        </svg>

        <div
          v-for="node in graph.nodes"
          :key="node.id"
          :ref="el => setCardRef(node.id, el)"
          class="er-table-card"
          :style="cardStyle(node.id)"
        >
          <div
            class="er-table-header"
            @mousedown="startDrag(node.id, $event)"
          >
            <span class="er-table-title">{{ node.label }}</span>
            <el-tag
              size="small"
              type="info"
              class="er-field-count"
            >
              {{ node.fields.length }}
            </el-tag>
          </div>
          <div class="er-field-list">
            <div
              v-for="(f, idx) in visibleFields(node)"
              :key="f.fieldName"
              class="er-field-row"
              :class="{ 'is-fk': f.isFk, 'is-pk': f.isPk }"
              :data-node="node.id"
              :data-field="f.fieldName"
            >
              <span class="er-field-icon">{{ f.isPk ? '🔑' : (f.isFk ? '🔗' : '') }}</span>
              <span class="er-field-name">{{ f.name }}</span>
              <span class="er-field-type">{{ f.dataType }}</span>
            </div>
            <div
              v-if="node.fields.length > COLLAPSE_LIMIT"
              class="er-show-more"
              @click="toggleExpand(node.id)"
            >
              {{ expanded.has(node.id)
                ? t('erDiagram.showLess')
                : t('erDiagram.showMore', { count: node.fields.length - COLLAPSE_LIMIT }) }}
            </div>
          </div>
        </div>
      </div>

      <el-empty
        v-if="!loading && !hasData"
        :description="t('erDiagram.empty')"
        class="er-empty"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import { relationTableStructureApi, type RelationTableResponse } from '@/api/relationTable'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

const COLLAPSE_LIMIT = 5

const focusId = computed<number | null>(() => {
  const raw = route.params.id
  if (raw == null || raw === '') return null
  const n = Number(raw)
  return Number.isNaN(n) ? null : n
})

const loading = ref(false)
const tables = ref<RelationTableResponse[]>([])
const hiddenEdgeCount = ref(0)

const canvasRef = ref<HTMLDivElement | null>(null)
const cardEls = new Map<number, HTMLElement>()
const expanded = reactive(new Set<number>())
const positions = reactive<Record<number, { x: number; y: number }>>({})
const canvasSize = reactive({ w: 1200, h: 700 })

const focusTableName = computed(() => {
  const tb = tables.value.find(t => t.id === focusId.value)
  return tb?.displayName || tb?.tableName || ''
})

interface ErField { fieldName: string; name: string; dataType: string; isPk: boolean; isFk: boolean }
interface ErNode { id: number; label: string; fields: ErField[] }
interface ErEdge { sourceId: number; targetId: number; sourceField: string; targetFields: string[] }
interface ErGraph { nodes: ErNode[]; edges: ErEdge[]; hiddenEdgeCount: number }

/**
 * 纯函数：从表列表派生 ER 图数据。
 * - 仅保留 DEPLOYED 表。
 * - 每个 isForeignKey 字段生成一条 源表 -> refTableId 目标表 的有向边（带源字段名用于连线锚点）。
 * - 目标表不在已部署集合中的边被忽略（计入 hiddenEdgeCount）。
 * - focusId 非空时只保留该表及其一跳邻居（引用它的 + 它引用的）。
 */
function buildErGraph(allTables: RelationTableResponse[], fid: number | null): ErGraph {
  const deployed = allTables.filter(t => t.status === 'DEPLOYED')
  const deployedIds = new Set(deployed.map(t => t.id))

  let hidden = 0
  const allEdges: ErEdge[] = []
  for (const tb of deployed) {
    for (const f of tb.fieldDefinitions || []) {
      if (!f.isForeignKey || f.refTableId == null) continue
      if (!deployedIds.has(f.refTableId)) { hidden++; continue }
      allEdges.push({
        sourceId: tb.id,
        targetId: f.refTableId,
        sourceField: f.fieldName,
        targetFields: f.refPrimaryKeyFields || [],
      })
    }
  }

  let keepIds: Set<number>
  let edges: ErEdge[]
  if (fid != null && deployedIds.has(fid)) {
    keepIds = new Set<number>([fid])
    for (const e of allEdges) {
      if (e.sourceId === fid) keepIds.add(e.targetId)
      if (e.targetId === fid) keepIds.add(e.sourceId)
    }
    edges = allEdges.filter(e => e.sourceId === fid || e.targetId === fid)
  } else {
    keepIds = deployedIds
    edges = allEdges
  }

  const nodes: ErNode[] = deployed
    .filter(t => keepIds.has(t.id))
    .map(t => ({
      id: t.id,
      label: t.displayName || t.tableName,
      fields: (t.fieldDefinitions || [])
        .slice()
        .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
        .map(f => ({
          fieldName: f.fieldName,
          name: f.displayName || f.fieldName,
          dataType: f.dataType,
          isPk: !!f.isPrimaryKey,
          isFk: !!f.isForeignKey,
        })),
    }))

  return { nodes, edges, hiddenEdgeCount: hidden }
}

const graph = computed<ErGraph>(() => buildErGraph(tables.value, focusId.value))
const hasData = computed(() => graph.value.nodes.length > 0)

function visibleFields(node: ErNode): ErField[] {
  if (expanded.has(node.id)) return node.fields
  return node.fields.slice(0, COLLAPSE_LIMIT)
}

function setCardRef(id: number, el: unknown) {
  if (el) cardEls.set(id, el as HTMLElement)
  else cardEls.delete(id)
}

function cardStyle(id: number) {
  const p = positions[id] || { x: 0, y: 0 }
  return { transform: `translate(${p.x}px, ${p.y}px)` }
}

function toggleExpand(id: number) {
  if (expanded.has(id)) expanded.delete(id)
  else expanded.add(id)
  scheduleRecalc()
}

// ---------- 初始网格布局 ----------
const CARD_W = 240
const GAP_X = 120
const GAP_Y = 60

function autoLayout() {
  const nodes = graph.value.nodes
  const cols = Math.max(1, Math.min(nodes.length, Math.ceil(Math.sqrt(nodes.length))))
  // 先用估算行高占位，渲染后再用真实高度修正 canvas 尺寸
  let rowMaxBottom = 0
  let x = 40
  let y = 40
  let colHeights: number[] = new Array(cols).fill(40)
  nodes.forEach((n, i) => {
    const col = i % cols
    x = 40 + col * (CARD_W + GAP_X)
    y = colHeights[col]
    positions[n.id] = { x, y }
    const estHeight = 44 + Math.min(n.fields.length, COLLAPSE_LIMIT) * 30 + (n.fields.length > COLLAPSE_LIMIT ? 30 : 0)
    colHeights[col] = y + estHeight + GAP_Y
    rowMaxBottom = Math.max(rowMaxBottom, colHeights[col])
  })
  canvasSize.w = Math.max(1000, 40 + cols * (CARD_W + GAP_X))
  canvasSize.h = Math.max(600, rowMaxBottom)
  scheduleRecalc()
}

// ---------- 拖拽 ----------
let dragState: { id: number; startX: number; startY: number; origX: number; origY: number } | null = null

function startDrag(id: number, ev: MouseEvent) {
  ev.preventDefault()
  const p = positions[id] || { x: 0, y: 0 }
  dragState = { id, startX: ev.clientX, startY: ev.clientY, origX: p.x, origY: p.y }
  window.addEventListener('mousemove', onDrag)
  window.addEventListener('mouseup', endDrag)
}
function onDrag(ev: MouseEvent) {
  if (!dragState) return
  const nx = Math.max(0, dragState.origX + (ev.clientX - dragState.startX))
  const ny = Math.max(0, dragState.origY + (ev.clientY - dragState.startY))
  positions[dragState.id] = { x: nx, y: ny }
  recalcConnectors()
}
function endDrag() {
  dragState = null
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', endDrag)
}

// ---------- 连线计算（基于真实 DOM 位置）----------
interface Connector { key: string; path: string; label: string; labelX: number; labelY: number }
const connectors = ref<Connector[]>([])

function rowAnchorY(nodeId: number, fieldName: string, canvasRect: DOMRect): number | null {
  const card = cardEls.get(nodeId)
  if (!card) return null
  const row = card.querySelector(`.er-field-row[data-node="${nodeId}"][data-field="${CSS.escape(fieldName)}"]`) as HTMLElement | null
  if (!row) return null
  const r = row.getBoundingClientRect()
  return r.top - canvasRect.top + (canvasRef.value?.scrollTop ?? 0) + r.height / 2
}

function cardBox(nodeId: number, canvasRect: DOMRect) {
  const card = cardEls.get(nodeId)
  if (!card) return null
  const r = card.getBoundingClientRect()
  const left = r.left - canvasRect.left + (canvasRef.value?.scrollLeft ?? 0)
  const top = r.top - canvasRect.top + (canvasRef.value?.scrollTop ?? 0)
  return { left, top, right: left + r.width, bottom: top + r.height, midY: top + r.height / 2, width: r.width }
}

function recalcConnectors() {
  const canvas = canvasRef.value
  if (!canvas) { connectors.value = []; return }
  const canvasRect = canvas.getBoundingClientRect()
  const out: Connector[] = []

  graph.value.edges.forEach((e, i) => {
    const srcBox = cardBox(e.sourceId, canvasRect)
    const tgtBox = cardBox(e.targetId, canvasRect)
    if (!srcBox || !tgtBox) return

    // 源锚点：FK 字段行的右/左缘；若该行被折叠，则退化到卡片中部缘。
    const srcY = rowAnchorY(e.sourceId, e.sourceField, canvasRect) ?? srcBox.midY
    const srcOnRight = tgtBox.left >= srcBox.right - 20
    const sx = srcOnRight ? srcBox.right : srcBox.left
    const tx = srcOnRight ? tgtBox.left : tgtBox.right
    const ty = tgtBox.midY

    const dx = Math.max(40, Math.abs(tx - sx) / 2)
    const c1x = srcOnRight ? sx + dx : sx - dx
    const c2x = srcOnRight ? tx - dx : tx + dx
    const path = `M ${sx} ${srcY} C ${c1x} ${srcY}, ${c2x} ${ty}, ${tx} ${ty}`

    out.push({
      key: `${e.sourceId}-${e.targetId}-${e.sourceField}-${i}`,
      path,
      label: e.targetFields.length ? `→ ${e.targetFields.join(', ')}` : '',
      labelX: (sx + tx) / 2,
      labelY: (srcY + ty) / 2 - 6,
    })
  })

  connectors.value = out
}

let recalcRaf = 0
function scheduleRecalc() {
  if (recalcRaf) cancelAnimationFrame(recalcRaf)
  recalcRaf = requestAnimationFrame(() => {
    recalcRaf = 0
    recalcConnectors()
  })
}

function onResize() {
  scheduleRecalc()
}

const fetchTables = async () => {
  loading.value = true
  try {
    tables.value = await relationTableStructureApi.list()
    hiddenEdgeCount.value = graph.value.hiddenEdgeCount
    await nextTick()
    autoLayout()
    await nextTick()
    recalcConnectors()
  } catch {
    tables.value = []
  } finally {
    loading.value = false
  }
}

// 字段展开/折叠会改变卡片高度 -> 重算连线
watch(expanded, () => nextTick(recalcConnectors), { deep: true })

onMounted(() => {
  window.addEventListener('resize', onResize)
  fetchTables()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', endDrag)
  if (recalcRaf) cancelAnimationFrame(recalcRaf)
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}
.er-card {
  min-height: 640px;
}
.er-legend {
  display: flex;
  gap: 20px;
  align-items: center;
  padding: 10px 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}
.legend-hint {
  margin-left: auto;
  color: var(--el-text-color-secondary);
}
.er-canvas {
  position: relative;
  width: 100%;
  height: 660px;
  overflow: auto;
  background:
    linear-gradient(90deg, var(--el-border-color-lighter) 1px, transparent 1px) 0 0 / 24px 24px,
    linear-gradient(var(--el-border-color-lighter) 1px, transparent 1px) 0 0 / 24px 24px;
}
.er-svg {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}
.er-edge {
  fill: none;
  stroke: #409eff;
  stroke-width: 1.6;
}
.er-edge-label {
  fill: #606266;
  font-size: 11px;
  text-anchor: middle;
}
.er-table-card {
  position: absolute;
  top: 0;
  left: 0;
  width: 240px;
  background: #fff;
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  user-select: none;
}
.er-table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  background: var(--el-color-primary);
  color: #fff;
  cursor: move;
}
.er-table-title {
  font-weight: 600;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.er-field-count {
  flex-shrink: 0;
}
.er-field-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  font-size: 13px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.er-field-row.is-pk {
  background: var(--el-color-warning-light-9);
}
.er-field-row.is-fk {
  background: var(--el-color-primary-light-9);
}
.er-field-icon {
  width: 16px;
  flex-shrink: 0;
  text-align: center;
}
.er-field-name {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.er-field-type {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  flex-shrink: 0;
}
.er-show-more {
  padding: 6px 12px;
  font-size: 12px;
  text-align: center;
  color: var(--el-color-primary);
  cursor: pointer;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}
.er-show-more:hover {
  background: var(--el-color-primary-light-9);
}
.er-empty {
  padding: 80px 0;
}
</style>
