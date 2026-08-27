<template>
  <GuideArticle
    test-id="up-tasks-to-claim-guide-page"
    page-title-key="upTasksToClaimGuide.pageTitle"
    intro-key="upTasksToClaimGuide.intro"
    crumb-key="upTasksToClaimGuide.crumb"
    flow-title-key="upTasksToClaimGuide.flowTitle"
    :flow-keys="flowKeys"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideSection } from '@/components/GuideArticle.vue'

const flowKeys = [
  'upTasksToClaimGuide.flow1',
  'upTasksToClaimGuide.flow2',
  'upTasksToClaimGuide.flow3',
  'upTasksToClaimGuide.flow4',
]

const sections: GuideSection[] = [
  {
    anchor: 'list',
    titleKey: 'upTasksToClaimGuide.listTitle',
    bodyKey: 'upTasksToClaimGuide.listBody',
  },
  {
    anchor: 'claim',
    titleKey: 'upTasksToClaimGuide.claimTitle',
    bodyKey: 'upTasksToClaimGuide.claimBody',
    samples: [
      { code: 'Claim', hintKey: 'upTasksToClaimGuide.claimSample' },
      { code: 'Unclaim', hintKey: 'upTasksToClaimGuide.unclaimSample' },
      { code: 'Claimed By', hintKey: 'upTasksToClaimGuide.claimedBySample' },
    ],
  },
  {
    anchor: 'detail',
    titleKey: 'upTasksToClaimGuide.detailTitle',
    bodyKey: 'upTasksToClaimGuide.detailBody',
  },
  {
    anchor: 'upgrade',
    titleKey: 'upTasksToClaimGuide.upgradeTitle',
    bodyKey: 'upTasksToClaimGuide.upgradeBody',
  },
  { titleKey: 'upTasksToClaimGuide.failTitle', bodyKey: 'upTasksToClaimGuide.failBody' },
]
</script>
