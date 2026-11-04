import { isEventPromise } from './async'
import { FormEventOperation } from './operation'

/** Single-form host. Batch adds row isolation around the same operation/API contract. */
export class FormEventHost<T> {
  private current?: { key: string; operation: FormEventOperation; baseline: T; tail?: Promise<unknown> }
  private accepted?: T
  error: unknown
  constructor(private readonly state: { snapshot(): T; restore(value: T): void; notify(): void }) {}
  get pending() { return !!this.current?.tail }
  reset() { this.cancel(); this.accepted = undefined; this.error = undefined; this.state.notify() }
  acceptCurrent() { if (!this.current) this.accepted = this.state.snapshot() }
  cancel() {
    const current = this.current
    this.current = undefined
    if (!current) return
    current.operation.cancel()
    this.state.restore(current.baseline)
    current.operation.finish()
    this.state.notify()
  }
  run(key: string, action: (operation: FormEventOperation) => unknown, join: boolean | 'queue' = false): unknown {
    if (join !== 'queue' && (!join || this.current?.key !== key)) this.cancel()
    this.accepted ??= this.state.snapshot()
    const current = this.current ?? { key, operation: new FormEventOperation(), baseline: this.accepted }
    this.current = current
    this.error = undefined
    const failed = (error: unknown) => {
      if (this.current !== current) return false
      current.operation.cancel(error)
      this.state.restore(current.baseline)
      this.error = error
      this.current = undefined
      current.operation.finish()
      this.state.notify()
      return false
    }
    const finished = (value: unknown) => {
      if (this.current !== current) return false
      try { current.operation.assertActive(); current.operation.finish() } catch (error) { return failed(error) }
      this.accepted = this.state.snapshot()
      this.current = undefined
      this.state.notify()
      return value
    }
    try {
      const result = current.tail
        ? current.tail.then(() => { current.operation.assertActive(); return action(current.operation) })
        : action(current.operation)
      if (!isEventPromise(result)) return finished(result)
      const tail = current.operation.wait(result).then(value => {
        if (current.tail === tail) return finished(value)
        return value
      }, failed)
      current.tail = tail
      this.state.notify()
      return tail
    } catch (error) { return failed(error) }
  }
  async wait(): Promise<boolean> {
    while (this.current?.tail) await this.current.tail
    return this.error == null
  }
}
