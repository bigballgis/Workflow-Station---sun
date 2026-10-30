<template>
  <div v-if="task.assignmentWriteAccess?.evaluation?.tracked === true" class="claim-banner__handoff">
    <AssignmentHandoffAction :task-id="task.taskId" :enabled="true" :disabled="task.assignmentWriteCheckPending || submitting" />
  </div>
  <div
    v-if="task.claimPoolTask || writeRestricted"
    class="section claim-banner"
    data-test="task-claim-banner"
  >
    <el-alert
      :type="alertType"
      :closable="false"
      show-icon
    >
      <template #title>
        <span data-test="task-claim-banner-title">{{ title }}</span>
      </template>
      <template #default>
        <div class="claim-banner__body">
          <span class="claim-banner__hint">{{ hint }}</span>
          <el-button
            v-if="task.assignmentLocalDraftRetained"
            size="small"
            data-test="assignment-reload-server-form"
            :disabled="task.assignmentWriteCheckPending"
            @click="reloadServerForm"
          >
            {{ t('task.assignmentReloadForm') }}
          </el-button>
          <el-button
            v-if="task.claimable"
            type="primary"
            size="small"
            :loading="submitting"
            data-test="task-claim-btn"
            :disabled="task.assignmentWriteCheckPending"
            @click="emit('claim')"
          >
            {{ t('task.claim') }}
          </el-button>
          <el-button
            v-else-if="task.claimedByCurrentUser"
            size="small"
            :loading="submitting"
            data-test="task-unclaim-btn"
            :disabled="task.assignmentWriteCheckPending"
            @click="emit('unclaim')"
          >
            {{ t('task.unclaim') }}
          </el-button>
          <el-button
            v-else-if="task.canForceUnclaim"
            type="warning"
            size="small"
            :loading="submitting"
            data-test="task-force-unclaim-btn"
            :disabled="task.assignmentWriteCheckPending"
            @click="emit('force-unclaim')"
          >
            {{ t('task.forceUnclaim') }}
          </el-button>
          <el-button
            v-if="task.canReassign"
            type="primary"
            plain
            size="small"
            :loading="submitting"
            data-test="task-reassign-btn"
            :disabled="task.assignmentWriteCheckPending"
            @click="emit('reassign')"
          >
            {{ t('task.reassign') }}
          </el-button>
        </div>
      </template>
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import type { AssignmentClaimAccess } from '@/types/assignmentClaimAccess'
import type { AssignmentWriteAccess } from '@/types/assignmentWriteAccess'
import { isAssignmentWriteBlocked } from '@/utils/assignmentWriteAccess'
import AssignmentHandoffAction from './AssignmentHandoffAction.vue'

interface ClaimBannerTask {
  taskId?: string
  claimPoolTask?: boolean
  claimable?: boolean
  claimedByCurrentUser?: boolean
  canForceUnclaim?: boolean
  canReassign?: boolean
  assignee?: string
  assigneeName?: string
  assignmentClaimAccess?: AssignmentClaimAccess
  assignmentWriteAccess?: AssignmentWriteAccess | null
  assignmentLocalDraftRetained?: boolean
  assignmentWriteCheckPending?: boolean
}

const props = defineProps<{
  task: ClaimBannerTask
  submitting: boolean
}>()

const emit = defineEmits<{
  (e: 'claim'): void
  (e: 'unclaim'): void
  (e: 'force-unclaim'): void
  (e: 'reassign'): void
}>()

const { t } = useI18n()
const router = useRouter()
const writeRestricted = computed(() => isAssignmentWriteBlocked(props.task)
  && (props.task.assignmentLocalDraftRetained || !(props.task.claimPoolTask && !props.task.assignee)))

const qualificationMessage = computed(() => {
  if (props.task.assignmentWriteCheckPending) return 'assignmentWriteChecking'
  if (props.task.assignmentLocalDraftRetained) return 'assignmentDraftRetained'
  if (writeRestricted.value) {
    switch (props.task.assignmentWriteAccess?.evaluation?.reason) {
      case 'ASSIGNMENT_EXCLUDED': return 'assignmentWriteExcluded'
      case 'ASSIGNMENT_SYNC_PENDING': return 'assignmentWriteSyncPending'
      case 'ASSIGNMENT_STATE_CHANGED': return 'assignmentWriteChanged'
      case 'CLAIM_REQUIRED': return 'assignmentWriteClaimRequired'
      case 'CURRENT': return 'assignmentWriteDenied'
      default: return 'assignmentWriteUnavailable'
    }
  }
  const access = props.task.assignmentClaimAccess
  if (!access || access.state === 'LEGACY' || props.task.claimable || props.task.assignee) return null
  switch (access.state) {
    case 'RECONCILING': return 'claimRecalculating'
    case 'SYNC_PENDING': return 'claimSyncPending'
    case 'BLOCKED_NO_ELIGIBLE_USER': return 'claimNoEligibleUser'
    case 'ACTOR_INACTIVE': return 'claimActorInactive'
    case 'NOT_ACTIVE': return 'claimTaskChanged'
    case 'CONTEXT_UNAVAILABLE':
    case 'DEPENDENCY_UNAVAILABLE': return 'claimQualificationUnavailable'
    default: return 'claimCurrentlyIneligible'
  }
})

async function reloadServerForm(): Promise<void> {
  try {
    await ElMessageBox.confirm(t('task.assignmentDiscardDraftConfirm'), t('task.assignmentReloadForm'), {
      type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'),
    })
  } catch (action: unknown) {
    if (action === 'cancel' || action === 'close') return
    throw action
  }
  router.go(0)
}

const alertType = computed(() => {
  if (writeRestricted.value) return 'info'
  if (props.task.claimedByCurrentUser) return 'success'
  return props.task.claimable ? 'warning' : 'info'
})

const title = computed(() => {
  if (qualificationMessage.value) return t(`task.${qualificationMessage.value}`)
  if (props.task.claimedByCurrentUser) return t('task.claimHeldByYou')
  if (props.task.claimable) return t('task.claimAvailable')
  return t('task.claimHeldByOther', { user: props.task.assigneeName || props.task.assignee || '-' })
})

const hint = computed(() => {
  if (qualificationMessage.value) return t(`task.${qualificationMessage.value}Hint`)
  if (props.task.claimedByCurrentUser) return t('task.claimHeldByYouHint')
  if (props.task.claimable) return t('task.claimAvailableHint')
  if (props.task.canForceUnclaim) return t('task.forceUnclaimHint')
  if (props.task.canReassign) return t('task.reassignHint')
  return t('task.claimHeldByOtherHint')
})
</script>

<style lang="scss" scoped>
.claim-banner {
  background: transparent;
  border: none;

  &__handoff { display: flex; justify-content: flex-end; }

  &__body {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  &__hint {
    color: var(--text-secondary);
  }
}
</style>
