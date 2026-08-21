<!--
  Automation flows list — the AP project's flows through the per-user session.

  The "业务键" (metadata.hermesFlowKey) is the ONLY reference BPMN service tasks
  carry (ap:flowKey), so it is shown as a first-class column with a copy action.
  Keys are stamped at creation and immutable afterwards (FR-C11): renames touch
  the display name only.
-->
<template>
  <div class="flows-panel">
    <div class="flows-panel__toolbar">
      <el-input
        v-model="keyword"
        class="flows-panel__search"
        :placeholder="t('automation.searchPlaceholder')"
        clearable
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <span class="flows-panel__count">{{ t('automation.total', { count: filteredFlows.length }) }}</span>
      <div class="flows-panel__actions">
        <el-button @click="reload">
          <el-icon><Refresh /></el-icon>{{ t('common.refresh') }}
        </el-button>
        <el-button
          type="primary"
          @click="openCreateDialog"
        >
          <el-icon><Plus /></el-icon>{{ t('automation.create') }}
        </el-button>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="filteredFlows"
      stripe
      style="width: 100%"
      @row-click="openDesigner"
    >
      <el-table-column
        :label="t('automation.colName')"
        min-width="180"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span class="flows-panel__name">{{ row.version?.displayName || row.id }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('automation.colKey')"
        min-width="220"
      >
        <template #default="{ row }">
          <div
            v-if="flowKey(row)"
            class="flows-panel__key"
          >
            <code>{{ flowKey(row) }}</code>
            <el-tooltip
              :content="t('automation.copyKey')"
              placement="top"
            >
              <el-button
                link
                size="small"
                @click.stop="copyKey(flowKey(row))"
              >
                <el-icon><CopyDocument /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
          <span
            v-else
            class="flows-panel__no-key"
          >{{ t('automation.noKey') }}</span>
        </template>
      </el-table-column>
      <!-- 就绪阶梯（与原 AC 迁移页同一语义）：草稿 → 已发布未启用 → 运行中 -->
      <el-table-column
        :label="t('automation.colState')"
        width="130"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="readiness(row).type"
            size="small"
            :effect="readiness(row).effect"
            disable-transitions
          >
            {{ t(readiness(row).labelKey) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('automation.colUpdated')"
        width="150"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.updated) }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('common.operation')"
        width="160"
        align="center"
        fixed="right"
      >
        <template #default="{ row }">
          <div
            class="flows-panel__row-actions"
            @click.stop
          >
            <el-button
              link
              type="primary"
              size="small"
              @click="openDesigner(row)"
            >
              {{ t('automation.design') }}
            </el-button>
            <el-dropdown
              trigger="click"
              @command="(cmd: string) => handleRowCommand(cmd, row)"
            >
              <el-button
                link
                size="small"
                :loading="actingId === row.id"
                :aria-label="t('common.operation')"
              >
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="publish">
                    {{ t('automation.publish') }}
                  </el-dropdown-item>
                  <el-dropdown-item
                    command="toggle"
                    :disabled="!row.publishedVersionId"
                  >
                    {{ row.status === 'ENABLED' ? t('automation.disable') : t('automation.enable') }}
                  </el-dropdown-item>
                  <el-dropdown-item command="rename">
                    {{ t('automation.rename') }}
                  </el-dropdown-item>
                  <el-dropdown-item
                    command="delete"
                    divided
                  >
                    <span class="flows-panel__danger">{{ t('common.delete') }}</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div
      v-if="nextCursor"
      class="flows-panel__more"
    >
      <el-button
        :loading="loadingMore"
        @click="loadMore"
      >
        {{ t('automation.loadMore') }}
      </el-button>
    </div>

    <!-- 创建：名称 + 业务键（默认由名称 slug + 随机 6 位生成，可改；创建后不可再改 FR-C11） -->
    <el-dialog
      v-model="createDialogVisible"
      :title="t('automation.createTitle')"
      width="520px"
      @closed="resetCreateDialog"
    >
      <el-form
        label-width="110px"
        @submit.prevent
      >
        <el-form-item :label="t('automation.createName')">
          <el-input
            v-model="createForm.displayName"
            :placeholder="t('automation.createNamePlaceholder')"
            maxlength="80"
            @input="syncGeneratedKey"
          />
        </el-form-item>
        <el-form-item :label="t('automation.createKey')">
          <el-input
            v-model="createForm.flowKey"
            :placeholder="t('automation.createKeyPlaceholder')"
            maxlength="80"
            @input="keyTouched = true"
          />
          <div class="flows-panel__tip">
            {{ t('automation.createKeyTip') }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="creating"
          :disabled="!createForm.displayName.trim() || !createForm.flowKey.trim()"
          @click="handleCreate"
        >
          {{ t('common.create') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 重命名：只改显示名，业务键不动 -->
    <el-dialog
      v-model="renameDialogVisible"
      :title="t('automation.renameTitle')"
      width="480px"
    >
      <el-form
        label-width="110px"
        @submit.prevent
      >
        <el-form-item :label="t('automation.createName')">
          <el-input
            v-model="renameForm.displayName"
            maxlength="80"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="renaming"
          :disabled="!renameForm.displayName.trim()"
          @click="handleRename"
        >
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument, MoreFilled, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  applyAutomationFlowOperation,
  createAutomationFlow,
  deleteAutomationFlow,
  listAutomationFlows,
  type ApFlow,
  type ServiceTaskSession,
} from '@/api/automation'
import { formatDateTime, generateFlowKey } from '../automationUi'

const props = defineProps<{ session: ServiceTaskSession }>()
const emit = defineEmits<{ (e: 'session-expired'): void }>()

const { t } = useI18n()
const router = useRouter()

const flows = ref<ApFlow[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const nextCursor = ref('')
const keyword = ref('')
const actingId = ref('')

const filteredFlows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return flows.value
  return flows.value.filter(
    (flow) =>
      (flow.version?.displayName || '').toLowerCase().includes(kw) ||
      (flow.metadata?.hermesFlowKey || '').toLowerCase().includes(kw) ||
      flow.id.toLowerCase().includes(kw),
  )
})

function flowKey(flow: ApFlow): string {
  return flow.metadata?.hermesFlowKey || ''
}

/** 就绪阶梯：webhook 只触发已发布版本，「有无发布版本」是第一道门槛，启停是第二道 */
function readiness(flow: ApFlow) {
  if (!flow.publishedVersionId) {
    return { type: 'info' as const, effect: 'plain' as const, labelKey: 'automation.stateDraft' }
  }
  return flow.status === 'ENABLED'
    ? { type: 'success' as const, effect: 'light' as const, labelKey: 'automation.stateLive' }
    : { type: 'warning' as const, effect: 'plain' as const, labelKey: 'automation.stateStopped' }
}

function handleApError(error: unknown, fallbackKey: string) {
  const status = (error as { response?: { status?: number } })?.response?.status
  if (status === 401) {
    emit('session-expired')
    return
  }
  const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
  ElMessage.error(message || t(fallbackKey))
  console.error('[FlowsPanel]', error)
}

async function reload() {
  loading.value = true
  try {
    const page = await listAutomationFlows({
      token: props.session.token,
      projectId: props.session.projectId,
      limit: 50,
    })
    flows.value = page.data || []
    nextCursor.value = page.next || ''
  } catch (error) {
    handleApError(error, 'automation.loadFailed')
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!nextCursor.value) return
  loadingMore.value = true
  try {
    const page = await listAutomationFlows({
      token: props.session.token,
      projectId: props.session.projectId,
      cursor: nextCursor.value,
      limit: 50,
    })
    flows.value = [...flows.value, ...(page.data || [])]
    nextCursor.value = page.next || ''
  } catch (error) {
    handleApError(error, 'automation.loadFailed')
  } finally {
    loadingMore.value = false
  }
}

function openDesigner(flow: ApFlow) {
  router.push(`/automation/${flow.id}`)
}

async function copyKey(key: string) {
  try {
    await navigator.clipboard.writeText(key)
    ElMessage.success(t('automation.keyCopied'))
  } catch {
    ElMessage.error(t('automation.copyFailed'))
  }
}

/* ---- create ---- */
const createDialogVisible = ref(false)
const creating = ref(false)
const keyTouched = ref(false)
const createForm = reactive({ displayName: '', flowKey: '' })

function openCreateDialog() {
  createDialogVisible.value = true
}

function resetCreateDialog() {
  createForm.displayName = ''
  createForm.flowKey = ''
  keyTouched.value = false
}

/** 用户没手改过键时，键随名称联动生成（slug + 随机 6 位） */
function syncGeneratedKey() {
  if (keyTouched.value) return
  createForm.flowKey = createForm.displayName.trim()
    ? generateFlowKey(createForm.displayName)
    : ''
}

async function handleCreate() {
  creating.value = true
  try {
    const flow = await createAutomationFlow({
      token: props.session.token,
      projectId: props.session.projectId,
      displayName: createForm.displayName.trim(),
      flowKey: createForm.flowKey.trim(),
    })
    ElMessage.success(t('automation.created', { name: createForm.displayName.trim() }))
    createDialogVisible.value = false
    // 直接进编排器：新建流程的下一步永远是设计它
    router.push(`/automation/${flow.id}`)
  } catch (error) {
    handleApError(error, 'automation.createFailed')
  } finally {
    creating.value = false
  }
}

/* ---- rename ---- */
const renameDialogVisible = ref(false)
const renaming = ref(false)
const renameForm = reactive({ flowId: '', displayName: '' })

function openRenameDialog(flow: ApFlow) {
  renameForm.flowId = flow.id
  renameForm.displayName = flow.version?.displayName || ''
  renameDialogVisible.value = true
}

async function handleRename() {
  renaming.value = true
  try {
    await applyAutomationFlowOperation(renameForm.flowId, props.session.token, {
      type: 'CHANGE_NAME',
      request: { displayName: renameForm.displayName.trim() },
    })
    ElMessage.success(t('automation.renamed'))
    renameDialogVisible.value = false
    await reload()
  } catch (error) {
    handleApError(error, 'automation.renameFailed')
  } finally {
    renaming.value = false
  }
}

/* ---- row commands ---- */
function handleRowCommand(command: string, flow: ApFlow) {
  if (command === 'publish') void handlePublish(flow)
  else if (command === 'toggle') void handleToggle(flow)
  else if (command === 'rename') openRenameDialog(flow)
  else if (command === 'delete') void handleDelete(flow)
}

async function handlePublish(flow: ApFlow) {
  actingId.value = flow.id
  try {
    await applyAutomationFlowOperation(flow.id, props.session.token, {
      type: 'LOCK_AND_PUBLISH',
      request: { status: 'ENABLED' },
    })
    ElMessage.success(t('automation.published', { name: flow.version?.displayName || flow.id }))
    await reload()
  } catch (error) {
    handleApError(error, 'automation.publishFailed')
  } finally {
    actingId.value = ''
  }
}

async function handleToggle(flow: ApFlow) {
  const enable = flow.status !== 'ENABLED'
  actingId.value = flow.id
  try {
    await applyAutomationFlowOperation(flow.id, props.session.token, {
      type: 'CHANGE_STATUS',
      request: { status: enable ? 'ENABLED' : 'DISABLED' },
    })
    ElMessage.success(
      t(enable ? 'automation.enabled' : 'automation.disabled', {
        name: flow.version?.displayName || flow.id,
      }),
    )
    await reload()
  } catch (error) {
    handleApError(error, 'automation.toggleFailed')
  } finally {
    actingId.value = ''
  }
}

async function handleDelete(flow: ApFlow) {
  const name = flow.version?.displayName || flow.id
  try {
    await ElMessageBox.confirm(
      t('automation.deleteConfirm', { name }),
      t('common.delete'),
      { type: 'warning', confirmButtonText: t('common.delete') },
    )
  } catch {
    return
  }
  actingId.value = flow.id
  try {
    await deleteAutomationFlow(flow.id, props.session.token)
    ElMessage.success(t('automation.deleted', { name }))
    await reload()
  } catch (error) {
    handleApError(error, 'automation.deleteFailed')
  } finally {
    actingId.value = ''
  }
}

onMounted(reload)
</script>

<style scoped lang="scss">
.flows-panel {
  .flows-panel__toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
  }

  .flows-panel__search {
    width: 280px;
  }

  .flows-panel__count {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  .flows-panel__actions {
    margin-left: auto;
    display: flex;
    gap: 8px;
  }

  .flows-panel__name {
    font-weight: 500;
  }

  .flows-panel__key {
    display: flex;
    align-items: center;
    gap: 4px;
    min-width: 0;

    code {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 12px;
    }
  }

  .flows-panel__no-key {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  .flows-panel__row-actions {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 4px;
  }

  .flows-panel__danger {
    color: var(--el-color-danger);
  }

  .flows-panel__more {
    display: flex;
    justify-content: center;
    margin-top: 12px;
  }

  .flows-panel__tip {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.5;
    margin-top: 4px;
  }

  :deep(.el-table__row) {
    cursor: pointer;
  }
}
</style>
