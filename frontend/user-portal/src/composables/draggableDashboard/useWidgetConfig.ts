import { ref } from 'vue'
import type { Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DashboardWidget, WidgetType } from './types'

interface WidgetConfigOptions {
  layoutWidgets: Ref<DashboardWidget[]>
  getDefaultLayout: () => DashboardWidget[]
  emitUpdate: () => void
  emitSave: () => void
  t: (key: string) => string
}

// 部件配置：选择器/设置/编辑对话框状态，以及增删改、保存与重置布局
export function useWidgetConfig(options: WidgetConfigOptions) {
  const { layoutWidgets, getDefaultLayout, emitUpdate, emitSave, t } = options

  // 对话框状态
  const showWidgetSelector = ref(false)
  const showLayoutSettings = ref(false)
  const showWidgetEditor = ref(false)
  const editingWidget = ref<DashboardWidget | null>(null)

  // 获取下一个可用行
  const getNextAvailableRow = () => {
    if (layoutWidgets.value.length === 0) return 1
    const maxRow = Math.max(...layoutWidgets.value.map(w => w.row + w.rowSpan))
    return maxRow
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
    emitUpdate()
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
      emitUpdate()
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
        emitUpdate()
      }
    } catch {
      // 取消删除
    }
  }

  // 切换全屏
  const toggleFullscreen = (_widget: DashboardWidget) => {
    // 实现全屏逻辑
  }

  // 保存布局
  const saveLayout = () => {
    emitSave()
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
      emitUpdate()
    } catch {
      // 取消重置
    }
  }

  // 组件刷新
  const handleWidgetRefresh = (_widget: DashboardWidget) => {
    // 处理组件刷新
  }

  return {
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
  }
}
