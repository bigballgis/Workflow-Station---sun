import { shallowRef, onBeforeUnmount, type Ref } from 'vue'

/**
 * wangeditor instance lifecycle for editor-type columns in the sub-table
 * add/edit dialog. Instances are tracked per field; content changes are
 * mirrored into formData and editors are destroyed on unmount.
 *
 * Behaviour preserved verbatim from the original SFC.
 */
export function useSubTableDialogEditor(formData: Ref<Record<string, any>>) {
  const editorInstances = shallowRef<Record<string, any>>({})

  function onEditorCreated(editor: any, field: string) {
    editorInstances.value = { ...editorInstances.value, [field]: editor }
  }

  function onEditorChange(editor: any, field: string) {
    formData.value[field] = editor.getHtml()
  }

  function destroyEditors() {
    for (const editor of Object.values(editorInstances.value)) {
      if (editor && typeof editor.destroy === 'function') {
        editor.destroy()
      }
    }
    editorInstances.value = {}
  }

  onBeforeUnmount(() => {
    destroyEditors()
  })

  return {
    editorInstances,
    onEditorCreated,
    onEditorChange,
    destroyEditors,
  }
}
