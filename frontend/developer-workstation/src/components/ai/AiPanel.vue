<template>
  <Teleport to="body">
    <Transition :name="isDetached ? '' : 'ai-panel-slide'">
      <div
        v-if="visible"
        ref="panelRef"
        class="ai-panel"
        :class="{ 'ai-panel--detached': isDetached }"
        :style="panelStyle"
      >
        <!-- Header -->
        <div
          class="ai-panel__header"
          @mousedown="onHeaderMouseDown"
        >
          <span class="ai-panel__title">
            <el-icon :size="18"><MagicStick /></el-icon>
            {{ t('ai.panel.title') }}
          </span>
          <div class="ai-panel__header-actions">
            <!-- Task 17.4: Session history dropdown -->
            <el-dropdown
              v-if="ready"
              trigger="click"
              @command="handleSessionSwitch"
            >
              <el-button
                :icon="Clock"
                circle
                size="small"
                :title="t('ai.panel.sessionHistory')"
              />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="s in sortedSessions"
                    :key="s.sessionId"
                    :command="s.sessionId"
                    :class="{ 'is-active': s.sessionId === currentSessionId }"
                  >
                    <span class="ai-panel__session-time">{{ formatTime(s.createdAt) }}</span>
                    <el-tag
                      size="small"
                      :type="s.status === 'ACTIVE' ? 'success' : 'info'"
                    >
                      {{ s.status }}
                    </el-tag>
                    <el-tag size="small">
                      {{ s.mode }}
                    </el-tag>
                    <el-tag
                      size="small"
                      type="warning"
                    >
                      {{ s.currentPhase }}
                    </el-tag>
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="sortedSessions.length === 0"
                    disabled
                  >
                    {{ t('ai.panel.sessionHistory') }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-tooltip :content="isDetached ? t('ai.panel.attach') : t('ai.panel.detach')">
              <el-button
                :icon="isDetached ? ScaleToOriginal : FullScreen"
                circle
                size="small"
                @click="toggleDetach"
              />
            </el-tooltip>
            <el-button
              :icon="Close"
              circle
              size="small"
              @click="handleClose"
            />
          </div>
        </div>

        <!-- Lock Conflict Overlay -->
        <div
          v-if="lockConflict"
          class="ai-panel__lock-overlay"
        >
          <el-icon
            :size="48"
            color="#E6A23C"
          >
            <Lock />
          </el-icon>
          <div class="ai-panel__lock-info">
            <p class="ai-panel__lock-title">
              {{ t('ai.panel.lockTitle') }}
            </p>
            <p class="ai-panel__lock-detail">
              {{ t('ai.panel.lockUser') }}<strong>{{ conflictLockInfo?.userName || t('ai.panel.unknownUser') }}</strong>
            </p>
            <p class="ai-panel__lock-detail">
              {{ t('ai.panel.lockTime') }}{{ formatTime(conflictLockInfo?.lockedAt) }}
            </p>
          </div>
          <el-button
            type="warning"
            @click="handleRequestForceUnlock"
          >
            {{ t('ai.panel.requestForceUnlock') }}
          </el-button>
        </div>

        <!-- Main Content -->
        <div
          v-else-if="ready"
          class="ai-panel__body"
        >
          <div class="ai-panel__chat">
            <ChatDialog
              ref="chatDialogRef"
              :function-unit-id="functionUnitId"
              :session-id="currentSessionId"
              :phase="sessionComposable.currentPhase.value"
              :mode="currentMode"
              :completed-phases="completedPhases"
              :initial-messages="initialMessages"
              @phase-complete="handlePhaseComplete"
              @apply="handleApply"
              @regenerate="handleRegenerate"
              @send-message="handleSendMessage"
              @document="handleDocumentReceived"
              @session-created="handleSessionCreated"
            />
          </div>
          <div class="ai-panel__doc">
            <DocumentPanel
              ref="documentPanelRef"
              :function-unit-id="functionUnitId"
            />
          </div>
        </div>

        <!-- Loading -->
        <div
          v-else
          class="ai-panel__loading"
        >
          <el-icon
            class="is-loading"
            :size="32"
          >
            <Loading />
          </el-icon>
          <span>{{ t('ai.panel.initializing') }}</span>
        </div>

        <!-- Resize handle for detached mode -->
        <div
          v-if="isDetached"
          class="ai-panel__resize-handle"
          @mousedown="onResizeMouseDown"
        />
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Lock, MagicStick, Loading, FullScreen, ScaleToOriginal } from '@element-plus/icons-vue'
import { Clock } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import ChatDialog from './ChatDialog.vue'
import DocumentPanel from './DocumentPanel.vue'
import { useAiLock } from '@/composables/useAiLock'
import { useAiSession } from '@/composables/useAiSession'
import { useAiEvents } from '@/composables/useAiEvents'
import { useAiChat } from '@/composables/useAiChat'
import { useAiPanelSidebar } from '@/composables/aiPanel/useAiPanelSidebar'
import { useAiPanelLayout } from '@/composables/aiPanel/useAiPanelLayout'
import { aiGenerationApi } from '@/api/aiGeneration'
import type {
  AiPhase,
  AiMode,
  AiMessage,
  AiGeneratedData,
  AiValidationError,
  AiDocumentType
} from '@/types/aiGeneration'

const { t } = useI18n()

const props = defineProps<{
  functionUnitId: number
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  dataApplied: []
}>()

// Refs
const chatDialogRef = ref<InstanceType<typeof ChatDialog> | null>(null)
const documentPanelRef = ref<InstanceType<typeof DocumentPanel> | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const ready = ref(false)
const currentMode = ref<AiMode>('NEW')
const completedPhases = ref<AiPhase[]>([])
const initialMessages = ref<AiMessage[]>([])

// Sidebar width tracking (docked-mode left offset)
const {
  sidebarWidth,
  updateSidebarWidth,
  startWatchingSidebar,
  stopWatchingSidebar
} = useAiPanelSidebar()

// Docked/detached layout: panel style, drag + resize interactions
const {
  isDetached,
  panelStyle,
  toggleDetach,
  onHeaderMouseDown,
  onResizeMouseDown
} = useAiPanelLayout(sidebarWidth)

// Composables
const lockComposable = useAiLock()
const { lockConflict, conflictLockInfo } = lockComposable

const sessionComposable = useAiSession()

const functionUnitIdRef = ref(props.functionUnitId)
const eventsComposable = useAiEvents(functionUnitIdRef)

const chatComposable = useAiChat()

// Computed
const currentSessionId = computed(() =>
  sessionComposable.currentSession.value?.sessionId || ''
)

// Task 17.4: Sorted sessions for history dropdown (desc by creation time)
const sortedSessions = computed(() => {
  return [...sessionComposable.sessions.value].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  )
})

// Format time helper
function formatTime(time?: string) {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : ''
}

// Open sequence
async function openPanel() {
  ready.value = false
  functionUnitIdRef.value = props.functionUnitId
  updateSidebarWidth()
  startWatchingSidebar()

  try {
    const lockSuccess = await lockComposable.acquireLock(props.functionUnitId)
    if (!lockSuccess) return

    eventsComposable.connect()
    registerEventHandlers()

    await sessionComposable.loadSessions(props.functionUnitId)

    const activeSession = sessionComposable.findActiveSession(props.functionUnitId)
    if (activeSession) {
      const msgs = await sessionComposable.restoreSession(activeSession)
      currentMode.value = activeSession.mode
      initialMessages.value = [...msgs]

      // 根据已有文档推断实际阶段（防止后端阶段未更新的情况）
      const actualPhase = await detectPhaseFromDocuments(props.functionUnitId, activeSession.currentPhase)
      if (actualPhase !== activeSession.currentPhase) {
        sessionComposable.setPhase(actualPhase)
        // 同步更新后端
        if (activeSession.sessionId) {
          aiGenerationApi.updateSessionPhase(activeSession.sessionId, actualPhase)
            .catch(err => console.error('Failed to sync phase:', err))
        }
      }
      computeCompletedPhases(sessionComposable.currentPhase.value)
      ready.value = true
      return
    }

    const completedSession = sessionComposable.findLatestCompletedSession(props.functionUnitId)
    if (completedSession) {
      await showCompletedSessionDialog(completedSession)
      return
    }

    startNewSession()
  } catch (err: any) {
    ElMessage.error(err.message || t('ai.panel.initFailed'))
    closePanel()
  }
}

async function showCompletedSessionDialog(completedSession: any) {
  try {
    const action = await ElMessageBox.confirm(
      t('ai.panel.sessionCompleted'),
      t('ai.panel.sessionOption'),
      {
        confirmButtonText: t('ai.panel.newSession'),
        cancelButtonText: t('ai.panel.viewLastSession'),
        distinguishCancelAndClose: true,
        type: 'info'
      }
    )
    if (action === 'confirm') {
      startNewSession()
    }
  } catch (action) {
    if (action === 'cancel') {
      const msgs = await sessionComposable.restoreSession(completedSession)
      currentMode.value = completedSession.mode
      initialMessages.value = [...msgs]
      computeCompletedPhases(completedSession.currentPhase)
      ready.value = true
    } else {
      closePanel()
    }
  }
}

function startNewSession() {
  sessionComposable.createSession(props.functionUnitId, 'NEW')
  currentMode.value = 'NEW'
  completedPhases.value = []
  initialMessages.value = []
  ready.value = true
}

function computeCompletedPhases(currentPhase: AiPhase) {
  const phases: AiPhase[] = ['REQUIREMENTS', 'DESIGN', 'GENERATION']
  const idx = phases.indexOf(currentPhase)
  completedPhases.value = phases.slice(0, idx)
}

/**
 * 根据已有文档推断实际阶段。
 * 如果 DESIGN 文档已存在但会话阶段仍为 REQUIREMENTS，说明后端阶段未同步，需要修正。
 */
async function detectPhaseFromDocuments(functionUnitId: number, dbPhase: AiPhase): Promise<AiPhase> {
  try {
    // 检查是否有 DESIGN 文档
    const designRes = await aiGenerationApi.getDocumentVersions(functionUnitId, 'DESIGN' as AiDocumentType)
    const hasDesign = designRes.data && designRes.data.length > 0

    if (hasDesign && dbPhase === 'REQUIREMENTS') {
      return 'DESIGN'
    }
    // 可以扩展：如果有 GENERATION 数据但阶段是 DESIGN，也可以修正
  } catch {
    // 查询失败不影响正常流程
  }
  return dbPhase
}

/**
 * Task 17.4: Switch to a historical session.
 * COMPLETED sessions are loaded in read-only mode (no new messages allowed).
 */
async function handleSessionSwitch(sessionId: string) {
  if (sessionId === currentSessionId.value) return
  const session = sessionComposable.sessions.value.find(s => s.sessionId === sessionId)
  if (!session) return

  try {
    const msgs = await sessionComposable.restoreSession(session)
    currentMode.value = session.mode
    initialMessages.value = [...msgs]
    computeCompletedPhases(session.currentPhase)
    // Force ChatDialog to re-render with new messages
    chatDialogRef.value?.setMessages?.([...msgs])
  } catch (err: any) {
    ElMessage.error(err.message || t('ai.panel.initFailed'))
  }
}

function handleClose() {
  closePanel()
}

async function closePanel() {
  chatComposable.cancel()
  stopWatchingSidebar()
  try {
    if (lockComposable.isLocked.value) {
      await lockComposable.releaseLock(props.functionUnitId)
    }
  } catch { /* ignore */ }

  eventsComposable.disconnect()
  sessionComposable.endSession()

  ready.value = false
  isDetached.value = false
  lockComposable.reset()
  initialMessages.value = []
  completedPhases.value = []

  emit('update:visible', false)
}

function registerEventHandlers() {
  eventsComposable.onForceUnlockRequest((data: any) => {
    showForceUnlockDialog(data)
  })

  eventsComposable.onForceUnlockResponse((data: any) => {
    if (data.accepted) {
      ElMessage.success(t('ai.panel.forceUnlockAccepted'))
      nextTick(() => openPanel())
    } else {
      ElMessage.warning(t('ai.panel.forceUnlockRejected'))
    }
  })

  eventsComposable.onWriteSuccess((data: any) => {
    emit('dataApplied')
    ElMessage.success(t('ai.panel.dataApplied'))
    // Pass warnings from write_success to ChatDialog if present
    if (data?.warnings && data.warnings.length > 0) {
      chatDialogRef.value?.setValidationWarnings(data.warnings)
    }
  })

  eventsComposable.onWriteError((data: any) => {
    ElMessage.error(data?.message || t('ai.panel.dataApplyFailed'))
  })
}

async function showForceUnlockDialog(data: any) {
  try {
    await ElMessageBox.confirm(
      t('ai.panel.forceUnlockMsg', { name: data.requesterName || data.requesterId || t('ai.panel.unknownUser') }),
      t('ai.panel.forceUnlockRequest'),
      {
        confirmButtonText: t('ai.panel.forceUnlockAccept'),
        cancelButtonText: t('ai.panel.forceUnlockReject'),
        type: 'warning',
        distinguishCancelAndClose: true
      }
    )
    await lockComposable.respondForceUnlock(props.functionUnitId, true)
    closePanel()
  } catch (action) {
    if (action === 'cancel') {
      await lockComposable.respondForceUnlock(props.functionUnitId, false)
    }
  }
}

async function handleRequestForceUnlock() {
  try {
    await lockComposable.requestForceUnlock(props.functionUnitId)
    ElMessage.info(t('ai.panel.forceUnlockSent'))
  } catch (err: any) {
    ElMessage.error(err.message || t('ai.panel.forceUnlockSentFailed'))
  }
}

function handlePhaseComplete(_phase: AiPhase) {
  // 后端已自动推进阶段，这里只更新前端状态
  const advanced = sessionComposable.advancePhase()
  if (advanced) {
    computeCompletedPhases(sessionComposable.currentPhase.value)
    // Auto-trigger AI generation for the new phase
    const newPhase = sessionComposable.currentPhase.value
    if (newPhase === 'DESIGN' || newPhase === 'GENERATION') {
      // 使用 guard 防止重复触发（如果已经在 streaming 则跳过）
      if (!chatComposable.isStreaming.value) {
        nextTick(() => {
          autoTriggerPhase(newPhase)
        })
      }
    }
  }
}

async function autoTriggerPhase(phase: AiPhase) {
  const triggerMessages: Record<string, string> = {
    DESIGN: '[AUTO_TRIGGER] Please generate a complete design document based on the existing requirements document.',
    GENERATION: '[AUTO_TRIGGER] Please generate complete function unit component data based on the existing requirements and design documents.'
  }
  const message = triggerMessages[phase]
  if (!message) return

  chatDialogRef.value?.autoSendMessage(message)
}

async function handleApply(data: AiGeneratedData) {
  try {
    let sessionId = currentSessionId.value
    if (!sessionId) {
      // Fallback: refresh sessions from backend in case local state missed SSE "session" event.
      await sessionComposable.loadSessions(props.functionUnitId)
      const activeSession = sessionComposable.findActiveSession(props.functionUnitId)
      if (activeSession?.sessionId) {
        sessionComposable.setCurrentSession(activeSession)
        sessionId = activeSession.sessionId
      }
    }

    if (!sessionId) {
      ElMessage.error(t('ai.panel.initFailed'))
      return
    }

    await aiGenerationApi.applyGeneratedData(props.functionUnitId, {
      sessionId,
      generatedData: data
    })
  } catch (err: any) {
    if (err.response?.status === 422) {
      const errors: AiValidationError[] = err.response.data?.error?.details?.errors || []
      chatDialogRef.value?.setValidationErrors(errors)
    } else {
      const msg = err.response?.data?.error?.message
        || err.response?.data?.message
        || err.message
        || t('ai.panel.applyFailed')
      ElMessage.error(msg)
    }
  }
}

function handleRegenerate() { /* ChatDialog handles internally */ }
function handleSendMessage() { /* lock extension handled server-side */ }

function handleDocumentReceived(docType: string, _content: string) {
  // 收到文档 SSE 事件后，刷新右侧文档面板
  documentPanelRef.value?.refreshDocType(docType as any)
}

function handleSessionCreated(sessionId: string) {
  sessionComposable.setCurrentSession({
    sessionId,
    functionUnitId: props.functionUnitId,
    currentPhase: sessionComposable.currentPhase.value,
    mode: currentMode.value,
    status: 'ACTIVE',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  })
}

watch(() => props.visible, (newVal) => {
  if (newVal) openPanel()
})

watch(() => props.functionUnitId, (newVal) => {
  functionUnitIdRef.value = newVal
})
</script>

<style lang="scss" scoped>
.ai-panel {
  position: fixed;
  bottom: 0;
  right: 0;
  height: 80vh;
  /* left is set via :style binding */
  background: #fff;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.15);
  border-radius: 12px 12px 0 0;

  &--detached {
    /* Override docked styles */
    bottom: auto;
    right: auto;
    height: auto;
    border-radius: 12px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
    min-width: 600px;
    min-height: 400px;
  }
}

.ai-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
  user-select: none;

  .ai-panel--detached & {
    cursor: move;
  }
}

.ai-panel__header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.ai-panel__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.ai-panel__body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.ai-panel__chat {
  width: 60%;
  border-right: 1px solid #ebeef5;
  overflow: hidden;
}

.ai-panel__doc {
  width: 40%;
  overflow: hidden;
}

.ai-panel__loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #909399;
  font-size: 14px;
}

.ai-panel__lock-overlay {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 40px;
}

.ai-panel__lock-info {
  text-align: center;
}

.ai-panel__lock-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.ai-panel__lock-detail {
  font-size: 14px;
  color: #606266;
  margin: 4px 0;
}

.ai-panel__session-time {
  font-size: 12px;
  color: #606266;
  margin-right: 6px;
}

.ai-panel__resize-handle {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 16px;
  height: 16px;
  cursor: nwse-resize;
  background: linear-gradient(135deg, transparent 50%, #c0c4cc 50%, #c0c4cc 60%, transparent 60%, transparent 70%, #c0c4cc 70%, #c0c4cc 80%, transparent 80%);
  border-radius: 0 0 12px 0;
}

// Slide transition (docked mode only)
.ai-panel-slide-enter-active,
.ai-panel-slide-leave-active {
  transition: transform 0.3s ease;
}

.ai-panel-slide-enter-from,
.ai-panel-slide-leave-to {
  transform: translateY(100%);
}
</style>
