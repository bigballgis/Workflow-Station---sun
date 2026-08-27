<script>
/**
 * fc-designer Validate.handleCommand appends a Validation+ row but does not emit.
 * Leaving Error empty then never triggers the other @change emitters, so auto-save
 * (getRule snapshot) and manual save (flush formData) both miss the new rule.
 */
import UpstreamValidate from '@form-create/designer/src/components/Validate.vue'

function resolveComponentMethods(comp) {
  return comp.methods || comp.__vccOpts?.methods || {}
}

export default {
  name: 'Validate',
  extends: UpstreamValidate,
  methods: {
    handleCommand(mode) {
      const parent = resolveComponentMethods(UpstreamValidate).handleCommand
      if (typeof parent !== 'function') return
      parent.call(this, mode)
      this.onInput()
    },
  },
}
</script>
