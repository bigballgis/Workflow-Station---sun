// Stub for @wangeditor/editor-for-vue (incompatible with Vue 3 ESM)
// Replaced with a simple textarea for MFE extraction
import { h, defineComponent } from 'vue'

export const Editor = defineComponent({
  name: 'WangEditorStub',
  props: { modelValue: String, mode: { type: String, default: 'default' } },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () => h('textarea', {
      value: props.modelValue,
      style: { width: '100%', minHeight: '200px', border: '1px solid #dcdfe6', borderRadius: '4px', padding: '8px' },
      onInput: (e: any) => emit('update:modelValue', e.target.value)
    })
  }
})

export const Toolbar = defineComponent({
  name: 'WangEditorToolbarStub',
  setup() {
    return () => null
  }
})
