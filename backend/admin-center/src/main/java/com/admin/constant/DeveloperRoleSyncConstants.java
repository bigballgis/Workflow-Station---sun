package com.admin.constant;

/**
 * Constants for syncing Developers virtual group membership to sys_user_roles.
 * Used when revoking the synced role on member removal.
 */
public final class DeveloperRoleSyncConstants {

    /** Developers virtual group id (sys_virtual_groups.id). Used for remove/sync id lookup. */
    public static final String DEVELOPERS_VIRTUAL_GROUP_ID = "vg-developers";

    /** Developers virtual group code (sys_virtual_groups.code). Use this to detect Developers group regardless of id. */
    public static final String DEVELOPERS_VIRTUAL_GROUP_CODE = "DEVELOPERS";

    /** Id prefix for sys_user_roles rows synced from Developers VG (id = prefix + userId). */
    public static final String SYNCED_DEVELOPER_ROLE_ID_PREFIX = "ur-vgdev-";

    /** Role id for DEVELOPER in sys_roles (used when syncing Developers VG to sys_user_roles). */
    public static final String DEVELOPER_ROLE_ID = "DEVELOPER_ROLE";

    private DeveloperRoleSyncConstants() {}
}
