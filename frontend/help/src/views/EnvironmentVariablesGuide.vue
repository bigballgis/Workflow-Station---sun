<template>
  <GuideArticle
    test-id="environment-variables-guide-page"
    page-title-key="environmentVariablesGuide.pageTitle"
    intro-key="environmentVariablesGuide.intro"
    crumb-key="environmentVariablesGuide.crumb"
    flow-title-key="environmentVariablesGuide.flowTitle"
    :flow-keys="flowKeys"
    :jump-links="jumpLinks"
    :related="related"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, {
  type GuideJump,
  type GuideRelated,
  type GuideSection,
} from '@/components/GuideArticle.vue'

const flowKeys = [
  'environmentVariablesGuide.flow1',
  'environmentVariablesGuide.flow2',
  'environmentVariablesGuide.flow3',
]

const jumpLinks: GuideJump[] = [
  { anchor: 'catalog', titleKey: 'environmentVariablesGuide.catalogTitle' },
  { anchor: 'bind', titleKey: 'environmentVariablesGuide.bindTitle' },
  { anchor: 'fail', titleKey: 'environmentVariablesGuide.failTitle' },
]

const related: GuideRelated[] = [
  { to: '/email-send#connection', titleKey: 'guides.emailSend.title' },
  { to: '/email-monitor', titleKey: 'guides.emailMonitor.title' },
]

const catalog = { sampleLayout: 'block' as const }

const sections: GuideSection[] = [
  {
    anchor: 'catalog',
    titleKey: 'environmentVariablesGuide.catalogTitle',
    bodyKey: 'environmentVariablesGuide.catalogBody',
    bodyKeys: ['environmentVariablesGuide.catalogLead'],
    ...catalog,
    samples: [
      { code: 'Kind TEXT', hintKey: 'environmentVariablesGuide.fKindText' },
      { code: 'Kind VAULT', hintKey: 'environmentVariablesGuide.fKindVault' },
      { code: 'Key', hintKey: 'environmentVariablesGuide.fKey' },
      { code: 'Display name', hintKey: 'environmentVariablesGuide.fDisplayName' },
      { code: 'Default value', hintKey: 'environmentVariablesGuide.fDefault' },
      { code: 'Current value', hintKey: 'environmentVariablesGuide.fCurrent' },
      { code: 'Vault secret path', hintKey: 'environmentVariablesGuide.fPath' },
    ],
  },
  {
    anchor: 'bind',
    titleKey: 'environmentVariablesGuide.bindTitle',
    bodyKey: 'environmentVariablesGuide.bindBody',
    ...catalog,
    samples: [
      { code: 'Password (VAULT)', hintKey: 'environmentVariablesGuide.fBindPassword' },
    ],
  },
  {
    anchor: 'fail',
    titleKey: 'environmentVariablesGuide.failTitle',
    bodyKey: 'environmentVariablesGuide.failBody',
    failKeys: [
      'environmentVariablesGuide.failTextBlank',
      'environmentVariablesGuide.failVault404',
      'environmentVariablesGuide.failTextForMail',
    ],
  },
]
</script>
