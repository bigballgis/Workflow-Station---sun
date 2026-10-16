package com.platform.common.recordnote;

/** Shared Sub-Task Config interpretation; row values still decide between allowed assignment modes. */
public final class SubTaskAssignmentMode {
    private SubTaskAssignmentMode() {}
    public static boolean allowsRole(String mode) {
        return mode==null || "role".equalsIgnoreCase(mode.trim()) || "both".equalsIgnoreCase(mode.trim());
    }
}
