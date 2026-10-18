<template>
  <div
    v-if="!isCompletedTask && !claimLocked"
    class="section action-section"
  >
    <div class="action-buttons">
      <div class="left-actions">
        <el-button @click="$router.back()">
          {{ $t('task.backToList') }}
        </el-button>
      </div>
      <!-- Exactly the Actions configured on this node in Developer Workstation: no implicit
           Save, no default Approve / Reject. A Save button is a SAVE Action bound to the node. -->
      <div class="right-actions">
        <el-button
          v-for="action in visibleActions"
          :key="action.actionId"
          :type="resolveButtonType(action)"
          :style="resolveButtonStyle(action)"
          @click="$emit('customAction', action)"
        >
          <el-icon v-if="resolveIconName(action)">
            <component :is="getIconComponent(resolveIconName(action))" />
          </el-icon>
          {{ getActionLabel(action) }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'
import type { TaskActionInfo } from '@/api/task'
import { getStoredUser } from '@/api/auth'
import { actionButtonStyle, resolveActionButtonType } from '@/utils/actionButtonColor'
import { isActionVisibleForSession } from '@/utils/actionAllowedRoles'

/**
 * Delegate / Transfer / Urge are no longer hardcoded here: they are Action
 * definitions configured in the Developer Workstation Action Designer and bound
 * to the user task node in Process Design, exactly like every other Action.
 * This map only supplies the icons the built-in buttons used to have. Button
 * presentation is resolved centrally from actionType + buttonColor.
 */
const BUILT_IN_ICONS: Record<string, string> = {
  DELEGATE: 'user',
  TRANSFER: 'switch',
  URGE: 'bell'
}
const props = defineProps<{
  isCompletedTask: boolean
  /** BU Role pool task the signed-in user does not hold: view only until they claim it. */
  claimLocked?: boolean
  actions: TaskActionInfo[] | undefined | null
  canDelegate: boolean
  getIconComponent: (iconName?: string) => Component | null
  getActionLabel: (action: TaskActionInfo) => string
}>()

defineEmits<{
  (e: 'customAction', action: TaskActionInfo): void
}>()

function actionTypeOf(action: TaskActionInfo): string {
  return (action.actionType || '').trim().toUpperCase()
}

/** Delegate needs an assignee. Allowed Roles hide the button for the current session. */
const visibleActions = computed<TaskActionInfo[]>(() => {
  const user = getStoredUser()
  const roles = user?.roles ?? []
  const systemAdministrator = user?.systemAdministrator === true
  return (props.actions || []).filter((action) => {
    if (actionTypeOf(action) === 'DELEGATE' && !props.canDelegate) return false
    return isActionVisibleForSession(action.configJson, roles, systemAdministrator)
  })
})

function resolveIconName(action: TaskActionInfo): string | undefined {
  return action.icon || BUILT_IN_ICONS[actionTypeOf(action)]
}

function resolveButtonType(action: TaskActionInfo) {
  return resolveActionButtonType(action.actionType, action.buttonColor)
}

function resolveButtonStyle(action: TaskActionInfo): Record<string, string> | undefined {
  return actionButtonStyle(action.buttonColor)
}
</script>

<style lang="scss" scoped>
.section {
  background: white;
  border-radius: 8px;
  border: 1px solid var(--border-color, #e4e7ed);
}

.action-section {
  position: sticky;
  bottom: 0;
  z-index: 10;
  padding: 16px 20px;
  border-top: 1px solid var(--border-color, #e4e7ed);
}

.action-buttons {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .left-actions, .right-actions {
    display: flex;
    gap: 12px;
  }
}
</style>
