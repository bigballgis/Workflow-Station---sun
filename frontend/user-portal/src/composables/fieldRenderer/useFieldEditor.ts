// ---------------------------------------------------------------------------
// FieldRenderer — wangeditor rich-text editor (Task 6.2)
// Behaviour copied verbatim from FieldRenderer.vue. Registers its own
// onBeforeUnmount to destroy the editor instance.
// ---------------------------------------------------------------------------
import { computed, shallowRef, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FieldRendererProps, FieldRendererEmit } from './types'

export function useFieldEditor(props: FieldRendererProps, emit: FieldRendererEmit) {
  const { t } = useI18n()

  const editorInstance = shallowRef<any>(null)

  const editorToolbarConfig = {}

  const editorConfig = computed(() => ({
    placeholder: props.field.placeholder || t('fieldRenderer.editorPlaceholder'),
    readOnly: props.disabled,
  }))

  function onEditorCreated(editor: any) {
    editorInstance.value = editor
  }

  function onEditorChange(editor: any) {
    const html = editor.getHtml()
    emit('update:modelValue', html)
  }

  onBeforeUnmount(() => {
    if (editorInstance.value) {
      editorInstance.value.destroy()
      editorInstance.value = null
    }
  })

  return {
    editorInstance,
    editorToolbarConfig,
    editorConfig,
    onEditorCreated,
    onEditorChange,
  }
}
