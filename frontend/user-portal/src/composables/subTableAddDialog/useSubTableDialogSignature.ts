import { ref, type Ref } from 'vue'

/**
 * Signature pad state + canvas drawing handlers (mouse + touch) for the
 * sub-table add/edit dialog. Each signature column has its own canvas ref;
 * the data URL is written back to formData on stroke end.
 *
 * Behaviour preserved verbatim from the original SFC (Req 42 touch support).
 */
export function useSubTableDialogSignature(formData: Ref<Record<string, any>>) {
  const signatureCanvasRefs = ref<Record<string, HTMLCanvasElement>>({})
  const signingField = ref<string | null>(null)

  function startSign(e: MouseEvent, field: string) {
    signingField.value = field
    const canvas = signatureCanvasRefs.value[field]
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const rect = canvas.getBoundingClientRect()
    ctx.beginPath()
    ctx.moveTo(e.clientX - rect.left, e.clientY - rect.top)
  }

  function drawSign(e: MouseEvent, field: string) {
    if (signingField.value !== field) return
    const canvas = signatureCanvasRefs.value[field]
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const rect = canvas.getBoundingClientRect()
    ctx.lineWidth = 2
    ctx.lineCap = 'round'
    ctx.strokeStyle = '#000'
    ctx.lineTo(e.clientX - rect.left, e.clientY - rect.top)
    ctx.stroke()
  }

  function endSign(field: string) {
    if (signingField.value !== field) return
    signingField.value = null
    const canvas = signatureCanvasRefs.value[field]
    if (!canvas) return
    formData.value[field] = canvas.toDataURL('image/png')
  }

  function clearSignature(field: string) {
    const canvas = signatureCanvasRefs.value[field]
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    formData.value[field] = ''
  }

  // Touch event handlers for mobile signature support (Req 42)
  function startSignTouch(e: TouchEvent, field: string) {
    signingField.value = field
    const canvas = signatureCanvasRefs.value[field]
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const rect = canvas.getBoundingClientRect()
    const touch = e.touches[0]
    ctx.beginPath()
    ctx.moveTo(touch.clientX - rect.left, touch.clientY - rect.top)
  }

  function drawSignTouch(e: TouchEvent, field: string) {
    if (signingField.value !== field) return
    const canvas = signatureCanvasRefs.value[field]
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const rect = canvas.getBoundingClientRect()
    const touch = e.touches[0]
    ctx.lineWidth = 2
    ctx.lineCap = 'round'
    ctx.strokeStyle = '#000'
    ctx.lineTo(touch.clientX - rect.left, touch.clientY - rect.top)
    ctx.stroke()
  }

  return {
    signatureCanvasRefs,
    signingField,
    startSign,
    drawSign,
    endSign,
    clearSignature,
    startSignTouch,
    drawSignTouch,
  }
}
