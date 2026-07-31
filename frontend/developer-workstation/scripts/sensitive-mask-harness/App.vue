<template>
  <div class="harness" data-testid="sensitive-mask-harness">
    <h1>Sensitive Mask Props Editor</h1>
    <p class="hint">Verification harness — not shipped in production routes.</p>
    <div class="panel">
      <SensitiveMaskPropsEditor v-model="cfg" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import SensitiveMaskPropsEditor from '@/components/designer/SensitiveMaskPropsEditor.vue'
import type { SensitiveMaskConfig } from '@/utils/sensitiveMask'

const cfg = ref<SensitiveMaskConfig>({
  enabled: true,
  preset: 'ranges',
  keepPrefix: 0,
  keepSuffix: 4,
  maskPrefix: 3,
  maskSuffix: 4,
  maskRanges: [
    { start: 0, end: 3 },
    { start: -4, end: null },
  ],
  maskChar: '*',
  revealPlainOnFocus: false,
})
</script>

<style>
body {
  margin: 0;
  font-family: "Segoe UI", sans-serif;
  background: #f0f2f5;
}
.harness {
  max-width: 420px;
  margin: 24px auto;
  padding: 16px 20px 24px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}
.harness h1 {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.hint {
  margin: 0 0 16px;
  font-size: 12px;
  color: #909399;
}
.panel {
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}
</style>
