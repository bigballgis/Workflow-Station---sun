package com.developer.enums;

/**
 * How a Function Unit may be started.
 *
 * <p>Declared explicitly by the designer rather than inferred from role grants:
 * "nobody has been granted a role on it" and "it is designed to be called by another
 * Function Unit" are different facts, and deriving the second from the first is the
 * kind of indirect-signal guess the config-over-heuristics rule forbids.
 */
public enum FunctionUnitStartupMode {

    /**
     * Only a user may start it, from the Portal catalog.
     * Default for every Function Unit, so existing units keep today's behaviour.
     */
    STANDALONE,

    /**
     * Only another Function Unit may start it via a BPMN {@code callActivity}.
     * Not offered as a standalone Portal entry.
     */
    CALLABLE,

    /** Either way. */
    BOTH;

    /** Whether another Function Unit's callActivity is allowed to target this unit. */
    public boolean allowsBeingCalled() {
        return this == CALLABLE || this == BOTH;
    }

    /** Whether a user may start this unit directly from the Portal catalog. */
    public boolean allowsUserInitiation() {
        return this == STANDALONE || this == BOTH;
    }
}
