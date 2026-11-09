<template>
  <el-alert
    v-if="hasReferences(notice)"
    class="reference-notice"
    type="warning"
    :closable="false"
    show-icon
    :title="t('referenceNotice.title')"
  >
    <div v-if="notice!.functionUnits.length">
      {{ t('referenceNotice.functionUnits') }}
      <ul class="reference-list">
        <li
          v-for="fu in notice!.functionUnits"
          :key="fu.code"
        >
          {{ fu.name }} ({{ fu.code }})
        </li>
      </ul>
    </div>
    <ul
      v-if="notice!.others.length"
      class="reference-list"
    >
      <li
        v-for="line in notice!.others"
        :key="line"
      >
        {{ line }}
      </li>
    </ul>
    <p class="reference-hint">
      {{ t('referenceNotice.hint') }}
    </p>
  </el-alert>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { hasReferences, type ReferenceNotice } from '@/types/referenceNotice'

defineProps<{ notice: ReferenceNotice | null }>()

const { t } = useI18n()
</script>

<style scoped>
.reference-notice { margin-bottom: 16px; }
.reference-list { margin: 4px 0; padding-left: 18px; }
.reference-hint { margin: 4px 0 0; }
</style>
