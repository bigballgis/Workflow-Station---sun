/**
 * Scroll handling for help articles.
 *
 * Articles scroll inside `main.help-main` on wide screens and in the window on
 * narrow ones (see help.css, max-width: 840px). vue-router's own scroll
 * handling only moves the window, so `#hash` links never reached their section
 * on wide screens and back / forward lost the reading position. This module
 * handles both scrollers.
 */

export interface HelpScrollPosition {
  /** `main.help-main` scrollTop (wide layout). */
  main: number
  /** Window scrollY (narrow layout). */
  page: number
}

export type HelpScrollTarget =
  | { kind: 'restore'; position: HelpScrollPosition }
  | { kind: 'anchor'; id: string }
  | { kind: 'top' }

/** Browser side effects, injected so the policy can be tested without a DOM. */
export interface HelpScrollDom {
  read(): HelpScrollPosition
  write(position: HelpScrollPosition): void
  /** Scrolls the element with this id into view; false when there is none. */
  reveal(id: string): boolean
  /** Resolves once the article's images have loaded (or waiting timed out). */
  settled(): Promise<void>
}

export interface HelpScrollController {
  /** Remember the current position for the current history entry (on every scroll). */
  record(): void
  /** The next navigation is a back / forward traversal (popstate). */
  traversal(): void
  /** The reader scrolled, typed or clicked: stop re-applying the target. */
  userInput(): void
  /** Scroll for a finished navigation; `entryKey` identifies its history entry. */
  navigate(entryKey: string, hash: string): Promise<void>
}

const TOP: HelpScrollPosition = { main: 0, page: 0 }

/** Back / forward returns to where the reader was; otherwise the hash section, else the top. */
export function resolveHelpScrollTarget(
  hash: string,
  saved: HelpScrollPosition | undefined,
): HelpScrollTarget {
  if (saved) return { kind: 'restore', position: saved }
  const id = hash.replace(/^#/, '')
  return id ? { kind: 'anchor', id } : { kind: 'top' }
}

export function createHelpScroll(dom: HelpScrollDom): HelpScrollController {
  const saved = new Map<string, HelpScrollPosition>()
  let currentEntry: string | undefined
  let traversing = false
  let navigation = 0
  let interrupted = false

  function apply(target: HelpScrollTarget): void {
    if (target.kind === 'restore') dom.write(target.position)
    else if (target.kind === 'top' || !dom.reveal(target.id)) dom.write(TOP)
  }

  return {
    record() {
      if (currentEntry !== undefined) saved.set(currentEntry, dom.read())
    },
    traversal() {
      traversing = true
    },
    userInput() {
      interrupted = true
    },
    async navigate(entryKey, hash) {
      // A link click makes a new history entry. Its key can repeat that of an entry the browser discarded; the new
      // entry starts with nothing recorded, so a later back to it does not land on the discarded entry's position.
      if (!traversing) saved.delete(entryKey)
      const target = resolveHelpScrollTarget(hash, traversing ? saved.get(entryKey) : undefined)
      traversing = false
      currentEntry = entryKey
      interrupted = false
      const token = ++navigation
      apply(target)
      // Figures have no fixed height, so the target moves while they load.
      await dom.settled()
      if (token === navigation && !interrupted) apply(target)
    },
  }
}

const IMAGE_WAIT_MS = 2000

function browserHelpScrollDom(): HelpScrollDom {
  const main = (): HTMLElement | null => document.querySelector<HTMLElement>('main.help-main')
  return {
    read: () => ({ main: main()?.scrollTop ?? 0, page: window.scrollY }),
    write(position) {
      const el = main()
      if (el) el.scrollTop = position.main
      window.scrollTo(0, position.page)
    },
    reveal(id) {
      const el = document.getElementById(id)
      if (!el) return false
      el.scrollIntoView({ block: 'start' })
      return true
    },
    settled() {
      const pending = Array.from(main()?.querySelectorAll('img') ?? []).filter((img) => !img.complete)
      if (!pending.length) return Promise.resolve()
      const loaded = pending.map(
        (img) =>
          new Promise<void>((resolve) => {
            img.addEventListener('load', () => resolve(), { once: true })
            img.addEventListener('error', () => resolve(), { once: true })
          }),
      )
      const timeout = new Promise<void>((resolve) => window.setTimeout(resolve, IMAGE_WAIT_MS))
      return Promise.race([Promise.all(loaded).then(() => undefined), timeout])
    },
  }
}

/** Same idea as vue-router's scroll key: history position plus path. */
export function helpHistoryEntryKey(fullPath: string): string {
  const state = window.history.state as { position?: number } | null
  return `${state?.position ?? -1}${fullPath}`
}

/** Wires the controller to the browser; call once before the router starts. */
export function installHelpScroll(): HelpScrollController {
  const scroll = createHelpScroll(browserHelpScrollDom())
  const passive = { capture: true, passive: true }
  // Element scroll events do not bubble; a capturing listener sees `main` and the window.
  document.addEventListener('scroll', () => scroll.record(), passive)
  window.addEventListener('popstate', () => scroll.traversal())
  for (const type of ['wheel', 'touchstart', 'keydown', 'pointerdown']) {
    window.addEventListener(type, () => scroll.userInput(), passive)
  }
  return scroll
}
