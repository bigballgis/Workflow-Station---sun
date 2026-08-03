package com.admin.enums;

/**
 * Virtual group type.
 *
 * <ul>
 *   <li>{@link #SYSTEM}: built-in; cannot be deleted; create via seed only</li>
 *   <li>{@link #CUSTOM}: business / task-pool groups; user-creatable</li>
 *   <li>{@link #DEVELOPER}: Developer Workstation team groups; user-creatable</li>
 * </ul>
 *
 * All types may bind an AD group via {@code ad_group}.
 */
public enum VirtualGroupType {
    /** Built-in virtual group — not deletable */
    SYSTEM,
    /** Business / portal task-pool virtual group — deletable */
    CUSTOM,
    /** Developer Workstation team virtual group — deletable */
    DEVELOPER
}
