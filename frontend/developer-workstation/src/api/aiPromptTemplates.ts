import api from './index'

/** 三段系统提示词的相位标识，与后端 dw_ai_prompt_templates.phase 一致 */
export type AiPromptPhase = 'REQUIREMENTS' | 'DESIGN' | 'GENERATION'

export const AI_PROMPT_PHASES: AiPromptPhase[] = ['REQUIREMENTS', 'DESIGN', 'GENERATION']

export interface AiPromptTemplate {
  phase: AiPromptPhase
  /** 当前实际送给模型的全文 */
  content: string
  /** BUILT_IN = 镜像内置默认值；CUSTOM = 库里的覆盖值 */
  source: 'BUILT_IN' | 'CUSTOM'
  /** 内置默认值全文（无论 source 都返回，供"还原默认"前的对比） */
  defaultContent: string
  updatedBy?: string | null
  updatedAt?: string | null
}

export const aiPromptTemplateApi = {
  list: () =>
    api.get<unknown, { data: AiPromptTemplate[] }>('/ai-generation/prompt-templates'),

  /** 保存覆盖值——编辑保存与导入文件都走这个接口 */
  save: (phase: AiPromptPhase, content: string) =>
    api.put<unknown, { data: AiPromptTemplate }>(`/ai-generation/prompt-templates/${phase}`, { content }),

  /** 删除覆盖值，恢复到镜像内置默认值 */
  reset: (phase: AiPromptPhase) =>
    api.delete<unknown, { data: AiPromptTemplate }>(`/ai-generation/prompt-templates/${phase}`)
}
