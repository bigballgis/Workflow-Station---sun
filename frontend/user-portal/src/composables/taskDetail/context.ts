import type { useI18n } from 'vue-i18n'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { useTaskForm } from '@/composables/tasks/useTaskForm'
import type { useBpmnParser } from '@/composables/tasks/useBpmnParser'
import type { useTaskDisplay } from '@/composables/tasks/useTaskDisplay'
import type { TaskDetailState } from './useTaskDetailState'
import type { TaskDetailFormSchemaFns } from './useTaskDetailFormSchema'
import type { TaskDetailFieldExtractionFns } from './useTaskDetailFieldExtraction'
import type { TaskDetailLinkTargetFns } from './useTaskDetailLinkTargets'
import type { TaskDetailMiScopeFns } from './useTaskDetailMiScope'
import type { TaskDetailHydrationFns } from './useTaskDetailSubTableHydration'
import type { TaskDetailSyncFns } from './useTaskDetailSubTableSync'
import type { TaskDetailMiLinkChildFns } from './useTaskDetailMiLinkChild'
import type { TaskDetailMiBackfillFns } from './useTaskDetailMiBackfill'
import type { TaskDetailMiIsolationFns } from './useTaskDetailMiIsolation'
import type { TaskDetailMiResyncFns } from './useTaskDetailMiResync'
import type { TaskDetailMiPersistFns } from './useTaskDetailMiPersist'
import type { TaskDetailNodeFormMapFns } from './useTaskDetailNodeFormMap'
import type { TaskDetailLayoutSyncFns } from './useTaskDetailLayoutSync'
import type { TaskDetailDiagramFns } from './useTaskDetailDiagram'
import type { TaskDetailFuLoaderFns } from './useTaskDetailFuLoader'
import type { TaskDetailPrevFormsFns } from './useTaskDetailPrevForms'
import type { TaskDetailFormsLoaderFns } from './useTaskDetailFormsLoader'

/**
 * Late-bound externals provided by the SFC after the corresponding composable
 * is created (creation order must mirror the original detail.vue setup order).
 */
export interface TaskDetailExternals {
  t: ReturnType<typeof useI18n>['t']
  route: RouteLocationNormalizedLoaded
  taskId: string
  taskForm: ReturnType<typeof useTaskForm>
  bpmn: ReturnType<typeof useBpmnParser>
  display: ReturnType<typeof useTaskDisplay>
  loadTaskDetail: () => Promise<void>
}

/**
 * Cross-module function registry. Functions are registered onto the shared
 * context by the SFC during setup; all cross-module calls go through `ctx.*`
 * at invocation time (post-setup), mirroring the hoisting semantics the
 * original single-file functions relied on.
 */
export type TaskDetailFns =
  TaskDetailFormSchemaFns &
  TaskDetailFieldExtractionFns &
  TaskDetailLinkTargetFns &
  TaskDetailMiScopeFns &
  TaskDetailHydrationFns &
  TaskDetailSyncFns &
  TaskDetailMiLinkChildFns &
  TaskDetailMiBackfillFns &
  TaskDetailMiIsolationFns &
  TaskDetailMiResyncFns &
  TaskDetailMiPersistFns &
  TaskDetailNodeFormMapFns &
  TaskDetailLayoutSyncFns &
  TaskDetailDiagramFns &
  TaskDetailFuLoaderFns &
  TaskDetailPrevFormsFns &
  TaskDetailFormsLoaderFns

export type TaskDetailCtx = TaskDetailState & TaskDetailExternals & TaskDetailFns
