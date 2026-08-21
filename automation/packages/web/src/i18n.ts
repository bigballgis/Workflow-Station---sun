import { LocalesEnum } from '@activepieces/core-utils';
import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import Backend from 'i18next-http-backend';
import ICU from 'i18next-icu';
import { initReactI18next } from 'react-i18next';

// HERMES L1: the lib-mode embed bundle is served from the host's own origin under
// an arbitrary base (e.g. /dev/service-task-builder/), so the default root-absolute
// /locales/... would hit the host app and 404, leaving raw keys on screen. The
// locales are copied next to the bundle, so resolve them relative to this module.
const localesBase = import.meta.env.AP_EMBED_BUILD
  ? new URL('.', import.meta.url).href
  : '/';

i18n
  .use(ICU)
  .use(Backend)
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    fallbackLng: 'en',
    debug: false,
    interpolation: {
      escapeValue: false, // not needed for react as it escapes by default
    },
    supportedLngs: Object.values(LocalesEnum),
    keySeparator: false,
    nsSeparator: false,
    returnEmptyString: false,
    backend: {
      loadPath: `${localesBase}locales/{{lng}}/{{ns}}.json`,
    },
  });
export default i18n;
