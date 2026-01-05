<template>
  <div class="draggable-dashboard">
    <div class="dashboard-toolbar" v-if="editable">
      <el-button type="primary" :icon="Plus" @click="showWidgetSelector = true">
        {{ $t('dashboard.addWidget') }}
      </el-button>
      <el-button :icon="Setting" @click="showLayoutSettings = true">
        {{ $t('dashboard.layoutSettings') }}
      </el-button>
      <el-button type="success" :icon="Check" @click="saveLayout">
        {{ $t('common.save') }}
      </el-button>
      <el-button @click="resetLayout">
        {{ $t('common.reset') }}
      </el-button>
    </div>

    <div 
      class="dashboard-grid"
      ref="gridRef"
      :style="{ gridTemplateColumns: `repeat(${columns}, 1fr)` }"
    >
      <div
        v-for="widget in layoutWidgets"
        :key="widget.id"
        class="grid-item"
        :class="{ 
          'is-dragging': draggingId === widget.id,
          'is-resizing': resizingId === widget.id
        }"
        :style="getWidgetStyle(widget)"
        :draggable="editable"
        @dragstart="handleDragStart($event, widget)"
        @dragend="handleDragEnd"
        @dragover.prevent="handleDragOver($event, widget)"
        @drop="handleDrop($event, widget)"
      >
        <div class="widget-header" v-if="editable">
          <span class="widget-title">{{ widget.title }}</span>
          <div class="widget-actions">
            <el-button
              link
              :icon="FullScreen"
              @click="toggleFullscreen(widget)"
            />
            <el-button
              link
              :icon="Setting"
              @click="editWidget(widget)"
            />
            <el-button
              link
              type="danger"
              :icon="Delete"
              @click="removeWidget(widget)"
            />
          </div>
        </div>
        <div class="widget-content">
          <component
            :is="getWidgetComponent(widget.type)"
            v-bind="widget.props"
            @refresh="handleWidgetRefresh(widget)"
          />
        </div>
        <div
          v-if="editable"
          class="resize-handle"
          @mousedown="startResize($event, widget)"
        ></div>
      </div>
    </div>

    <!-- 组件选择器 -->
    <el-dialog
      v-model="showWidgetSelector"
      :title="$t('dashboard.selectWidget')"
      width="600px"
    >
      <div class="widget-selector">
        <div
          v-for="type in availableWidgets"
          :key="type.type"
          class="widget-option"
          @click="addWidget(type)"
        >
          <el-icon :size="32"><component :is="type.icon" /></el-icon>
          <span class="widget-name">{{ type.name }}</span>
          <span class="widget-desc">{{ type.description }}</span>
        </div>
      </div>
    </el-dialog>

    <!-- 布局设置 -->
    <el-dialog
      v-model="showLayoutSettings"
      :title="$t('dashboard.layoutSettings')"
      width="400px"
    >
      <el-form label-width="100px">
        <el-form-item :label="$t('dashboard.columns')">
          <el-slider v-model="columns" :min="6" :max="24" :step="1" show-input />
        </el-form-item>
        <el-form-item :label="$t('dashboard.rowHeight')">
          <el-slider v-model="rowHeight" :min="50" :max="200" :step="10" show-input />
        </el-form-item>
        <el-form-item :label="$t('dashboard.gap')">
          <el-slider v-model="gap" :min="5" :max="30" :step="5" show-input />
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- 组件编辑 -->
    <el-dialog
      v-model="showWidgetEditor"
      :title="$t('dashboard.editWidget')"
      width="500px"
    >
      <el-form v-if="editingWidget" label-width="100px">
        <el-form-item :label="$t('dashboard.widgetTitle')">
          <el-input v-model="editingWidget.title" />
        </el-form-item>
        <el-form-item :label="$t('dashboard.widgetWidth')">
          <el-slider v-model="editingWidget.colSpan" :min="1" :max="columns" />
        </el-form-item>
        <el-form-item :label="$t('dashboard.widgetHeight')">
          <el-slider v-model="editingWidget.rowSpan" :min="1" :max="6" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showWidgetEditor = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveWidgetEdit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, markRaw, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Setting,
  Check,
  Delete,
  FullScreen,
  DataLine,
  List,
  PieChart,
  Bell,
  Calendar,
  Timer
} from '@element-plus/icons-vue'

// 导入仪表盘组件
import TaskOverviewWidget from './widgets/TaskOverviewWidget.vue'
import ProcessStatsWidget from './widgets/ProcessStatsWidget.vue'
import PerformanceWidget from './widgets/PerformanceWidget.vue'
import QuickActionsWidget from './widgets/QuickActionsWidget.vue'
import NotificationsWidget from './widgets/NotificationsWidget.vue'
import CalendarWidget from './widgets/CalendarWidget.vue'

export interface DashboardWidget {
  id: string
  type: string
  title: string
  col: number
  row: number
  colSpan: number
  rowSpan: number
  props?: Record<string, any>
}

export interface WidgetType {
  type: string
  name: string
  description: string
  icon: any
  defaultColSpan: number
  defaultRowSpan: number
  component: any
}

interface Props {
  layout?: DashboardWidget[]
  editable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  layout: () => [],
  editable: false
})

const emit = defineEmits<{
  (e: 'update:layout', layout: DashboardWidget[]): void
  (e: 'save', layout: DashboardWidget[]): void
}>()

const { t } = useI18n()

// 网格配置
const columns = ref(12)
const rowHeight = ref(100)
const gap = ref(15)

// 布局数据
const layoutWidgets = ref<DashboardWidget[]>([])

// 拖拽状态
const draggingId = ref<string | null>(null)
const dragOverId = ref<string | null>(null)
const resizingId = ref<string | null>(null)

// 对话框状态
const showWidgetSelector = ref(false)
const showLayoutSettings = ref(false)
const showWidgetEditor = ref(false)
const editingWidget = ref<DashboardWidget | null>(null)

// 可用组件类型
const availableWidgets: WidgetType[] = [
  {
    type: 'taskOverview',
    name: '任务概览',
    description: '显示待办任务统计',
    icon: markRaw(List),
    defaultColSpan: 4,
    defaultRowSpan: 2,
    component: markRaw(TaskOverviewWidget)
  },
  {
    type: 'processStats',
    name: '流程统计',
    description: '显示流程处理统计',
    icon: markRaw(PieChart),
    defaultColSpan: 4,
    defaultRowSpan: 2,
    component: markRaw(ProcessStatsWidget)
  },
  {
    type: 'performance',
    name: '个人绩效',
    description: '显示个人绩效指标',
    icon: markRaw(DataLine),
    defaultColSpan: 4,
    defaultRowSpan: 2,
    component: markRaw(PerformanceWidget)
  },
  {
    type: 'quickActions',
    name: '快捷操作',
    description: '常用操作入口',
    icon: markRaw(Timer),
    defaultColSpan: 3,
    defaultRowSpan: 2,
    component: markRaw(QuickActionsWidget)
  },
  {
    type: 'notifications',
    name: '通知中心',
    description: '最新通知消息',
    icon: markRaw(Bell),
    defaultColSpan: 3,
    defaultRowSpan: 2,
    component: markRaw(NotificationsWidget)
  },
  {
    type: 'calendar',
    name: '日程安排',
    description: '任务日历视图',
    icon: markRaw(Calendar),
    defaultColSpan: 6,
    defaultRowSpan: 3,
    component: markRaw(CalendarWidget)
  }
]

// 组件映射
const widgetComponents = new Map<string, any>([
  ['taskOverview', markRaw(TaskOverviewWidget)],
  ['processStats', markRaw(ProcessStatsWidget)],
  ['performance', markRaw(PerformanceWidget)],
  ['quickActions', markRaw(QuickActionsWidget)],
  ['notifications', markRaw(NotificationsWidget)],
  ['calendar', markRaw(CalendarWidget)]
])

// 获取组件
const getWidgetComponent = (type: string) => {
  return widgetComponents.get(type) || 'div'
}

// 获取组件样式
const getWidgetStyle = (widget: DashboardWidget) => {
  return {
    gridColumn: `${widget.col} / span ${widget.colSpan}`,
    gridRow: `${widget.row} / span ${widget.rowSpan}`,
    minHeight: `${widget.rowSpan * rowHeight.value}px`
  }
}

// 拖拽开始
const handleDragStart = (e: DragEvent, widget: DashboardWidget) => {
  draggingId.value = widget.id
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', widget.id)
  }
}

// 拖拽结束
const handleDragEnd = () => {
  draggingId.value = null
  dragOverId.value = null
}

// 拖拽经过
const handleDragOver = (e: DragEvent, widget: DashboardWidget) => {
  if (draggingId.value && draggingId.value !== widget.id) {
    dragOverId.value = widget.id
  }
}

// 放置
const handleDrop = (e: DragEvent, targetWidget: DashboardWidget) => {
  if (!draggingId.value || draggingId.value === targetWidget.id) return

  const sourceIndex = layoutWidgets.value.findIndex(w => w.id === draggingId.value)
  const targetIndex = layoutWidgets.value.findIndex(w => w.id === targetWidget.id)

  if (sourceIndex !== -1 && targetIndex !== -1) {
    // 交换位置
    const sourceWidget = layoutWidgets.value[sourceIndex]
    const tempCol = sourceWidget.col
    const tempRow = sourceWidget.row

    sourceWidget.col = targetWidget.col
    sourceWidget.row = targetWidget.row
    targetWidget.col = tempCol
    targetWidget.row = tempRow

    emit('update:layout', [...layoutWidgets.value])
  }

  draggingId.value = null
  dragOverId.value = null
}

// 开始调整大小
const startResize = (e: MouseEvent, widget: DashboardWidget) => {
  e.preventDefault()
  resizingId.value = widget.id

  const startX = e.clientX
  const startY = e.clientY
  const startColSpan = widget.colSpan
  const startRowSpan = widget.rowSpan

  const handleMouseMove = (moveEvent: MouseEvent) => {
    const deltaX = moveEvent.clientX - startX
    const deltaY = moveEvent.clientY - startY

    const colDelta = Math.round(deltaX / (100 + gap.value))
    const rowDelta = Math.round(deltaY / (rowHeight.value + gap.value))

    widget.colSpan = Math.max(1, Math.min(columns.value - widget.col + 1, startColSpan + colDelta))
    widget.rowSpan = Math.max(1, Math.min(6, startRowSpan + rowDelta))
  }

  const handleMouseUp = () => {
    resizingId.value = null
    document.removeEventListener('mousemove', handleMouseMove)
    document.removeEventListener('mouseup', handleMouseUp)
    emit('update:layout', [...layoutWidgets.value])
  }

  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
}

// 添加组件
const addWidget = (type: WidgetType) => {
  const newWidget: DashboardWidget = {
    id: `widget_${Date.now()}`,
    type: type.type,
    title: type.name,
    col: 1,
    row: getNextAvailableRow(),
    colSpan: type.defaultColSpan,
    rowSpan: type.defaultRowSpan
  }

  layoutWidgets.value.push(newWidget)
  showWidgetSelector.value = false
  emit('update:layout', [...layoutWidgets.value])
}

// 获取下一个可用行
const getNextAvailableRow = () => {
  if (layoutWidgets.value.length === 0) return 1
  const maxRow = Math.max(...layoutWidgets.value.map(w => w.row + w.rowSpan))
  return maxRow
}

// 编辑组件
const editWidget = (widget: DashboardWidget) => {
  editingWidget.value = { ...widget }
  showWidgetEditor.value = true
}

// 保存组件编辑
const saveWidgetEdit = () => {
  if (!editingWidget.value) return

  const index = layoutWidgets.value.findIndex(w => w.id === editingWidget.value!.id)
  if (index !== -1) {
    layoutWidgets.value[index] = { ...editingWidget.value }
    emit('update:layout', [...layoutWidgets.value])
  }

  showWidgetEditor.value = false
  editingWidget.value = null
}

// 移除组件
const removeWidget = async (widget: DashboardWidget) => {
  try {
    await ElMessageBox.confirm(
      t('dashboard.confirmRemoveWidget'),
      t('common.warning'),
      { type: 'warning' }
    )

    const index = layoutWidgets.value.findIndex(w => w.id === widget.id)
    if (index !== -1) {
      layoutWidgets.value.splice(index, 1)
      emit('update:layout', [...layoutWidgets.value])
    }
  } catch {
    // 取消删除
  }
}

// 切换全屏
const toggleFullscreen = (widget: DashboardWidget) => {
  // 实现全屏逻辑
}

// 保存布局
const saveLayout = () => {
  emit('save', [...layoutWidgets.value])
  ElMessage.success(t('common.success'))
}

// 重置布局
const resetLayout = async () => {
  try {
    await ElMessageBox.confirm(
      t('dashboard.confirmResetLayout'),
      t('common.warning'),
      { type: 'warning' }
    )

    layoutWidgets.value = getDefaultLayout()
    emit('update:layout', [...layoutWidgets.value])
  } catch {
    // 取消重置
  }
}

// 获取默认布局
const getDefaultLayout = (): DashboardWidget[] => {
  return [
    { id: 'w1', type: 'taskOverview', title: '任务概览', col: 1, row: 1, colSpan: 4, rowSpan: 2 },
    { id: 'w2', type: 'processStats', title: '流程统计', col: 5, row: 1, colSpan: 4, rowSpan: 2 },
    { id: 'w3', type: 'performance', title: '个人绩效', col: 9, row: 1, colSpan: 4, rowSpan: 2 },
    { id: 'w4', type: 'quickActions', title: '快捷操作', col: 1, row: 3, colSpan: 3, rowSpan: 2 },
    { id: 'w5', type: 'notifications', title: '通知中心', col: 4, row: 3, colSpan: 3, rowSpan: 2 },
    { id: 'w6', type: 'calendar', title: '日程安排', col: 7, row: 3, colSpan: 6, rowSpan: 3 }
  ]
}

// 组件刷新
const handleWidgetRefresh = (widget: DashboardWidget) => {
  // 处理组件刷新
}

// 初始化
onMounted(() => {
  if (props.layout && props.layout.length > 0) {
    layoutWidgets.value = [...props.layout]
  } else {
    layoutWidgets.value = getDefaultLayout()
  }
})

defineExpose({
  saveLayout,
  resetLayout
})
</script>

<style scoped lang="scss">
.draggable-dashboard {
  .dashboard-toolbar {
    display: flex;
    gap: 10px;
    margin-bottom: 16px;
    padding: 12px;
    background: #f5f7fa;
    border-radius: 4px;
  }

  .dashboard-grid {
    display: grid;
    gap: v-bind('gap + "px"');
    min-height: 500px;
  }

  .grid-item {
    position: relative;
    background: white;
    border-radius: 8px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    overflow: hidden;
    transition: box-shadow 0.3s, transform 0.2s;

    &:hover {
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
    }

    &.is-dragging {
      opacity: 0.5;
      transform: scale(0.98);
    }

    &.is-resizing {
      box-shadow: 0 4px 20px rgba(219, 0, 17, 0.2);
    }

    .widget-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 16px;
      background: #fafafa;
      border-bottom: 1px solid #e4e7ed;
      cursor: move;

      .widget-title {
        font-weight: 600;
        color: #303133;
      }

      .widget-actions {
        display: flex;
        gap: 4px;
      }
    }

    .widget-content {
      padding: 16px;
      height: calc(100% - 50px);
      overflow: auto;
    }

    .resize-handle {
      position: absolute;
      right: 0;
      bottom: 0;
      width: 20px;
      height: 20px;
      cursor: se-resize;

      &::after {
        content: '';
        position: absolute;
        right: 4px;
        bottom: 4px;
        width: 8px;
        height: 8px;
        border-right: 2px solid #c0c4cc;
        border-bottom: 2px solid #c0c4cc;
      }
    }
  }

  .widget-selector {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;

    .widget-option {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 20px;
      border: 1px solid #e4e7ed;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.3s;

      &:hover {
        border-color: #DB0011;
        background: #fff5f5;
      }

      .el-icon {
        color: #DB0011;
        margin-bottom: 10px;
      }

      .widget-name {
        font-weight: 600;
        color: #303133;
        margin-bottom: 5px;
      }

      .widget-desc {
        font-size: 12px;
        color: #909399;
        text-align: center;
      }
    }
  }
}
</style>
