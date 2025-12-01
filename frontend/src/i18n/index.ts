import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

// Import translations
import viCommon from './locales/vi/common.json'
import enCommon from './locales/en/common.json'

// Define resources
const resources = {
  vi: {
    common: viCommon,
  },
  en: {
    common: enCommon,
  },
}

i18n
  // Detect user language
  .use(LanguageDetector)
  // Pass the i18n instance to react-i18next
  .use(initReactI18next)
  // Init i18next
  .init({
    resources,
    fallbackLng: 'vi', // Default to Vietnamese
    defaultNS: 'common',
    ns: ['common'],

    detection: {
      // Order of language detection
      order: ['localStorage', 'navigator'],
      // Key to store language preference
      lookupLocalStorage: 'i18nextLng',
      // Cache user language selection
      caches: ['localStorage'],
    },

    interpolation: {
      escapeValue: false, // React already escapes values
    },

    react: {
      useSuspense: false, // Disable suspense for now
    },
  })

export default i18n

// Export supported languages for LanguageSwitcher
export const supportedLanguages = [
  { code: 'vi', name: 'Tiếng Việt', flag: '🇻🇳' },
  { code: 'en', name: 'English', flag: '🇺🇸' },
]
