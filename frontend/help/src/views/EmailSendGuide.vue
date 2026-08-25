<template>
  <GuideArticle
    test-id="email-send-guide-page"
    page-title-key="emailSendGuide.pageTitle"
    intro-key="emailSendGuide.intro"
    crumb-key="emailSendGuide.crumb"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideSection } from '@/components/GuideArticle.vue'

const sections: GuideSection[] = [
  {
    anchor: 'connection',
    titleKey: 'emailSendGuide.connectionTitle',
    bodyKey: 'emailSendGuide.connectionBody',
    figure: { src: 'guides/dw-connections.png', captionKey: 'emailSendGuide.connectionFigure' },
    samples: [{ code: 'Outbound (send)', hintKey: 'emailSendGuide.connectionSample' }],
  },
  {
    anchor: 'template',
    titleKey: 'emailSendGuide.templateTitle',
    bodyKey: 'emailSendGuide.templateBody',
    figure: { src: 'guides/dw-email-templates.png', captionKey: 'emailSendGuide.templateFigure' },
    samples: [{ code: 'Insert Variable', hintKey: 'emailSendGuide.templateSample' }],
  },
  {
    titleKey: 'emailSendGuide.bodyTitle',
    bodyKey: 'emailSendGuide.bodyBody',
    figure: { src: 'guides/dw-email-body.png', captionKey: 'emailSendGuide.bodyFigure' },
  },
  {
    anchor: 'send-task',
    titleKey: 'emailSendGuide.sendTaskTitle',
    bodyKey: 'emailSendGuide.sendTaskBody',
    figure: { src: 'guides/dw-send-task.png', captionKey: 'emailSendGuide.sendTaskFigure' },
    samples: [{ code: 'assigneeEmail', hintKey: 'emailSendGuide.sendTaskSample' }],
  },
  { titleKey: 'emailSendGuide.extraTitle', bodyKey: 'emailSendGuide.extraBody' },
  { titleKey: 'emailSendGuide.failTitle', bodyKey: 'emailSendGuide.failBody' },
]
</script>
