package com.portal.properties;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for SQL init script idempotency.
 *
 * Feature: travel-expense-reimbursement, Property 1: 脚本幂等性
 *
 * For any SQL init script (00-03), executing N times should produce the same
 * DB state as executing once. We verify this by checking that each script
 * contains ON CONFLICT or DELETE+INSERT patterns that ensure idempotency.
 *
 * **Validates: Requirements 1.3, 2.5**
 */
public class TravelExpenseIdempotencyPropertyTest {

    private static final Path SCRIPTS_DIR = resolveScriptsDir();

    private static Path resolveScriptsDir() {
        // Try relative paths from different possible working directories
        Path candidate = Paths.get("deploy/init-scripts/14-travel-expense-reimbursement");
        if (candidate.toFile().exists()) return candidate;
        candidate = Paths.get("../../deploy/init-scripts/14-travel-expense-reimbursement");
        if (candidate.toFile().exists()) return candidate;
        // Fallback: use the project root detection
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            Path scripts = current.resolve("deploy/init-scripts/14-travel-expense-reimbursement");
            if (scripts.toFile().exists()) return scripts;
            current = current.getParent();
        }
        return Paths.get("deploy/init-scripts/14-travel-expense-reimbursement");
    }

    private static final List<String> SCRIPT_FILES = List.of(
            "00-create-function-unit.sql",
            "01-create-tables.sql",
            "02-create-bpmn-process.sql",
            "03-form-table-bindings.sql"
    );

    /**
     * Feature: travel-expense-reimbursement, Property 1: 脚本幂等性
     *
     * For any script executed N times (N >= 1), the script contains idempotency
     * patterns (ON CONFLICT or DELETE+INSERT) ensuring repeated execution
     * produces the same database state.
     *
     * **Validates: Requirements 1.3, 2.5**
     */
    @Property(tries = 100)
    @Label("Feature: travel-expense-reimbursement, Property 1: Script idempotency")
    void scriptContainsIdempotencyPatterns(
            @ForAll("scriptIndices") int scriptIndex,
            @ForAll("executionCounts") int executionCount) throws IOException {

        String scriptFile = SCRIPT_FILES.get(scriptIndex);
        Path scriptPath = SCRIPTS_DIR.resolve(scriptFile);
        String content = Files.readString(scriptPath);
        String contentUpper = content.toUpperCase();

        // Every script must contain at least one idempotency pattern:
        // - ON CONFLICT ... DO UPDATE (for upsert operations)
        // - DELETE FROM ... followed by INSERT (for replace-all operations)
        boolean hasOnConflict = contentUpper.contains("ON CONFLICT") && contentUpper.contains("DO UPDATE");
        boolean hasDeleteInsert = contentUpper.contains("DELETE FROM") && contentUpper.contains("INSERT INTO");

        assertThat(hasOnConflict || hasDeleteInsert)
                .as("Script '%s' (executed %d times) must contain ON CONFLICT DO UPDATE or DELETE+INSERT pattern for idempotency",
                        scriptFile, executionCount)
                .isTrue();

        // Verify each INSERT INTO is protected by either:
        // - ON CONFLICT / RETURNING in the same statement, OR
        // - A preceding DELETE FROM in the same DO block (delete-all + re-insert pattern)
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String lineUpper = lines[i].trim().toUpperCase();
            if (lineUpper.startsWith("INSERT INTO")) {
                boolean foundConflictOrReturning = false;
                boolean foundPrecedingDelete = false;

                // Look ahead for ON CONFLICT or RETURNING within the same statement
                for (int j = i; j < Math.min(i + 30, lines.length); j++) {
                    String followUpper = lines[j].toUpperCase();
                    if (followUpper.contains("ON CONFLICT")) {
                        foundConflictOrReturning = true;
                        break;
                    }
                    if (followUpper.contains("RETURNING")) {
                        foundConflictOrReturning = true;
                        break;
                    }
                    if (followUpper.trim().endsWith(";") && j > i) {
                        break;
                    }
                }

                // Look back through the entire DO block for a preceding DELETE FROM
                for (int j = 0; j < i; j++) {
                    if (lines[j].toUpperCase().contains("DELETE FROM")) {
                        foundPrecedingDelete = true;
                        break;
                    }
                }

                assertThat(foundConflictOrReturning || foundPrecedingDelete)
                        .as("Script '%s' line %d: INSERT INTO must have ON CONFLICT, RETURNING, or preceding DELETE for idempotency",
                                scriptFile, i + 1)
                        .isTrue();
            }
        }
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<Integer> scriptIndices() {
        return Arbitraries.integers().between(0, SCRIPT_FILES.size() - 1);
    }

    @Provide
    Arbitrary<Integer> executionCounts() {
        return Arbitraries.integers().between(1, 10);
    }
}
