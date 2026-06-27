<template>
  <div class="relation-diagram-editor">
    <el-alert
      type="info"
      :closable="false"
      class="diagram-hint"
    >
      {{ t('erDiagram.diagramHint') }}
    </el-alert>

    <div
      v-if="!tables.length"
      class="diagram-empty"
    >
      {{ t('erDiagram.empty') }}
    </div>

    <div
      v-else
      class="diagram-canvas"
    >
      <VueFlow
        :nodes="nodes"
        :edges="edges"
        :node-types="{}"
        :default-viewport="{ zoom: 0.9 }"
        :min-zoom="0.2"
        :max-zoom="2"
        :connection-mode="ConnectionMode.Loose"
        :default-edge-options="defaultEdgeOptions"
        :is-valid-connection="isValidConnection"
        fit-view-on-init
        @connect="handleConnect"
        @edge-click="handleEdgeClick"
        @node-drag-stop="handleNodeDragStop"
      >
        <Background
          :gap="20"
          pattern-color="#dcdfe6"
        />
        <Controls :show-interactive="false" />

        <template #node-erTable="nodeProps">
          <div class="er-node">
            <div class="er-node-header">
              <span class="er-node-title">{{ nodeProps.data.displayName }}</span>
              <span class="er-node-subtitle">{{ nodeProps.data.tableName }}</span>
            </div>
            <div class="er-node-body">
              <div
                v-for="field in nodeProps.data.fields"
                :key="field.fieldName"
                class="er-field-row"
                :class="{ 'is-pk': field.isPrimaryKey, 'is-fk': field.isForeignKey }"
              >
                <Handle
                  :id="`${field.fieldName}::l`"
                  type="source"
                  :position="Position.Left"
                  class="er-handle"
                  :class="{ 'er-handle-pk': field.isPrimaryKey }"
                />
                <span class="er-field-badges">
                  <el-tag
                    v-if="field.isPrimaryKey"
                    size="small"
                    type="warning"
                    effect="dark"
                    disable-transitions
                  >{{ t('erDiagram.legendPk') }}</el-tag>
                  <el-tag
                    v-if="field.isForeignKey"
                    size="small"
                    type="success"
                    disable-transitions
                  >{{ t('erDiagram.legendFk') }}</el-tag>
                </span>
                <span class="er-field-name">{{ field.displayName || field.fieldName }}</span>
                <span class="er-field-type">{{ field.dataType }}</span>
                <Handle
                  :id="`${field.fieldName}::r`"
                  type="source"
                  :position="Position.Right"
                  class="er-handle"
                  :class="{ 'er-handle-pk': field.isPrimaryKey }"
                />
              </div>
            </div>
          </div>
        </template>
      </VueFlow>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  VueFlow,
  Handle,
  Position,
  MarkerType,
  ConnectionMode,
  useVueFlow,
  type Connection,
  type Edge,
  type Node,
} from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import type { RelationTableResponse, FieldDefinitionResponse } from '@/api/relationTable'

import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'

/** Relation row shape shared with the host page. */
export interface RelationRow {
  sourceTableId: number | null
  sourceFieldName: string
  relationType: string
  targetTableId: number | null
  targetFieldName: string
}

const props = defineProps<{
  tables: RelationTableResponse[]
  modelValue: RelationRow[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: RelationRow[]): void
}>()

const { t } = useI18n()
const { fitView } = useVueFlow()

const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])

// Keep dragged positions so node rebuilds don't reset layout.
const positionMap = ref<Record<string, { x: number; y: number }>>({})

const NODE_WIDTH = 240
const COL_GAP = 320
const ROW_GAP = 280
const PER_ROW = 3

function gridPosition(index: number): { x: number; y: number } {
  const col = index % PER_ROW
  const row = Math.floor(index / PER_ROW)
  return { x: col * COL_GAP, y: row * ROW_GAP }
}

function buildNodes() {
  nodes.value = props.tables.map((table, index) => {
    const id = String(table.id)
    const position = positionMap.value[id] || gridPosition(index)
    positionMap.value[id] = position
    return {
      id,
      type: 'erTable',
      position,
      data: {
        tableName: table.tableName,
        displayName: table.displayName || table.tableName,
        fields: (table.fieldDefinitions || []).map((f: FieldDefinitionResponse) => ({
          fieldName: f.fieldName,
          displayName: f.displayName,
          dataType: f.dataType,
          isPrimaryKey: !!f.isPrimaryKey,
          isForeignKey: !!f.isForeignKey,
        })),
      },
      style: { width: `${NODE_WIDTH}px` },
    } as Node
  })
}

function relationTypeShortLabel(relationType: string): string {
  switch (relationType) {
    case 'ONE_TO_ONE':
      return '1:1'
    case 'MANY_TO_MANY':
      return 'N:N'
    default:
      return '1:N'
  }
}

function fieldExists(tableId: number | null, fieldName: string): boolean {
  if (tableId == null) return false
  const table = props.tables.find(tb => tb.id === tableId)
  return !!table?.fieldDefinitions?.some(f => f.fieldName === fieldName)
}

const defaultEdgeOptions = {
  type: 'smoothstep',
  markerEnd: MarkerType.ArrowClosed,
}

/** Each field exposes two anchors: `<fieldName>::l` and `<fieldName>::r`. */
function handleId(fieldName: string, side: 'l' | 'r'): string {
  return `${fieldName}::${side}`
}

function fieldFromHandle(handle: string | null | undefined): string {
  if (!handle) return ''
  const idx = handle.lastIndexOf('::')
  return idx >= 0 ? handle.slice(0, idx) : handle
}

function nodeCenterX(tableId: number | null): number {
  if (tableId == null) return 0
  const pos = positionMap.value[String(tableId)]
  return pos ? pos.x + NODE_WIDTH / 2 : 0
}

/** Pick the closest sides so edges enter/exit toward each other instead of wrapping around. */
function chooseSides(fkTableId: number | null, pkTableId: number | null): { src: 'l' | 'r'; tgt: 'l' | 'r' } {
  return nodeCenterX(fkTableId) >= nodeCenterX(pkTableId)
    ? { src: 'l', tgt: 'r' }
    : { src: 'r', tgt: 'l' }
}

function buildEdges() {
  edges.value = props.modelValue
    .filter(
      r =>
        r.sourceTableId != null &&
        r.targetTableId != null &&
        r.sourceFieldName &&
        r.targetFieldName &&
        fieldExists(r.sourceTableId, r.sourceFieldName) &&
        fieldExists(r.targetTableId, r.targetFieldName),
    )
    .map(r => {
      const sides = chooseSides(r.sourceTableId, r.targetTableId)
      return {
        id: `rel-${r.sourceTableId}-${r.sourceFieldName}`,
        source: String(r.sourceTableId),
        sourceHandle: handleId(r.sourceFieldName, sides.src),
        target: String(r.targetTableId),
        targetHandle: handleId(r.targetFieldName, sides.tgt),
        type: 'smoothstep',
        label: relationTypeShortLabel(r.relationType),
        markerEnd: MarkerType.ArrowClosed,
        data: { relation: r },
        style: { strokeWidth: 1.5, stroke: '#409eff' },
        labelBgStyle: { fill: '#ecf5ff' },
      } as Edge
    })
}

function isPkField(tableId: number, fieldName: string): boolean {
  const table = props.tables.find(tb => tb.id === tableId)
  return !!table?.fieldDefinitions?.some(f => f.fieldName === fieldName && f.isPrimaryKey)
}

function isValidConnection(connection: Connection): boolean {
  if (connection.source === connection.target) return false
  // A foreign key must reference a primary key on one of the two endpoints.
  const aTable = Number(connection.source)
  const bTable = Number(connection.target)
  const aField = fieldFromHandle(connection.sourceHandle)
  const bField = fieldFromHandle(connection.targetHandle)
  return isPkField(aTable, aField) || isPkField(bTable, bField)
}

function handleConnect(connection: Connection) {
  const aTableId = Number(connection.source)
  const bTableId = Number(connection.target)
  const aField = fieldFromHandle(connection.sourceHandle)
  const bField = fieldFromHandle(connection.targetHandle)

  if (!aField || !bField) return
  if (aTableId === bTableId) {
    ElMessage.warning(t('erDiagram.selfRefError'))
    return
  }

  // Orient FK -> PK by primary-key membership, regardless of drag direction (loose mode).
  let sourceTableId: number
  let sourceFieldName: string
  let targetTableId: number
  let targetFieldName: string
  if (isPkField(bTableId, bField)) {
    sourceTableId = aTableId
    sourceFieldName = aField
    targetTableId = bTableId
    targetFieldName = bField
  } else if (isPkField(aTableId, aField)) {
    sourceTableId = bTableId
    sourceFieldName = bField
    targetTableId = aTableId
    targetFieldName = aField
  } else {
    ElMessage.warning(t('erDiagram.targetNotPkError'))
    return
  }

  // One FK field references exactly one target: replace any existing relation on this source field.
  const existing = props.modelValue.find(
    r => r.sourceTableId === sourceTableId && r.sourceFieldName === sourceFieldName,
  )
  const next = props.modelValue.filter(
    r => !(r.sourceTableId === sourceTableId && r.sourceFieldName === sourceFieldName),
  )
  next.push({
    sourceTableId,
    sourceFieldName,
    targetTableId,
    targetFieldName,
    relationType: existing?.relationType || 'ONE_TO_MANY',
  })
  emit('update:modelValue', next)
}

async function handleEdgeClick({ edge }: { edge: Edge }) {
  const relation = (edge.data as { relation?: RelationRow } | undefined)?.relation
  if (!relation) return
  try {
    await ElMessageBox.confirm(
      t('erDiagram.deleteConfirm', { field: relation.sourceFieldName }),
      t('erDiagram.title'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  const next = props.modelValue.filter(
    r =>
      !(
        r.sourceTableId === relation.sourceTableId &&
        r.sourceFieldName === relation.sourceFieldName &&
        r.targetTableId === relation.targetTableId &&
        r.targetFieldName === relation.targetFieldName
      ),
  )
  emit('update:modelValue', next)
}

function handleNodeDragStop({ node }: { node: Node }) {
  positionMap.value[node.id] = { x: node.position.x, y: node.position.y }
  // Re-pick closest sides now that relative positions changed.
  buildEdges()
}

watch(
  () => props.tables,
  () => {
    buildNodes()
    buildEdges()
    nextTick(() => fitView({ padding: 0.2 }))
  },
  { immediate: true, deep: true },
)

watch(
  () => props.modelValue,
  () => buildEdges(),
  { deep: true },
)
</script>

<style lang="scss" scoped>
.relation-diagram-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.diagram-hint {
  margin-bottom: 12px;
}

.diagram-empty {
  height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  background: #f5f7fa;
  border-radius: 4px;
}

.diagram-canvas {
  position: relative;
  height: 620px;
  width: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
  overflow: hidden;
}

.er-node {
  border: 1px solid #c0c4cc;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  font-size: 12px;
}

.er-node-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 10px;
  background: #409eff;
  color: #ffffff;
}

.er-node-title {
  font-weight: 600;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.er-node-subtitle {
  font-size: 11px;
  opacity: 0.85;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.er-node-body {
  display: flex;
  flex-direction: column;
}

.er-field-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-top: 1px solid #f0f2f5;
  white-space: nowrap;

  &.is-pk {
    background: #fdf6ec;
  }
}

.er-field-badges {
  display: inline-flex;
  gap: 2px;

  :deep(.el-tag) {
    height: 16px;
    padding: 0 4px;
    line-height: 16px;
  }
}

.er-field-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #303133;
}

.er-field-type {
  color: #909399;
  font-size: 11px;
}

.er-handle {
  width: 7px;
  height: 7px;
  background: #c0c4cc;
  border: 1px solid #ffffff;
  opacity: 0.55;
  transition: opacity 0.15s, background 0.15s, transform 0.15s;
}

.er-handle-pk {
  background: #e6a23c;
}

.er-field-row:hover .er-handle {
  opacity: 1;
  transform: scale(1.25);
}

.er-handle:hover {
  opacity: 1;
  background: #409eff;
  transform: scale(1.4);
}
</style>
