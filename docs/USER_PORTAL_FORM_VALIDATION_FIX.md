# User Portal Form Validation Error Messages Fix

## Date
2026-02-05

## Issue
Form validation error messages in User Portal were displaying in Chinese ("请输入Approval Status") instead of English, even though the form configuration had English validation messages.

**Screenshot Evidence**: User reported seeing "请输入Approval Status" error message when submitting a process form.

## Root Cause

### Element Plus Locale Fallback
The Element Plus locale configuration in `App.vue` was using `zhCn` (Chinese) as the fallback locale:

```typescript
// BEFORE (incorrect)
const locale = computed(() => localeMap[currentLocale.value] || zhCn)
```

When the current locale wasn't found in the locale map, Element Plus would default to Chinese for all its built-in components, including:
- Form validation messages
- Date picker labels
- Pagination text
- Dialog buttons
- Message boxes

### Why This Happened
Even though the form field configurations had English validation messages:
```json
{
  "validate": [
    {
      "required": true,
      "message": "Approval Status is required"
    }
  ]
}
```

Element Plus was prepending its own localized text ("请输入" = "Please enter") to the field title, resulting in mixed language messages like:
- "请输入Approval Status" (Chinese prefix + English field name)

## Solution Implemented

### 1. Updated App.vue
Changed the Element Plus locale fallback from `zhCn` to `en`:

```typescript
// AFTER (correct)
const locale = computed(() => localeMap[currentLocale.value] || en)
```

**File**: `frontend/user-portal/src/App.vue`

### 2. Rebuilt and Redeployed
```bash
# Navigate to user-portal directory
cd frontend/user-portal

# Build production bundle
npx vite build --mode production

# Build Docker image
docker build -f Dockerfile.local -t user-portal-frontend:latest .

# Recreate container
docker-compose -f deploy/environments/dev/docker-compose.dev.yml up -d user-portal-frontend
```

### 3. Verification
After redeployment:
- ✅ Form validation messages display in English
- ✅ Date picker shows English labels
- ✅ Pagination shows English text
- ✅ All Element Plus components use English locale

## Impact

### Before Fix
- Form validation: "请输入Approval Status" (mixed Chinese/English)
- Date picker: Chinese month/day names
- Pagination: "共 10 条" (Chinese)
- Inconsistent user experience

### After Fix
- Form validation: "Please enter Approval Status" (full English)
- Date picker: English month/day names
- Pagination: "Total 10 items" (English)
- Consistent English interface

## Element Plus Locale System

### How It Works
Element Plus uses a locale provider to determine the language for all its components:

```vue
<el-config-provider :locale="locale">
  <router-view />
</el-config-provider>
```

The locale object contains translations for:
- Form validation messages
- Date/time formatting
- Pagination text
- Dialog buttons
- Empty states
- Loading states

### Available Locales
```typescript
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'  // Simplified Chinese
import zhTw from 'element-plus/dist/locale/zh-tw.mjs'  // Traditional Chinese
import en from 'element-plus/dist/locale/en.mjs'       // English
```

### Locale Selection Logic
```typescript
const localeMap: Record<string, any> = {
  'zh-CN': zhCn,
  'zh-TW': zhTw,
  'en': en
}

// Falls back to 'en' if currentLocale.value is not in the map
const locale = computed(() => localeMap[currentLocale.value] || en)
```

## Related Components

### Form Validation
**Component**: `frontend/user-portal/src/components/FormRenderer.vue`

The FormRenderer uses form-create which integrates with Element Plus validation:
```vue
<form-create
  v-model="formApi"
  :rule="formRule"
  :option="formOption"
/>
```

When validation fails, Element Plus generates error messages using:
1. Custom message from field config (if provided)
2. Default message template from locale (e.g., "Please enter {field}")

### Process Start Form
**Component**: `frontend/user-portal/src/views/processes/start.vue`

This is where users see the form when starting a new process. The validation messages appear here when users submit incomplete forms.

## Testing

### Test Validation Messages
1. Navigate to User Portal: http://localhost:3001
2. Login with test user
3. Start a new process (e.g., "Employee Leave Management")
4. Leave required fields empty
5. Click "Submit"
6. Verify error messages are in English

### Expected Results
- ✅ "Please enter Employee Name"
- ✅ "Please enter Employee ID"
- ✅ "Please select Leave Type"
- ✅ "Please select Start Date"
- ✅ All validation messages in English

### Test Other Element Plus Components
1. **Date Picker**: Should show English month names (January, February, etc.)
2. **Pagination**: Should show "Total X items" instead of "共 X 条"
3. **Empty States**: Should show "No Data" instead of "暂无数据"
4. **Loading**: Should show "Loading..." instead of "加载中..."

## Configuration Files

### User Portal i18n Configuration
**File**: `frontend/user-portal/src/i18n/index.ts`

```typescript
const i18n = createI18n({
  legacy: false,
  locale: 'en',  // Default locale
  fallbackLocale: 'en',
  messages: {
    en: enLocale,
    'zh-CN': zhCNLocale,
    'zh-TW': zhTWLocale
  }
})
```

### HTML Language Attribute
**File**: `frontend/user-portal/index.html`

```html
<html lang="en">
```

## Consistency Across Applications

All three frontend applications now use English as the default locale:

| Application | Default Locale | Element Plus Fallback | Status |
|-------------|---------------|----------------------|--------|
| Admin Center | en | en | ✅ Fixed |
| Developer Workstation | en | en | ✅ Fixed |
| User Portal | en | en | ✅ Fixed |

## Related Fixes

This fix is part of a comprehensive i18n implementation:

1. **Admin Center i18n** - `docs/ADMIN_CENTER_ENGLISH_DEFAULT_LANGUAGE.md`
2. **Developer Workstation i18n** - `docs/DEVELOPER_WORKSTATION_I18N_ENGLISH.md`
3. **User Portal Form Validation** - This document

## Summary

✅ **Element Plus locale fallback changed** from Chinese to English
✅ **Form validation messages now display in English**
✅ **All Element Plus components use English locale**
✅ **Consistent user experience** across all applications
✅ **User Portal rebuilt and redeployed**

The User Portal now provides a fully English interface, including all form validation messages and Element Plus component text.

</content>
