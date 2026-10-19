<template>
  <div class="tasks-page">
    <TodoPageHeader
      :auto-claim-on-open="preferenceStore.autoClaimOnOpen"
      :auto-preview-on-open="preferenceStore.autoPreviewOnOpen"
      :saving="preferenceStore.saving"
      :busy="claimAllBusy"
      @claim-all="handleClaimAll"
      @unclaim-all="handleUnclaimAll"
      @auto-claim-change="onAutoClaimChange"
      @auto-preview-change="onAutoPreviewChange"
    />

    <el-tabs
      :model-value="activeTab"
      class="todo-tabs"
      data-test="todo-tabs"
      @tab-change="(name: string | number) => switchTab(name === 'batch' ? 'batch' : 'all')"
    >
      <el-tab-pane name="all">
        <template #label>
          {{ t('task.tabAll') }}
          <span class="todo-tabs__count">{{ pendingTaskStore.count }}</span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="batch">
        <template #label>
          {{ t('task.tabBatch') }}
          <span class="todo-tabs__count">{{ batchTotal }}</span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <div
      v-if="activeTab === 'batch' && !groupRef"
      class="portal-card portal-card--fit"
    >
      <BatchTaskGroups
        :groups="groups"
        :total="batchTotal"
        :loading="groupsLoading"
        @open="openGroup"
      />
    </div>

    <!-- v-show, not v-else: the grid measures its scroll container once on mount -->
    <div
      v-show="activeTab === 'all' || groupRef"
      v-loading="loading"
      class="portal-card"
    >
      <div
        v-if="groupRef"
        class="batch-group-head"
        data-test="batch-group-head"
      >
        <el-link
          type="primary"
          :underline="false"
          class="batch-group-head__back"
          @click="backToGroups"
        >
          <el-icon><ArrowLeft /></el-icon>
          {{ t('task.backToGroups') }}
        </el-link>
        <div class="batch-group-head__title">
          <span class="batch-group-head__name">{{ activeGroup?.functionUnitName || activeGroup?.processDefinitionKey || '-' }}</span>
          <!-- Same "v1.0.x" badge Developer Workstation shows next to the Function Unit name. -->
          <el-tag
            v-if="activeGroup?.version"
            size="small"
            type="info"
          >
            v{{ activeGroup.version }}
          </el-tag>
        </div>
        <!-- The group-wide values that would repeat on every row (BATCH_GROUP_COMMON_FIELDS):
             small label over value, no borders. -->
        <dl
          class="batch-group-head__facts"
          data-test="batch-group-facts"
        >
          <div class="batch-group-head__fact">
            <dt>{{ t('task.taskName') }}</dt>
            <dd>{{ activeGroup?.nodeName || '-' }}</dd>
          </div>
          <div class="batch-group-head__fact">
            <dt>{{ t('task.assignmentType') }}</dt>
            <dd>
              <el-tag
                v-for="a in groupAssignmentTypes"
                :key="a.key"
                size="small"
                class="assignment-tag"
                :class="a.tagClass"
              >
                {{ t(`task.${a.key}`) }}
              </el-tag>
              <span v-if="groupAssignmentTypes.length === 0">-</span>
            </dd>
          </div>
          <div class="batch-group-head__fact">
            <dt>{{ t('task.currentAssignee') }}</dt>
            <dd>{{ groupAssignees }}</dd>
          </div>
          <div class="batch-group-head__fact">
            <dt>{{ t('task.batchGroupCount') }}</dt>
            <dd class="batch-group-head__count">
              {{ pagination.total }}
            </dd>
          </div>
        </dl>
      </div>
      <TodoListToolbar
        v-model:assignment-types="filterForm.assignmentTypes"
        v-model:priorities="filterForm.priorities"
        v-model:keyword="filterForm.keyword"
        :show-assignment-types="!groupRef"
        @search="handleSearch"
        @reset="handleReset"
        @export="exportGridCsv('todo-tasks')"
      />
      <div
        ref="gridScrollRef"
        class="list-data-grid-scroll"
      >
        <div
          class="list-data-grid-inner"
          :style="gridInnerStyle"
        >
          <el-table
            :data="displayRows"
            stripe
            :fit="false"
            table-layout="fixed"
            style="width: 100%;"
            class="list-data-grid"
            :class="{ 'list-data-grid--fit': gridFits }"
            scrollbar-always-on
            :height="gridTableHeight || '100%'"
            @selection-change="handleSelectionChange"
          >
            <template #empty>
              <div
                v-if="loading"
                class="table-empty-loading"
              >
                <el-icon class="table-empty-loading__icon is-loading">
                  <Loading />
                </el-icon>
                <span>{{ t('common.loading') }}</span>
              </div>
              <span v-else>{{ t('task.noTasks') }}</span>
            </template>
            <!-- Do not set `fixed`: EP's left overlay fills the viewport and hides the data columns. -->
            <el-table-column
              type="selection"
              width="50"
            />
            <el-table-column
              v-for="(col, colIndex) in displayColumns"
              :key="col.field"
              :prop="col.field"
              :width="widthOf(col.field)"
              show-overflow-tooltip
            >
              <template #header>
                <ListColumnHeader
                  :column="col"
                  :sort="sort.field === col.field ? sort.direction : null"
                  :filtered="!!columnFilters[col.field]"
                  :width="widthOf(col.field)"
                  :show-move="displayColumns.length > 1"
                  :can-move-left="colIndex > 0"
                  :can-move-right="colIndex < displayColumns.length - 1"
                  @sort-change="(direction: 'ASC' | 'DESC') => onSort(col.field, direction)"
                  @clear-sort="onClearSort"
                  @filter-open="openFilter(col.field)"
                  @clear-filter="onClearFilter(col.field)"
                  @move="(direction: 'left' | 'right') => moveColumn(col.field, direction)"
                  @width-change="(width: number) => setWidth(col.field, width)"
                  @width-commit="persistWidths"
                />
              </template>
              <template #default="{ row }">
                <el-link
                  v-if="col.field === 'requestId'"
                  type="primary"
                  @click="viewTask(row)"
                >
                  {{ row.requestId || '-' }}
                </el-link>
                <span
                  v-else-if="col.field === 'functionUnitCode'"
                >
                  {{ row.functionUnitName || row.functionUnitCode || '-' }}
                </span>
                <template v-else-if="col.field === 'taskName'">
                  <!-- Flags the rows Batch Actions can approve/reject together; opens that group. -->
                  <el-tag
                    v-if="groupOfRow(row)"
                    size="small"
                    type="success"
                    class="todo-batch-mark"
                    :title="t('task.batchMarkHint')"
                    data-test="todo-batch-mark"
                    @click.stop="openRowGroup(row)"
                  >
                    {{ t('task.batchMark') }}
                  </el-tag>
                  {{ row.taskName || '-' }}
                </template>
                <el-tag
                  v-else-if="col.field === 'assignmentType'"
                  size="small"
                  class="assignment-tag"
                  :class="assignmentTagClass(row)"
                >
                  {{ t(`task.${assignmentDisplayKey(row)}`) }}
                </el-tag>
                <template v-else-if="col.field === 'assigneeName'">
                  <el-tag
                    v-if="isTodoClaimHold(row)"
                    :type="row.claimedByCurrentUser ? 'success' : 'info'"
                    size="small"
                    data-test="todo-current-assignee"
                  >
                    {{ formatTodoCurrentAssignee(row, t('task.claimedByMe')) }}
                  </el-tag>
                  <span
                    v-else
                    data-test="todo-current-assignee"
                  >
                    {{ formatTodoCurrentAssignee(row, t('task.claimedByMe')) }}
                  </span>
                </template>
                <span
                  v-else-if="col.field === 'priority'"
                  class="priority"
                  :class="getPriorityClass(row.priority)"
                >
                  {{ getPriorityLabel(row.priority) }}
                </span>
                <span
                  v-else-if="col.field === 'createTime' || col.field === 'dueDate'"
                  style="white-space: nowrap;"
                  :class="{ overdue: col.field === 'dueDate' && row.isOverdue }"
                >
                  {{ formatDate(row[col.field]) }}
                  <el-tag
                    v-if="col.field === 'dueDate' && row.isOverdue"
                    type="danger"
                    size="small"
                    style="margin-left: 4px;"
                  >
                    {{ t('task.overdue') }}
                  </el-tag>
                </span>
                <template v-else>
                  {{ row[col.field as keyof TaskInfo] || '-' }}
                </template>
              </template>
            </el-table-column>
            <!-- Claim operations belong to the full To Do; a batch group holds only tasks I already handle. -->
            <el-table-column
              v-if="!groupRef"
              :width="CLAIM_ACTION_WIDTH"
              class-name="todo-claim-action-col"
            >
              <template #header>
                <span class="todo-claim-action-header">{{ t('task.action') }}</span>
              </template>
              <template #default="{ row }">
                <TodoClaimRowActions
                  :task="row"
                  :loading="actingTaskId === row.taskId"
                  @claim="handleClaim"
                  @unclaim="handleUnclaim"
                  @force-unclaim="handleForceUnclaim"
                  @reassign="handleReassign"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div
        v-if="groupRef && selectedTasks.length > 0"
        class="batch-actions"
        data-test="batch-complete-bar"
      >
        <span>{{ t('task.selected', { count: selectedTasks.length }) }}</span>
        <span class="batch-actions__scope">{{ t('task.batchScopeThisPage') }}</span>
        <span class="batch-actions__spacer" />
        <!-- The node's Actions as configured in DW, presented exactly like TaskActionBar. -->
        <el-button
          v-for="a in activeGroup?.actions ?? []"
          :key="a.actionId"
          :type="resolveActionButtonType(a.actionType, a.buttonColor)"
          :style="actionButtonStyle(a.buttonColor)"
          :loading="runningActionId === a.actionId"
          :disabled="batchRunning && runningActionId !== a.actionId"
          data-test="batch-complete-btn"
          @click="onBatchAction(a)"
        >
          <el-icon v-if="actionIconComponent(a.icon) && runningActionId !== a.actionId">
            <component :is="actionIconComponent(a.icon)" />
          </el-icon>
          {{ a.actionName }}
        </el-button>
      </div>
      <div
        v-else-if="selectedTasks.length > 0"
        class="batch-actions"
      >
        <span>{{ t('task.selected', { count: selectedTasks.length }) }}</span>
        <el-button
          type="primary"
          size="small"
          data-test="todo-claim-selected-btn"
          :disabled="selectedClaimableIds.length === 0"
          :loading="claimAllBusy"
          :title="selectedClaimableIds.length === 0 ? t('task.claimSelectedEmpty') : undefined"
          @click="handleClaimSelected"
        >
          {{ t('task.claim') }}
        </el-button>
        <el-button
          size="small"
          data-test="todo-unclaim-selected-btn"
          :disabled="selectedHeldIds.length === 0"
          :loading="claimAllBusy"
          :title="selectedHeldIds.length === 0 ? t('task.unclaimSelectedEmpty') : undefined"
          @click="handleUnclaimSelected"
        >
          {{ t('task.unclaim') }}
        </el-button>
        <el-button
          type="warning"
          size="small"
          @click="handleBatchUrge"
        >
          {{ t('task.batchUrge') }}
        </el-button>
      </div>

      <ListPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        :loading="loading"
        @change="loadTasks"
      />
    </div>

    <ListFilterDialog
      v-model:visible="filterDialog.visible"
      :column="activeFilterColumn"
      :filter="activeFilter"
      @apply="onFilterApply"
      @clear="onFilterClear"
    />

    <el-dialog
      v-model="actionDialogVisible"
      :title="actionDialogTitle"
      width="500px"
      class="task-action-form"
    >
      <el-form
        :model="actionForm"
        label-width="100px"
      >
        <el-form-item
          v-if="currentAction !== 'urge' && currentAction !== 'batchUrge'"
          :label="t('task.targetUser')"
          required
        >
          <el-input
            v-model="actionForm.targetUserId"
            :placeholder="t('task.enterUserId')"
          />
        </el-form-item>
        <el-form-item :label="t('common.reason')">
          <el-input
            v-model="actionForm.reason"
            type="textarea"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          @click="submitAction"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <TaskReassignDialog
      v-model="reassignDialogVisible"
      :candidate-user-ids="reassignTask?.candidateUserIds ?? []"
      :current-holder-id="reassignTask?.assignee"
      :submitting="claimAllBusy"
      @confirm="confirmReassign"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onActivated, watch } from 'vue'
import { ArrowLeft, Loading } from '@element-plus/icons-vue'
import TodoListToolbar from './TodoListToolbar.vue'
import TodoPageHeader from './TodoPageHeader.vue'
import TodoClaimRowActions from '@/components/tasks/TodoClaimRowActions.vue'
import TaskReassignDialog from '@/components/tasks/TaskReassignDialog.vue'
import ListColumnHeader from '@platform-shared/list/ListColumnHeader.vue'
import ListFilterDialog from '@platform-shared/list/ListFilterDialog.vue'
import ListPagination from '@platform-shared/list/ListPagination.vue'
import type { TaskInfo } from '@/api/task'
import { CLAIM_ACTION_WIDTH, useTodoTasksPage } from '@/composables/tasks/useTodoTasksPage'
import { formatDate } from '@/utils/dateFormat'
import { assignmentDisplayKey, assignmentTagClass } from '@/utils/taskAssignmentDisplay'
import { formatTodoCurrentAssignee, isTodoClaimHold } from '@/utils/todoCurrentAssignee'
import BatchTaskGroups from '@/components/tasks/BatchTaskGroups.vue'
import type { BatchTaskAction } from '@/api/task'
import { useTodoBatchMode } from '@/composables/tasks/useTodoBatchMode'
import { usePendingTaskStore } from '@/stores/pendingTask'
import { actionButtonStyle, resolveActionButtonType } from '@/utils/actionButtonColor'
import { actionIconComponent } from '@/utils/actionIcon'

defineOptions({ name: 'Tasks' })

const pendingTaskStore = usePendingTaskStore()
const {
  groups,
  groupsLoading,
  batchRunning,
  runningActionId,
  activeTab,
  groupRef,
  activeGroup,
  batchTotal,
  groupOfRow,
  loadGroups,
  switchTab,
  openGroup,
  backToGroups,
  runBatchAction,
} = useTodoBatchMode()

function openRowGroup(row: TaskInfo) {
  const group = groupOfRow(row)
  if (group) openGroup(group)
}

const {
  t,
  preferenceStore,
  loading,
  selectedTasks,
  selectedClaimableIds,
  selectedHeldIds,
  filterForm,
  displayColumns,
  displayRows,
  columnFilters,
  sort,
  filterDialog,
  pagination,
  activeFilterColumn,
  activeFilter,
  gridScrollRef,
  gridFits,
  gridTableHeight,
  gridInnerStyle,
  widthOf,
  setWidth,
  persistWidths,
  moveColumn,
  openFilter,
  actionDialogVisible,
  actionDialogTitle,
  actionForm,
  actingTaskId,
  claimAllBusy,
  loadTasks,
  handleSearch,
  handleReset,
  onSort,
  onClearSort,
  onClearFilter,
  onFilterApply,
  onFilterClear,
  handleSelectionChange,
  exportGridCsv,
  viewTask,
  handleClaim,
  handleUnclaim,
  handleForceUnclaim,
  handleReassign,
  confirmReassign,
  reassignDialogVisible,
  reassignTask,
  handleClaimAll,
  handleUnclaimAll,
  handleClaimSelected,
  handleUnclaimSelected,
  onAutoClaimChange,
  onAutoPreviewChange,
  handleBatchUrge,
  submitAction,
  getPriorityLabel,
  getPriorityClass,
} = useTodoTasksPage(() => groupRef.value)

/**
 * Group header values, read from the rows with the same helpers the hidden columns used.
 * A group is one node of one release, so normally each list has a single entry.
 */
const groupAssignmentTypes = computed(() => {
  const byKey = new Map<string, string>()
  for (const row of displayRows.value) {
    const key = assignmentDisplayKey(row)
    if (!byKey.has(key)) byKey.set(key, assignmentTagClass(row))
  }
  return [...byKey].map(([key, tagClass]) => ({ key, tagClass }))
})
const groupAssignees = computed(() => {
  const names = new Set(displayRows.value.map((row) => formatTodoCurrentAssignee(row, t('task.claimedByMe'))))
  names.delete('-')
  return names.size > 0 ? [...names].join(', ') : '-'
})

// Same grid, different scope: entering / leaving a batch group (or switching tab) re-queries it.
watch(groupRef, (next, prev) => {
  if (next?.groupKey === prev?.groupKey
    && next?.taskDefinitionKey === prev?.taskDefinitionKey) return
  selectedTasks.value = []
  pagination.page = 1
  loadTasks()
})

async function onBatchAction(action: BatchTaskAction) {
  const ran = await runBatchAction(action, selectedTasks.value)
  if (!ran) return
  selectedTasks.value = []
  await Promise.all([loadTasks(), loadGroups(), pendingTaskStore.fetchPendingCount()])
  if (groupRef.value && !activeGroup.value) backToGroups()
}

let skipActivateGroups = true
onMounted(() => {
  void loadGroups()
  // PortalLayout leaves the To Do badge on /tasks to this page's full list; opened straight
  // into a group view (link / refresh) the page lists only a subset, so fetch the real count.
  if (groupRef.value) void pendingTaskStore.fetchPendingCount()
})
onActivated(() => {
  if (skipActivateGroups) {
    skipActivateGroups = false
    return
  }
  void loadGroups()
})
</script>

<style lang="scss">
@import '@/styles/listDataGrid.scss';
</style>

<style lang="scss" scoped>
.table-empty-loading {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  padding: 24px 0;

  &__icon {
    font-size: 18px;
  }
}

.tasks-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;

  :deep(.portal-card) {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  // The group list is short: size the card to it rather than a mostly empty page.
  // It still shrinks (and the list scrolls) once the groups outgrow the viewport.
  .portal-card--fit {
    flex: 0 1 auto;
  }

  .batch-actions {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-top: 12px;
    padding: 12px 16px;
    background: #f5f7fa;
    border-radius: 4px;
    flex-shrink: 0;

    span {
      color: var(--text-secondary);
    }

    &__scope {
      font-size: 12px;
    }

    &__spacer {
      flex: 1;
    }
  }

  .todo-tabs {
    flex-shrink: 0;

    :deep(.el-tabs__header) {
      margin-bottom: 12px;
    }

    &__count {
      margin-left: 6px;
      font-size: 12px;
      color: var(--text-secondary);
    }
  }

  .batch-group-head {
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding-bottom: 16px;
    margin-bottom: 16px;
    border-bottom: 1px solid var(--ws-line);

    &__back {
      align-self: flex-start;
      gap: 4px;
      font-size: 13px;
    }

    &__title {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    &__name {
      font-size: 18px;
      font-weight: 600;
      color: var(--text-primary);
    }

    &__facts {
      display: flex;
      flex-wrap: wrap;
      gap: 12px 48px;
      margin: 0;
    }

    // Label in the To Do grid's header voice (ws-theme.scss), value at body size beneath it.
    &__fact {
      dt {
        font-size: 11px;
        font-weight: 600;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: #8f8f8a;
      }

      dd {
        margin: 4px 0 0;
        min-height: 22px;
        display: flex;
        align-items: center;
        font-size: 14px;
        color: var(--text-primary);
      }
    }

    &__count {
      font-weight: 600;
    }
  }

  .todo-batch-mark {
    margin-right: 6px;
    cursor: pointer;
  }

  .overdue {
    color: var(--error-red);
  }

  .todo-claim-action-header {
    font-weight: 500;
  }
}
</style>

<style lang="scss">
.task-action-form .el-form-item__label {
  white-space: nowrap;
}
</style>
