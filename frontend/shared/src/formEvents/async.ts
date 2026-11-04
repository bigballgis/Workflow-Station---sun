/** Keep existing synchronous event timing, while awaiting explicitly returned promises. */
export type EventResult<T = unknown> = T | Promise<T>

export function isEventPromise<T>(value: T | PromiseLike<T>): value is PromiseLike<T> {
  return value != null && typeof (value as PromiseLike<T>).then === 'function'
}

export function afterEvent<T, R>(value: T | PromiseLike<T>, next: (value: T) => R): R | Promise<Awaited<R>> {
  return isEventPromise(value) ? Promise.resolve(value).then(next) as Promise<Awaited<R>> : next(value)
}

/** false stops an event array; later handlers must not run after a rejected guard. */
export function eventSequence(items: Array<() => unknown>): unknown {
  let index = 0
  const next = (): unknown => {
    while (index < items.length) {
      const result = items[index++]!()
      if (isEventPromise(result)) return Promise.resolve(result).then(value => value === false ? false : next())
      if (result === false) return false
    }
  }
  return next()
}
