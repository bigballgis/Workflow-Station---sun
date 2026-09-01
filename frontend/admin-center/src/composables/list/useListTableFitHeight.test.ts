import { describe, expect, it } from 'vitest'
import { contentHeight, leftoverForTable, paginationReserve } from '@/composables/list/useListTableFitHeight'

describe('useListTableFitHeight helpers', () => {
  it('reserves pagination height plus its top margin', () => {
    const card = document.createElement('div')
    const pag = document.createElement('div')
    pag.className = 'list-pagination'
    Object.defineProperty(pag, 'offsetHeight', { value: 32 })
    pag.style.marginTop = '16px'
    card.appendChild(pag)
    document.body.appendChild(card)
    expect(paginationReserve(card)).toBe(48)
    card.remove()
  })

  it('caps leftover by page chrome, not the stretched table card', () => {
    const page = document.createElement('div')
    page.className = 'page-container'
    Object.defineProperty(page, 'clientHeight', { value: 800 })
    const filter = document.createElement('div')
    filter.className = 'filter-card'
    Object.defineProperty(filter, 'offsetHeight', { value: 200 })
    filter.style.marginBottom = '12px'
    const card = document.createElement('div')
    card.className = 'table-card'
    Object.defineProperty(card, 'clientHeight', { value: 800 })
    const pag = document.createElement('div')
    pag.className = 'list-pagination'
    Object.defineProperty(pag, 'offsetHeight', { value: 32 })
    pag.style.marginTop = '16px'
    card.appendChild(pag)
    page.appendChild(filter)
    page.appendChild(card)
    document.body.appendChild(page)
    expect(leftoverForTable(card)).toBe(540)
    page.remove()
  })

  it('sums data rows so a stretched body table is ignored', () => {
    const scroll = document.createElement('div')
    const header = document.createElement('div')
    header.className = 'el-table__header-wrapper'
    Object.defineProperty(header, 'offsetHeight', { value: 40 })
    const wrap = document.createElement('div')
    wrap.className = 'el-table__body-wrapper'
    Object.defineProperty(wrap, 'clientWidth', { value: 800 })
    Object.defineProperty(wrap, 'scrollWidth', { value: 800 })
    const body = document.createElement('table')
    body.className = 'el-table__body'
    Object.defineProperty(body, 'offsetHeight', { value: 2000 })
    const tbody = document.createElement('tbody')
    for (let i = 0; i < 2; i++) {
      const tr = document.createElement('tr')
      tr.className = 'el-table__row'
      Object.defineProperty(tr, 'offsetHeight', { value: 32 })
      tbody.appendChild(tr)
    }
    body.appendChild(tbody)
    wrap.appendChild(body)
    scroll.appendChild(header)
    scroll.appendChild(wrap)
    expect(contentHeight(scroll)).toBe(104)
  })
})
