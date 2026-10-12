<template>
  <GuideArticle
    test-id="table-data-guide-page"
    page-title-key="tableDataGuide.title"
    intro-key="tableDataGuide.intro"
    crumb-key="tableDataGuide.crumb"
    flow-title-key="app.flowAria"
    :flow-keys="['tableDataGuide.flow1', 'tableDataGuide.flow2', 'tableDataGuide.flow3']"
    :sections="sections"
    :related="[{ to: '/table-design', titleKey: 'guides.tableDesign.title' }]"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideSection } from '@/components/GuideArticle.vue'

const sections: GuideSection[] = [
  { anchor: 'navigation', titleKey: 'tableDataGuide.navigationTitle', bodyKeys: ['tableDataGuide.groups', 'tableDataGuide.search', 'tableDataGuide.expand', 'tableDataGuide.resize'] },
  { anchor: 'table', titleKey: 'tableDataGuide.tableTitle', bodyKeys: ['tableDataGuide.context', 'tableDataGuide.columns', 'tableDataGuide.records', 'tableDataGuide.export', 'tableDataGuide.import'] },
  { anchor: 'links', titleKey: 'tableDataGuide.linksTitle', bodyKey: 'tableDataGuide.links' },
  { anchor: 'failures', titleKey: 'tableDataGuide.failTitle', failKeys: ['tableDataGuide.noMatches', 'tableDataGuide.loadError', 'tableDataGuide.unavailable'] },
]
</script>
