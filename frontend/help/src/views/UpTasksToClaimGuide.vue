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
    anchor: 'row-actions',
    titleKey: 'upTasksToClaimGuide.rowActionsTitle',
    bodyKey: 'upTasksToClaimGuide.rowActionsBody',
    samples: [
      { code: 'Claim', hintKey: 'upTasksToClaimGuide.claimButtonSample' },
      { code: 'Unclaim', hintKey: 'upTasksToClaimGuide.unclaimButtonSample' },
      { code: 'Release Claim', hintKey: 'upTasksToClaimGuide.releaseClaimButtonSample' },
      { code: 'Reassign', hintKey: 'upTasksToClaimGuide.reassignButtonSample' },
      { code: 'Take over', hintKey: 'upTasksToClaimGuide.takeOverButtonSample' },
      { code: 'More', hintKey: 'upTasksToClaimGuide.moreButtonSample' },
    ],
  },
  {
    anchor: 'claim',
    titleKey: 'upTasksToClaimGuide.claimTitle',
    bodyKey: 'upTasksToClaimGuide.claimBody',
    samples: [
      { code: 'Claim', hintKey: 'upTasksToClaimGuide.claimSample' },
      { code: 'Claim all', hintKey: 'upTasksToClaimGuide.claimAllSample' },
      { code: 'Unclaim', hintKey: 'upTasksToClaimGuide.unclaimSample' },
      { code: 'Unclaim all', hintKey: 'upTasksToClaimGuide.unclaimAllSample' },
      { code: 'N selected · Claim', hintKey: 'upTasksToClaimGuide.claimSelectedSample' },
      { code: 'N selected · Unclaim', hintKey: 'upTasksToClaimGuide.unclaimSelectedSample' },
      { code: 'Current Assignee', hintKey: 'upTasksToClaimGuide.claimedBySample' },
    ],
  },
  {
    anchor: 'auto-claim',
    titleKey: 'upTasksToClaimGuide.autoClaimTitle',
    bodyKey: 'upTasksToClaimGuide.autoClaimBody',
    samples: [
      { code: 'Auto-claim on open', hintKey: 'upTasksToClaimGuide.autoClaimSample' },
    ],
  },
  {
    anchor: 'auto-preview',
    titleKey: 'upTasksToClaimGuide.autoPreviewTitle',
    bodyKey: 'upTasksToClaimGuide.autoPreviewBody',
    samples: [
      { code: 'Auto-preview on open', hintKey: 'upTasksToClaimGuide.autoPreviewSample' },
    ],
  },
  {
    anchor: 'file-preview',
    titleKey: 'upTasksToClaimGuide.filePreviewTitle',
    bodyKey: 'upTasksToClaimGuide.filePreviewBody',
    samples: [
      { code: 'Fit', hintKey: 'upTasksToClaimGuide.filePreviewSample' },
    ],
  },
  {
    anchor: 'detail',
    titleKey: 'upTasksToClaimGuide.detailTitle',
    bodyKey: 'upTasksToClaimGuide.detailBody',
  },
  { anchor: 'change-history', titleKey: 'upTasksToClaimGuide.historyTitle', bodyKey: 'upTasksToClaimGuide.historyBody' },
  {
    anchor: 'leader',
    titleKey: 'upTasksToClaimGuide.leaderTitle',
    bodyKey: 'upTasksToClaimGuide.leaderBody',
    samples: [
      { code: 'Release Claim', hintKey: 'upTasksToClaimGuide.forceUnclaimSample' },
      { code: 'Reassign', hintKey: 'upTasksToClaimGuide.reassignSample' },
    ],
  },
  {
    anchor: 'lost-access',
    titleKey: 'upTasksToClaimGuide.lostTitle',
    bodyKey: 'upTasksToClaimGuide.lostBody',
    bodyKeys: [
      'upTasksToClaimGuide.lostDirectBody',
      'upTasksToClaimGuide.leftBody',
      'upTasksToClaimGuide.roleChangeBody',
      'upTasksToClaimGuide.standingDelegationBody',
    ],
    samples: [
      { code: 'Assignee has lost Portal access', hintKey: 'upTasksToClaimGuide.lostTagSample' },
      { code: 'Take over', hintKey: 'upTasksToClaimGuide.takeOverSample' },
      { code: 'Reassigned to manager', hintKey: 'upTasksToClaimGuide.managerReassignSample' },
      {
        code: 'Not in effect: the delegator has lost Portal access',
        hintKey: 'upTasksToClaimGuide.delegationInactiveSample',
      },
    ],
  },
  {
    anchor: 'apply',
    titleKey: 'upTasksToClaimGuide.applyTitle',
    bodyKey: 'upTasksToClaimGuide.applyBody',
  },
  {
    anchor: 'upgrade',
    titleKey: 'upTasksToClaimGuide.upgradeTitle',
    bodyKey: 'upTasksToClaimGuide.upgradeBody',
  },
  { titleKey: 'upTasksToClaimGuide.failTitle', bodyKey: 'upTasksToClaimGuide.failBody' },
]
</script>
