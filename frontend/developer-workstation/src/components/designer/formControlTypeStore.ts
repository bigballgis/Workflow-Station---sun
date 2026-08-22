import { reactive } from 'vue'

/** Active form-create rule for Control type Input ↔ Owner (VARCHAR). */
export const formControlTypeStore = reactive({
  activeRule: null as Record<string, unknown> | null,
})
