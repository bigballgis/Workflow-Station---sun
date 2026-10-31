import type { Component, InjectionKey } from 'vue'

export const batchLookupComponentKey: InjectionKey<Component> = Symbol('batch-lookup-component')
