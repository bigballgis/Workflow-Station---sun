package com.admin.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin Center runs where Developer Workstation is not deployed: production has the {@code dw_*} tables, but nothing
 * writes them (WP12). Runtime code reads released data only — the catalog and its published content — so no SQL or
 * table name in the main sources may name a {@code dw_} table. Comments may still mention them.
 */
class RuntimeReadsNoDesignerTablesTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final Pattern DESIGNER_TABLE = Pattern.compile("\\bdw_[a-z_]+");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(?m)^\\s*//.*$|\\s//\\s.*$");

    @Test
    void mainSourcesNameNoDesignerTable() throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String code = LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(
                        Files.readString(file, StandardCharsets.UTF_8)).replaceAll(" ")).replaceAll("");
                Matcher matcher = DESIGNER_TABLE.matcher(code);
                while (matcher.find()) {
                    hits.add(MAIN_SOURCES.relativize(file) + ": " + matcher.group());
                }
            }
        }
        assertThat(hits).isEmpty();
    }
}
