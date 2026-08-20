package com.platform.common.fk;

import com.platform.common.dto.PkGenerationConfig;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Custom PK template: {@code {DATETIME:pattern}}, {@code {SEQNUM:n}}, {@code {RANDSTRING:n}}.
 * {@code DATETIME} uses Asia/Shanghai (same zone as table audit fields).
 * {@code SEQNUM} may reset daily or monthly when {@code resetPeriod} is set.
 */
public final class CustomPkFormat {

    public static final String STRATEGY = "customFormat";
    /** Saved configs from the removed date-prefixed strategy are migrated at allocate/parse time. */
    public static final String LEGACY_DATE_PREFIXED_STRATEGY = "datePrefixedSequence";
    public static final String DEFAULT_FORMAT = "{DATETIME:yyyy-dd-MM}-{SEQNUM:4}";
    public static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd";
    public static final int DEFAULT_SEQ_PAD = 4;
    public static final int MIN_SEQ_WIDTH = 1;
    public static final int MAX_SEQ_WIDTH = 20;
    public static final int MIN_RAND_LENGTH = 1;
    public static final int MAX_RAND_LENGTH = 16;

    private static final char[] RAND_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    public sealed interface Segment permits Literal, Datetime, SeqNum, RandString {
    }

    public record Literal(String text) implements Segment {
    }

    public record Datetime(String pattern) implements Segment {
    }

    public record SeqNum(int width) implements Segment {
    }

    public record RandString(int length) implements Segment {
    }

    public record Parsed(List<Segment> segments, int seqWidth) {
    }

    private CustomPkFormat() {
    }

    public static Parsed parse(String format) {
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Custom PK format is required");
        }
        List<Segment> segments = new ArrayList<>();
        int seqWidth = 0;
        int i = 0;
        while (i < format.length()) {
            int open = format.indexOf('{', i);
            if (open < 0) {
                segments.add(new Literal(format.substring(i)));
                break;
            }
            if (open > i) {
                segments.add(new Literal(format.substring(i, open)));
            }
            int close = format.indexOf('}', open + 1);
            if (close < 0) {
                throw new IllegalArgumentException("Unclosed placeholder in custom PK format");
            }
            Segment segment = parsePlaceholder(format.substring(open + 1, close));
            if (segment instanceof SeqNum seqNum && seqWidth == 0) {
                seqWidth = seqNum.width();
            }
            segments.add(segment);
            i = close + 1;
        }
        if (seqWidth == 0) {
            throw new IllegalArgumentException("Custom PK format must include {SEQNUM:n}");
        }
        return new Parsed(List.copyOf(segments), seqWidth);
    }

    /**
     * Rewrites legacy {@code datePrefixedSequence} metadata into {@code customFormat}.
     * Other strategies are returned unchanged.
     */
    public static PkGenerationConfig normalizeConfig(PkGenerationConfig config) {
        if (config == null || !LEGACY_DATE_PREFIXED_STRATEGY.equals(config.getStrategy())) {
            return config;
        }
        int pad = config.getPadWidth() != null && config.getPadWidth() > 0
                ? config.getPadWidth() : DEFAULT_SEQ_PAD;
        String pattern = config.getDatePattern() == null || config.getDatePattern().isBlank()
                ? DEFAULT_DATE_PATTERN : config.getDatePattern();
        return PkGenerationConfig.builder()
                .strategy(STRATEGY)
                .format("{DATETIME:" + pattern + "}-{SEQNUM:" + pad + "}")
                .resetPeriod(config.getResetPeriod())
                .startValue(config.getStartValue())
                .build();
    }

    public static void validateReset(Parsed parsed, PkResetPeriod reset) {
        PkResetPeriod period = reset != null ? reset : PkResetPeriod.NONE;
        if (period == PkResetPeriod.DAY && !allowsDailyReset(parsed)) {
            throw new IllegalArgumentException(
                    "Daily reset requires a {DATETIME:...} pattern that includes a day token");
        }
        if (period == PkResetPeriod.MONTH && !allowsMonthlyReset(parsed)) {
            throw new IllegalArgumentException(
                    "Monthly reset requires a {DATETIME:...} pattern that includes a month token");
        }
    }

    public static boolean allowsDailyReset(Parsed parsed) {
        return datetimePatternSource(parsed).indexOf('d') >= 0;
    }

    public static boolean allowsMonthlyReset(Parsed parsed) {
        return datetimePatternSource(parsed).indexOf('M') >= 0;
    }

    private static String datetimePatternSource(Parsed parsed) {
        if (parsed == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Segment segment : parsed.segments()) {
            if (segment instanceof Datetime datetime) {
                out.append(datetime.pattern());
            }
        }
        return out.toString();
    }

    public static String render(Parsed parsed, Clock clock, long seq, Random random) {
        if (parsed == null) {
            throw new IllegalArgumentException("Custom PK format is required");
        }
        StringBuilder out = new StringBuilder();
        for (Segment segment : parsed.segments()) {
            out.append(renderSegment(segment, clock, seq, random));
        }
        return out.toString();
    }

    public static String render(String format, Clock clock, long seq, Random random) {
        return render(parse(format), clock, seq, random);
    }

    private static Segment parsePlaceholder(String body) {
        int colon = body.indexOf(':');
        String token = colon < 0 ? body.trim() : body.substring(0, colon).trim();
        String arg = colon < 0 ? "" : body.substring(colon + 1);
        return switch (token) {
            case "DATETIME" -> new Datetime(requireDatetimePattern(arg));
            case "SEQNUM" -> new SeqNum(requireWidth(arg, MIN_SEQ_WIDTH, MAX_SEQ_WIDTH, "SEQNUM"));
            case "RANDSTRING" -> new RandString(
                    requireWidth(arg, MIN_RAND_LENGTH, MAX_RAND_LENGTH, "RANDSTRING"));
            default -> throw new IllegalArgumentException("Unknown placeholder: " + token);
        };
    }

    private static String requireDatetimePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("DATETIME pattern is required");
        }
        for (int i = 0; i < pattern.length(); i++) {
            if (!isAllowedDatetimeChar(pattern.charAt(i))) {
                throw new IllegalArgumentException("Unsupported DATETIME pattern: " + pattern);
            }
        }
        DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
        return pattern;
    }

    private static boolean isAllowedDatetimeChar(char c) {
        return c == 'y' || c == 'M' || c == 'd' || c == 'H' || c == 'm' || c == 's'
                || c == '-' || c == '/' || c == ':' || c == ' ';
    }

    private static int requireWidth(String raw, int min, int max, String token) {
        int width;
        try {
            width = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(token + " width must be an integer");
        }
        if (width < min || width > max) {
            throw new IllegalArgumentException(token + " width must be between " + min + " and " + max);
        }
        return width;
    }

    private static String renderSegment(Segment segment, Clock clock, long seq, Random random) {
        if (segment instanceof Literal literal) {
            return literal.text();
        }
        if (segment instanceof Datetime datetime) {
            return formatDatetime(clock, datetime.pattern());
        }
        if (segment instanceof SeqNum seqNum) {
            return String.format(Locale.ROOT, "%0" + seqNum.width() + "d", seq);
        }
        if (segment instanceof RandString randString) {
            return randomString(randString.length(), random);
        }
        throw new IllegalArgumentException("Unknown custom PK segment");
    }

    private static String formatDatetime(Clock clock, String pattern) {
        Clock source = clock != null ? clock : CalendarDateSequence.systemClock();
        ZonedDateTime zoned = source.instant().atZone(CalendarDateSequence.ZONE);
        return zoned.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
    }

    private static String randomString(int length, Random random) {
        if (random == null) {
            throw new IllegalArgumentException("Random source is required for RANDSTRING");
        }
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(RAND_ALPHABET[random.nextInt(RAND_ALPHABET.length)]);
        }
        return out.toString();
    }
}
