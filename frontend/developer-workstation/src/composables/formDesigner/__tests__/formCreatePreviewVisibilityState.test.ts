import { describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { useFormPreviewEventVisibility } from '../useFormPreviewEventVisibility'
import { createPortalFormApi } from '@/utils/formCreateEventRuntime'
import type { PreviewVisibilityBridge } from '@/utils/formCreatePreviewEvents'
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

describe('Preview rule projection', () => {
  it('keeps native rules stable across pending banners and model writes; invalidates for rule overlays', async () => {
    const model = ref<Record<string, unknown>>({ source: '' })
    const rules = [{ type: 'input', field: 'source' }]
    let preview!: ReturnType<typeof useFormPreviewEventVisibility>
    const wrapper = mount(defineComponent({ setup() {
      preview = useFormPreviewEventVisibility({ items: () => [{ kind: 'fields', rule: rules, modelKey: 'root' }],
        previewOption: () => ({}), previewModel: computed(() => model.value), isMyRequestsPreview: computed(() => false),
        emitPreviewData: value => { model.value = value } })
      return () => h('div', JSON.stringify([model.value, preview.formNotifications.value]))
    } }))
    const first = preview.visiblePreviewRules(rules)
    model.value = { source: 'edited' }
    preview.formNotifications.value = [{ uniqueId: 'pending', level: 'INFO', message: 'Waiting' }]
    await nextTick()
    expect(preview.visiblePreviewRules(rules)).toBe(first)
    const bridge = preview.previewVisibilityBridge() as PreviewVisibilityBridge
    const api = createPortalFormApi(() => model.value, patch => { model.value = { ...model.value, ...patch } }, undefined, bridge, undefined, undefined, bridge.overlays)
    api.updateRule('source', { info: 'Updated' })
    expect(preview.visiblePreviewRules(rules)).not.toBe(first)
    wrapper.unmount()
  })
})
