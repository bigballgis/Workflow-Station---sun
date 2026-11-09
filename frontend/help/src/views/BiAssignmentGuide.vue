<template>
  <GuideArticle :test-id="`${prefix}-page`" :page-title-key="`${prefix}.title`" :intro-key="`${prefix}.intro`"
    :flow-keys="flow" :sections="sections" :related="related" />
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import GuideArticle, { type GuideSection } from '@/components/GuideArticle.vue'
const route = useRoute()
const prefix = computed(() => route.name === 'ac-bi-assign' ? 'biAudienceGuide' : 'biViewGuide')
const flow = computed(() => [1, 2, 3].map(n => `${prefix.value}.step${n}`))
/** Data View Assignment is limited by team (Technical Lead); Audience Assignment is not (D-15). */
const sections = computed<GuideSection[]>(() => {
  const p = prefix.value
  return [
    { anchor: 'configure', titleKey: `${p}.configureTitle`, bodyKeys: [`${p}.fields`, `${p}.options`] },
    { anchor: 'example', titleKey: `${p}.exampleTitle`, bodyKey: `${p}.example` },
    { anchor: 'status', titleKey: `${p}.statusTitle`, bodyKey: `${p}.status` },
    { anchor: 'permissions', titleKey: `${p}.permTitle`, bodyKey: `${p}.permBody`, sampleLayout: 'block',
      figure: { src: p === 'biViewGuide' ? 'guides/ac-bi-data-view-tl.png' : 'guides/ac-bi-audience-auditor.png',
        captionKey: `${p}.permFigure` },
      samples: [
        { code: 'System Administrator', hintKey: `${p}.permAdmin` },
        { code: 'Technical Lead', hintKey: `${p}.permLead` },
        { code: 'Auditor', hintKey: `${p}.permAuditor` },
      ] },
    { anchor: 'troubleshoot', titleKey: `${p}.troubleTitle`, bodyKey: `${p}.trouble`,
      failKeys: p === 'biViewGuide' ? ['biViewGuide.failNotLed'] : undefined },
  ]
})
const related = computed(() => [{ to: prefix.value === 'biAudienceGuide' ? '/bi-data-view-assignment' : '/bi-audience-assignment',
  titleKey: prefix.value === 'biAudienceGuide' ? 'biViewGuide.title' : 'biAudienceGuide.title' }])
</script>
