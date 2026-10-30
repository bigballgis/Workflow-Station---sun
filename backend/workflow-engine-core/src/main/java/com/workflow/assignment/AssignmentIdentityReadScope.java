package com.workflow.assignment;

import java.util.function.Supplier;

/** Transport freshness only. This local scope is never used as a cross-process authorization lock. */
public final class AssignmentIdentityReadScope {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();
    private AssignmentIdentityReadScope() { }

    public static <T> T fresh(Supplier<T> action) {
        Integer previous = DEPTH.get();
        DEPTH.set(previous == null ? 1 : previous + 1);
        try { return action.get(); }
        finally {
            if (previous == null) DEPTH.remove();
            else DEPTH.set(previous);
        }
    }

    public static boolean active() { return DEPTH.get() != null; }
}
