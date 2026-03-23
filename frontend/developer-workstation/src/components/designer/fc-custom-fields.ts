/**
 * Custom field components for form-create / fc-designer.
 *
 * form-create does not natively support editor, signature, transfer, cascader,
 * or slider types. We register thin Vue wrappers so that both the designer
 * canvas and the preview dialog can render them.
 */
import { defineComponent, h, ref, onMounted, onBeforeUnmount } from 'vue'
import {
  ElInput,
  ElTransfer,
  ElCascader,
  ElSlider,
} from 'element-plus'

/* ── editor ─────────────────────────────────────────────────────────────────── */
export const FcEditor = defineComponent({
  name: 'FcEditor',
  props: {
    modelValue: { type: String, default: '' },
    rows: { type: Number, default: 5 },
    placeholder: { type: String, default: '' },
    maxlength: { type: Number },
    disabled: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h(ElInput, {
        modelValue: props.modelValue,
        type: 'textarea',
        rows: props.rows,
        placeholder: props.placeholder || 'Rich text editor',
        maxlength: props.maxlength,
        disabled: props.disabled,
        'onUpdate:modelValue': (v: string) => emit('update:modelValue', v),
      })
  },
})

/* ── signature ──────────────────────────────────────────────────────────────── */
export const FcSignature = defineComponent({
  name: 'FcSignature',
  props: {
    modelValue: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    const canvasRef = ref<HTMLCanvasElement | null>(null)
    let signing = false
    let observer: ResizeObserver | null = null

    function getCtx() {
      return canvasRef.value?.getContext('2d') ?? null
    }

    function syncSize() {
      const canvas = canvasRef.value
      if (!canvas) return
      const w = canvas.parentElement?.clientWidth || canvas.offsetWidth || 400
      if (canvas.width !== w || canvas.height !== 120) {
        canvas.width = w
        canvas.height = 120
      }
    }

    onMounted(() => {
      // Initial sync after a tick so the layout has settled
      setTimeout(syncSize, 50)
      // Watch for resize so canvas pixel buffer stays in sync with CSS size
      if (canvasRef.value) {
        observer = new ResizeObserver(syncSize)
        observer.observe(canvasRef.value.parentElement || canvasRef.value)
      }
    })

    // Clean up observer
    onBeforeUnmount(() => { observer?.disconnect() })

    function onDown(e: MouseEvent) {
      if (props.disabled) return
      syncSize()
      signing = true
      const ctx = getCtx()
      if (!ctx || !canvasRef.value) return
      const r = canvasRef.value.getBoundingClientRect()
      ctx.beginPath()
      ctx.moveTo(e.clientX - r.left, e.clientY - r.top)
    }
    function onMove(e: MouseEvent) {
      if (!signing) return
      const ctx = getCtx()
      if (!ctx || !canvasRef.value) return
      const r = canvasRef.value.getBoundingClientRect()
      ctx.lineWidth = 2
      ctx.lineCap = 'round'
      ctx.strokeStyle = '#000'
      ctx.lineTo(e.clientX - r.left, e.clientY - r.top)
      ctx.stroke()
    }
    function onUp() {
      if (!signing) return
      signing = false
      if (canvasRef.value) {
        emit('update:modelValue', canvasRef.value.toDataURL('image/png'))
      }
    }
    function clear() {
      const ctx = getCtx()
      if (ctx && canvasRef.value) {
        ctx.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height)
      }
      emit('update:modelValue', '')
    }

    return () =>
      h('div', { style: 'width:100%' }, [
        h('canvas', {
          ref: canvasRef,
          style: 'display:block;width:100%;height:120px;border:1px solid #dcdfe6;border-radius:4px;cursor:crosshair;background:#fff',
          onMousedown: onDown,
          onMousemove: onMove,
          onMouseup: onUp,
          onMouseleave: onUp,
        }),
        h(
          'div',
          { style: 'margin-top:4px' },
          [
            h(
              'button',
              {
                type: 'button',
                style: 'padding:4px 12px;font-size:12px;cursor:pointer;border:1px solid #dcdfe6;border-radius:3px;background:#fff',
                onClick: clear,
              },
              'Clear',
            ),
          ],
        ),
      ])
  },
})

/* ── transfer ───────────────────────────────────────────────────────────────── */
export const FcTransfer = defineComponent({
  name: 'FcTransfer',
  props: {
    modelValue: { type: Array, default: () => [] },
    options: { type: Array, default: () => [] },
    leftTitle: { type: String, default: 'Source' },
    rightTitle: { type: String, default: 'Target' },
    disabled: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h(ElTransfer, {
        modelValue: props.modelValue as any[],
        data: (props.options as any[]).map((o: any) => ({ key: o.value, label: o.label })),
        titles: [props.leftTitle, props.rightTitle],
        filterable: true,
        disabled: props.disabled,
        'onUpdate:modelValue': (v: any) => emit('update:modelValue', v),
      })
  },
})

/* ── cascader ───────────────────────────────────────────────────────────────── */
export const FcCascader = defineComponent({
  name: 'FcCascader',
  inheritAttrs: false,
  props: {
    modelValue: { type: Array, default: () => [] },
    options: { type: Array, default: () => [] },
    placeholder: { type: String, default: 'Please select' },
    cascaderProps: { type: Object },
    disabled: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  setup(props, { emit, attrs }) {
    return () => {
      // form-create may pass options via props or attrs; prefer whichever has children
      const opts = (props.options && (props.options as any[]).length > 0 ? props.options : (attrs.options as any[] || [])) as any[]
      return h(ElCascader, {
        modelValue: props.modelValue as any[],
        options: opts,
        props: props.cascaderProps,
        placeholder: props.placeholder,
        clearable: true,
        disabled: props.disabled,
        style: 'width:100%',
        'onUpdate:modelValue': (v: any) => emit('update:modelValue', v),
      })
    }
  },
})

/* ── slider ─────────────────────────────────────────────────────────────────── */
export const FcSlider = defineComponent({
  name: 'FcSlider',
  props: {
    modelValue: { type: Number, default: 0 },
    min: { type: Number, default: 0 },
    max: { type: Number, default: 100 },
    step: { type: Number, default: 1 },
    disabled: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h(ElSlider, {
        modelValue: props.modelValue,
        min: props.min,
        max: props.max,
        step: props.step,
        disabled: props.disabled,
        style: 'width:100%',
        'onUpdate:modelValue': (v: any) => emit('update:modelValue', v),
      })
  },
})
