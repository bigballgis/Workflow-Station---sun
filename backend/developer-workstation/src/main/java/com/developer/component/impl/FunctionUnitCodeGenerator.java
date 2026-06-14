package com.developer.component.impl;

import com.developer.repository.FunctionUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 功能单元编码生成协作类。
 * 负责把功能单元名称规范化为 BPMN/数据库安全的 code 前缀，并生成全局唯一 code。
 */
@Component
@RequiredArgsConstructor
class FunctionUnitCodeGenerator {

    private final FunctionUnitRepository functionUnitRepository;

    /**
     * Generate a unique function unit code
     * Format: {functionUnitName}-{yyyyMMdd}-{random6chars}
     *
     * Note: prefix is sanitized to [a-z0-9-] only; empty or digit-leading prefixes are avoided for BPMN compatibility.
     */
    String generateUniqueCode(String functionUnitName) {
        String prefix = normalizeCodePrefix(functionUnitName);
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        SecureRandom random = new SecureRandom();
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";

        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder randomPart = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                randomPart.append(chars.charAt(random.nextInt(chars.length())));
            }
            String code = prefix + "-" + datePart + "-" + randomPart;
            if (!functionUnitRepository.existsByCode(code)) {
                return code;
            }
        }
        // Use timestamp as last resort
        return prefix + "-" + datePart + "-" + (System.currentTimeMillis() % 1000000);
    }

    /**
     * Normalize FunctionUnit name into a prefix usable as code/processId.
     *
     * Flowable/BPMN constraints (XML Name / xsd:ID safe subset):
     * - First character must be [a-z_] (no leading digit)
     * - Subsequent characters only [a-z0-9_.-]
     * - Used for `<bpmn:process id="...">` and dw_function_units.code (length=50); truncated so total length stays within 50.
     */
    String normalizeCodePrefix(String name) {
        // Reserve space for "-yyyyMMdd-random6" => 1 + 8 + 1 + 6 = 16 chars
        // total length limit is 50 => prefix max length is 34
        final int maxPrefixLen = 34;

        if (name == null) {
            return "fu";
        }
        String raw = name.trim();
        if (raw.isEmpty()) {
            return "fu";
        }

        StringBuilder out = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            char mapped;
            if (Character.isLetterOrDigit(c)) {
                mapped = Character.toLowerCase(c);
            } else if (c == '_' || c == '-' || c == '.') {
                mapped = c;
            } else if (Character.isWhitespace(c)) {
                mapped = '-';
            } else {
                continue;
            }
            // collapse repeating separators
            if ((mapped == '-' || mapped == '_' || mapped == '.') && mapped == prev) {
                continue;
            }
            out.append(mapped);
            prev = mapped;
            if (out.length() >= maxPrefixLen + 8) {
                // avoid excessive work on very long names; we'll truncate later anyway
                break;
            }
        }

        String s = out.toString();
        // trim separators on both ends
        s = s.replaceAll("^[-_.]+", "").replaceAll("[-_.]+$", "");
        if (s.isEmpty()) {
            return "fu";
        }

        // XML Name: first char must be letter or '_' (we restrict to [a-z_])
        char first = s.charAt(0);
        boolean firstOk = (first >= 'a' && first <= 'z') || first == '_';
        if (!firstOk) {
            s = "fu-" + s;
        }
        // avoid leading separators after prefixing
        s = s.replaceAll("^[-_.]+", "");
        if (s.isEmpty()) {
            return "fu";
        }

        // keep within prefix budget (leave room for date+random suffix)
        if (s.length() > maxPrefixLen) {
            s = s.substring(0, maxPrefixLen);
            s = s.replaceAll("[-_.]+$", "");
            if (s.isEmpty()) {
                return "fu";
            }
        }

        // extra safety: if starts with "xml" (case-insensitive) it may confuse tooling; prefix it
        if (s.length() >= 3 && s.regionMatches(true, 0, "xml", 0, 3)) {
            s = "fu-" + s;
            if (s.length() > maxPrefixLen) {
                s = s.substring(0, maxPrefixLen).replaceAll("[-_.]+$", "");
                if (s.isEmpty()) {
                    return "fu";
                }
            }
        }

        return s;
    }
}
