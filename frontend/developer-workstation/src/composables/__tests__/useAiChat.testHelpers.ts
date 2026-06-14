import type { AiChatRequest } from '@/types/aiGeneration'

export function createMockSSEStream(events: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder()
  return new ReadableStream({
    start(controller) {
      for (const event of events) {
        controller.enqueue(encoder.encode(event))
      }
      controller.close()
    }
  })
}

export const mockRequest: AiChatRequest = {
  functionUnitId: 1,
  sessionId: 'session-1',
  message: 'Hello AI',
  phase: 'REQUIREMENTS',
  mode: 'NEW'
}
