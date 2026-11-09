package com.admin.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard for WP8b-2: a claim pool follows the UBR holders only if every write to {@code sys_user_business_unit_roles}
 * reports its pools to {@link ClaimPoolResyncService}. A new write site fails here until it does and is listed.
 */
class UbrWriteSitesReportClaimPoolsTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final Pattern UBR_WRITE = Pattern.compile(
            "userBusinessUnitRoleRepository(?:\\.|::)(?:save\\w*|delete\\w*)\\b");
    private static final Pattern UBR_SQL_WRITE = Pattern.compile(
            "(?i)(?:insert\\s+into|delete\\s+from|update)\\s+sys_user_business_unit_roles\\b");
    private static final String REPORT_CALL = "claimPoolResyncService.ubrChanged(";

    /** Write call sites per file. The tier update in the component is a write that keeps the pool as it is. */
    private static final Map<String, Integer> KNOWN_WRITE_SITES = Map.of(
            "UserBusinessUnitRoleManagerComponent.java", 3,
            "MemberManagementService.java", 5);

    @Test
    void everyUbrWriteSiteIsKnownAndReportsItsPools() throws IOException {
        Map<String, Integer> writeSites = new TreeMap<>();
        Map<String, String> sources = new TreeMap<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                assertThat(UBR_SQL_WRITE.matcher(source).find())
                        .as("raw SQL write to sys_user_business_unit_roles in %s bypasses the claim-pool resync", file)
                        .isFalse();
                int count = count(UBR_WRITE.matcher(source));
                if (count > 0) {
                    writeSites.put(file.getFileName().toString(), count);
                    sources.put(file.getFileName().toString(), source);
                }
            }
        }

        assertThat(writeSites)
                .as("UBR write sites changed: make the new site call ClaimPoolResyncService.ubrChanged, then update the list")
                .isEqualTo(KNOWN_WRITE_SITES);
        sources.forEach((file, source) -> assertThat(source).as(file).contains(REPORT_CALL));
    }

    private static int count(Matcher matcher) {
        int n = 0;
        while (matcher.find()) {
            n++;
        }
        return n;
    }
}
