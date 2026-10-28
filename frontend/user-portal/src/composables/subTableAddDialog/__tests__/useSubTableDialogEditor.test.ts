import { describe, expect, it } from 'vitest'
import { createApp, defineComponent, h, nextTick, ref } from 'vue'
import * as fc from 'fast-check'
import { useSubTableDialogEditor } from '../useSubTableDialogEditor'

const EMPTY_SHELL = '<p><br></p>'
const MANUAL_HTML = '<p>row typed</p>'
const EMAIL_HTML = '<p>Hi <strong>team</strong></p><p>line</p>'

function editorWithHtml(html: string): { getHtml: () => string } {
  return { getHtml: () => html }
}

function withSetup<T>(fn: () => T): { api: T; unmount: () => void } {
  let result!: T
  const app = createApp(defineComponent({
    setup() {
      result = fn()
      return () => h('div')
    },
  }))
  app.mount(document.createElement('div'))
  return { api: result, unmount: () => app.unmount() }
}

describe('useSubTableDialogEditor', () => {
  it('does not write create-time empty shell over a manually saved row value', () => {
    const formData = ref<Record<string, unknown>>({ notes: MANUAL_HTML })
    const { api, unmount } = withSetup(() => useSubTableDialogEditor(formData))
    try {
      api.onEditorCreated(editorWithHtml(EMPTY_SHELL), 'notes')
      api.onEditorChange(editorWithHtml(EMPTY_SHELL), 'notes')
      expect(formData.value.notes).toBe(MANUAL_HTML)
    } finally {
      unmount()
    }
  })

  it('does not write create-time empty shell over Email Monitor HTML_RICH', () => {
    const formData = ref<Record<string, unknown>>({ email_body: EMAIL_HTML })
    const { api, unmount } = withSetup(() => useSubTableDialogEditor(formData))
    try {
      api.onEditorCreated(editorWithHtml(EMPTY_SHELL), 'email_body')
      api.onEditorChange(editorWithHtml(EMPTY_SHELL), 'email_body')
      expect(formData.value.email_body).toBe(EMAIL_HTML)
    } finally {
      unmount()
    }
  })

  it('does not write when onChange fires before onCreated', () => {
    const formData = ref<Record<string, unknown>>({ notes: MANUAL_HTML })
    const { api, unmount } = withSetup(() => useSubTableDialogEditor(formData))
    try {
      api.onEditorChange(editorWithHtml(EMPTY_SHELL), 'notes')
      expect(formData.value.notes).toBe(MANUAL_HTML)
    } finally {
      unmount()
    }
  })

  it('writes after create is ready so the user can clear the column', async () => {
    const formData = ref<Record<string, unknown>>({ notes: MANUAL_HTML })
    const { api, unmount } = withSetup(() => useSubTableDialogEditor(formData))
    try {
      api.onEditorCreated(editorWithHtml(MANUAL_HTML), 'notes')
      await nextTick()
      api.onEditorChange(editorWithHtml(EMPTY_SHELL), 'notes')
      expect(formData.value.notes).toBe(EMPTY_SHELL)
    } finally {
      unmount()
    }
  })

  it('keeps an unready field when a sibling field is already ready', async () => {
    const formData = ref<Record<string, unknown>>({
      notes: MANUAL_HTML,
      email_body: EMAIL_HTML,
    })
    const { api, unmount } = withSetup(() => useSubTableDialogEditor(formData))
    try {
      api.onEditorCreated(editorWithHtml(MANUAL_HTML), 'notes')
      await nextTick()
      api.onEditorCreated(editorWithHtml(EMPTY_SHELL), 'email_body')
      api.onEditorChange(editorWithHtml(EMPTY_SHELL), 'email_body')
      expect(formData.value.email_body).toBe(EMAIL_HTML)
      api.onEditorChange(editorWithHtml('<p>notes edit</p>'), 'notes')
      expect(formData.value.notes).toBe('<p>notes edit</p>')
    } finally {
      unmount()
    }
  })

  it('re-gates a field after destroyEditors', async () => {
    const formData = ref<Record<string, unknown>>({ notes: MANUAL_HTML })
    const { api, unmount } = withSetup(() => useSubTableDialogEditor(formData))
    try {
      const editor = { getHtml: () => EMPTY_SHELL, destroy: () => {} }
      api.onEditorCreated(editor, 'notes')
      await nextTick()
      api.destroyEditors()
      api.onEditorCreated(editor, 'notes')
      api.onEditorChange(editor, 'notes')
      expect(formData.value.notes).toBe(MANUAL_HTML)
    } finally {
      unmount()
    }
  })

  it('does not write after unmount', async () => {
    const formData = ref<Record<string, unknown>>({ notes: MANUAL_HTML })
    const { api, unmount } = withSetup(() => useSubTableDialogEditor(formData))
    api.onEditorCreated(editorWithHtml(MANUAL_HTML), 'notes')
    await nextTick()
    unmount()
    api.onEditorChange(editorWithHtml(EMPTY_SHELL), 'notes')
    expect(formData.value.notes).toBe(MANUAL_HTML)
  })

  it('never overwrites saved HTML with create-time onChange', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 40 }).filter((s) => s !== 'notes'),
        fc.string({ minLength: 1, maxLength: 200 }),
        fc.string({ maxLength: 80 }),
        (field, saved, createHtml) => {
          const formData = ref<Record<string, unknown>>({ [field]: saved })
          const { api, unmount } = withSetup(() => useSubTableDialogEditor(formData))
          try {
            api.onEditorCreated(editorWithHtml(createHtml), field)
            api.onEditorChange(editorWithHtml(createHtml), field)
            expect(formData.value[field]).toBe(saved)
          } finally {
            unmount()
          }
        },
      ),
      { numRuns: 50 },
    )
  })
})
