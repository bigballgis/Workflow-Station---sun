import assert from 'node:assert/strict'
import test from 'node:test'
import {
  createHelpScroll,
  resolveHelpScrollTarget,
  type HelpScrollDom,
  type HelpScrollPosition,
} from './helpScroll.ts'

/** Fake article: `anchors` maps section ids to the scrollTop that reveals them. */
function fakeDom(anchors: Record<string, number>) {
  const state = {
    position: { main: 0, page: 0 } as HelpScrollPosition,
    log: [] as string[],
    settle: Promise.resolve(),
  }
  const dom: HelpScrollDom = {
    read: () => ({ ...state.position }),
    write(position) {
      state.position = { ...position }
      state.log.push(`write ${position.main}/${position.page}`)
    },
    reveal(id) {
      if (!(id in anchors)) return false
      state.position = { main: anchors[id], page: 0 }
      state.log.push(`reveal ${id}`)
      return true
    },
    settled: () => state.settle,
  }
  return { dom, state }
}

function deferred(): { promise: Promise<void>; resolve: () => void } {
  let resolve!: () => void
  const promise = new Promise<void>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

test('target: saved position wins, then the hash section, then the top', () => {
  const saved = { main: 420, page: 0 }
  assert.deepEqual(resolveHelpScrollTarget('#fail', saved), { kind: 'restore', position: saved })
  assert.deepEqual(resolveHelpScrollTarget('#fail', undefined), { kind: 'anchor', id: 'fail' })
  assert.deepEqual(resolveHelpScrollTarget('', undefined), { kind: 'top' })
  assert.deepEqual(resolveHelpScrollTarget('#', undefined), { kind: 'top' })
})

test('opening a link with a hash scrolls the section into view', async () => {
  const { dom, state } = fakeDom({ fail: 2300 })
  await createHelpScroll(dom).navigate('0/ac-audit-ldap-sync#fail', '#fail')
  assert.deepEqual(state.position, { main: 2300, page: 0 })
})

test('an unknown hash or no hash lands on the top', async () => {
  const { dom, state } = fakeDom({ fail: 2300 })
  const scroll = createHelpScroll(dom)
  state.position = { main: 900, page: 300 }
  await scroll.navigate('1/ac-audit-ldap-sync#gone', '#gone')
  assert.deepEqual(state.position, { main: 0, page: 0 })
  state.position = { main: 900, page: 300 }
  await scroll.navigate('2/table-design', '')
  assert.deepEqual(state.position, { main: 0, page: 0 })
})

test('back / forward return to where the reader was on that entry', async () => {
  const { dom, state } = fakeDom({ fail: 2300 })
  const scroll = createHelpScroll(dom)
  await scroll.navigate('0/ac-audit-ldap-sync#fail', '#fail')
  state.position = { main: 2750, page: 0 } // reader scrolled further down
  scroll.record()
  await scroll.navigate('1/table-design', '')
  assert.deepEqual(state.position, { main: 0, page: 0 })
  state.position = { main: 640, page: 0 }
  scroll.record()

  scroll.traversal() // back
  await scroll.navigate('0/ac-audit-ldap-sync#fail', '#fail')
  assert.deepEqual(state.position, { main: 2750, page: 0 })

  scroll.traversal() // forward
  await scroll.navigate('1/table-design', '')
  assert.deepEqual(state.position, { main: 640, page: 0 })
})

test('back to an entry with nothing recorded falls back to its hash', async () => {
  const { dom, state } = fakeDom({ fail: 2300 })
  const scroll = createHelpScroll(dom)
  scroll.traversal()
  await scroll.navigate('3/ac-audit-ldap-sync#fail', '#fail')
  assert.deepEqual(state.position, { main: 2300, page: 0 })
})

test('a new link click never restores a position, even for a recycled entry key', async () => {
  const { dom, state } = fakeDom({ fail: 2300 })
  const scroll = createHelpScroll(dom)
  await scroll.navigate('1/table-design', '')
  state.position = { main: 640, page: 0 }
  scroll.record()
  // Back to entry 0, then a click pushes /table-design again as entry 1.
  scroll.traversal()
  await scroll.navigate('0/', '')
  await scroll.navigate('1/table-design', '')
  assert.deepEqual(state.position, { main: 0, page: 0 })
})

test('back to a recycled entry key returns to where the reader left the new entry, not the discarded one', async () => {
  const { dom, state } = fakeDom({ fail: 2300 })
  const scroll = createHelpScroll(dom)
  await scroll.navigate('1/table-design', '')
  state.position = { main: 1500, page: 0 }
  scroll.record()
  // Back to entry 0, a click pushes /table-design again as entry 1 and the reader leaves it at the top
  // (no scroll, so nothing is recorded), then a click on to entry 2 and back to entry 1.
  scroll.traversal()
  await scroll.navigate('0/', '')
  await scroll.navigate('1/table-design', '')
  await scroll.navigate('2/ac-audit-ldap-sync', '')
  scroll.traversal()
  await scroll.navigate('1/table-design', '')
  assert.deepEqual(state.position, { main: 0, page: 0 })
})

test('the target is applied again once figures have loaded', async () => {
  const { dom, state } = fakeDom({ fail: 2300 })
  const images = deferred()
  state.settle = images.promise
  const done = createHelpScroll(dom).navigate('0/ac-audit-ldap-sync#fail', '#fail')
  assert.deepEqual(state.log, ['reveal fail'])
  images.resolve()
  await done
  assert.deepEqual(state.log, ['reveal fail', 'reveal fail'])
})

test('reader input or a newer navigation stops the re-apply', async () => {
  const { dom, state } = fakeDom({ fail: 2300, members: 1800 })
  const scroll = createHelpScroll(dom)

  const images = deferred()
  state.settle = images.promise
  const first = scroll.navigate('0/ac-audit-ldap-sync#fail', '#fail')
  scroll.userInput()
  state.position = { main: 1200, page: 0 }
  images.resolve()
  await first
  assert.deepEqual(state.position, { main: 1200, page: 0 })

  const slow = deferred()
  state.settle = slow.promise
  const older = scroll.navigate('1/ac-audit-ldap-sync#fail', '#fail')
  state.settle = Promise.resolve()
  await scroll.navigate('2/ac-audit-ldap-sync#members', '#members')
  slow.resolve()
  await older
  assert.deepEqual(state.position, { main: 1800, page: 0 })
})
