import { describe, expect, it, beforeEach } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { useListColumnLayout } from '@platform-shared/list/useListColumnLayout'
import { KIND_CONTENT_FLOOR } from '@platform-shared/list/columnWidthLayout'

function mountLayout(
  storageKey: string,
  fields: string[],
  opts: { extraWidth?: number; defaultWidthOf?: (field: string) => number } = {},
) {
  const Host = defineComponent({
    setup() {
      return useListColumnLayout({
        storageKey,
        fields,
        extraWidth: opts.extraWidth ?? 0,
        defaultWidthOf: opts.defaultWidthOf ?? ((field) => (field === 'wide' ? 180 : 120)),
      })
    },
    template: '<div />',
  })
  return mount(Host)
}

async function setViewport(
  wrapper: ReturnType<typeof mountLayout>,
  width: number,
  height = 400,
) {
  const el = document.createElement('div')
  Object.defineProperty(el, 'clientWidth', { value: width })
  Object.defineProperty(el, 'clientHeight', { value: height })
  wrapper.vm.gridScrollRef = el
  await nextTick()
}

describe('useListColumnLayout', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('starts from the per-field default and clamps a persisted base', async () => {
    const w = mountLayout('portal-list-layout:test', ['name', 'wide'])
    const { widthOf, setWidth } = w.vm

    expect(widthOf('name')).toBe(120)
    expect(widthOf('wide')).toBe(180)

    setWidth('name', 30)
    expect(widthOf('name')).toBe(60)
    setWidth('name', 900)
    expect(widthOf('name')).toBe(900)
    w.vm.persistWidths()
    expect(widthOf('name')).toBe(600)
    w.unmount()
  })

  it('remembers base widths for the same storage key and not another', async () => {
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

  it('exposes the scrollport height for el-table so the header can freeze', async () => {
    const w = mountLayout('portal-list-layout:height', ['name'])
    expect(w.vm.gridTableHeight).toBeUndefined()
    await setViewport(w, 800, 420)
    expect(w.vm.gridTableHeight).toBe(420)
    w.unmount()
  })

  it('keeps the inner wrapper at 100% when columns overflow so Action can stick', async () => {
    const w = mountLayout('portal-list-layout:overflow-inner', ['name', 'wide'], {
      extraWidth: 50,
      defaultWidthOf: (field) => (field === 'wide' ? 200 : 100),
    })
    await setViewport(w, 200)
    expect(w.vm.gridFits).toBe(false)
    expect(w.vm.gridInnerStyle).toEqual({ width: '100%', minWidth: '100%' })
    w.unmount()
  })

  it('spreads leftover across data columns instead of parking a spacer', async () => {
    const w = mountLayout('portal-list-layout:spread', ['name', 'wide'], {
      extraWidth: 50,
      defaultWidthOf: (field) => (field === 'wide' ? 200 : 100),
    })
    await setViewport(w, 450)
    expect(w.vm.widthOf('name')).toBe(133)
    expect(w.vm.widthOf('wide')).toBe(267)
    w.unmount()
  })

  it('persists the inverted base, not the leftover share', async () => {
    const w = mountLayout('portal-list-layout:invert', ['name', 'wide'], {
      extraWidth: 50,
      defaultWidthOf: (field) => (field === 'wide' ? 200 : 100),
    })
    await setViewport(w, 450)
    w.vm.setWidth('name', 133)
    w.vm.persistWidths()
    w.unmount()

    const restored = mountLayout('portal-list-layout:invert', ['name', 'wide'], {
      extraWidth: 50,
      defaultWidthOf: (field) => (field === 'wide' ? 200 : 100),
    })
    expect(restored.vm.widthOf('name')).toBe(100)
    restored.unmount()
  })

  it('does not redistribute leftover onto other columns while a drag is in progress', async () => {
    const w = mountLayout('portal-list-layout:preview', ['name', 'wide'], {
      extraWidth: 50,
      defaultWidthOf: (field) => (field === 'wide' ? 200 : 100),
    })
    await setViewport(w, 450)
    expect(w.vm.widthOf('name')).toBe(133)
    expect(w.vm.widthOf('wide')).toBe(267)
    w.vm.setWidth('name', 180)
    expect(w.vm.widthOf('name')).toBe(180)
    expect(w.vm.widthOf('wide')).toBe(267)
    w.unmount()
  })

  it('uses the kind content floor when no per-field default is supplied', () => {
    const Host = defineComponent({
      setup() {
        return useListColumnLayout({
          storageKey: 'portal-list-layout:kind-floor',
          fields: ['id'],
          labelOf: () => 'ID',
          kindOf: () => 'TEXT',
        })
      },
      template: '<div />',
    })
    const w = mount(Host)
    expect(w.vm.widthOf('id')).toBe(KIND_CONTENT_FLOOR.TEXT)
    w.unmount()
  })
})
