// ---------------------------------------------------------------------------
// FieldRenderer — signature canvas with Undo history (Task 6.3, Req 28)
// Behaviour copied verbatim from FieldRenderer.vue. Exposes setupSignatureCanvas
// (invoked from the orchestrator's onMounted) and registers its own
// onBeforeUnmount to disconnect the ResizeObserver.
// ---------------------------------------------------------------------------
import { ref, nextTick, onBeforeUnmount } from 'vue'
import type { FieldRendererProps, FieldRendererEmit } from './types'

export function useFieldSignature(props: FieldRendererProps, emit: FieldRendererEmit) {
  const signatureCanvasRef = ref<HTMLCanvasElement | null>(null)
  let signing = false
  let sigObserver: ResizeObserver | null = null

  // Signature history stack for Undo (Req 28, max 20 snapshots)
  const signatureHistory = ref<string[]>([])
  const MAX_SIGNATURE_HISTORY = 20

  function getSigCtx() {
    return signatureCanvasRef.value?.getContext('2d') ?? null
  }

  function syncCanvasSize() {
    const canvas = signatureCanvasRef.value
    if (!canvas) return
    const w = canvas.parentElement?.clientWidth || canvas.offsetWidth || 400
    if (canvas.width !== w || canvas.height !== 120) {
      canvas.width = w
      canvas.height = 120
    }
  }

  function getCanvasPos(e: MouseEvent | Touch) {
    const canvas = signatureCanvasRef.value
    if (!canvas) return { x: 0, y: 0 }
    const r = canvas.getBoundingClientRect()
    return { x: e.clientX - r.left, y: e.clientY - r.top }
  }

  function onSigDown(e: MouseEvent) {
    if (props.disabled) return
    syncCanvasSize()
    // Save snapshot before new stroke for Undo (Req 28)
    saveSignatureSnapshot()
    signing = true
    const ctx = getSigCtx()
    if (!ctx) return
    const pos = getCanvasPos(e)
    ctx.beginPath()
    ctx.moveTo(pos.x, pos.y)
  }

  function onSigMove(e: MouseEvent) {
    if (!signing) return
    const ctx = getSigCtx()
    if (!ctx) return
    const pos = getCanvasPos(e)
    ctx.lineWidth = 2
    ctx.lineCap = 'round'
    ctx.strokeStyle = '#000'
    ctx.lineTo(pos.x, pos.y)
    ctx.stroke()
  }

  function onSigUp() {
    if (!signing) return
    signing = false
    if (signatureCanvasRef.value) {
      emit('update:modelValue', signatureCanvasRef.value.toDataURL('image/png'))
    }
  }

  function onTouchStart(e: TouchEvent) {
    if (props.disabled || !e.touches.length) return
    syncCanvasSize()
    // Save snapshot before new stroke for Undo (Req 28)
    saveSignatureSnapshot()
    signing = true
    const ctx = getSigCtx()
    if (!ctx) return
    const pos = getCanvasPos(e.touches[0])
    ctx.beginPath()
    ctx.moveTo(pos.x, pos.y)
  }

  function onTouchMove(e: TouchEvent) {
    if (!signing || !e.touches.length) return
    const ctx = getSigCtx()
    if (!ctx) return
    const pos = getCanvasPos(e.touches[0])
    ctx.lineWidth = 2
    ctx.lineCap = 'round'
    ctx.strokeStyle = '#000'
    ctx.lineTo(pos.x, pos.y)
    ctx.stroke()
  }

  function saveSignatureSnapshot() {
    const canvas = signatureCanvasRef.value
    if (!canvas) return
    const snapshot = canvas.toDataURL('image/png')
    if (signatureHistory.value.length >= MAX_SIGNATURE_HISTORY) {
      signatureHistory.value.shift() // FIFO: remove oldest
    }
    signatureHistory.value.push(snapshot)
  }

  function undoSignature() {
    if (signatureHistory.value.length === 0) return
    const snapshot = signatureHistory.value.pop()!
    const canvas = signatureCanvasRef.value
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const img = new Image()
    img.onload = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height)
      ctx.drawImage(img, 0, 0)
      emit('update:modelValue', canvas.toDataURL('image/png'))
    }
    img.src = snapshot
  }

  function clearSignature() {
    const ctx = getSigCtx()
    if (ctx && signatureCanvasRef.value) {
      ctx.clearRect(0, 0, signatureCanvasRef.value.width, signatureCanvasRef.value.height)
    }
    signatureHistory.value = []
    emit('update:modelValue', '')
  }

  /** Signature canvas setup — invoked from the orchestrator's onMounted. */
  function setupSignatureCanvas() {
    if (props.field.type === 'signature' && !props.readonly) {
      nextTick(() => {
        setTimeout(syncCanvasSize, 50)
        if (signatureCanvasRef.value) {
          sigObserver = new ResizeObserver(syncCanvasSize)
          sigObserver.observe(
            signatureCanvasRef.value.parentElement || signatureCanvasRef.value,
          )
        }
      })
    }
  }

  onBeforeUnmount(() => {
    sigObserver?.disconnect()
  })

  return {
    signatureCanvasRef,
    signatureHistory,
    onSigDown,
    onSigMove,
    onSigUp,
    onTouchStart,
    onTouchMove,
    undoSignature,
    clearSignature,
    setupSignatureCanvas,
  }
}
