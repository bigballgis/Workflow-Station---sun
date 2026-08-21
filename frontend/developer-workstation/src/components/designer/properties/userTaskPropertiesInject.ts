import { inject, type InjectionKey } from 'vue'
import type { UserTaskPropertyContext } from '@/composables/userTaskProperties/types'
import type { useUserTaskAssignee } from '@/composables/userTaskProperties/useUserTaskAssignee'
import type { useUserTaskMultiInstance } from '@/composables/userTaskProperties/useUserTaskMultiInstance'
import type { useUserTaskActions } from '@/composables/userTaskProperties/useUserTaskActions'

export type UserTaskPanelInject = {
  ctx: UserTaskPropertyContext
  assignee: ReturnType<typeof useUserTaskAssignee>
  multiInstance: ReturnType<typeof useUserTaskMultiInstance>
  actions: ReturnType<typeof useUserTaskActions>
}

export const USER_TASK_PANEL_KEY: InjectionKey<UserTaskPanelInject> = Symbol('userTaskPanel')

export function injectUserTaskPanel(): UserTaskPanelInject {
  const panel = inject(USER_TASK_PANEL_KEY)
  if (!panel) {
    throw new Error('UserTask panel sections must be used inside UserTaskProperties')
  }
  return panel
}
