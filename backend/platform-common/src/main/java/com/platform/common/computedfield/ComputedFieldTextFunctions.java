package com.platform.common.computedfield;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.platform.common.computedfield.ComputedFieldArgs.checkArity;
import static com.platform.common.computedfield.ComputedFieldArgs.text;
import static com.platform.common.computedfield.ComputedFieldArgs.wholeNumber;

/**
 * Text functions — the Power Fx string set, mirroring {@code functionsText.ts}.
 *
 * <p>Index conventions follow Power Fx / Excel and NOT Java: positions are 1-based, so every
 * {@code substring} / {@code indexOf} here offsets by one. FIND returns blank rather than 0 or an
 * error when the needle is absent, so authors can test it with ISBLANK. The golden vectors pin the
 * boundary cases (empty needle, start beyond length, zero count).
 */
public final class ComputedFieldTextFunctions {

    private ComputedFieldTextFunctions() {
    }

    /**
     * Builds the text function table.
     *
     * @return function name (upper case) to implementation
     */
    public static Map<String, ComputedFieldFunction> create() {
        Map<String, ComputedFieldFunction> functions = new LinkedHashMap<>();
        functions.put("CONCAT", ComputedFieldTextFunctions::concat);
        functions.put("LEN", args -> simple("LEN", args, source -> length(source)));
        functions.put("TRIM", args -> simple("TRIM", args, source -> ComputedValue.of(source.trim())));
        functions.put("UPPER", args -> simple("UPPER", args, s -> ComputedValue.of(s.toUpperCase())));
        functions.put("LOWER", args -> simple("LOWER", args, s -> ComputedValue.of(s.toLowerCase())));
        functions.put("LEFT", ComputedFieldTextFunctions::left);
        functions.put("RIGHT", ComputedFieldTextFunctions::right);
        functions.put("MID", ComputedFieldTextFunctions::mid);
        functions.put("SUBSTITUTE", ComputedFieldTextFunctions::substitute);
        functions.put("FIND", ComputedFieldTextFunctions::find);
        functions.put("STARTSWITH", ComputedFieldTextFunctions::startsWith);
        functions.put("ENDSWITH", ComputedFieldTextFunctions::endsWith);
        functions.put("VALUE", ComputedFieldTextFunctions::value);
        functions.put("ISBLANK", ComputedFieldTextFunctions::isBlank);
        return functions;
    }

    private interface SingleArg {
        ComputedValue apply(String source);
    }

    private static EvalOutcome simple(String fn, List<ComputedValue> args, SingleArg apply) {
        EvalOutcome arityError = checkArity(fn, args, 1, 1);
        if (arityError != null) {
            return arityError;
        }
        return EvalOutcome.ok(apply.apply(text(args, 0)));
    }

    private static ComputedValue length(String source) {
        return ComputedValue.of(BigDecimal.valueOf(source.length()));
    }

    private static EvalOutcome concat(List<ComputedValue> args) {
        EvalOutcome arityError = checkArity("CONCAT", args, 1, ComputedFieldArgs.VARIADIC);
        if (arityError != null) {
            return arityError;
        }
        return EvalOutcome.ok(ComputedValue.of(
                args.stream().map(ComputedValues::toText).collect(Collectors.joining())));
    }

    private static EvalOutcome left(List<ComputedValue> args) {
        EvalOutcome arityError = checkArity("LEFT", args, 2, 2);
        if (arityError != null) {
            return arityError;
        }
        Object n = wholeNumber("LEFT", args, 1, 0);
        if (n instanceof EvalOutcome failure) {
            return failure;
        }
        String source = text(args, 0);
        int size = Math.min(Math.max((Integer) n, 0), source.length());
        return EvalOutcome.ok(ComputedValue.of(source.substring(0, size)));
    }

    private static EvalOutcome right(List<ComputedValue> args) {
        EvalOutcome arityError = checkArity("RIGHT", args, 2, 2);
        if (arityError != null) {
            return arityError;
        }
        Object n = wholeNumber("RIGHT", args, 1, 0);
        if (n instanceof EvalOutcome failure) {
            return failure;
        }
        String source = text(args, 0);
        int size = Math.min(Math.max((Integer) n, 0), source.length());
        return EvalOutcome.ok(ComputedValue.of(source.substring(source.length() - size)));
    }

    private static EvalOutcome mid(List<ComputedValue> args) {
        EvalOutcome arityError = checkArity("MID", args, 2, 3);
        if (arityError != null) {
            return arityError;
        }
        Object start = wholeNumber("MID", args, 1, 1);
        if (start instanceof EvalOutcome failure) {
            return failure;
        }
        String source = text(args, 0);
        int from = Math.min(Math.max((Integer) start - 1, 0), source.length());
        if (args.size() == 2) {
            return EvalOutcome.ok(ComputedValue.of(source.substring(from)));
        }
        Object length = wholeNumber("MID", args, 2, 0);
        if (length instanceof EvalOutcome failure) {
            return failure;
        }
        int size = Math.max((Integer) length, 0);
        int to = Math.min(from + size, source.length());
        return EvalOutcome.ok(ComputedValue.of(source.substring(from, to)));
    }

    private static EvalOutcome substitute(List<ComputedValue> args) {
        EvalOutcome arityError = checkArity("SUBSTITUTE", args, 3, 3);
        if (arityError != null) {
            return arityError;
        }
        String source = text(args, 0);
        String needle = text(args, 1);
        // Replacing "" would insert between every character; Power Fx returns the input unchanged.
        if (needle.isEmpty()) {
            return EvalOutcome.ok(ComputedValue.of(source));
        }
        return EvalOutcome.ok(ComputedValue.of(source.replace(needle, text(args, 2))));
    }

    private static EvalOutcome find(List<ComputedValue> args) {
        EvalOutcome arityError = checkArity("FIND", args, 2, 3);
        if (arityError != null) {
            return arityError;
        }
        String needle = text(args, 0);
        String haystack = text(args, 1);
        Object start = wholeNumber("FIND", args, 2, 1);
        if (start instanceof EvalOutcome failure) {
            return failure;
        }
        int from = Math.max((Integer) start - 1, 0);
        if (from > haystack.length()) {
            return EvalOutcome.ok(ComputedValue.BLANK);
        }
        int found = haystack.indexOf(needle, from);
        if (found < 0) {
            return EvalOutcome.ok(ComputedValue.BLANK);
        }
        return EvalOutcome.ok(ComputedValue.of(BigDecimal.valueOf(found + 1L)));
    }

    private static EvalOutcome startsWith(List<ComputedValue> args) {
        EvalOutcome arityError = checkArity("STARTSWITH", args, 2, 2);
        if (arityError != null) {
            return arityError;
        }
        return EvalOutcome.ok(ComputedValue.of(text(args, 0).startsWith(text(args, 1))));
    }

    private static EvalOutcome endsWith(List<ComputedValue> args) {
        EvalOutcome arityError = checkArity("ENDSWITH", args, 2, 2);
        if (arityError != null) {
            return arityError;
        }
        return EvalOutcome.ok(ComputedValue.of(text(args, 0).endsWith(text(args, 1))));
    }

    private static EvalOutcome value(List<ComputedValue> args) {
        EvalOutcome arityError = checkArity("VALUE", args, 1, 1);
        if (arityError != null) {
            return arityError;
        }
        ComputedValue input = args.get(0);
        if (input.isBlank()) {
            return EvalOutcome.ok(ComputedValue.BLANK);
        }
        if (input instanceof ComputedValue.Number) {
            return EvalOutcome.ok(input);
        }
        if (input instanceof ComputedValue.Bool) {
            return EvalOutcome.error(ComputedFieldErrorCode.TYPE_MISMATCH,
                    "VALUE cannot convert a boolean to a number");
        }
        String raw = ((ComputedValue.Text) input).value();
        BigDecimal parsed = ComputedFieldDecimals.parse(raw);
        if (parsed == null) {
            return EvalOutcome.error(ComputedFieldErrorCode.TYPE_MISMATCH,
                    "VALUE cannot parse \"" + raw + "\" as a number");
        }
        return EvalOutcome.ok(parsed);
    }

    private static EvalOutcome isBlank(List<ComputedValue> args) {
        EvalOutcome arityError = checkArity("ISBLANK", args, 1, 1);
        if (arityError != null) {
            return arityError;
        }
        return EvalOutcome.ok(ComputedValue.of(args.get(0).isBlank()));
    }
}
