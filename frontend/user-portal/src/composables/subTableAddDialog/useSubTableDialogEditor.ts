import { shallowRef, onBeforeUnmount, type Ref } from 'vue'
import { armEditorChangeGate } from '@/composables/editorCreateGate'

/**
 * wangeditor instance lifecycle for editor-type columns in the sub-table
 * add/edit dialog. Instances are tracked per field; content changes are
 * mirrored into formData and editors are destroyed on unmount.
 *
 * Create-time wangEditor onChange is ignored until the next tick.
 */
export function useSubTableDialogEditor(formData: Ref<Record<string, any>>) {
  const editorInstances = shallowRef<Record<string, any>>({})
  const changeReadyFields = new Set<string>()

  function onEditorCreated(editor: any, field: string) {
    armEditorChangeGate(
      () => editorInstances.value[field] === editor,
      (ready) => {
        if (ready) changeReadyFields.add(field)
        else changeReadyFields.delete(field)
      },
    )
    editorInstances.value = { ...editorInstances.value, [field]: editor }
  }

  function onEditorChange(editor: any, field: string) {
    if (!changeReadyFields.has(field)) return
    formData.value[field] = editor.getHtml()
  }

  function destroyEditors() {
    changeReadyFields.clear()
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
