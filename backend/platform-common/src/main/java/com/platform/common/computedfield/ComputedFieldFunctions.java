package com.platform.common.computedfield;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The computed-field function whitelist — the single authority for "does this function exist".
 *
 * <p>Both the design-time validator and the runtime interpreter read this table, so a function
 * cannot be accepted by one and rejected by the other. The TypeScript engine exposes the same set
 * through {@code knownFunctionNames()}; {@code ComputedFieldFunctionParityTest} asserts the two
 * lists match so a function added on one side cannot quietly go missing on the other.
 */
public final class ComputedFieldFunctions {

    /**
     * Functions the interpreter handles itself because they must NOT evaluate every argument.
     *
     * <p>Short-circuiting is a correctness requirement, not an optimization: without it the
     * standard divide guard {@code IF(qty = 0, 0, total / qty)} would still raise
     * DIVISION_BY_ZERO on the untaken branch, making the guard unwritable.
     */
    public static final Set<String> LAZY_FUNCTIONS =
            Set.of("IF", "AND", "OR", "SWITCH", "COALESCE");

    private static final Map<String, ComputedFieldFunction> EAGER_FUNCTIONS = buildEager();

    private ComputedFieldFunctions() {
    }

    private static Map<String, ComputedFieldFunction> buildEager() {
        Map<String, ComputedFieldFunction> functions = new LinkedHashMap<>();
        functions.putAll(ComputedFieldMathFunctions.create());
        functions.putAll(ComputedFieldTextFunctions.create());
        functions.putAll(ComputedFieldDateFunctions.create());
        functions.put("NOT", ComputedFieldFunctions::not);
        return Collections.unmodifiableMap(functions);
    }

    private static EvalOutcome not(List<ComputedValue> args) {
        EvalOutcome arityError = ComputedFieldArgs.checkArity("NOT", args, 1, 1);
        if (arityError != null) {
            return arityError;
        }
        Object flag = ComputedValues.toBoolean(args.get(0), "NOT");
        if (flag instanceof EvalOutcome failure) {
            return failure;
        }
        return EvalOutcome.ok(ComputedValue.of(!(Boolean) flag));
    }

    /**
     * Looks up an eager function.
     *
     * @param name upper-case function name
     * @return the implementation, or null when the name is lazy or unknown
     */
    public static ComputedFieldFunction eager(String name) {
        return EAGER_FUNCTIONS.get(name);
    }

    /**
     * Whether a name is a supported function, lazy or eager.
     *
     * @param name upper-case function name
     * @return true when the function exists
     */
    public static boolean isKnown(String name) {
        return LAZY_FUNCTIONS.contains(name) || EAGER_FUNCTIONS.containsKey(name);
    }

    /**
     * Every supported function name, sorted — used by the cross-language parity test and by the
     * designer's autocomplete payload.
     *
     * @return sorted function names
     */
    public static Set<String> allNames() {
        Set<String> names = new TreeSet<>(EAGER_FUNCTIONS.keySet());
        names.addAll(LAZY_FUNCTIONS);
        return Collections.unmodifiableSet(names);
    }
}
