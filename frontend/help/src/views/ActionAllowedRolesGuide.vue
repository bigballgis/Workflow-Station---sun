<template>
  <GuideArticle
    test-id="action-allowed-roles-guide-page"
    page-title-key="actionAllowedRolesGuide.pageTitle"
    intro-key="actionAllowedRolesGuide.intro"
    crumb-key="actionAllowedRolesGuide.crumb"
    flow-title-key="actionAllowedRolesGuide.flowTitle"
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
  'actionAllowedRolesGuide.flow1',
  'actionAllowedRolesGuide.flow2',
  'actionAllowedRolesGuide.flow3',
]

const jumpLinks: GuideJump[] = [
  { anchor: 'fields', titleKey: 'actionAllowedRolesGuide.fieldsTitle' },
  { anchor: 'portal', titleKey: 'actionAllowedRolesGuide.portalTitle' },
  { anchor: 'fail', titleKey: 'actionAllowedRolesGuide.failTitle' },
]

const related: GuideRelated[] = [
  { to: '/action-email', titleKey: 'guides.actionEmail.title' },
]

const sections: GuideSection[] = [
  {
    titleKey: 'actionAllowedRolesGuide.openTitle',
    bodyKey: 'actionAllowedRolesGuide.openBody',
    figure: {
      src: 'guides/dw-action-allowed-roles.png',
      captionKey: 'actionAllowedRolesGuide.openFigure',
    },
    samples: [
      { code: 'Action Design', hintKey: 'actionAllowedRolesGuide.openTab' },
      { code: 'Allowed Roles', hintKey: 'actionAllowedRolesGuide.openField' },
    ],
  },
  {
    anchor: 'fields',
    titleKey: 'actionAllowedRolesGuide.fieldsTitle',
    bodyKey: 'actionAllowedRolesGuide.fieldsBody',
    samples: [
      { code: 'Allowed Roles', hintKey: 'actionAllowedRolesGuide.fRoles' },
      { code: 'Select roles', hintKey: 'actionAllowedRolesGuide.fPlaceholder' },
    ],
  },
  {
    anchor: 'portal',
    titleKey: 'actionAllowedRolesGuide.portalTitle',
    bodyKey: 'actionAllowedRolesGuide.portalBody',
  },
  {
    anchor: 'fail',
    titleKey: 'actionAllowedRolesGuide.failTitle',
    bodyKey: 'actionAllowedRolesGuide.failBody',
    failKeys: [
      'actionAllowedRolesGuide.failEmpty',
      'actionAllowedRolesGuide.failMissing',
      'actionAllowedRolesGuide.failAdmin',
      'actionAllowedRolesGuide.failApi',
    ],
  },
]
</script>
