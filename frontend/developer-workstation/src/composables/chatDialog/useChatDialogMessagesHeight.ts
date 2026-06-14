import { ref, provide, onMounted, onBeforeUnmount } from 'vue'

/**
 * Observes the `.chat-dialog__messages` container height and provides it as
 * `chatMessagesHeight` so InlineDocumentViewer can size its max-height.
 *
 * Registers its own mount/unmount lifecycle hooks; behavior is identical to the
 * previous inline ResizeObserver wiring in ChatDialog.vue.
 */
export function useChatDialogMessagesHeight() {
  const messagesHeight = ref(400)
  let resizeObserver: ResizeObserver | null = null
  provide('chatMessagesHeight', messagesHeight)

  onMounted(() => {
    // Observe messages container height for InlineDocumentViewer
    const messagesEl = document.querySelector('.chat-dialog__messages')
    if (messagesEl) {
      resizeObserver = new ResizeObserver((entries) => {
        for (const entry of entries) {
          messagesHeight.value = entry.contentRect.height
        }
      })
      resizeObserver.observe(messagesEl)
    }
  })

  onBeforeUnmount(() => {
    resizeObserver?.disconnect()
    resizeObserver = null
  })

  return { messagesHeight }
}
