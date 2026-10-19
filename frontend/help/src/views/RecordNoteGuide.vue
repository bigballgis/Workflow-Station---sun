<template>
  <GuideArticle test-id="record-note-guide-page" page-title-key="recordNoteGuide.title"
    intro-key="recordNoteGuide.intro" crumb-key="recordNoteGuide.crumb" flow-title-key="recordNoteGuide.flowTitle"
    :flow-keys="flow" :sections="sections" :related="related" />
</template>
<script setup lang="ts">
import GuideArticle, { type GuideSection } from '@/components/GuideArticle.vue'
const flow = ['recordNoteGuide.flow1', 'recordNoteGuide.flow2', 'recordNoteGuide.flow3']
const sections: GuideSection[] = [
  { anchor: 'scope', titleKey: 'recordNoteGuide.scopeTitle', bodyKey: 'recordNoteGuide.scopeBody' },
  { anchor: 'fields', titleKey: 'recordNoteGuide.fieldsTitle', samples: [
    ['Scope', 'scopeField'], ['Panel Title', 'titleField'], ['Allow Attachments', 'attachmentField'],
    ['Max File Size (MB)', 'sizeField'], ['Visible Notes', 'pageField'], ['Readonly', 'readonlyField'],
  ].map(([code, key]) => ({ code, hintKey: `recordNoteGuide.${key}` })) },
  { anchor: 'attachment-notifications', titleKey: 'recordNoteGuide.notifyTitle', bodyKey: 'recordNoteGuide.notifyBody',
    samples: [ { code: 'Attachment Notifications', hintKey: 'recordNoteGuide.enableField' },
      { code: 'Target Sub-Task nodes', hintKey: 'recordNoteGuide.targetsField' } ] },
  { anchor: 'recipients', titleKey: 'recordNoteGuide.recipientsTitle', bodyKey: 'recordNoteGuide.recipientsBody' },
  { anchor: 'open', titleKey: 'recordNoteGuide.openTitle', bodyKey: 'recordNoteGuide.openBody' },
  { anchor: 'waiting', titleKey: 'recordNoteGuide.waitingTitle', bodyKey: 'recordNoteGuide.waitingBody' },
]
const related = [ { to: '/table-bindings', titleKey: 'guides.tableBindings.title' },
  { to: '/up-tasks-to-claim', titleKey: 'upTasksToClaimGuide.pageTitle' } ]
</script>
