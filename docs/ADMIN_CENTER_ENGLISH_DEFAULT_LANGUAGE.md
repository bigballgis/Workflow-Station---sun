# Admin Center - Set English as Default Language

**Date**: 2026-02-05  
**Status**: ✅ Complete

## Change

Set English as the default language for the Admin Center frontend and removed language switching functionality.

## Rationale

The system needs to use English as the default language for all users, with no option for users to switch languages. This provides a consistent user experience across the platform.

## Changes Made

### 1. i18n Configuration (`frontend/admin-center/src/i18n/index.ts`)

Already configured to use English as default:
```typescript
const i18n = createI18n({
  legacy: false,
  locale: 'en', // Fixed to English
  fallbackLocale: 'en',
  messages: {
    'zh-CN': zhCN,
    'zh-TW': zhTW,
    'en': en
  }
})
```

### 2. App.vue Element Plus Locale

Updated the fallback locale to English:
```typescript
// Before
const locale = computed(() => localeMap[currentLocale.value] || zhCn)

// After
const locale = computed(() => localeMap[currentLocale.value] || en)
```

### 3. HTML Document (`frontend/admin-center/index.html`)

Updated the HTML lang attribute and title:
```html
<!-- Before -->
<html lang="zh-CN">
  <title>管理员中心</title>

<!-- After -->
<html lang="en">
  <title>Admin Center</title>
```

## Language Switching

No language switcher component exists in the application. The language is fixed to English and cannot be changed by users.

## Translation Coverage

The English translation file (`frontend/admin-center/src/i18n/locales/en.ts`) contains complete translations for:
- Common UI elements (buttons, labels, messages)
- Menu items
- Dashboard
- User management
- Organization management
- Role & permission management
- Virtual group management
- Function unit management
- Dictionary management
- Audit logs
- Permission requests
- Profile settings

## Deployment

### Build and Deploy Steps

1. **Build the frontend**:
```bash
cd frontend/admin-center
npx vite build --mode production
```

2. **Build Docker image**:
```bash
docker build -f Dockerfile.local -t dev-admin-center-frontend .
```

3. **Restart container**:
```bash
docker stop platform-admin-center-frontend-dev
docker-compose -f deploy/environments/dev/docker-compose.dev.yml up -d admin-center-frontend
```

## Testing

To verify the changes:
1. **Clear browser cache** (Ctrl+Shift+Delete or Cmd+Shift+Delete)
2. **Hard refresh** (Ctrl+Shift+R or Cmd+Shift+R)
3. Login to Admin Center at http://localhost:3000
4. ✅ Verify that all UI text is displayed in English
5. ✅ Verify that there is no language switcher in the interface
6. ✅ Check various pages (Dashboard, Users, Roles, Virtual Groups, etc.)

## Impact

- ✅ Consistent English interface for all users
- ✅ No language switching confusion
- ✅ Simplified user experience
- ✅ All translations are complete and ready to use

## Related Files

- `frontend/admin-center/src/i18n/index.ts` - i18n configuration
- `frontend/admin-center/src/i18n/locales/en.ts` - English translations
- `frontend/admin-center/src/App.vue` - Element Plus locale configuration
- `frontend/admin-center/index.html` - HTML document language

## Notes

- The Chinese and Traditional Chinese translation files are still present in the codebase but are not used
- If language switching is needed in the future, the infrastructure is already in place
- The i18n configuration uses Vue I18n v11+ Composition API mode
- Element Plus components are also configured to use English locale
