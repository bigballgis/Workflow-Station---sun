<template>
  <GuideArticle test-id="node-action-order-guide-page" page-title-key="nodeActionOrderGuide.title" intro-key="nodeActionOrderGuide.intro" crumb-key="nodeActionOrderGuide.crumb" flow-title-key="nodeActionOrderGuide.flowTitle" :flow-keys="flowKeys" :sections="sections" :related="related" />
</template>
<script setup lang="ts">
import GuideArticle, { type GuideSection } from '@/components/GuideArticle.vue'
const flowKeys = ['nodeActionOrderGuide.flow1', 'nodeActionOrderGuide.flow2', 'nodeActionOrderGuide.flow3']
const sections: GuideSection[] = [
  { anchor: 'arrange', titleKey: 'nodeActionOrderGuide.arrangeTitle', bodyKey: 'nodeActionOrderGuide.arrangeBody',
    figure: { src: 'guides/dw-node-action-order.png', captionKey: 'nodeActionOrderGuide.figure' },
    sampleLayout: 'block', samples: [
      { code: 'Available Actions', hintKey: 'nodeActionOrderGuide.select' },
      { code: 'Button display order', hintKey: 'nodeActionOrderGuide.order' },
      { code: 'Global Actions · Global inherited', hintKey: 'nodeActionOrderGuide.global' },
      { code: '↕ / ↑ / ↓', hintKey: 'nodeActionOrderGuide.move' },
      { code: '×', hintKey: 'nodeActionOrderGuide.remove' },
    ] },
  { anchor: 'portal', titleKey: 'nodeActionOrderGuide.portalTitle', bodyKey: 'nodeActionOrderGuide.portalBody', samples: [
    { code: 'Save Draft → Approve → Reject', hintKey: 'nodeActionOrderGuide.example' },
  ] },
  { anchor: 'fail', titleKey: 'nodeActionOrderGuide.failTitle', failKeys: ['nodeActionOrderGuide.failSave', 'nodeActionOrderGuide.failMissing', 'nodeActionOrderGuide.failRoles'] },
]
const related = [{ to: '/action-allowed-roles', titleKey: 'guides.actionAllowedRoles.title' }]
</script>
