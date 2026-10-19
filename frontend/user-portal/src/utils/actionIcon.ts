import { markRaw, type Component } from 'vue'
import { Bell, Check, CircleCheck, CircleClose, Close, Files, Switch, User, Warning } from '@element-plus/icons-vue'

/** Icon names an Action can carry from the Developer Workstation Action Designer. */
const ACTION_ICONS: Record<string, Component> = {
  check: markRaw(Check),
  'check-circle': markRaw(CircleCheck),
  'times-circle': markRaw(CircleClose),
  close: markRaw(Close),
  'file-alt': markRaw(Files),
  files: markRaw(Files),
  warning: markRaw(Warning),
  bell: markRaw(Bell),
  user: markRaw(User),
  switch: markRaw(Switch),
}

/**
 * Icon component for an Action button. Shared by the task page action bar and the To Do
 * batch bar so a configured Action looks the same wherever it is offered.
 * An unknown name keeps the long-standing Check icon.
 */
export function actionIconComponent(iconName?: string | null): Component | null {
  if (!iconName) return null
  return ACTION_ICONS[iconName] || ACTION_ICONS.check
}
