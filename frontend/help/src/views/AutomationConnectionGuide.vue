<template>
  <GuideArticle
    test-id="automation-connection-guide-page"
    page-title-key="automationConnectionGuide.pageTitle"
    intro-key="automationConnectionGuide.intro"
    crumb-key="automationConnectionGuide.crumb"
    flow-title-key="automationConnectionGuide.flowTitle"
    :flow-keys="flowKeys"
    :jump-links="jumpLinks"
    :related="related"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideJump, type GuideRelated, type GuideSection } from '@/components/GuideArticle.vue'

const flowKeys = [
  'automationConnectionGuide.flow1',
  'automationConnectionGuide.flow2',
  'automationConnectionGuide.flow3',
]
const jumpLinks: GuideJump[] = [
  { anchor: 'identity', titleKey: 'automationConnectionGuide.identityTitle' },
  { anchor: 'scope', titleKey: 'automationConnectionGuide.scopeTitle' },
]
const related: GuideRelated[] = []
const sections: GuideSection[] = [
  {
    anchor: 'identity',
    titleKey: 'automationConnectionGuide.identityTitle',
    bodyKey: 'automationConnectionGuide.identityBody',
  },
  {
    anchor: 'scope',
    titleKey: 'automationConnectionGuide.scopeTitle',
    bodyKey: 'automationConnectionGuide.scopeBody',
  },
]
</script>
