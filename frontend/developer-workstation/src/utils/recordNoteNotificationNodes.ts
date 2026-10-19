export interface RecordNoteTargetOption { id: string; name: string }

/** Read explicit node/MI-scope configuration; names and labels never determine eligibility. */
export function recordNoteNotificationNodes(document: Document): RecordNoteTargetOption[] {
  const properties = (element: Element): Map<string, string> => {
    const result = new Map<string, string>()
    const extension = Array.from(element.children).find(child => child.localName === 'extensionElements')
    for (const property of Array.from(extension?.getElementsByTagName('*') ?? [])) {
      const name = property.getAttribute('name')
      const value = property.getAttribute('value')
      if (name && value) result.set(name, value)
    }
    return result
  }
  return Array.from(document.getElementsByTagNameNS('*', 'userTask')).filter(task => {
    const config = properties(task)
    let parent = task.parentElement
    let multi = Array.from(task.children).some(child => child.localName === 'multiInstanceLoopCharacteristics')
    while (parent) {
      if (parent.localName === 'subProcess') {
        multi ||= Array.from(parent.children).some(child => child.localName === 'multiInstanceLoopCharacteristics')
        for (const [key, value] of properties(parent)) if (!config.has(key)) config.set(key, value)
      }
      parent = parent.parentElement
    }
    return multi && config.has('subTableName') && (config.has('assigneeField') || config.has('roleField'))
  }).map(task => ({ id: task.getAttribute('id') ?? '', name: task.getAttribute('name') || task.getAttribute('id') || '' }))
    .filter(task => task.id.length > 0)
}
