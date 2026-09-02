<template>
  <GuideArticle
    test-id="email-send-guide-page"
    page-title-key="emailSendGuide.pageTitle"
    intro-key="emailSendGuide.intro"
    crumb-key="emailSendGuide.crumb"
    flow-title-key="emailSendGuide.flowTitle"
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
  'emailSendGuide.flow1',
  'emailSendGuide.flow2',
  'emailSendGuide.flow3',
  'emailSendGuide.flow4',
]

const jumpLinks: GuideJump[] = [
  { anchor: 'connection', titleKey: 'emailSendGuide.connectionTitle' },
  { anchor: 'template', titleKey: 'emailSendGuide.templateTitle' },
  { anchor: 'send-task', titleKey: 'emailSendGuide.sendTaskTitle' },
  { anchor: 'extra', titleKey: 'emailSendGuide.extraTitle' },
  { anchor: 'fail', titleKey: 'emailSendGuide.failTitle' },
]

const related: GuideRelated[] = [
  { to: '/email-monitor', titleKey: 'guides.emailMonitor.title' },
  { to: '/computed-fields', titleKey: 'guides.computedFields.title' },
  { to: '/form-upload', titleKey: 'guides.formUpload.title' },
]

const catalog = { sampleLayout: 'block' as const }

const sections: GuideSection[] = [
  {
    anchor: 'connection',
    titleKey: 'emailSendGuide.connectionTitle',
    bodyKey: 'emailSendGuide.connectionBody',
    bodyKeys: ['emailSendGuide.connectionCatalogLead'],
    figure: { src: 'guides/dw-connections.png', captionKey: 'emailSendGuide.connectionFigure' },
    ...catalog,
    samples: [
      { code: 'Direction', hintKey: 'emailSendGuide.fDirection' },
      { code: 'Outbound (send)', hintKey: 'emailSendGuide.fDirectionOutbound' },
      { code: 'Inbound (monitor)', hintKey: 'emailSendGuide.fDirectionInbound' },
      { code: 'Sender Email (From address)', hintKey: 'emailSendGuide.fFromEmail' },
      { code: 'Sender Display Name', hintKey: 'emailSendGuide.fFromName' },
      { code: 'Username', hintKey: 'emailSendGuide.fSmtpUsername' },
      { code: 'Password', hintKey: 'emailSendGuide.fSmtpPassword' },
      { code: 'SMTP Host', hintKey: 'emailSendGuide.fSmtpHost' },
      { code: 'Port', hintKey: 'emailSendGuide.fSmtpPort' },
      { code: 'Use TLS', hintKey: 'emailSendGuide.fUseTls' },
      { code: 'Email Provider', hintKey: 'emailSendGuide.fProvider' },
      { code: 'Enabled', hintKey: 'emailSendGuide.fConnEnabled' },
      { code: 'Test', hintKey: 'emailSendGuide.fTest' },
      { code: 'Test Recipient', hintKey: 'emailSendGuide.fTestRecipient' },
      { code: 'Send Test Email', hintKey: 'emailSendGuide.fSendTest' },
    ],
  },
  {
    anchor: 'template',
    titleKey: 'emailSendGuide.templateTitle',
    bodyKey: 'emailSendGuide.templateBody',
    bodyKeys: ['emailSendGuide.templateCatalogLead'],
    figure: { src: 'guides/dw-email-templates.png', captionKey: 'emailSendGuide.templateFigure' },
    ...catalog,
    samples: [
      { code: 'Template Name', hintKey: 'emailSendGuide.fTemplateName' },
      { code: 'Subject', hintKey: 'emailSendGuide.fTemplateSubject' },
      { code: 'Insert Variable', hintKey: 'emailSendGuide.fInsertVariable' },
      { code: 'Body', hintKey: 'emailSendGuide.fTemplateBody' },
      { code: 'PR Approved Notice', hintKey: 'emailSendGuide.templateNameSample' },
    ],
  },
  {
    titleKey: 'emailSendGuide.bodyTitle',
    bodyKey: 'emailSendGuide.bodyBody',
    bodyKeys: ['emailSendGuide.bodyCatalogLead'],
    figure: { src: 'guides/dw-email-body.png', captionKey: 'emailSendGuide.bodyFigure' },
    ...catalog,
    samples: [
      { code: 'Visual', hintKey: 'emailSendGuide.fBodyVisual' },
      { code: 'HTML', hintKey: 'emailSendGuide.fBodyHtml' },
      { code: 'Email preview', hintKey: 'emailSendGuide.fEmailPreview' },
      { code: 'Enabled', hintKey: 'emailSendGuide.fTemplateEnabled' },
    ],
  },
  {
    anchor: 'send-task',
    titleKey: 'emailSendGuide.sendTaskTitle',
    bodyKey: 'emailSendGuide.sendTaskBody',
    bodyKeys: ['emailSendGuide.sendTaskCatalogLead'],
    figure: { src: 'guides/dw-send-task.png', captionKey: 'emailSendGuide.sendTaskFigure' },
    ...catalog,
    samples: [
      { code: 'To', hintKey: 'emailSendGuide.fTo' },
      { code: '{ }', hintKey: 'emailSendGuide.fInsertField' },
      { code: 'Search fields…', hintKey: 'emailSendGuide.fInsertSearch' },
      { code: 'Process variables', hintKey: 'emailSendGuide.fProcessVars' },
      { code: 'initiator', hintKey: 'emailSendGuide.fInitiator' },
      { code: 'Connection', hintKey: 'emailSendGuide.fSendConnection' },
      { code: 'From (override)', hintKey: 'emailSendGuide.fFromOverride' },
      { code: 'Template', hintKey: 'emailSendGuide.fSendTemplate' },
      { code: 'Task ID', hintKey: 'emailSendGuide.fTaskId' },
      { code: 'Type', hintKey: 'emailSendGuide.fTaskType' },
      { code: 'Task Name', hintKey: 'emailSendGuide.fTaskName' },
    ],
  },
  {
    anchor: 'extra',
    titleKey: 'emailSendGuide.extraTitle',
    bodyKey: 'emailSendGuide.extraBody',
    bodyKeys: ['emailSendGuide.extraCatalogLead'],
    ...catalog,
    samples: [
      { code: 'Show advanced options', hintKey: 'emailSendGuide.fShowAdvanced' },
      { code: 'Cc', hintKey: 'emailSendGuide.fCc' },
      { code: 'Bcc', hintKey: 'emailSendGuide.fBcc' },
      { code: 'Attachments', hintKey: 'emailSendGuide.fAttachments' },
      { code: 'Upload field', hintKey: 'emailSendGuide.fUploadField' },
      { code: 'Add attachment', hintKey: 'emailSendGuide.fAddAttachment' },
      { code: 'Remove', hintKey: 'emailSendGuide.fRemoveAttachment' },
      { code: 'Sensitivity', hintKey: 'emailSendGuide.fSensitivity' },
      { code: 'Reply-To', hintKey: 'emailSendGuide.fReplyTo' },
      { code: 'Importance', hintKey: 'emailSendGuide.fImportance' },
    ],
  },
  {
    anchor: 'fail',
    titleKey: 'emailSendGuide.failTitle',
    bodyKey: 'emailSendGuide.failBody',
    failKeys: [
      'emailSendGuide.failConnection',
      'emailSendGuide.failTo',
      'emailSendGuide.failTemplate',
      'emailSendGuide.failSubject',
      'emailSendGuide.failAttachment',
      'emailSendGuide.failSmtp',
      'emailSendGuide.failRunning',
    ],
  },
]
</script>
