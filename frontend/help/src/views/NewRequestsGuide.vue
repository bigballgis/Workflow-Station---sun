<template>
  <GuideArticle
    test-id="new-requests-guide-page"
    page-title-key="newRequestsGuide.title"
    intro-key="newRequestsGuide.intro"
    crumb-key="newRequestsGuide.crumb"
    flow-title-key="newRequestsGuide.flowTitle"
    :flow-keys="flowKeys"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideSection } from '@/components/GuideArticle.vue'

const flowKeys = ['newRequestsGuide.flow1', 'newRequestsGuide.flow2', 'newRequestsGuide.flow3']
const sections: GuideSection[] = [
  { anchor: 'browse', titleKey: 'newRequestsGuide.browseTitle', bodyKey: 'newRequestsGuide.browseBody' },
  { anchor: 'favorites', titleKey: 'newRequestsGuide.favoritesTitle', bodyKey: 'newRequestsGuide.favoritesBody' },
  { anchor: 'missing', titleKey: 'newRequestsGuide.emptyTitle', bodyKey: 'newRequestsGuide.emptyBody' },
]
</script>
