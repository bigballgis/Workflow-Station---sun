<template>
  <div class="draggable-dashboard">
    <div
      v-if="editable"
      class="dashboard-toolbar"
    >
      <el-button
        type="primary"
        :icon="Plus"
        @click="showWidgetSelector = true"
      >
        {{ $t('dashboard.addWidget') }}
      </el-button>
      <el-button
        :icon="Setting"
        @click="showLayoutSettings = true"
      >
        {{ $t('dashboard.layoutSettings') }}
      </el-button>
      <el-button
        type="success"
        :icon="Check"
        @click="saveLayout"
      >
        {{ $t('common.save') }}
      </el-button>
      <el-button @click="resetLayout">
        {{ $t('common.reset') }}
      </el-button>
    </div>

    <div 
      ref="gridRef"
      class="dashboard-grid"
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
        <div
          v-if="editable"
          class="widget-header"
        >
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
        />
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
          <el-icon :size="32">
            <component :is="type.icon" />
          </el-icon>
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
      <el-form
        label-width="auto"
        label-position="left"
      >
        <el-form-item :label="$t('dashboard.columns')">
          <el-slider
            v-model="columns"
            :min="6"
            :max="24"
            :step="1"
            show-input
          />
        </el-form-item>
        <el-form-item :label="$t('dashboard.rowHeight')">
          <el-slider
            v-model="rowHeight"
            :min="50"
            :max="200"
            :step="10"
            show-input
          />
        </el-form-item>
        <el-form-item :label="$t('dashboard.gap')">
          <el-slider
            v-model="gap"
            :min="5"
            :max="30"
            :step="5"
            show-input
          />
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- 组件编辑 -->
    <el-dialog
      v-model="showWidgetEditor"
      :title="$t('dashboard.editWidget')"
      width="500px"
    >
      <el-form
        v-if="editingWidget"
        label-width="auto"
        label-position="left"
      >
        <el-form-item :label="$t('dashboard.widgetTitle')">
          <el-input v-model="editingWidget.title" />
        </el-form-item>
        <el-form-item :label="$t('dashboard.widgetWidth')">
          <el-slider
            v-model="editingWidget.colSpan"
            :min="1"
            :max="columns"
          />
        </el-form-item>
        <el-form-item :label="$t('dashboard.widgetHeight')">
          <el-slider
            v-model="editingWidget.rowSpan"
            :min="1"
            :max="6"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showWidgetEditor = false">
          {{ $t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          @click="saveWidgetEdit"
        >
          {{ $t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Plus,
  Setting,
  Check,
  Delete,
  FullScreen
} from '@element-plus/icons-vue'

import { useWidgetRegistry } from '@/composables/draggableDashboard/useWidgetRegistry'
import { useDashboardGrid } from '@/composables/draggableDashboard/useDashboardGrid'
import { useWidgetDragResize } from '@/composables/draggableDashboard/useWidgetDragResize'
import { useWidgetConfig } from '@/composables/draggableDashboard/useWidgetConfig'
import type { DashboardWidget } from '@/composables/draggableDashboard/types'

// 保留原 SFC 对外导出的公共类型
export type { DashboardWidget, WidgetType } from '@/composables/draggableDashboard/types'

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

// 布局数据
const layoutWidgets = ref<DashboardWidget[]>([])

// emit 包装：统一布局快照传递，并用闭包破除组合式之间的相互依赖
const emitUpdate = () => emit('update:layout', [...layoutWidgets.value])
const emitSave = () => emit('save', [...layoutWidgets.value])

// 部件注册表（可用类型 / 组件解析 / 默认布局）
const { availableWidgets, getWidgetComponent, getDefaultLayout } = useWidgetRegistry(t)

// 网格配置与样式
const { columns, rowHeight, gap, getWidgetStyle } = useDashboardGrid()

// 拖拽与缩放
const {
  draggingId,
  resizingId,
  handleDragStart,
  handleDragEnd,
  handleDragOver,
  handleDrop,
  startResize
} = useWidgetDragResize({ layoutWidgets, columns, rowHeight, gap, emitUpdate })

// 部件配置：对话框状态与增删改 / 保存 / 重置
const {
  showWidgetSelector,
  showLayoutSettings,
  showWidgetEditor,
  editingWidget,
  addWidget,
  editWidget,
  saveWidgetEdit,
  removeWidget,
  toggleFullscreen,
  saveLayout,
  resetLayout,
  handleWidgetRefresh
} = useWidgetConfig({ layoutWidgets, getDefaultLayout, emitUpdate, emitSave, t })

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
