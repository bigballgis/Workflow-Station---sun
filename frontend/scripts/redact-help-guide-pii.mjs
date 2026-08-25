/** Mask emails and process ids in the live DOM before a help/verification screenshot. */

export async function redactHelpGuidePii(targetPage) {
  await targetPage.evaluate(() => {
    const emailRe = /[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}/g
    const processIdRe = /[A-Za-z][\w-]*-\d{8}-[a-z0-9]+/g
    const mask = (s) => s.replace(emailRe, 'user@example.com').replace(processIdRe, 'sample-process')
    const walk = (node) => {
      if (node.nodeType === Node.TEXT_NODE && node.nodeValue) {
        node.nodeValue = mask(node.nodeValue)
      } else if (node.nodeType === Node.ELEMENT_NODE) {
        const el = /** @type {HTMLElement} */ (node)
        if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) {
          el.value = mask(el.value)
        }
        for (const attr of ['title', 'placeholder', 'aria-label', 'value', 'href']) {
          const current = el.getAttribute(attr)
          if (current) el.setAttribute(attr, mask(current))
        }
        if (el instanceof HTMLElement && el.shadowRoot) {
          for (const c of el.shadowRoot.childNodes) walk(c)
        }
        for (const c of el.childNodes) walk(c)
      }
    }
    walk(document.body)
  })
}
