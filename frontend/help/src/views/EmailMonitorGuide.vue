<template>
  <GuideArticle
    test-id="email-monitor-guide-page"
    page-title-key="emailMonitorGuide.pageTitle"
    intro-key="emailMonitorGuide.intro"
    crumb-key="emailMonitorGuide.crumb"
    flow-title-key="emailMonitorGuide.flowTitle"
    :flow-keys="flowKeys"
    :related="related"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideRelated, type GuideSection } from '@/components/GuideArticle.vue'

const flowKeys = [
  'emailMonitorGuide.flow1',
  'emailMonitorGuide.flow2',
  'emailMonitorGuide.flow3',
  'emailMonitorGuide.flow4',
  'emailMonitorGuide.flow5',
]

const related: GuideRelated[] = [
  { to: '/email-send#connection', titleKey: 'guides.emailSend.title' },
  { to: '/computed-fields', titleKey: 'guides.computedFields.title' },
]

const sections: GuideSection[] = [
  {
    titleKey: 'emailMonitorGuide.inboundTitle',
    bodyKey: 'emailMonitorGuide.inboundBody',
    figure: { src: 'guides/dw-connections-inbound.png', captionKey: 'emailMonitorGuide.inboundFigure' },
    samples: [{ code: 'Inbound (monitor)', hintKey: 'emailMonitorGuide.inboundSample' }],
  },
  {
    titleKey: 'emailMonitorGuide.templateTitle',
    bodyKey: 'emailMonitorGuide.templateBody',
    figure: { src: 'guides/dw-email-monitors.png', captionKey: 'emailMonitorGuide.templateFigure' },
    samples: [
      { code: 'Vendor quote to PR', hintKey: 'emailMonitorGuide.templateNameSample' },
      { code: 'INBOX', hintKey: 'emailMonitorGuide.templateSample' },
    ],
  },
  {
    titleKey: 'emailMonitorGuide.extractTitle',
    bodyKey: 'emailMonitorGuide.extractBody',
    samples: [{ code: 'request_title', hintKey: 'emailMonitorGuide.extractSample' }],
  },
  {
    titleKey: 'emailMonitorGuide.startEventTitle',
    bodyKey: 'emailMonitorGuide.startEventBody',
    figure: { src: 'guides/dw-start-event.png', captionKey: 'emailMonitorGuide.startEventFigure' },
    samples: [{ code: 'Quote', hintKey: 'emailMonitorGuide.subjectFilterSample' }],
  },
  { titleKey: 'emailMonitorGuide.deployTitle', bodyKey: 'emailMonitorGuide.deployBody' },
  { titleKey: 'emailMonitorGuide.deleteTitle', bodyKey: 'emailMonitorGuide.deleteBody' },
]
</script>
