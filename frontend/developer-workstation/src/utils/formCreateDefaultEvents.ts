/**
 * Default empty form-create event handlers (form-level options + per-component on/_hook).
 * Form-level: FnConfig expects [[FORM-CREATE-PREFIX-function onSubmit(formData, api){…}-FORM-CREATE-SUFFIX]].
 * Component-level: EventConfig body mode stores $FNX: + function body only.
 *
 * Barrel: implementation split into ./formCreateDefaultEvents/* by responsibility.
 * Public export names and the import path are preserved verbatim.
 */

export {
  FC_FN_PREFIX,
  FC_FN_SUFFIX,
  FC_COMPONENT_EVENT_PREFIX,
  FORM_LEVEL_EVENT_DEFS,
  COMPONENT_HOOK_NAMES,
  COMMON_COMPONENT_ON_EVENTS,
} from './formCreateDefaultEvents/constants'

export {
  emptyFormLevelEventFunction,
  emptyComponentEventFunction,
  extractFormCreateHandlerBody,
  normalizeEventEditorBody,
  isEmptyFormCreateHandler,
} from './formCreateDefaultEvents/handlerBody'

export {
  normalizeFormLevelEventHandler,
  ensureEmptyFormOptionsEvents,
  buildDefaultFormCreateOptions,
  serializeFormLevelEventHandlerForPersist,
  serializeFormCreateOptionsForPersist,
} from './formCreateDefaultEvents/formLevel'

export {
  mergeRuleOnHandlers,
  mergeRuleHookHandlers,
  flattenComponentEventsForPersist,
  serializeComponentEventHandlerForPersist,
  serializeComponentEventsForPersist,
  prepareFormCreateRulesForPersist,
  inflateComponentEventsForDesigner,
  isDesignerCanvasRule,
  getComponentEventNamesForRule,
  ensureEmptyRuleComponentEvents,
  walkRulesEnsureComponentEvents,
  buildDesignerUpdateDefaultRule,
} from './formCreateDefaultEvents/componentEvents'
