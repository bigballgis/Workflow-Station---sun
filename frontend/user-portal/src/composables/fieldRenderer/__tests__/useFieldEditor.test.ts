import { describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick } from 'vue'
import * as fc from 'fast-check'
import { useFieldEditor } from '../useFieldEditor'
import type { FieldRendererEmit, FieldRendererProps } from '../types'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

const EMPTY_SHELL = '<p><br></p>'
const MANUAL_HTML = '<p>typed by user</p>'
/** Same shape EmailFieldExtractor HTML_RICH stores into start variables. */
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

function setup(modelValue = MANUAL_HTML) {
  const emitted: string[] = []
  const props = {
    field: { key: 'email_body', label: 'Body', type: 'editor' },
    modelValue,
  } as FieldRendererProps
  const emit = ((event: string, value: unknown) => {
    if (event === 'update:modelValue') emitted.push(value as string)
  }) as FieldRendererEmit
  const { api, unmount } = withSetup(() => useFieldEditor(props, emit))
  return { api, emitted, unmount }
}

describe('useFieldEditor', () => {
  it('does not emit create-time empty shell over a manually saved value', () => {
    const { api, emitted, unmount } = setup(MANUAL_HTML)
    try {
      api.onEditorCreated(editorWithHtml(EMPTY_SHELL))
      api.onEditorChange(editorWithHtml(EMPTY_SHELL))
      expect(emitted).toEqual([])
    } finally {
      unmount()
    }
  })

  it('does not emit create-time empty shell over Email Monitor HTML_RICH', () => {
    const { api, emitted, unmount } = setup(EMAIL_HTML)
    try {
      api.onEditorCreated(editorWithHtml(EMPTY_SHELL))
      api.onEditorChange(editorWithHtml(EMPTY_SHELL))
      expect(emitted).toEqual([])
    } finally {
      unmount()
    }
  })

  it('does not emit when onChange fires before onCreated', () => {
    const { api, emitted, unmount } = setup(MANUAL_HTML)
    try {
      api.onEditorChange(editorWithHtml(EMPTY_SHELL))
      expect(emitted).toEqual([])
    } finally {
      unmount()
    }
  })

  it('does not emit create-time getHtml even when it already matches the saved value', () => {
    const { api, emitted, unmount } = setup(MANUAL_HTML)
    try {
      api.onEditorCreated(editorWithHtml(MANUAL_HTML))
      api.onEditorChange(editorWithHtml(MANUAL_HTML))
      expect(emitted).toEqual([])
    } finally {
      unmount()
    }
  })

  it('emits after create is ready so the user can clear the field', async () => {
    const { api, emitted, unmount } = setup(MANUAL_HTML)
    try {
      api.onEditorCreated(editorWithHtml(MANUAL_HTML))
      await nextTick()
      api.onEditorChange(editorWithHtml(EMPTY_SHELL))
      expect(emitted).toEqual([EMPTY_SHELL])
    } finally {
      unmount()
    }
  })

  it('emits real edits after create is ready', async () => {
    const { api, emitted, unmount } = setup('')
    try {
      api.onEditorCreated(editorWithHtml(''))
      await nextTick()
      api.onEditorChange(editorWithHtml(MANUAL_HTML))
      expect(emitted).toEqual([MANUAL_HTML])
    } finally {
      unmount()
    }
  })

  it('re-gates after the editor instance is recreated', async () => {
    const { api, emitted, unmount } = setup(EMAIL_HTML)
    try {
      const first = editorWithHtml(EMAIL_HTML)
      const second = editorWithHtml(EMPTY_SHELL)
      api.onEditorCreated(first)
      await nextTick()
      api.onEditorCreated(second)
      api.onEditorChange(second)
      expect(emitted).toEqual([])
      await nextTick()
      api.onEditorChange(editorWithHtml('<p>after recreate</p>'))
      expect(emitted).toEqual(['<p>after recreate</p>'])
    } finally {
      unmount()
    }
  })

  it('does not emit after unmount', async () => {
    const { api, emitted, unmount } = setup(MANUAL_HTML)
    api.onEditorCreated(editorWithHtml(MANUAL_HTML))
    await nextTick()
    unmount()
    api.onEditorChange(editorWithHtml(EMPTY_SHELL))
    expect(emitted).toEqual([])
  })

  it('never emits create-time onChange for any saved HTML', () => {
    fc.assert(
      fc.property(fc.string({ maxLength: 200 }), fc.string({ maxLength: 80 }), (saved, createHtml) => {
        const { api, emitted, unmount } = setup(saved)
        try {
          api.onEditorCreated(editorWithHtml(createHtml))
          api.onEditorChange(editorWithHtml(createHtml))
          expect(emitted).toEqual([])
        } finally {
          unmount()
        }
      }),
      { numRuns: 50 },
    )
  })
})
