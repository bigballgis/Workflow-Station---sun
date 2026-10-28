// ---------------------------------------------------------------------------
// FieldRenderer — wangeditor rich-text editor (Task 6.2)
// Registers its own onBeforeUnmount to destroy the editor instance.
// Create-time wangEditor onChange is ignored until the next tick.
// ---------------------------------------------------------------------------
import { computed, shallowRef, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FieldRendererProps, FieldRendererEmit } from './types'
import { armEditorChangeGate } from '@/composables/editorCreateGate'

export function useFieldEditor(props: FieldRendererProps, emit: FieldRendererEmit) {
  const { t } = useI18n()

  const editorInstance = shallowRef<any>(null)
  let changeReady = false

  const editorToolbarConfig = {}

  const editorConfig = computed(() => ({
    placeholder: props.field.placeholder || t('fieldRenderer.editorPlaceholder'),
    readOnly: props.disabled,
  }))

  function onEditorCreated(editor: any) {
    armEditorChangeGate(
      () => editorInstance.value === editor,
      (ready) => { changeReady = ready },
    )
    editorInstance.value = editor
  }

  function onEditorChange(editor: any) {
    if (!changeReady) return
    emit('update:modelValue', editor.getHtml())
  }

  onBeforeUnmount(() => {
    changeReady = false
    const editor = editorInstance.value
    if (editor && typeof editor.destroy === 'function') {
      editor.destroy()
    }
    editorInstance.value = null
  })

  return {
    editorInstance,
    editorToolbarConfig,
    editorConfig,
    onEditorCreated,
    onEditorChange,
  }
}
