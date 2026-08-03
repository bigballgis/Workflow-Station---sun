import { ref } from 'vue'
import type { AiGeneratedData, GenerationPreviewData } from '@/types/aiGeneration'
import {
  loadDraft as loadGenerationDraft,
  clearDraft as clearGenerationDraft
} from '@/composables/useAiChat'

/** Generation draft restored into the preview. */
export interface RestoredGenerationDraft {
  generatedData: AiGeneratedData
  previewData: GenerationPreviewData | null
  /** 这份结果已经 Apply 过：卡片直接还原成只读的 "Applied ✓"，不再提供 Apply 按钮。 */
  applied?: boolean
}

/**
 * Task 16.4 + 17.1: Draft restoration state.
 *
 * Owns two independent drafts:
 *  - degradation draft (`ai_draft_{functionUnitId}`) — a plain prompt string,
 *  - generation draft (`ai_generation_draft_{functionUnitId}_{sessionId}`).
 *
 * Behavior matches the previous inline ChatDialog implementation byte-for-byte;
 * UI-facing side effects (input text, preview restore, toasts) are delegated to
 * the supplied callbacks so the SFC stays the orchestrator.
 */
export function useChatDialogDraft(
  functionUnitId: () => number,
  sessionId: () => string,
  t: (key: string) => string,
  callbacks: {
    setInputText: (text: string) => void
    restoreGeneration: (draft: RestoredGenerationDraft) => void
  }
) {
  const hasDraft = ref(false)
  let draftData: { prompt: string; timestamp: number; functionUnitId: number } | null = null
  const hasGenerationDraft = ref(false)
  let generationDraftData: { generatedData: AiGeneratedData; previewData: GenerationPreviewData | null; timestamp: number; sessionId: string } | null = null

  function checkForDraft() {
    // Check for degradation draft (ai_draft_{functionUnitId})
    const draftKey = `ai_draft_${functionUnitId()}`
    const raw = localStorage.getItem(draftKey)
    if (raw) {
      try {
        const parsed = JSON.parse(raw)
        if (Date.now() - parsed.timestamp < 24 * 60 * 60 * 1000) {
          draftData = parsed
          hasDraft.value = true
        } else {
          localStorage.removeItem(draftKey)
        }
      } catch {
        localStorage.removeItem(draftKey)
      }
    }

    // Check for generation draft (ai_generation_draft_{functionUnitId}_{sessionId})
    if (sessionId()) {
      const genDraft = loadGenerationDraft(functionUnitId(), sessionId())
      if (genDraft?.applied) {
        // 已经写入过的结果不是"未完成的草稿"，没有什么可以让用户取舍的——直接把卡片放回聊天区
        // 的只读态。走 alert 分支会变成每次开面板都要点一次"恢复"才看得到自己刚生成的东西。
        callbacks.restoreGeneration({
          generatedData: genDraft.generatedData,
          previewData: genDraft.previewData,
          applied: true
        })
      } else if (genDraft) {
        generationDraftData = genDraft
        hasGenerationDraft.value = true
      }
    }
  }

  function saveDraftToLocalStorage(inputText: string) {
    const draft = { prompt: inputText, timestamp: Date.now(), functionUnitId: functionUnitId() }
    localStorage.setItem(`ai_draft_${functionUnitId()}`, JSON.stringify(draft))
    ElMessage.success(t('ai.degradation.draftSaved'))
  }

  function restoreDraft() {
    if (draftData) {
      callbacks.setInputText(draftData.prompt)
      ElMessage.info(t('ai.degradation.draftRestored'))
    }
    hasDraft.value = false
    draftData = null
    localStorage.removeItem(`ai_draft_${functionUnitId()}`)
  }

  function restoreGenerationDraft() {
    if (generationDraftData) {
      callbacks.restoreGeneration({
        generatedData: generationDraftData.generatedData,
        previewData: generationDraftData.previewData
      })
      ElMessage.info(t('ai.draft.restore'))
    }
    hasGenerationDraft.value = false
    generationDraftData = null
  }

  function dismissDraft() {
    hasDraft.value = false
    draftData = null
    localStorage.removeItem(`ai_draft_${functionUnitId()}`)
  }

  function dismissGenerationDraft() {
    hasGenerationDraft.value = false
    if (generationDraftData && sessionId()) {
      clearGenerationDraft(functionUnitId(), sessionId())
    }
    generationDraftData = null
  }

  return {
    hasDraft,
    hasGenerationDraft,
    checkForDraft,
    saveDraftToLocalStorage,
    restoreDraft,
    restoreGenerationDraft,
    dismissDraft,
    dismissGenerationDraft
  }
}
