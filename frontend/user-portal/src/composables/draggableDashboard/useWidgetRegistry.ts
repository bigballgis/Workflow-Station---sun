import { markRaw } from 'vue'
import {
  DataLine,
  List,
  PieChart,
  Bell,
  Calendar,
  Timer
} from '@element-plus/icons-vue'

// 导入仪表盘组件
import TaskOverviewWidget from '@/components/widgets/TaskOverviewWidget.vue'
import ProcessStatsWidget from '@/components/widgets/ProcessStatsWidget.vue'
import PerformanceWidget from '@/components/widgets/PerformanceWidget.vue'
import QuickActionsWidget from '@/components/widgets/QuickActionsWidget.vue'
import NotificationsWidget from '@/components/widgets/NotificationsWidget.vue'
import CalendarWidget from '@/components/widgets/CalendarWidget.vue'

import type { DashboardWidget, WidgetType } from './types'

// 部件注册表：可用部件类型、类型→组件映射、组件解析、默认布局
export function useWidgetRegistry(t: (key: string) => string) {
  // 可用组件类型
  const availableWidgets: WidgetType[] = [
    {
      type: 'taskOverview',
      name: t('widget.taskOverview'),
      description: t('widget.taskOverviewDesc'),
      icon: markRaw(List),
      defaultColSpan: 4,
      defaultRowSpan: 2,
      component: markRaw(TaskOverviewWidget)
    },
    {
      type: 'processStats',
      name: t('widget.processStats'),
      description: t('widget.processStatsDesc'),
      icon: markRaw(PieChart),
      defaultColSpan: 4,
      defaultRowSpan: 2,
      component: markRaw(ProcessStatsWidget)
    },
    {
      type: 'performance',
      name: t('widget.performance'),
      description: t('widget.performanceDesc'),
      icon: markRaw(DataLine),
      defaultColSpan: 4,
      defaultRowSpan: 2,
      component: markRaw(PerformanceWidget)
    },
    {
      type: 'quickActions',
      name: t('widget.quickActions'),
      description: t('widget.quickActionsDesc'),
      icon: markRaw(Timer),
      defaultColSpan: 3,
      defaultRowSpan: 2,
      component: markRaw(QuickActionsWidget)
    },
    {
      type: 'notifications',
      name: t('widget.notifications'),
      description: t('widget.notificationsDesc'),
      icon: markRaw(Bell),
      defaultColSpan: 3,
      defaultRowSpan: 2,
      component: markRaw(NotificationsWidget)
    },
    {
      type: 'calendar',
      name: t('widget.calendar'),
      description: t('widget.calendarDesc'),
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

  // 获取默认布局
  const getDefaultLayout = (): DashboardWidget[] => {
    return [
      { id: 'w1', type: 'taskOverview', title: t('widget.taskOverview'), col: 1, row: 1, colSpan: 4, rowSpan: 2 },
      { id: 'w2', type: 'processStats', title: t('widget.processStats'), col: 5, row: 1, colSpan: 4, rowSpan: 2 },
      { id: 'w3', type: 'performance', title: t('widget.performance'), col: 9, row: 1, colSpan: 4, rowSpan: 2 },
      { id: 'w4', type: 'quickActions', title: t('widget.quickActions'), col: 1, row: 3, colSpan: 3, rowSpan: 2 },
      { id: 'w5', type: 'notifications', title: t('widget.notifications'), col: 4, row: 3, colSpan: 3, rowSpan: 2 },
      { id: 'w6', type: 'calendar', title: t('widget.calendar'), col: 7, row: 3, colSpan: 6, rowSpan: 3 }
    ]
  }

  return {
    availableWidgets,
    getWidgetComponent,
    getDefaultLayout
  }
}
