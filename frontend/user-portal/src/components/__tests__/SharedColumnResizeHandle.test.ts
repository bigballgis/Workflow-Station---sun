import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ColumnResizeHandle from '@platform-shared/list/ColumnResizeHandle.vue'
import {
  COLUMN_DISPLAY_WIDTH_MAX,
  COLUMN_WIDTH_MIN,
} from '@platform-shared/list/columnResizeCursor'

function mouseEvent(type: string, clientX: number): MouseEvent {
  return new MouseEvent(type, { clientX, bubbles: true })
}

describe('shared ColumnResizeHandle', () => {
  it('emits clamped resize widths while dragging and resizeEnd on mouseup', async () => {
    const wrapper = mount(ColumnResizeHandle, { props: { initialWidth: 200 } })

    await wrapper.find('.col-resize-handle').trigger('mousedown', { clientX: 100 })
    expect(document.body.classList.contains('is-column-resizing')).toBe(true)
    expect(document.querySelector('.col-resize-guide')).not.toBeNull()

    document.dispatchEvent(mouseEvent('mousemove', 150))
    document.dispatchEvent(mouseEvent('mousemove', 100 - 500))
    document.dispatchEvent(mouseEvent('mousemove', 100 + 9000))
    document.dispatchEvent(mouseEvent('mouseup', 150))

    const resizes = wrapper.emitted('resize')
    expect(resizes).toEqual([[250], [COLUMN_WIDTH_MIN], [COLUMN_DISPLAY_WIDTH_MAX]])
    expect(wrapper.emitted('resizeEnd')).toHaveLength(1)
    expect(document.body.classList.contains('is-column-resizing')).toBe(false)
    expect(document.body.style.cursor).toBe('')
    expect(document.querySelector('.col-resize-guide')).toBeNull()
    wrapper.unmount()
  })

  it('cleans up document listeners and body state when unmounted mid-drag', async () => {
    const wrapper = mount(ColumnResizeHandle, { props: { initialWidth: 200 } })

    await wrapper.find('.col-resize-handle').trigger('mousedown', { clientX: 100 })
    document.dispatchEvent(mouseEvent('mousemove', 160))
    expect(wrapper.emitted('resize')).toEqual([[260]])

    wrapper.unmount()

    expect(document.body.classList.contains('is-column-resizing')).toBe(false)
    expect(document.body.style.cursor).toBe('')
    expect(document.body.style.userSelect).toBe('')
    expect(document.querySelector('.col-resize-guide')).toBeNull()
    // Listeners are gone: further moves must not throw or mutate body state.
    document.dispatchEvent(mouseEvent('mousemove', 300))
    expect(document.body.classList.contains('is-column-resizing')).toBe(false)
  })

  it('starts a drag from the rendered header width, not the stored floor', async () => {
    const th = document.createElement('th')
    Object.defineProperty(th, 'getBoundingClientRect', {
      value: () => ({ width: 280, height: 24, top: 0, left: 0, right: 280, bottom: 24 }),
    })
    document.body.appendChild(th)
    const wrapper = mount(ColumnResizeHandle, {
      props: { initialWidth: 160 },
      attachTo: th,
    })

    await wrapper.find('.col-resize-handle').trigger('mousedown', { clientX: 280 })
    document.dispatchEvent(mouseEvent('mousemove', 240))
    document.dispatchEvent(mouseEvent('mouseup', 240))

    expect(wrapper.emitted('resize')).toEqual([[240]])
    wrapper.unmount()
    th.remove()
  })
})
