import { ref } from 'vue'

export interface EmailAttachmentItem {
  name: string
  content: string
}

function parseAttachments(raw: unknown): EmailAttachmentItem[] {
  if (!raw || typeof raw !== 'string' || !raw.trim()) {
    return []
  }
  try {
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed
      .filter((item): item is Record<string, unknown> => item != null && typeof item === 'object')
      .map(item => ({
        name: typeof item.name === 'string' ? item.name : '',
        content: typeof item.content === 'string' ? item.content : ''
      }))
  } catch {
    return []
  }
}

export function useSendTaskEmailAttachments(
  updateExtProp: (name: string, value: unknown) => void
) {
  const emailAttachments = ref<EmailAttachmentItem[]>([])

  function loadFromExtension(raw: unknown) {
    emailAttachments.value = parseAttachments(raw)
  }

  function persistAttachments() {
    const items = emailAttachments.value.filter(a => a.name.trim() || a.content.trim())
    updateExtProp('emailAttachments', items.length > 0 ? JSON.stringify(items) : '')
  }

  function addAttachment() {
    emailAttachments.value.push({ name: '', content: '' })
  }

  function removeAttachment(index: number) {
    emailAttachments.value.splice(index, 1)
    persistAttachments()
  }

  function onAttachmentChange() {
    persistAttachments()
  }

  return {
    emailAttachments,
    loadFromExtension,
    addAttachment,
    removeAttachment,
    onAttachmentChange
  }
}
