import { afterEach, describe, expect, it } from 'vitest'
import { attachColumnResizeGuide } from '@platform-shared/list/columnResizeCursor'

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
