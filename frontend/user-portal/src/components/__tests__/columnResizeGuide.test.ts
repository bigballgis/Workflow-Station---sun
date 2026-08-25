import { afterEach, describe, expect, it } from 'vitest'
import {
  attachColumnResizeGuide,
  leftoverColumnWidth,
  startWidthFromHandle,
} from '@platform-shared/list/columnResizeCursor'

describe('attachColumnResizeGuide', () => {
  afterEach(() => {
    document.querySelectorAll('.col-resize-guide').forEach((n) => n.remove())
  })

  it('spans the table box while dragging and is removed on detach', () => {
    const table = document.createElement('div')
    table.className = 'el-table'
    Object.defineProperty(table, 'getBoundingClientRect', {
      value: () => ({ top: 80, left: 0, right: 800, bottom: 480, width: 800, height: 400 }),
    })
    const handle = document.createElement('span')
    Object.defineProperty(handle, 'getBoundingClientRect', {
      value: () => ({ top: 80, left: 180, right: 200, bottom: 104, width: 12, height: 24 }),
    })
    table.appendChild(handle)
    document.body.appendChild(table)

    const guide = attachColumnResizeGuide(handle, 200)
    const line = document.querySelector('.col-resize-guide') as HTMLElement
    expect(line).not.toBeNull()
    expect(line.style.top).toBe('80px')
    expect(line.style.height).toBe('400px')
    expect(line.style.left).toBe('199px')

    guide.move(260)
    expect(line.style.left).toBe('259px')

    guide.detach()
    expect(document.querySelector('.col-resize-guide')).toBeNull()
    table.remove()
  })
})

describe('leftoverColumnWidth', () => {
  it('returns the empty trailing width only when columns are narrower than the viewport', () => {
    expect(leftoverColumnWidth(1000, 800)).toBe(200)
    expect(leftoverColumnWidth(800, 800)).toBe(0)
    expect(leftoverColumnWidth(800, 900)).toBe(0)
    expect(leftoverColumnWidth(0, 800)).toBe(0)
  })
})

describe('startWidthFromHandle', () => {
  it('reads the header cell box when the handle sits in a th', () => {
    const th = document.createElement('th')
    Object.defineProperty(th, 'getBoundingClientRect', {
      value: () => ({ width: 280, height: 24, top: 0, left: 0, right: 280, bottom: 24 }),
    })
    const handle = document.createElement('span')
    th.appendChild(handle)
    expect(startWidthFromHandle(handle, 160)).toBe(280)
  })

  it('falls back to the stored width when there is no header cell', () => {
    expect(startWidthFromHandle(document.createElement('span'), 160)).toBe(160)
  })
})
