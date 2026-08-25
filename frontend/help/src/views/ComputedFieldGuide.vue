<template>
  <GuideArticle
    test-id="computed-field-guide-page"
    page-title-key="computedFieldGuide.pageTitle"
    intro-key="computedFieldGuide.intro"
    crumb-key="computedFieldGuide.crumb"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideSection } from '@/components/GuideArticle.vue'

const sections: GuideSection[] = [
  {
    titleKey: 'computedFieldGuide.basicsTitle',
    bodyKey: 'computedFieldGuide.basicsBody',
    figure: { src: 'guides/dw-table-design.png', captionKey: 'computedFieldGuide.basicsFigure' },
  },
  {
    titleKey: 'computedFieldGuide.thisRowTitle',
    bodyKey: 'computedFieldGuide.thisRowBody',
    samples: [{ code: 'price * quantity', hintKey: 'computedFieldGuide.thisRowSample' }],
  },
  {
    titleKey: 'computedFieldGuide.relatedRowsTitle',
    bodyKey: 'computedFieldGuide.relatedRowsBody',
    samples: [
      { code: 'SUM(date_info.day)', hintKey: 'computedFieldGuide.sampleSum' },
      { code: 'AVG(date_info.day)', hintKey: 'computedFieldGuide.sampleAvg' },
      { code: 'MIN(date_info.day) / MAX(date_info.day)', hintKey: 'computedFieldGuide.sampleMinMax' },
      { code: 'COUNT(date_info)', hintKey: 'computedFieldGuide.sampleCountRows' },
      { code: 'COUNT(date_info.day)', hintKey: 'computedFieldGuide.sampleCountCells' },
    ],
  },
  {
    titleKey: 'computedFieldGuide.parentTitle',
    bodyKey: 'computedFieldGuide.parentBody',
    samples: [{ code: 'leave_request.name', hintKey: 'computedFieldGuide.parentSample' }],
  },
  {
    titleKey: 'computedFieldGuide.datesTitle',
    bodyKey: 'computedFieldGuide.datesBody',
    samples: [
      { code: 'enddate - startdate', hintKey: 'computedFieldGuide.dateMinus' },
      { code: 'DATEDIFF(startdate, enddate)', hintKey: 'computedFieldGuide.dateDatediff' },
    ],
  },
  {
    titleKey: 'computedFieldGuide.logicTitle',
    bodyKey: 'computedFieldGuide.logicBody',
    samples: [
      { code: 'IF(condition, then, else?)', hintKey: 'computedFieldGuide.fnIf' },
      { code: 'AND(...) / OR(...)', hintKey: 'computedFieldGuide.fnAndOr' },
      { code: 'SWITCH(value, match1, result1, …, default?)', hintKey: 'computedFieldGuide.fnSwitch' },
      { code: 'COALESCE(...)', hintKey: 'computedFieldGuide.fnCoalesce' },
    ],
  },
  { titleKey: 'computedFieldGuide.mathTitle', bodyKey: 'computedFieldGuide.mathBody' },
  { titleKey: 'computedFieldGuide.textTitle', bodyKey: 'computedFieldGuide.textBody' },
  { titleKey: 'computedFieldGuide.errorsTitle', bodyKey: 'computedFieldGuide.errorsBody' },
  {
    titleKey: 'computedFieldGuide.relationTitle',
    bodyKey: 'computedFieldGuide.relationBody',
    anchor: 'relation',
  },
]
</script>
