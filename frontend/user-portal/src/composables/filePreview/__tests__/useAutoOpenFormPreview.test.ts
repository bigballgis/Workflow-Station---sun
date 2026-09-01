import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick, ref } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { FILE_PREVIEW_STORAGE_KEY } from '../filePreviewSync'
import { closeFilePreview, useFilePreviewState } from '../useFilePreview'
import { autoOpenPreviewWatchKey, useAutoOpenFormPreview } from '../useAutoOpenFormPreview'

const loadPreference = vi.fn()

vi.mock('@/stores/userPreference', () => ({
  useUserPreferenceStore: () => ({
    autoPreviewOnOpen: true,
    load: (...args: unknown[]) => loadPreference(...args),
  }),
}))

function flushDebounce() {
  vi.advanceTimersByTime(400)
  return Promise.resolve().then(() => Promise.resolve())
}

function mountAutoOpen(opts: {
  enabled?: () => boolean
  processInstanceId?: () => string | undefined
  collect: () => { url: string; name: string; cannotDownload?: boolean }[]
}): VueWrapper {
  const Comp = defineComponent({
    setup() {
      useAutoOpenFormPreview({
        enabled: opts.enabled ?? (() => true),
        processInstanceId: opts.processInstanceId ?? (() => 'pi-1'),
        collect: opts.collect,
      })
      return () => null
    },
  })
  return mount(Comp)
}

describe('useAutoOpenFormPreview', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    loadPreference.mockReset()
    loadPreference.mockResolvedValue(undefined)
    vi.spyOn(window, 'open').mockReturnValue(null)
  })

  afterEach(() => {
    closeFilePreview()
    const state = useFilePreviewState()
    state.url = ''
    state.name = ''
    state.items = []
    state.index = 0
    localStorage.removeItem(FILE_PREVIEW_STORAGE_KEY)
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('opens after an in-place upload URL write, not only formData replacement', async () => {
    const formData = ref<Record<string, unknown>>({ upload: '' })
    const wrapper = mountAutoOpen({
      collect: () => {
        const url = String(formData.value.upload || '')
        return url ? [{ url, name: 'a.pdf' }] : []
      },
    })
    formData.value.upload = '/stored/a.pdf'
    await nextTick()
    await flushDebounce()
    expect(useFilePreviewState().url).toBe('/stored/a.pdf')
    expect(useFilePreviewState().visible).toBe(true)
    wrapper.unmount()
  })

  it('does not open when the caller has not opted in', async () => {
    const wrapper = mountAutoOpen({
      enabled: () => false,
      collect: () => [{ url: '/stored/a.pdf', name: 'a.pdf' }],
    })
    await nextTick()
    await flushDebounce()
    expect(useFilePreviewState().visible).toBe(false)
    expect(useFilePreviewState().url).toBe('')
    wrapper.unmount()
  })

  it('changes the watch key when collect returns a new URL', () => {
    expect(autoOpenPreviewWatchKey(true, 'pi-1', [])).not.toBe(
      autoOpenPreviewWatchKey(true, 'pi-1', [{ url: '/a.pdf', name: 'a.pdf' }]),
    )
  })
})
