import api from './index'
import { readAmToken } from '@/utils/amToken'
import type { HermesMasterChatPayload, HermesMasterChatResult } from '@/types/hermesMaster'

export const hermesMasterApi = {
  /**
   * Hermes Master 单轮对话。后端默认经 Automation 走 Content Organizer，此时用不到 AMToken；
   * 只有后端切回直连 AI gateway（ai-generation.hermes-master.provider=GAIP）时才用它，
   * 所以仍照常经 X-AM-Token 头透传，读不到就不带。
   * 错误由气泡自己展示，所以关掉全局错误提示。
   */
  chat: (data: HermesMasterChatPayload, signal?: AbortSignal) => {
    const amToken = readAmToken()
    return api.post<unknown, { data: HermesMasterChatResult }>(
      '/ai-generation/hermes-master/chat',
      data,
      {
        timeout: 120000,
        signal,
        silentError: true,
        ...(amToken ? { headers: { 'X-AM-Token': amToken } } : {})
      }
    )
  }
}
