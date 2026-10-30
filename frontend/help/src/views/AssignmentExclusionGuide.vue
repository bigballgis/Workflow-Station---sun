<template>
  <GuideArticle test-id="assignment-exclusion-guide-page" page-title-key="assignmentExclusionGuide.title"
    intro-key="assignmentExclusionGuide.intro" crumb-key="assignmentExclusionGuide.crumb"
    flow-title-key="assignmentExclusionGuide.flowTitle" :flow-keys="flow" :sections="sections" :related="related" />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideSection } from '@/components/GuideArticle.vue'

const key = (name: string) => `assignmentExclusionGuide.${name}`
const flow = ['flow1', 'flow2', 'flow3'].map(key)
const sections: GuideSection[] = [
  { anchor: 'rules', titleKey: key('rulesTitle'), bodyKey: key('rulesBody'), sampleLayout: 'block', samples: [
    { code: 'Exclude the original initiator', hintKey: key('initiator') },
    { code: 'Exclude previous handlers of this request', hintKey: key('previous') },
    { code: 'Exclude handlers at selected nodes', hintKey: key('selected') },
    { code: 'Nodes in this function unit', hintKey: key('nodes') },
  ] },
  { anchor: 'backup', titleKey: key('backupTitle'), bodyKey: key('backupBody'), sampleLayout: 'block', samples: [
    { code: 'Use backup candidates', hintKey: key('enabled') },
    { code: 'Backup source', hintKey: key('source') },
    { code: 'Business unit source', hintKey: key('buSource') },
    { code: 'Business unit', hintKey: key('bu') },
    { code: 'Roles', hintKey: key('roles') },
    { code: 'Selected users', hintKey: key('users') },
  ] },
  { anchor: 'example', titleKey: key('exampleTitle'), bodyKey: key('exampleBody'), bodyKeys: [key('emptyBody'), key('responsibility')] },
  { anchor: 'save', titleKey: key('saveTitle'), bodyKey: key('saveBody'), failKeys: [key('missingNodes'), key('missingBackup'), key('nodeBu'), key('damaged')],
    samples: [{ code: 'Reset exclusions', hintKey: key('reset') }] },
]
const related = [{ to: '/task-delegate', titleKey: 'taskDelegateGuide.title' }, { to: '/up-tasks-to-claim', titleKey: 'guides.upTasksToClaim.title' }]
</script>
