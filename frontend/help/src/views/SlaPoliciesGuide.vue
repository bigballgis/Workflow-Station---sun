<template>
  <GuideArticle
    test-id="sla-policies-guide-page"
    page-title-key="slaPoliciesGuide.pageTitle"
    intro-key="slaPoliciesGuide.intro"
    crumb-key="slaPoliciesGuide.crumb"
    flow-title-key="slaPoliciesGuide.flowTitle"
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
  'slaPoliciesGuide.flow1',
  'slaPoliciesGuide.flow2',
  'slaPoliciesGuide.flow3',
  'slaPoliciesGuide.flow4',
]

const jumpLinks: GuideJump[] = [
  { anchor: 'list', titleKey: 'slaPoliciesGuide.whatTitle' },
  { anchor: 'edit', titleKey: 'slaPoliciesGuide.editTitle' },
  { anchor: 'recalculate', titleKey: 'slaPoliciesGuide.recalcTitle' },
  { anchor: 'details', titleKey: 'slaPoliciesGuide.detailTitle' },
  { anchor: 'permissions', titleKey: 'slaPoliciesGuide.permTitle' },
  { anchor: 'rules', titleKey: 'slaPoliciesGuide.ruleTitle' },
  { anchor: 'fail', titleKey: 'slaPoliciesGuide.failTitle' },
]

const related: GuideRelated[] = [
  { to: '/table-design#sla-due-date', titleKey: 'guides.tableDesign.title' },
  { to: '/environment-variables', titleKey: 'guides.environmentVariables.title' },
]

const catalog = { sampleLayout: 'block' as const }

const sections: GuideSection[] = [
  {
    anchor: 'list',
    titleKey: 'slaPoliciesGuide.whatTitle',
    bodyKey: 'slaPoliciesGuide.whatBody',
    bodyKeys: ['slaPoliciesGuide.listLead'],
    figure: { src: 'guides/ac-sla-policies-list.png', captionKey: 'slaPoliciesGuide.listFigure' },
    ...catalog,
    samples: [
      { code: 'Function Unit Code', hintKey: 'slaPoliciesGuide.fCode' },
      { code: 'Function Unit', hintKey: 'slaPoliciesGuide.fName' },
      { code: 'Lead Time (days)', hintKey: 'slaPoliciesGuide.fDays' },
      { code: 'Version', hintKey: 'slaPoliciesGuide.fVersion' },
      { code: 'Updated By', hintKey: 'slaPoliciesGuide.fUpdatedBy' },
      { code: 'Updated At', hintKey: 'slaPoliciesGuide.fUpdatedAt' },
      { code: 'Recalculation', hintKey: 'slaPoliciesGuide.fJob' },
      { code: 'Edit · Recalculate · Details · Export', hintKey: 'slaPoliciesGuide.fActions' },
    ],
  },
  {
    anchor: 'edit',
    titleKey: 'slaPoliciesGuide.editTitle',
    bodyKey: 'slaPoliciesGuide.editBody',
    bodyKeys: ['slaPoliciesGuide.editLead'],
    figure: { src: 'guides/ac-sla-policies-edit.png', captionKey: 'slaPoliciesGuide.editFigure' },
    ...catalog,
    samples: [
      { code: 'Function Unit', hintKey: 'slaPoliciesGuide.fEditFu' },
      { code: 'Current lead time', hintKey: 'slaPoliciesGuide.fEditCurrent' },
      { code: 'Lead Time (days)', hintKey: 'slaPoliciesGuide.fEditDays' },
      { code: 'Change reason', hintKey: 'slaPoliciesGuide.fEditReason' },
      { code: 'Save', hintKey: 'slaPoliciesGuide.fEditSave' },
    ],
  },
  {
    anchor: 'recalculate',
    titleKey: 'slaPoliciesGuide.recalcTitle',
    bodyKey: 'slaPoliciesGuide.recalcBody',
  },
  {
    anchor: 'details',
    titleKey: 'slaPoliciesGuide.detailTitle',
    bodyKey: 'slaPoliciesGuide.detailBody',
    bodyKeys: ['slaPoliciesGuide.detailLead'],
    figure: { src: 'guides/ac-sla-policies-detail.png', captionKey: 'slaPoliciesGuide.detailFigure' },
    ...catalog,
    samples: [
      { code: 'Cases', hintKey: 'slaPoliciesGuide.fJobCounts' },
      { code: 'Error', hintKey: 'slaPoliciesGuide.fJobError' },
      { code: 'Outcome', hintKey: 'slaPoliciesGuide.fItemOutcome' },
      { code: 'Old Due Date · New Due Date', hintKey: 'slaPoliciesGuide.fItemDates' },
      { code: 'Reason', hintKey: 'slaPoliciesGuide.fItemReason' },
      { code: 'Lead time', hintKey: 'slaPoliciesGuide.fHistChange' },
      { code: 'Recalculation start', hintKey: 'slaPoliciesGuide.fHistDispatch' },
    ],
  },
  {
    anchor: 'permissions',
    titleKey: 'slaPoliciesGuide.permTitle',
    bodyKey: 'slaPoliciesGuide.permBody',
  },
  {
    anchor: 'rules',
    titleKey: 'slaPoliciesGuide.ruleTitle',
    bodyKey: 'slaPoliciesGuide.ruleBody',
    ...catalog,
    samples: [
      { code: 'Not set', hintKey: 'slaPoliciesGuide.rule1' },
      { code: 'Skipped', hintKey: 'slaPoliciesGuide.rule2' },
      { code: 'Superseded', hintKey: 'slaPoliciesGuide.rule3' },
      { code: 'Failed', hintKey: 'slaPoliciesGuide.rule4' },
    ],
  },
  {
    anchor: 'fail',
    titleKey: 'slaPoliciesGuide.failTitle',
    bodyKey: 'slaPoliciesGuide.failBody',
    failKeys: [
      'slaPoliciesGuide.failNotListed',
      'slaPoliciesGuide.failInvalid',
      'slaPoliciesGuide.failNoPermission',
      'slaPoliciesGuide.failDispatch',
      'slaPoliciesGuide.failPartial',
    ],
  },
]
</script>
