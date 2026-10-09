<template>
  <GuideArticle
    test-id="action-email-guide-page"
    page-title-key="actionEmailGuide.pageTitle"
    intro-key="actionEmailGuide.intro"
    crumb-key="actionEmailGuide.crumb"
    flow-title-key="actionEmailGuide.flowTitle"
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
  'actionEmailGuide.flow1',
  'actionEmailGuide.flow2',
  'actionEmailGuide.flow3',
  'actionEmailGuide.flow4',
  'actionEmailGuide.flow5',
]

const jumpLinks: GuideJump[] = [
  { anchor: 'fields', titleKey: 'actionEmailGuide.fieldsTitle' },
  { anchor: 'types', titleKey: 'actionEmailGuide.typesTitle' },
  { anchor: 'fail', titleKey: 'actionEmailGuide.failTitle' },
]

const related: GuideRelated[] = [
  { to: '/email-send#connection', titleKey: 'guides.emailSend.title' },
  { to: '/email-send#template', titleKey: 'nav.emailTemplates' },
  { to: '/email-send#send-task', titleKey: 'actionEmailGuide.relatedSendTask' },
]

const catalog = { sampleLayout: 'block' as const }

const sections: GuideSection[] = [
  {
    titleKey: 'actionEmailGuide.openTitle',
    bodyKey: 'actionEmailGuide.openBody',
    figure: { src: 'guides/dw-action-email.png', captionKey: 'actionEmailGuide.openFigure' },
    samples: [
      { code: 'Action Design', hintKey: 'actionEmailGuide.openTab' },
      { code: 'Edit', hintKey: 'actionEmailGuide.openEdit' },
    ],
  },
  {
    anchor: 'fields',
    titleKey: 'actionEmailGuide.fieldsTitle',
    bodyKey: 'actionEmailGuide.fieldsBody',
    bodyKeys: ['actionEmailGuide.fieldsCatalogLead'],
    ...catalog,
    samples: [
      { code: 'Send email after this Action', hintKey: 'actionEmailGuide.fTitle' },
      { code: 'Send email after success', hintKey: 'actionEmailGuide.fEnabled' },
      { code: 'To', hintKey: 'actionEmailGuide.fTo' },
      { code: '{ }', hintKey: 'actionEmailGuide.fInsertField' },
      { code: 'Search fields…', hintKey: 'actionEmailGuide.fInsertSearch' },
      { code: 'initiator', hintKey: 'actionEmailGuide.fInitiator' },
      { code: 'Email connection', hintKey: 'actionEmailGuide.fConnection' },
      { code: 'Email template', hintKey: 'actionEmailGuide.fTemplate' },
      { code: 'Show advanced', hintKey: 'actionEmailGuide.fShowAdvanced' },
      { code: 'Hide advanced', hintKey: 'actionEmailGuide.fHideAdvanced' },
      { code: 'From', hintKey: 'actionEmailGuide.fFrom' },
      { code: 'Cc', hintKey: 'actionEmailGuide.fCc' },
      { code: 'Bcc', hintKey: 'actionEmailGuide.fBcc' },
      { code: 'Attachments', hintKey: 'actionEmailGuide.fAttachments' },
    ],
  },
  {
    anchor: 'types',
    titleKey: 'actionEmailGuide.typesTitle',
    bodyKey: 'actionEmailGuide.typesBody',
    bodyKeys: ['actionEmailGuide.typesCatalogLead'],
    ...catalog,
    samples: [
      {
        code: 'Approve, Reject, Transfer, Delegate, Urge, Rollback, Withdraw, Draft, Save, Process Submit, Process Reject, Form Popup',
        hintKey: 'actionEmailGuide.fSupportedTypes',
      },
      { code: 'API Call', hintKey: 'actionEmailGuide.fApiCall' },
      { code: 'Custom Script', hintKey: 'actionEmailGuide.fCustomScript' },
      { code: 'Composite Action', hintKey: 'actionEmailGuide.fComposite' },
    ],
  },
  {
    anchor: 'fail',
    titleKey: 'actionEmailGuide.failTitle',
    bodyKey: 'actionEmailGuide.failBody',
    failKeys: [
      'actionEmailGuide.failIncomplete',
      'actionEmailGuide.failUnsupported',
      'actionEmailGuide.failConnection',
      'actionEmailGuide.failTemplate',
      'actionEmailGuide.failUnbound',
      'actionEmailGuide.failAutosave',
    ],
  },
]
</script>
