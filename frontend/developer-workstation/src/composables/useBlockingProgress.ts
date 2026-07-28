import { ref } from 'vue'

/**
 * Full-screen blocking progress for long-running designer ops
 * (e.g. Save that provisions missing tables after cross-FU JSON paste).
 * Indeterminate only — no fake percentages.
 */
export function useBlockingProgress() {
  const visible = ref(false)
  const message = ref('')
  const detail = ref('')

  function open(msg: string, detailMsg = '') {
    message.value = msg
    detail.value = detailMsg
    visible.value = true
  }

  function setMessage(msg: string, detailMsg?: string) {
    message.value = msg
    if (detailMsg !== undefined) {
      detail.value = detailMsg
    }
  }

  function close() {
    visible.value = false
    message.value = ''
    detail.value = ''
  }

  return { visible, message, detail, open, setMessage, close }
}

export type BlockingProgressApi = ReturnType<typeof useBlockingProgress>
