/** Max simultaneous POSTs to {@code /upload} across all widgets on the page. */
export const MAX_UPLOAD_CONCURRENCY = 3

export function createUploadQueue(concurrency = MAX_UPLOAD_CONCURRENCY) {
  let active = 0
  const waiting: Array<() => void> = []

  function acquire(): Promise<void> {
    if (active < concurrency) {
      active += 1
      return Promise.resolve()
    }
    return new Promise((resolve) => {
      waiting.push(resolve)
    })
  }

  function release(): void {
    const next = waiting.shift()
    if (next) {
      next()
      return
    }
    active = Math.max(0, active - 1)
  }

  async function run<T>(fn: () => Promise<T>): Promise<T> {
    await acquire()
    try {
      return await fn()
    } finally {
      release()
    }
  }

  return { run }
}

let sharedQueue: ReturnType<typeof createUploadQueue> | null = null

export function sharedUploadQueue(): ReturnType<typeof createUploadQueue> {
  if (!sharedQueue) sharedQueue = createUploadQueue(MAX_UPLOAD_CONCURRENCY)
  return sharedQueue
}
