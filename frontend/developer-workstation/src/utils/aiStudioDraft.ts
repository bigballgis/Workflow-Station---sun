import type { ComposerTranslation } from 'vue-i18n'

/**
 * AI Studio 草稿契约。
 *
 * 入口弹窗（AiStudioEntryDialog）与未来的 AI Studio 工作台共享这份 localStorage 契约：
 * 工作台每完成一个阶段就写入草稿，入口弹窗读取后展示 "Continue AI draft · 阶段名"。
 * 草稿按功能单元隔离（key 带 functionUnitId），换功能单元不会串草稿。
 */

/**
 * AI Studio 引导阶段，顺序即 UI 展示顺序 = FunctionUnitEdit 设计器 Tab 顺序，
 * 末尾追加 Validation（最终校验门禁）。Tab 顺序变了这里要跟着调。
 */
export const AI_STUDIO_PHASES = [
  'PROCESS_DESIGN',
  'TABLE_DESIGN',
  'FORM_DESIGN',
  'VIEW_DESIGN',
  'ACTION_DESIGN',
  'AUTOMATION',
  'CONNECTIONS',
  'EMAIL_TEMPLATES',
  'EMAIL_MONITORS',
  'DECISION_DESIGN',
  'VALIDATION'
] as const

export type AiStudioPhase = (typeof AI_STUDIO_PHASES)[number]

export interface AiStudioDraft {
  /** 草稿名称，取功能单元名 */
  name: string
  /** 上次停留的阶段 */
  phase: AiStudioPhase
  /** 已确认（Confirm phase）的阶段 */
  completedPhases?: AiStudioPhase[]
  /** ISO 时间戳，工作台写入时更新 */
  updatedAt?: string
}

export type AiStudioEntryMode = 'new' | 'continue'

export interface AiStudioOpenPayload {
  mode: AiStudioEntryMode
  /** mode 为 continue 时必有；new 时为 null */
  draft: AiStudioDraft | null
}

const STORAGE_PREFIX = 'dw-ai-studio-draft:'

export function aiStudioDraftStorageKey(functionUnitId: number): string {
  return `${STORAGE_PREFIX}${functionUnitId}`
}

/**
 * 读取该功能单元的 AI Studio 草稿；没有或已损坏返回 null。
 * 损坏的草稿会 console.warn 并清除——入口弹窗随之禁用 "Continue AI draft" 卡片，
 * 用户能看到"没有可继续的草稿"，不是静默吞掉。
 */
export function loadAiStudioDraft(functionUnitId: number): AiStudioDraft | null {
  const key = aiStudioDraftStorageKey(functionUnitId)
  const raw = localStorage.getItem(key)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Partial<AiStudioDraft>
    // 末阶段曾叫 REVIEW（2026-08-09 更名 VALIDATION）：旧草稿就地迁移而不是作废
    if ((parsed.phase as string) === 'REVIEW') parsed.phase = 'VALIDATION'
    if (Array.isArray(parsed.completedPhases)) {
      parsed.completedPhases = parsed.completedPhases.map(p =>
        ((p as string) === 'REVIEW' ? 'VALIDATION' : p) as AiStudioPhase)
    }
    if (
      typeof parsed.name === 'string' &&
      parsed.name.length > 0 &&
      (AI_STUDIO_PHASES as readonly string[]).includes(parsed.phase ?? '')
    ) {
      // completedPhases 里混入未知值时只丢弃该值，不整个作废草稿
      const completed = Array.isArray(parsed.completedPhases)
        ? parsed.completedPhases.filter((p): p is AiStudioPhase =>
          (AI_STUDIO_PHASES as readonly string[]).includes(p))
        : []
      return { ...(parsed as AiStudioDraft), completedPhases: completed }
    }
    throw new Error(`unexpected draft shape: ${raw}`)
  } catch (e) {
    console.warn(`[ai-studio] discarding corrupt draft at ${key}`, e)
    localStorage.removeItem(key)
    return null
  }
}

/** 工作台写草稿；入口弹窗据此点亮 "Continue AI draft"。 */
export function saveAiStudioDraft(functionUnitId: number, draft: AiStudioDraft): void {
  localStorage.setItem(aiStudioDraftStorageKey(functionUnitId), JSON.stringify(draft))
}

export function clearAiStudioDraft(functionUnitId: number): void {
  localStorage.removeItem(aiStudioDraftStorageKey(functionUnitId))
}

/**
 * 阶段 key → 当前语言下的阶段名。除 Review 外全部复用设计器 Tab 的既有 i18n key，
 * 保证引导条文案与 Tab 文案永远一致，不再维护一套平行翻译。
 */
export function aiStudioPhaseLabel(t: ComposerTranslation, phase: AiStudioPhase): string {
  const keyByPhase: Record<AiStudioPhase, string> = {
    PROCESS_DESIGN: 'functionUnit.process',
    TABLE_DESIGN: 'functionUnit.tables',
    FORM_DESIGN: 'functionUnit.forms',
    VIEW_DESIGN: 'functionUnit.viewDesign',
    ACTION_DESIGN: 'functionUnit.actionDesign',
    AUTOMATION: 'functionUnit.automation',
    CONNECTIONS: 'connection.title',
    EMAIL_TEMPLATES: 'emailTemplate.title',
    EMAIL_MONITORS: 'emailMonitor.title',
    DECISION_DESIGN: 'functionUnit.decisions',
    VALIDATION: 'ai.studio.step.validation'
  }
  return t(keyByPhase[phase])
}
