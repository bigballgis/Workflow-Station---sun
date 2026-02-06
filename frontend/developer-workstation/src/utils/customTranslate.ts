/**
 * Custom translate module for BPMN.js
 * Provides English translations for all BPMN.js UI elements
 */

import translations from './bpmnTranslations'

export default function customTranslate(template: string, replacements?: Record<string, any>): string {
  // Get translation from our translations object
  let translatedTemplate = translations[template as keyof typeof translations] || template

  // Replace placeholders if any
  if (replacements) {
    Object.keys(replacements).forEach(key => {
      translatedTemplate = translatedTemplate.replace(new RegExp(`\\{${key}\\}`, 'g'), replacements[key])
    })
  }

  return translatedTemplate
}

// Export as a module that can be injected into BPMN.js
export const customTranslateModule = {
  translate: ['value', customTranslate]
}
