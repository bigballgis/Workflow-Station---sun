<template>
  <GuideArticle
    test-id="email-monitor-guide-page"
    page-title-key="emailMonitorGuide.pageTitle"
    intro-key="emailMonitorGuide.intro"
    crumb-key="emailMonitorGuide.crumb"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideSection } from '@/components/GuideArticle.vue'

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
    samples: [{ code: 'INBOX', hintKey: 'emailMonitorGuide.templateSample' }],
  },
  { titleKey: 'emailMonitorGuide.extractTitle', bodyKey: 'emailMonitorGuide.extractBody' },
  {
    titleKey: 'emailMonitorGuide.startEventTitle',
    bodyKey: 'emailMonitorGuide.startEventBody',
    figure: { src: 'guides/dw-start-event.png', captionKey: 'emailMonitorGuide.startEventFigure' },
  },
  { titleKey: 'emailMonitorGuide.deployTitle', bodyKey: 'emailMonitorGuide.deployBody' },
  { titleKey: 'emailMonitorGuide.deleteTitle', bodyKey: 'emailMonitorGuide.deleteBody' },
]
</script>
