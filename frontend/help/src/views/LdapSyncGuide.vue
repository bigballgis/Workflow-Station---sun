<template>
  <GuideArticle
    test-id="ldap-sync-guide-page"
    page-title-key="ldapSyncGuide.pageTitle"
    intro-key="ldapSyncGuide.intro"
    crumb-key="ldapSyncGuide.crumb"
    flow-title-key="ldapSyncGuide.flowTitle"
    :flow-keys="flowKeys"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideSection } from '@/components/GuideArticle.vue'

const flowKeys = ['ldapSyncGuide.flow1', 'ldapSyncGuide.flow2', 'ldapSyncGuide.flow3']

const catalog = { sampleLayout: 'block' as const }

const sections: GuideSection[] = [
  {
    anchor: 'list',
    titleKey: 'ldapSyncGuide.listTitle',
    bodyKey: 'ldapSyncGuide.listBody',
    bodyKeys: ['ldapSyncGuide.listLead'],
    figure: { src: 'guides/ac-ldap-sync-records.png', captionKey: 'ldapSyncGuide.listFigure' },
    ...catalog,
    samples: [
      { code: 'Type', hintKey: 'ldapSyncGuide.fType' },
      { code: 'Status', hintKey: 'ldapSyncGuide.fStatus' },
      { code: 'Started', hintKey: 'ldapSyncGuide.fStarted' },
      { code: 'Duration', hintKey: 'ldapSyncGuide.fDuration' },
      { code: 'Fetched', hintKey: 'ldapSyncGuide.fFetched' },
      { code: 'Added · Updated', hintKey: 'ldapSyncGuide.fAddedUpdated' },
      { code: 'Failed items', hintKey: 'ldapSyncGuide.fFailedItems' },
      { code: 'Skipped items', hintKey: 'ldapSyncGuide.fSkipped' },
      { code: 'Message', hintKey: 'ldapSyncGuide.fMessage' },
    ],
  },
  {
    anchor: 'status',
    titleKey: 'ldapSyncGuide.statusTitle',
    bodyKey: 'ldapSyncGuide.statusLead',
    ...catalog,
    samples: [
      { code: 'Running', hintKey: 'ldapSyncGuide.sRunning' },
      { code: 'Succeeded', hintKey: 'ldapSyncGuide.sSucceeded' },
      { code: 'Partly succeeded', hintKey: 'ldapSyncGuide.sPartial' },
      { code: 'Failed', hintKey: 'ldapSyncGuide.sFailed' },
    ],
  },
  {
    anchor: 'schedule',
    titleKey: 'ldapSyncGuide.scheduleTitle',
    bodyKey: 'ldapSyncGuide.scheduleBody',
    bodyKeys: ['ldapSyncGuide.scheduleLock'],
  },
  {
    anchor: 'find',
    titleKey: 'ldapSyncGuide.findTitle',
    bodyKey: 'ldapSyncGuide.findLead',
    ...catalog,
    samples: [
      { code: 'Search type, status, message or groups', hintKey: 'ldapSyncGuide.fSearch' },
      { code: 'Column header menu', hintKey: 'ldapSyncGuide.fColumnMenu' },
      { code: 'Export', hintKey: 'ldapSyncGuide.fExport' },
      { code: 'Refresh', hintKey: 'ldapSyncGuide.fRefresh' },
      { code: 'N records', hintKey: 'ldapSyncGuide.fTotal' },
    ],
  },
  {
    anchor: 'access',
    titleKey: 'ldapSyncGuide.accessTitle',
    bodyKey: 'ldapSyncGuide.accessBody',
    bodyKeys: ['ldapSyncGuide.accessMessage'],
    ...catalog,
    samples: [
      { code: 'System Administrator', hintKey: 'ldapSyncGuide.fRoleAdmin' },
      { code: 'Technical Lead', hintKey: 'ldapSyncGuide.fRoleLead' },
      { code: 'Auditor', hintKey: 'ldapSyncGuide.fRoleAuditor' },
    ],
  },
  {
    anchor: 'members',
    titleKey: 'ldapSyncGuide.membersTitle',
    bodyKey: 'ldapSyncGuide.membersBody',
  },
  {
    anchor: 'fail',
    titleKey: 'ldapSyncGuide.failTitle',
    failKeys: [
      'ldapSyncGuide.failEmpty',
      'ldapSyncGuide.failRunning',
      'ldapSyncGuide.failFailed',
      'ldapSyncGuide.failPartialStatus',
      'ldapSyncGuide.failNotRead',
      'ldapSyncGuide.failPartial',
      'ldapSyncGuide.failNoMembers',
      'ldapSyncGuide.failKept',
      'ldapSyncGuide.failBusy',
      'ldapSyncGuide.failLockUnavailable',
      'ldapSyncGuide.failNoMapping',
      'ldapSyncGuide.failLoad',
      'ldapSyncGuide.failMenu',
    ],
  },
]
</script>
