import { describe, expect, it, beforeEach } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { useListColumnLayout } from '../useListColumnLayout'
import { leftoverColumnWidth } from '@platform-shared/list/columnResizeCursor'

function mountLayout(storageKey: string, fields: string[]) {
  const Host = defineComponent({
    setup() {
      return useListColumnLayout({
        storageKey,
        fields,
        defaultWidthOf: (field) => (field === 'wide' ? 180 : 120),
      })
    },
    template: '<div />',
  })
  return mount(Host)
}

describe('useListColumnLayout', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('starts from the per-field default and clamps a drag', async () => {
    const w = mountLayout('portal-list-layout:test', ['name', 'wide'])
    const { widthOf, setWidth } = w.vm

    expect(widthOf('name')).toBe(120)
    expect(widthOf('wide')).toBe(180)

    setWidth('name', 30)
    expect(widthOf('name')).toBe(60)
    setWidth('name', 900)
    expect(widthOf('name')).toBe(600)
    w.unmount()
  })

  it('remembers widths for the same storage key and not another', async () => {
    const first = mountLayout('portal-list-layout:a', ['name'])
    first.vm.setWidth('name', 200)
    first.vm.persistWidths()
    first.unmount()

    const restored = mountLayout('portal-list-layout:a', ['name'])
    expect(restored.vm.widthOf('name')).toBe(200)
    restored.unmount()

    const other = mountLayout('portal-list-layout:b', ['name'])
    expect(other.vm.widthOf('name')).toBe(120)
    other.unmount()
  })

  it('drops a corrupt session entry instead of crashing the list', async () => {
    sessionStorage.setItem('portal-list-layout:bad', '{not-json')
    const w = mountLayout('portal-list-layout:bad', ['name'])
    expect(w.vm.widthOf('name')).toBe(120)
    w.unmount()
  })

  it('does not write session storage when the key is empty', async () => {
    const w = mountLayout('', ['name'])
    w.vm.setWidth('name', 200)
    w.vm.persistWidths()
    await nextTick()
    expect(sessionStorage.length).toBe(0)
    w.unmount()
  })

  it('parks leftover width at the trailing edge instead of stretching columns', () => {
    expect(leftoverColumnWidth(1000, 800)).toBe(200)
    expect(leftoverColumnWidth(800, 800)).toBe(0)
    expect(leftoverColumnWidth(800, 900)).toBe(0)
    expect(leftoverColumnWidth(0, 800)).toBe(0)
  })
})
