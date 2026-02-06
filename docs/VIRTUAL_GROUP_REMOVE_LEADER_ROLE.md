# Virtual Group - Remove Leader Role

**Date**: 2026-02-05  
**Status**: ✅ Complete

## Change

Removed the "Leader" role option from virtual group member management. Virtual groups now only support "Role Members" (MEMBER role).

## Rationale

Virtual groups are designed to be flat organizational structures without hierarchical leadership. The Leader role was unnecessary and added complexity to the member management interface.

## Changes Made

### Frontend Changes

**File**: `frontend/admin-center/src/views/virtual-group/components/VirtualGroupMembersDialog.vue`

1. **Member List Display** - Simplified role display to always show "Role Members":
```vue
<!-- Before -->
<el-tag :type="row.role === 'LEADER' ? 'warning' : 'info'" size="small">
  {{ row.role === 'LEADER' ? t('organization.leader') : t('role.members') }}
</el-tag>

<!-- After -->
<el-tag type="info" size="small">
  {{ t('role.members') }}
</el-tag>
```

2. **Add Member Dialog** - Removed Leader option and disabled role selection:
```vue
<!-- Before -->
<el-select v-model="newMember.role" style="width: 100%">
  <el-option :label="t('organization.leader')" value="LEADER" />
  <el-option :label="t('role.members')" value="MEMBER" />
</el-select>

<!-- After -->
<el-select v-model="newMember.role" style="width: 100%" disabled>
  <el-option :label="t('role.members')" value="MEMBER" />
</el-select>
```

3. **TypeScript Type** - Simplified role type:
```typescript
// Before
const newMember = reactive({ userId: '', role: 'MEMBER' as 'LEADER' | 'MEMBER' })

// After
const newMember = reactive({ userId: '', role: 'MEMBER' as const })
```

## Impact

- ✅ Simplified user interface - no more confusing role selection
- ✅ All virtual group members are now equal (no leaders)
- ✅ Cleaner, more intuitive member management
- ✅ Reduced complexity in virtual group logic

## Backend Compatibility

The backend still supports both LEADER and MEMBER roles in the database for backward compatibility. However, the frontend now only allows adding members with the MEMBER role.

If needed, existing LEADER roles in the database will still be displayed correctly, but new members can only be added as MEMBER.

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

### Browser Cache Issue Fix

**Problem**: Browsers aggressively cache JavaScript files, causing users to see old code even after deployment.

**Solution**: Updated `nginx.conf` to prevent caching of `index.html` while still caching hashed asset files:

```nginx
# Never cache index.html to ensure users get latest version
location = /index.html {
    add_header Cache-Control "no-cache, no-store, must-revalidate";
    add_header Pragma "no-cache";
    add_header Expires "0";
}

# Cache static assets with hash in filename (Vite generates these)
location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}
```

This ensures:
- `index.html` is never cached (always fetched fresh)
- Hashed asset files (e.g., `index-DgPCEYmJ.js`) are cached for 1 year
- When code changes, Vite generates new hashes, forcing browser to fetch new files

## Testing

To verify the changes:
1. **Clear browser cache** (Ctrl+Shift+Delete or Cmd+Shift+Delete)
2. **Hard refresh** (Ctrl+Shift+R or Cmd+Shift+R)
3. Login to Admin Center at http://localhost:3000
4. Navigate to Virtual Group management
5. Click "Manage Members" for any virtual group
6. Click "Add Member"
7. ✅ Verify that the role dropdown only shows "Role Members" and is disabled
8. ✅ Verify that existing members all show "Role Members" tag

**If you still see the old interface**:
- Open browser DevTools (F12)
- Go to Network tab
- Hard refresh (Ctrl+Shift+R)
- Verify that `index.html` returns `200` (not `304 Not Modified`)
- Check that the JavaScript file has the new hash (e.g., `index-DgPCEYmJ.js`)

## Related Files

- `frontend/admin-center/src/views/virtual-group/components/VirtualGroupMembersDialog.vue` - Member management dialog
- `frontend/admin-center/nginx.conf` - Nginx configuration with cache headers
- `frontend/admin-center/src/i18n/index.ts` - i18n configuration (English as default)
- `frontend/admin-center/index.html` - HTML document (English lang attribute)

## Notes

- The backend API still accepts both LEADER and MEMBER roles for backward compatibility
- Database schema remains unchanged (still has role column)
- This is a UI-only change that simplifies the user experience
- **Cache Fix**: Updated nginx configuration to prevent caching of `index.html`, ensuring users always get the latest version after deployment
- **Language**: System default language is set to English with no user-switchable language options
