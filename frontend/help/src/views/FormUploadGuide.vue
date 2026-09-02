<template>
  <GuideArticle
    test-id="form-upload-guide-page"
    page-title-key="formUploadGuide.pageTitle"
    intro-key="formUploadGuide.intro"
    crumb-key="formUploadGuide.crumb"
    flow-title-key="formUploadGuide.flowTitle"
    :flow-keys="flowKeys"
    :related="related"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideRelated, type GuideSection } from '@/components/GuideArticle.vue'

const flowKeys = [
  'formUploadGuide.flow1',
  'formUploadGuide.flow2',
  'formUploadGuide.flow3',
]

const related: GuideRelated[] = [
  { to: '/email-send#send-task', titleKey: 'guides.emailSend.title' },
]

const sections: GuideSection[] = [
  {
    anchor: 'max-files',
    titleKey: 'formUploadGuide.maxTitle',
    bodyKey: 'formUploadGuide.maxBody',
    samples: [{ code: 'Max files = 10', hintKey: 'formUploadGuide.maxSample' }],
  },
  {
    titleKey: 'formUploadGuide.runtimeTitle',
    bodyKey: 'formUploadGuide.runtimeBody',
    samples: [{ code: 'report.pdf +2', hintKey: 'formUploadGuide.runtimeSample' }],
  },
  {
    titleKey: 'formUploadGuide.failTitle',
    bodyKey: 'formUploadGuide.failBody',
  },
]
</script>
