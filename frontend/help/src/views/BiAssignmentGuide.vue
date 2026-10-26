<template>
  <GuideArticle :test-id="`${prefix}-page`" :page-title-key="`${prefix}.title`" :intro-key="`${prefix}.intro`"
    :flow-keys="flow" :sections="sections" :related="related" />
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import GuideArticle from '@/components/GuideArticle.vue'
const route = useRoute()
const prefix = computed(() => route.name === 'ac-bi-assign' ? 'biAudienceGuide' : 'biViewGuide')
const flow = computed(() => [1, 2, 3].map(n => `${prefix.value}.step${n}`))
const sections = computed(() => [
  { anchor: 'configure', titleKey: `${prefix.value}.configureTitle`, bodyKeys: [`${prefix.value}.fields`, `${prefix.value}.options`] },
  { anchor: 'example', titleKey: `${prefix.value}.exampleTitle`, bodyKey: `${prefix.value}.example` },
  { anchor: 'status', titleKey: `${prefix.value}.statusTitle`, bodyKey: `${prefix.value}.status` },
  { anchor: 'troubleshoot', titleKey: `${prefix.value}.troubleTitle`, bodyKey: `${prefix.value}.trouble` },
])
const related = computed(() => [{ to: prefix.value === 'biAudienceGuide' ? '/bi-data-view-assignment' : '/bi-audience-assignment',
  titleKey: prefix.value === 'biAudienceGuide' ? 'biViewGuide.title' : 'biAudienceGuide.title' }])
</script>
