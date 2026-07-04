package com.platform.common.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Sanitizers for values that flow from request parameters into internal service URLs.
 *
 * <p>Internal HTTP clients build targets as {@code fixedBaseUrl + "/path/" + id}. Even though the
 * base URL/host/scheme come from trusted configuration, appending an unvalidated id lets a caller
 * inject additional path segments, query separators, or an absolute/authority component
 * (e.g. {@code ../}, {@code //evil.host}, {@code %2F}, {@code ?}, {@code #}) — the SSRF / URL
 * manipulation risk. These helpers must be applied <strong>inline, immediately before</strong> the
 * value is concatenated into a URL, and the returned value used, so that the taint is broken:</p>
 *
 * <ul>
 *   <li>{@link #requirePathToken(String)} — allowlist-validate identifiers/codes used as path
 *       segments; rejects any URL-significant character. Use for ids, codes, keys.</li>
 *   <li>{@link #encodeQueryValue(String)} — percent-encode free-text used as a query parameter
 *       value (names, keywords, reasons).</li>
 * </ul>
 */
public final class SafeUrlInput {

    /**
     * Characters legitimately present in this platform's identifiers: UUIDs ({@code -}),
     * relation/table codes ({@code _}), Flowable definition ids ({@code :}), dotted usernames
     * ({@code .}), email-like principals ({@code @}). Deliberately excludes {@code / \ ? # % & = }
     * whitespace, control chars and non-ASCII so no path/authority/query injection is possible.
     */
    private static final Pattern PATH_TOKEN = Pattern.compile("[A-Za-z0-9._:@-]{1,256}");

    private SafeUrlInput() {
    }

    /**
     * Validates an identifier destined for a URL path segment.
     *
     * @return the same value, unchanged, when it is a safe token
     * @throws IllegalArgumentException if {@code value} is null, blank, over 256 chars, or contains
     *         any URL-significant character
     */
    public static String requirePathToken(String value) {
        if (value == null || !PATH_TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid identifier for URL path");
        }
        return value;
    }

    /**
     * Percent-encodes a free-text value for safe use as a URL query parameter value.
     *
     * @return the encoded value ({@code ""} when {@code value} is null)
     */
    public static String encodeQueryValue(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
